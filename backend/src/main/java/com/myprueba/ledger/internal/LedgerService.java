package com.myprueba.ledger.internal;

import com.myprueba.catalog.ProductCatalog;
import com.myprueba.catalog.ProductView;
import com.myprueba.customers.CustomerDirectory;
import com.myprueba.customers.CustomerView;
import com.myprueba.ledger.PurchaseItemsChanged;
import com.myprueba.ledger.SaleItemsChanged;
import com.myprueba.shared.Money;
import com.myprueba.shared.NotFoundException;
import com.myprueba.shared.Quantity;
import com.myprueba.shared.ValidationException;
import com.myprueba.shared.Violations;
import com.myprueba.suppliers.SupplierDirectory;
import com.myprueba.suppliers.SupplierView;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class LedgerService {

    private final LedgerRepository ledger;
    private final ProductCatalog catalog;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final CustomerDirectory customers;
    private final ReceivablesRepository receivables;
    private final SupplierDirectory suppliers;
    private final PayablesRepository payables;

    LedgerService(
            LedgerRepository ledger,
            ProductCatalog catalog,
            Clock clock,
            ApplicationEventPublisher events,
            CustomerDirectory customers,
            ReceivablesRepository receivables,
            SupplierDirectory suppliers,
            PayablesRepository payables) {
        this.ledger = ledger;
        this.catalog = catalog;
        this.clock = clock;
        this.events = events;
        this.customers = customers;
        this.receivables = receivables;
        this.suppliers = suppliers;
        this.payables = payables;
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
        CustomerView customer = input.customerId() == null
                ? null
                : customers
                        .find(businessId, input.customerId())
                        .orElseThrow(() -> new NotFoundException("customer_not_found"));
        UUID customerId = customer == null ? null : customer.id();
        String customerName = customer == null ? null : customer.name();
        if (input.amountCents() != null) {
            return new SaleData(
                    SaleKind.FREE,
                    input.amountCents(),
                    List.of(),
                    input.concept(),
                    input.paymentMethod(),
                    input.occurredAt(),
                    input.note(),
                    customerId,
                    customerName,
                    input.dueDate());
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
                SaleKind.ITEMS,
                total,
                lines,
                input.concept(),
                input.paymentMethod(),
                input.occurredAt(),
                input.note(),
                customerId,
                customerName,
                input.dueDate());
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
                sale.note(),
                sale.customerId(),
                sale.customerName(),
                sale.paymentMethod() == PaymentMethod.CREDIT ? PaymentStatus.CREDIT : PaymentStatus.PAID,
                sale.dueDate());
    }

    // --- expenses ----------------------------------------------------------------------------

    @Transactional
    ExpenseResponse createExpense(UUID businessId, ZoneId zone, ExpenseRequest request) {
        Instant now = clock.instant();
        ExpenseData expense = resolve(businessId, LedgerInput.expense(request, zone, now));
        UUID id = UUID.randomUUID();
        ledger.insertExpense(businessId, id, expense, now);
        publishPurchase(businessId, id, PurchaseItemsChanged.Kind.RECORDED, List.of(), expense.lines());
        return toResponse(id, expense);
    }

    @Transactional
    ExpenseResponse updateExpense(UUID businessId, ZoneId zone, UUID id, ExpenseRequest request) {
        Instant now = clock.instant();
        ExpenseData previous = ledger.findExpense(businessId, id).orElseThrow(NotFoundException::new);
        ExpenseData expense = resolve(businessId, LedgerInput.expense(request, zone, now));
        ledger.updateExpense(businessId, id, expense, now);
        publishPurchase(businessId, id, PurchaseItemsChanged.Kind.EDITED, previous.lines(), expense.lines());
        return toResponse(id, expense);
    }

    ExpenseResponse getExpense(UUID businessId, UUID id) {
        return toResponse(id, ledger.findExpense(businessId, id).orElseThrow(NotFoundException::new));
    }

    @Transactional
    void deleteExpense(UUID businessId, UUID id) {
        ExpenseData previous = ledger.findExpense(businessId, id).orElseThrow(NotFoundException::new);
        ledger.deleteExpense(businessId, id);
        publishPurchase(businessId, id, PurchaseItemsChanged.Kind.DELETED, previous.lines(), List.of());
    }

    private void publishPurchase(
            UUID businessId,
            UUID expenseId,
            PurchaseItemsChanged.Kind kind,
            List<ExpenseLine> before,
            List<ExpenseLine> after) {
        Map<UUID, Long> previous = purchased(before);
        Map<UUID, Long> current = purchased(after);
        if (!previous.isEmpty() || !current.isEmpty()) {
            events.publishEvent(new PurchaseItemsChanged(businessId, expenseId, kind, previous, current));
        }
    }

    private static Map<UUID, Long> purchased(List<ExpenseLine> lines) {
        Map<UUID, Long> quantities = new HashMap<>();
        lines.stream()
                .filter(line -> line.productId() != null)
                .forEach(line -> quantities.merge(line.productId(), line.quantityMilli(), Long::sum));
        return Map.copyOf(quantities);
    }

    private ExpenseData resolve(UUID businessId, LedgerInput.ExpenseInput input) {
        SupplierView supplier = input.supplierId() == null
                ? null
                : suppliers
                        .find(businessId, input.supplierId())
                        .orElseThrow(() -> new NotFoundException("supplier_not_found"));
        List<ExpenseLine> lines = new ArrayList<>();
        long total = input.amountCents() == null ? 0 : input.amountCents();
        Violations violations = new Violations();
        for (int i = 0; i < input.items().size(); i++) {
            LedgerInput.ExpenseItemInput item = input.items().get(i);
            UUID productId = null;
            String name = item.name();
            Long unitCost = item.unitCostCents();
            if (item.productId() != null) {
                ProductView product = catalog.find(businessId, item.productId())
                        .orElseThrow(() -> new NotFoundException("product_not_found"));
                productId = product.id();
                name = product.name();
                unitCost = unitCost != null ? unitCost : product.costCents();
            }
            if (unitCost == null) {
                violations.add("items[" + i + "].unitCost", "required");
                continue;
            }
            long lineTotal = Money.lineTotalCents(item.quantityMilli(), unitCost);
            total = Money.add(total, lineTotal);
            lines.add(new ExpenseLine(productId, name, unitCost, item.quantityMilli(), lineTotal));
        }
        violations.throwIfAny();
        return new ExpenseData(
                total,
                lines,
                input.category(),
                input.concept(),
                input.paymentMethod(),
                input.occurredAt(),
                input.note(),
                supplier == null ? null : supplier.id(),
                supplier == null ? null : supplier.name(),
                input.dueDate());
    }

    private static ExpenseResponse toResponse(UUID id, ExpenseData expense) {
        return new ExpenseResponse(
                id,
                MovementType.EXPENSE,
                Money.format(expense.amountCents()),
                expense.lines().stream()
                        .map(line -> new ExpenseItemResponse(
                                line.productId(),
                                line.productName(),
                                Money.format(line.unitCostCents()),
                                Quantity.format(line.quantityMilli()),
                                Money.format(line.lineTotalCents())))
                        .toList(),
                expense.category(),
                expense.concept(),
                expense.paymentMethod(),
                expense.occurredAt(),
                expense.note(),
                expense.supplierId(),
                expense.supplierName(),
                expense.paymentMethod() == PaymentMethod.CREDIT ? PaymentStatus.CREDIT : PaymentStatus.PAID,
                expense.dueDate());
    }

    // --- movements & balance -----------------------------------------------------------------

    MovementsResponse movements(UUID businessId, ZoneId zone, String period, String from, String to, String type) {
        String filter = type == null || type.isBlank() ? "all" : type.strip();
        boolean sales = filter.equals("all") || filter.equals("sales");
        boolean expenses = filter.equals("all") || filter.equals("expenses");
        if (!sales && !expenses) {
            throw ValidationException.of("type", "invalid_value");
        }
        Period resolved = Periods.resolve(period, from, to, zone, clock.instant());
        Map<UUID, String> lines = ledger.lineSummaries(businessId, resolved);
        List<MovementResponse> items = ledger.movements(businessId, resolved, sales, expenses).stream()
                .map(row -> new MovementResponse(
                        row.id(),
                        row.type(),
                        Money.format(row.amountCents()),
                        description(row, lines.get(row.id())),
                        row.paymentMethod(),
                        row.occurredAt(),
                        row.category(),
                        row.customerName(),
                        row.supplierName()))
                .toList();
        return new MovementsResponse(PeriodView.of(resolved), items);
    }

    /** The concept, or for a sale or purchase with product lines, "2 × Pan, 1 × Leche". */
    private static String description(MovementRow row, String lines) {
        return row.concept() != null ? row.concept() : lines;
    }

    BalanceResponse balance(UUID businessId, ZoneId zone, String period, String from, String to) {
        Period resolved = Periods.resolve(period, from, to, zone, clock.instant());
        long sales = ledger.sumPaidSales(businessId, resolved);
        long payments = ledger.sumPayments(businessId, resolved);
        long expenses = Money.add(
                ledger.sumPaidExpenses(businessId, resolved), ledger.sumSupplierPayments(businessId, resolved));
        return new BalanceResponse(
                PeriodView.of(resolved),
                Money.format(sales),
                Money.format(payments),
                Money.format(expenses),
                Money.format(sales + payments - expenses),
                Money.format(receivables.totalDebt(businessId)),
                Money.format(payables.totalDebt(businessId)));
    }
}
