package com.myprueba.inventory.internal;

import com.myprueba.accounts.BusinessPolicies;
import com.myprueba.catalog.ProductCatalog;
import com.myprueba.catalog.ProductCreated;
import com.myprueba.catalog.ProductView;
import com.myprueba.inventory.internal.InventoryRepository.Stock;
import com.myprueba.ledger.SaleItemsChanged;
import com.myprueba.shared.ApiException;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every product has stock. Sales move it through ledger events; owners adjust it by hand. */
@Service
class InventoryService {

    static final int MOVEMENT_LIMIT = 100;
    private static final int MAX_NOTE = 500;

    private final InventoryRepository inventory;
    private final ProductCatalog catalog;
    private final BusinessPolicies policies;
    private final Clock clock;

    InventoryService(InventoryRepository inventory, ProductCatalog catalog, BusinessPolicies policies, Clock clock) {
        this.inventory = inventory;
        this.catalog = catalog;
        this.policies = policies;
        this.clock = clock;
    }

    InventoryResponse overview(UUID businessId, boolean lowOnly) {
        Map<UUID, Stock> stocks = inventory.findAll(businessId);
        List<ProductView> products = catalog.list(businessId);
        List<InventoryItem> items = products.stream()
                .map(product -> toItem(product, stockOf(stocks, product.id())))
                .toList();
        int low = (int) items.stream().filter(InventoryItem::low).count();
        long value = 0;
        for (ProductView product : products) {
            Stock stock = stockOf(stocks, product.id());
            if (stock.quantityMilli() > 0 && product.costCents() != null) {
                value = Money.add(value, Money.lineTotalCents(stock.quantityMilli(), product.costCents()));
            }
        }
        List<InventoryItem> shown =
                lowOnly ? items.stream().filter(InventoryItem::low).toList() : items;
        return new InventoryResponse(new InventorySummary(items.size(), low, Money.format(value)), shown);
    }

    @Transactional
    InventoryItem configure(UUID businessId, UUID productId, StockSettingsRequest request) {
        ProductView product = catalog.find(businessId, productId).orElseThrow(NotFoundException::new);
        Long min = request.minStock() == null || request.minStock().isBlank()
                ? null
                : Quantity.parseNonNegativeMilli("minStock", request.minStock());
        Stock stock = current(businessId, productId);
        inventory.save(businessId, new Stock(productId, true, stock.quantityMilli(), min), clock.instant());
        return toItem(product, current(businessId, productId));
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
        Stock stock = current(businessId, productId);
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
        return toItem(product, current(businessId, productId));
    }

    List<StockMovement> movements(UUID businessId, UUID productId) {
        catalog.find(businessId, productId).orElseThrow(NotFoundException::new);
        return inventory.movements(businessId, productId, MOVEMENT_LIMIT);
    }

    /** A new product starts with the stock given at creation, recorded as its first movement. */
    @EventListener
    void on(ProductCreated event) {
        Instant now = clock.instant();
        inventory.save(
                event.businessId(),
                new Stock(event.productId(), true, event.initialStockMilli(), event.minStockMilli()),
                now);
        inventory.addMovement(
                event.businessId(),
                event.productId(),
                MovementType.INITIAL,
                event.initialStockMilli(),
                event.initialStockMilli(),
                null,
                null,
                now);
    }

    /**
     * Runs synchronously inside the sale's transaction: a failed stock update fails the sale. When the
     * business does not allow negative stock, selling more than available is refused.
     */
    @EventListener
    void on(SaleItemsChanged event) {
        Set<UUID> products = new HashSet<>(event.before().keySet());
        products.addAll(event.after().keySet());
        MovementType type = switch (event.kind()) {
            case RECORDED -> MovementType.SALE;
            case EDITED -> MovementType.SALE_EDIT;
            case DELETED -> MovementType.SALE_DELETE;
        };
        boolean allowNegative = policies.allowsNegativeStock(event.businessId());
        Instant now = clock.instant();
        for (UUID productId : products) {
            long sold =
                    event.after().getOrDefault(productId, 0L) - event.before().getOrDefault(productId, 0L);
            if (sold == 0) {
                continue;
            }
            Stock stock = current(event.businessId(), productId);
            long after = stock.quantityMilli() - sold;
            if (after < 0 && sold > 0 && !allowNegative) {
                String name = catalog.find(event.businessId(), productId)
                        .map(ProductView::name)
                        .orElse("");
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "insufficient_stock",
                        Map.of("productName", name, "available", Quantity.format(stock.quantityMilli())));
            }
            inventory.save(event.businessId(), new Stock(productId, true, after, stock.minMilli()), now);
            inventory.addMovement(event.businessId(), productId, type, -sold, after, null, event.saleId(), now);
        }
    }

    /** Products created before every product had stock may lack a row: they start at zero. */
    private Stock current(UUID businessId, UUID productId) {
        return inventory.find(businessId, productId).orElse(new Stock(productId, true, 0, null));
    }

    private static Stock stockOf(Map<UUID, Stock> stocks, UUID productId) {
        return stocks.getOrDefault(productId, new Stock(productId, true, 0, null));
    }

    private static InventoryItem toItem(ProductView product, Stock stock) {
        boolean low =
                stock.quantityMilli() < 0 || (stock.minMilli() != null && stock.quantityMilli() <= stock.minMilli());
        return new InventoryItem(
                product.id(),
                product.name(),
                Money.format(product.priceCents()),
                product.costCents() == null ? null : Money.format(product.costCents()),
                Quantity.format(stock.quantityMilli()),
                stock.minMilli() == null ? null : Quantity.format(stock.minMilli()),
                low);
    }
}
