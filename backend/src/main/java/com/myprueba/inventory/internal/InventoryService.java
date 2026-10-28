package com.myprueba.inventory.internal;

import com.myprueba.catalog.ProductCatalog;
import com.myprueba.catalog.ProductView;
import com.myprueba.inventory.internal.InventoryRepository.Stock;
import com.myprueba.ledger.SaleItemsChanged;
import com.myprueba.shared.ConflictException;
import com.myprueba.shared.Money;
import com.myprueba.shared.NotFoundException;
import com.myprueba.shared.Quantity;
import com.myprueba.shared.Text;
import com.myprueba.shared.Violations;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class InventoryService {

    static final int MOVEMENT_LIMIT = 100;
    private static final int MAX_NOTE = 500;

    private final InventoryRepository inventory;
    private final ProductCatalog catalog;
    private final Clock clock;

    InventoryService(InventoryRepository inventory, ProductCatalog catalog, Clock clock) {
        this.inventory = inventory;
        this.catalog = catalog;
        this.clock = clock;
    }

    InventoryResponse overview(UUID businessId, boolean lowOnly) {
        Map<UUID, Stock> stocks = inventory.findAll(businessId);
        List<ProductView> products = catalog.list(businessId);
        List<InventoryItem> items = products.stream()
                .map(product -> toItem(product, stocks.get(product.id())))
                .toList();
        int tracked = (int) items.stream().filter(InventoryItem::tracked).count();
        int low = (int) items.stream().filter(InventoryItem::low).count();
        long value = 0;
        for (ProductView product : products) {
            Stock stock = stocks.get(product.id());
            if (stock != null && stock.tracked() && stock.quantityMilli() > 0 && product.costCents() != null) {
                value = Money.add(value, Money.lineTotalCents(stock.quantityMilli(), product.costCents()));
            }
        }
        List<InventoryItem> shown =
                lowOnly ? items.stream().filter(InventoryItem::low).toList() : items;
        return new InventoryResponse(new InventorySummary(tracked, low, Money.format(value)), shown);
    }

    @Transactional
    InventoryItem configure(UUID businessId, UUID productId, TrackingRequest request) {
        ProductView product = catalog.find(businessId, productId).orElseThrow(NotFoundException::new);
        Stock current = inventory.find(businessId, productId).orElse(null);
        boolean wasTracked = current != null && current.tracked();
        Instant now = clock.instant();
        if (request.tracked() == null || !request.tracked()) {
            if (current != null) {
                inventory.save(
                        businessId, new Stock(productId, false, current.quantityMilli(), current.minMilli()), now);
            }
            return toItem(product, inventory.find(businessId, productId).orElse(null));
        }
        Violations violations = new Violations();
        Long initial = wasTracked
                ? Long.valueOf(current.quantityMilli())
                : violations.capture(() -> Quantity.parseNonNegativeMilli("initialStock", request.initialStock()));
        Long min = request.minStock() == null || request.minStock().isBlank()
                ? null
                : violations.capture(() -> Quantity.parseNonNegativeMilli("minStock", request.minStock()));
        violations.throwIfAny();
        inventory.save(businessId, new Stock(productId, true, initial, min), now);
        if (!wasTracked) {
            long previous = current == null ? 0 : current.quantityMilli();
            inventory.addMovement(
                    businessId, productId, MovementType.INITIAL, initial - previous, initial, null, null, now);
        }
        return toItem(product, inventory.find(businessId, productId).orElseThrow());
    }

    @Transactional
    InventoryItem adjust(UUID businessId, UUID productId, AdjustmentRequest request) {
        ProductView product = catalog.find(businessId, productId).orElseThrow(NotFoundException::new);
        Violations violations = new Violations();
        AdjustmentType type = violations.capture(() -> Text.requiredEnum("type", request.type(), AdjustmentType.class));
        Long quantity = violations.capture(() -> type == AdjustmentType.COUNT
                ? Quantity.parseNonNegativeMilli("quantity", request.quantity())
                : Quantity.parseMilli("quantity", request.quantity()));
        String note = violations.capture(() -> Text.optional("note", request.note(), MAX_NOTE));
        violations.throwIfAny();
        Stock stock = inventory
                .find(businessId, productId)
                .filter(Stock::tracked)
                .orElseThrow(() -> new ConflictException("not_tracked"));
        long after = switch (type) {
            case PURCHASE -> stock.quantityMilli() + quantity;
            case LOSS -> stock.quantityMilli() - quantity;
            case COUNT -> quantity;
        };
        Instant now = clock.instant();
        inventory.save(businessId, new Stock(productId, true, after, stock.minMilli()), now);
        inventory.addMovement(
                businessId,
                productId,
                MovementType.valueOf(type.name()),
                after - stock.quantityMilli(),
                after,
                note,
                null,
                now);
        return toItem(product, inventory.find(businessId, productId).orElseThrow());
    }

    List<StockMovement> movements(UUID businessId, UUID productId) {
        catalog.find(businessId, productId).orElseThrow(NotFoundException::new);
        return inventory.movements(businessId, productId, MOVEMENT_LIMIT);
    }

    /** Runs synchronously inside the sale's transaction: a failed stock update fails the sale. */
    @EventListener
    void on(SaleItemsChanged event) {
        Set<UUID> products = new HashSet<>(event.before().keySet());
        products.addAll(event.after().keySet());
        MovementType type = switch (event.kind()) {
            case RECORDED -> MovementType.SALE;
            case EDITED -> MovementType.SALE_EDIT;
            case DELETED -> MovementType.SALE_DELETE;
        };
        Instant now = clock.instant();
        for (UUID productId : products) {
            long sold =
                    event.after().getOrDefault(productId, 0L) - event.before().getOrDefault(productId, 0L);
            if (sold == 0) {
                continue;
            }
            inventory.find(event.businessId(), productId).filter(Stock::tracked).ifPresent(stock -> {
                long after = stock.quantityMilli() - sold;
                inventory.save(event.businessId(), new Stock(productId, true, after, stock.minMilli()), now);
                inventory.addMovement(event.businessId(), productId, type, -sold, after, null, event.saleId(), now);
            });
        }
    }

    private static InventoryItem toItem(ProductView product, Stock stock) {
        boolean tracked = stock != null && stock.tracked();
        boolean low = tracked
                && (stock.quantityMilli() < 0
                        || (stock.minMilli() != null && stock.quantityMilli() <= stock.minMilli()));
        return new InventoryItem(
                product.id(),
                product.name(),
                Money.format(product.priceCents()),
                product.costCents() == null ? null : Money.format(product.costCents()),
                tracked,
                tracked ? Quantity.format(stock.quantityMilli()) : null,
                tracked && stock.minMilli() != null ? Quantity.format(stock.minMilli()) : null,
                low);
    }
}
