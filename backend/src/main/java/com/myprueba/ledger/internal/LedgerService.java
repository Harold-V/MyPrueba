package com.myprueba.ledger.internal;

import com.myprueba.catalog.ProductCatalog;
import com.myprueba.catalog.ProductView;
import com.myprueba.ledger.SaleItemsChanged;
import com.myprueba.shared.Money;
import com.myprueba.shared.NotFoundException;
import com.myprueba.shared.Quantity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class LedgerService {

    private final LedgerRepository ledger;
    private final ProductCatalog catalog;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    LedgerService(LedgerRepository ledger, ProductCatalog catalog, Clock clock, ApplicationEventPublisher events) {
        this.ledger = ledger;
        this.catalog = catalog;
        this.clock = clock;
        this.events = events;
    }

    // --- sales -------------------------------------------------------------------------------

    @Transactional
    SaleResponse createSale(UUID businessId, ZoneId zone, SaleRequest request) {
        Instant now = clock.instant();
        SaleData sale = resolve(businessId, LedgerInput.sale(request, zone, now));
        UUID id = UUID.randomUUID();
        ledger.insertSale(businessId, id, sale, now);
        publish(businessId, id, SaleItemsChanged.Kind.RECORDED, List.of(), sale.lines());
        return toResponse(id, sale);
    }

    @Transactional
    SaleResponse updateSale(UUID businessId, ZoneId zone, UUID id, SaleRequest request) {
        Instant now = clock.instant();
        SaleData previous = ledger.findSale(businessId, id).orElseThrow(NotFoundException::new);
        SaleData sale = resolve(businessId, LedgerInput.sale(request, zone, now));
        ledger.updateSale(businessId, id, sale, now);
        publish(businessId, id, SaleItemsChanged.Kind.EDITED, previous.lines(), sale.lines());
        return toResponse(id, sale);
    }

    SaleResponse getSale(UUID businessId, UUID id) {
        return toResponse(id, ledger.findSale(businessId, id).orElseThrow(NotFoundException::new));
    }

    @Transactional
    void deleteSale(UUID businessId, UUID id) {
        SaleData previous = ledger.findSale(businessId, id).orElseThrow(NotFoundException::new);
        ledger.deleteSale(businessId, id);
        publish(businessId, id, SaleItemsChanged.Kind.DELETED, previous.lines(), List.of());
    }

    private void publish(
            UUID businessId, UUID saleId, SaleItemsChanged.Kind kind, List<SaleLine> before, List<SaleLine> after) {
        Map<UUID, Long> previous = quantities(before);
        Map<UUID, Long> current = quantities(after);
        if (!previous.isEmpty() || !current.isEmpty()) {
            events.publishEvent(new SaleItemsChanged(businessId, saleId, kind, previous, current));
        }
    }

    private static Map<UUID, Long> quantities(List<SaleLine> lines) {
        Map<UUID, Long> quantities = new HashMap<>();
        lines.stream()
                .filter(line -> line.productId() != null)
                .forEach(line -> quantities.merge(line.productId(), line.quantityMilli(), Long::sum));
        return Map.copyOf(quantities);
    }

    private SaleData resolve(UUID businessId, LedgerInput.SaleInput input) {
        if (input.amountCents() != null) {
            return new SaleData(
                    SaleKind.FREE,
                    input.amountCents(),
                    List.of(),
                    input.concept(),
                    input.paymentMethod(),
                    input.occurredAt(),
                    input.note());
        }
        List<SaleLine> lines = new ArrayList<>();
        long total = 0;
        for (LedgerInput.ItemInput item : input.items()) {
            UUID productId = null;
            String name = item.name();
            Long unitPrice = item.unitPriceCents();
            if (item.productId() != null) {
                ProductView product = catalog.find(businessId, item.productId())
                        .orElseThrow(() -> new NotFoundException("product_not_found"));
                productId = product.id();
                name = product.name();
                unitPrice = unitPrice != null ? unitPrice : product.priceCents();
            }
            long lineTotal = Money.lineTotalCents(item.quantityMilli(), unitPrice);
            total = Money.add(total, lineTotal);
            lines.add(new SaleLine(productId, name, unitPrice, item.quantityMilli(), lineTotal));
        }
        return new SaleData(
                SaleKind.ITEMS, total, lines, input.concept(), input.paymentMethod(), input.occurredAt(), input.note());
    }

    private static SaleResponse toResponse(UUID id, SaleData sale) {
        return new SaleResponse(
                id,
                MovementType.SALE,
                sale.kind(),
                Money.format(sale.totalCents()),
                sale.lines().stream()
                        .map(line -> new SaleItemResponse(
                                line.productId(),
                                line.productName(),
                                Money.format(line.unitPriceCents()),
                                Quantity.format(line.quantityMilli()),
                                Money.format(line.lineTotalCents())))
                        .toList(),
                sale.concept(),
                sale.paymentMethod(),
                sale.occurredAt(),
                sale.note());
    }

    // --- expenses ----------------------------------------------------------------------------

    @Transactional
    ExpenseResponse createExpense(UUID businessId, ZoneId zone, ExpenseRequest request) {
        Instant now = clock.instant();
        ExpenseData expense = LedgerInput.expense(request, zone, now);
        UUID id = UUID.randomUUID();
        ledger.insertExpense(businessId, id, expense, now);
        return toResponse(id, expense);
    }

    @Transactional
    ExpenseResponse updateExpense(UUID businessId, ZoneId zone, UUID id, ExpenseRequest request) {
        Instant now = clock.instant();
        ledger.findExpense(businessId, id).orElseThrow(NotFoundException::new);
        ExpenseData expense = LedgerInput.expense(request, zone, now);
        ledger.updateExpense(businessId, id, expense, now);
        return toResponse(id, expense);
    }

    ExpenseResponse getExpense(UUID businessId, UUID id) {
        return toResponse(id, ledger.findExpense(businessId, id).orElseThrow(NotFoundException::new));
    }

    @Transactional
    void deleteExpense(UUID businessId, UUID id) {
        if (ledger.deleteExpense(businessId, id) == 0) {
            throw new NotFoundException();
        }
    }

    private static ExpenseResponse toResponse(UUID id, ExpenseData expense) {
        return new ExpenseResponse(
                id,
                MovementType.EXPENSE,
                Money.format(expense.amountCents()),
                expense.category(),
                expense.concept(),
                expense.paymentMethod(),
                expense.occurredAt(),
                expense.note());
    }

    // --- movements & balance -----------------------------------------------------------------

    MovementsResponse movements(UUID businessId, ZoneId zone, String period, String from, String to, String type) {
        String filter = type == null || type.isBlank() ? "all" : type.strip();
        boolean sales = filter.equals("all") || filter.equals("sales");
        boolean expenses = filter.equals("all") || filter.equals("expenses");
        if (!sales && !expenses) {
            throw com.myprueba.shared.ValidationException.of("type", "invalid_value");
        }
        Period resolved = Periods.resolve(period, from, to, zone, clock.instant());
        Map<UUID, List<SaleLine>> lines = sales ? ledger.linesInPeriod(businessId, resolved) : Map.of();
        List<MovementResponse> items = ledger.movements(businessId, resolved, sales, expenses).stream()
                .map(row -> new MovementResponse(
                        row.id(),
                        row.type(),
                        Money.format(row.amountCents()),
                        description(row, lines.get(row.id())),
                        row.paymentMethod(),
                        row.occurredAt(),
                        row.category()))
                .toList();
        return new MovementsResponse(PeriodView.of(resolved), items);
    }

    private static String description(MovementRow row, List<SaleLine> lines) {
        if (row.concept() != null || lines == null || lines.isEmpty()) {
            return row.concept();
        }
        return lines.stream()
                .map(line -> Quantity.format(line.quantityMilli()) + " × " + line.productName())
                .collect(Collectors.joining(", "));
    }

    BalanceResponse balance(UUID businessId, ZoneId zone, String period, String from, String to) {
        Period resolved = Periods.resolve(period, from, to, zone, clock.instant());
        long sales = ledger.sumSales(businessId, resolved);
        long expenses = ledger.sumExpenses(businessId, resolved);
        return new BalanceResponse(
                PeriodView.of(resolved), Money.format(sales), Money.format(expenses), Money.format(sales - expenses));
    }
}
