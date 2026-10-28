package com.myprueba.ledger.internal;

import com.myprueba.shared.Quantity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class LedgerRepository {

    private final JdbcClient jdbc;

    LedgerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // --- sales -------------------------------------------------------------------------------

    /** Stores a new sale with the next number of its business, and returns it numbered. */
    SaleData insertSale(UUID businessId, UUID id, SaleData sale, Instant now) {
        long number = jdbc.sql("SELECT COALESCE(MAX(number), 0) + 1 FROM sales WHERE business_id = ?")
                .param(businessId.toString())
                .query(Long.class)
                .single();
        jdbc.sql("""
                        INSERT INTO sales (id, business_id, number, kind, subtotal_cents, discount_cents, total_cents,
                                           concept, payment_method, occurred_at, note, customer_id, customer_name,
                                           due_date, received_cents, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id.toString(),
                        businessId.toString(),
                        number,
                        sale.kind().name(),
                        sale.subtotalCents(),
                        sale.discountCents(),
                        sale.totalCents(),
                        sale.concept(),
                        sale.paymentMethod().name(),
                        Timestamp.from(sale.occurredAt()),
                        sale.note(),
                        sale.customerId() == null ? null : sale.customerId().toString(),
                        sale.customerName(),
                        sqlDate(sale.dueDate()),
                        sale.receivedCents(),
                        Timestamp.from(now),
                        Timestamp.from(now))
                .update();
        insertLines(id, sale.lines());
        return new SaleData(
                number,
                sale.kind(),
                sale.subtotalCents(),
                sale.discountCents(),
                sale.totalCents(),
                sale.lines(),
                sale.concept(),
                sale.paymentMethod(),
                sale.occurredAt(),
                sale.note(),
                sale.customerId(),
                sale.customerName(),
                sale.dueDate(),
                sale.receivedCents());
    }

    /** Rewrites a sale and its lines; its number never changes. */
    int updateSale(UUID businessId, UUID id, SaleData sale, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE sales SET kind = ?, subtotal_cents = ?, discount_cents = ?, total_cents = ?, concept = ?,
                                         payment_method = ?, occurred_at = ?, note = ?, customer_id = ?,
                                         customer_name = ?, due_date = ?, received_cents = ?, updated_at = ?
                        WHERE business_id = ? AND id = ?
                        """)
                .params(
                        sale.kind().name(),
                        sale.subtotalCents(),
                        sale.discountCents(),
                        sale.totalCents(),
                        sale.concept(),
                        sale.paymentMethod().name(),
                        Timestamp.from(sale.occurredAt()),
                        sale.note(),
                        sale.customerId() == null ? null : sale.customerId().toString(),
                        sale.customerName(),
                        sqlDate(sale.dueDate()),
                        sale.receivedCents(),
                        Timestamp.from(now),
                        businessId.toString(),
                        id.toString())
                .update();
        if (updated > 0) {
            jdbc.sql("DELETE FROM sale_items WHERE sale_id = ?")
                    .param(id.toString())
                    .update();
            insertLines(id, sale.lines());
        }
        return updated;
    }

    private void insertLines(UUID saleId, List<SaleLine> lines) {
        for (int i = 0; i < lines.size(); i++) {
            SaleLine line = lines.get(i);
            jdbc.sql("""
                            INSERT INTO sale_items (id, sale_id, product_id, product_name, unit_price_cents, quantity_milli,
                                                    line_total_cents, discount_cents, position)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """)
                    .params(
                            UUID.randomUUID().toString(),
                            saleId.toString(),
                            line.productId() == null ? null : line.productId().toString(),
                            line.productName(),
                            line.unitPriceCents(),
                            line.quantityMilli(),
                            line.lineTotalCents(),
                            line.discountCents(),
                            i)
                    .update();
        }
    }

    Optional<SaleData> findSale(UUID businessId, UUID id) {
        return jdbc.sql("""
                        SELECT number, kind, subtotal_cents, discount_cents, total_cents, concept, payment_method,
                               occurred_at, note, customer_id, customer_name, due_date, received_cents
                        FROM sales WHERE business_id = ? AND id = ?
                        """)
                .params(businessId.toString(), id.toString())
                .query((rs, n) -> new SaleData(
                        rs.getLong("number"),
                        SaleKind.valueOf(rs.getString("kind")),
                        rs.getLong("subtotal_cents"),
                        rs.getLong("discount_cents"),
                        rs.getLong("total_cents"),
                        List.of(),
                        rs.getString("concept"),
                        PaymentMethod.valueOf(rs.getString("payment_method")),
                        instant(rs, "occurred_at"),
                        rs.getString("note"),
                        uuidOrNull(rs.getString("customer_id")),
                        rs.getString("customer_name"),
                        localDate(rs.getDate("due_date")),
                        rs.getObject("received_cents") == null ? null : rs.getLong("received_cents")))
                .optional()
                .map(sale -> sale.withLines(findLines(id)));
    }

    private List<SaleLine> findLines(UUID saleId) {
        return jdbc.sql("""
                        SELECT product_id, product_name, unit_price_cents, quantity_milli, line_total_cents, discount_cents
                        FROM sale_items WHERE sale_id = ? ORDER BY position
                        """)
                .param(saleId.toString())
                .query((rs, n) -> new SaleLine(
                        uuidOrNull(rs.getString("product_id")),
                        rs.getString("product_name"),
                        rs.getLong("unit_price_cents"),
                        rs.getLong("quantity_milli"),
                        rs.getLong("line_total_cents"),
                        rs.getLong("discount_cents")))
                .list();
    }

    int deleteSale(UUID businessId, UUID id) {
        return jdbc.sql("DELETE FROM sales WHERE business_id = ? AND id = ?")
                .params(businessId.toString(), id.toString())
                .update();
    }

    // --- expenses ----------------------------------------------------------------------------

    void insertExpense(UUID businessId, UUID id, ExpenseData expense, Instant now) {
        jdbc.sql("""
                        INSERT INTO expenses (id, business_id, amount_cents, category, concept, payment_method, occurred_at,
                                              note, supplier_id, supplier_name, due_date, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id.toString(),
                        businessId.toString(),
                        expense.amountCents(),
                        expense.category().name(),
                        expense.concept(),
                        expense.paymentMethod().name(),
                        Timestamp.from(expense.occurredAt()),
                        expense.note(),
                        expense.supplierId() == null
                                ? null
                                : expense.supplierId().toString(),
                        expense.supplierName(),
                        sqlDate(expense.dueDate()),
                        Timestamp.from(now),
                        Timestamp.from(now))
                .update();
        insertExpenseLines(id, expense.lines());
    }

    int updateExpense(UUID businessId, UUID id, ExpenseData expense, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE expenses SET amount_cents = ?, category = ?, concept = ?, payment_method = ?,
                                            occurred_at = ?, note = ?, supplier_id = ?, supplier_name = ?,
                                            due_date = ?, updated_at = ?
                        WHERE business_id = ? AND id = ?
                        """)
                .params(
                        expense.amountCents(),
                        expense.category().name(),
                        expense.concept(),
                        expense.paymentMethod().name(),
                        Timestamp.from(expense.occurredAt()),
                        expense.note(),
                        expense.supplierId() == null
                                ? null
                                : expense.supplierId().toString(),
                        expense.supplierName(),
                        sqlDate(expense.dueDate()),
                        Timestamp.from(now),
                        businessId.toString(),
                        id.toString())
                .update();
        if (updated > 0) {
            jdbc.sql("DELETE FROM expense_items WHERE expense_id = ?")
                    .param(id.toString())
                    .update();
            insertExpenseLines(id, expense.lines());
        }
        return updated;
    }

    private void insertExpenseLines(UUID expenseId, List<ExpenseLine> lines) {
        for (int i = 0; i < lines.size(); i++) {
            ExpenseLine line = lines.get(i);
            jdbc.sql("""
                            INSERT INTO expense_items (id, expense_id, product_id, product_name, unit_cost_cents,
                                                       quantity_milli, line_total_cents, position)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """)
                    .params(
                            UUID.randomUUID().toString(),
                            expenseId.toString(),
                            line.productId() == null ? null : line.productId().toString(),
                            line.productName(),
                            line.unitCostCents(),
                            line.quantityMilli(),
                            line.lineTotalCents(),
                            i)
                    .update();
        }
    }

    Optional<ExpenseData> findExpense(UUID businessId, UUID id) {
        return jdbc.sql("""
                        SELECT amount_cents, category, concept, payment_method, occurred_at, note, supplier_id,
                               supplier_name, due_date
                        FROM expenses WHERE business_id = ? AND id = ?
                        """)
                .params(businessId.toString(), id.toString())
                .query((rs, n) -> new ExpenseData(
                        rs.getLong("amount_cents"),
                        List.of(),
                        ExpenseCategory.valueOf(rs.getString("category")),
                        rs.getString("concept"),
                        PaymentMethod.valueOf(rs.getString("payment_method")),
                        instant(rs, "occurred_at"),
                        rs.getString("note"),
                        uuidOrNull(rs.getString("supplier_id")),
                        rs.getString("supplier_name"),
                        localDate(rs.getDate("due_date"))))
                .optional()
                .map(expense -> expense.withLines(findExpenseLines(id)));
    }

    private List<ExpenseLine> findExpenseLines(UUID expenseId) {
        return jdbc.sql("""
                        SELECT product_id, product_name, unit_cost_cents, quantity_milli, line_total_cents
                        FROM expense_items WHERE expense_id = ? ORDER BY position
                        """)
                .param(expenseId.toString())
                .query((rs, n) -> new ExpenseLine(
                        uuidOrNull(rs.getString("product_id")),
                        rs.getString("product_name"),
                        rs.getLong("unit_cost_cents"),
                        rs.getLong("quantity_milli"),
                        rs.getLong("line_total_cents")))
                .list();
    }

    int deleteExpense(UUID businessId, UUID id) {
        return jdbc.sql("DELETE FROM expenses WHERE business_id = ? AND id = ?")
                .params(businessId.toString(), id.toString())
                .update();
    }

    // --- movements & balance -----------------------------------------------------------------

    /**
     * Sales and customer payments ({@code income}) and/or expenses and supplier payments in the
     * period, newest first.
     */
    List<MovementRow> movements(UUID businessId, Period period, boolean income, boolean expenses) {
        String salesQuery = """
                SELECT id, 'SALE' AS type, total_cents AS amount_cents, concept, payment_method, occurred_at,
                       NULL AS category, created_at, customer_name, NULL AS supplier_name
                FROM sales WHERE business_id = :business AND occurred_at >= :start AND occurred_at < :end
                """;
        String paymentsQuery = """
                SELECT id, 'PAYMENT' AS type, amount_cents, note AS concept, payment_method, occurred_at,
                       NULL AS category, created_at, customer_name, NULL AS supplier_name
                FROM customer_payments WHERE business_id = :business AND occurred_at >= :start AND occurred_at < :end
                """;
        String expensesQuery = """
                SELECT id, 'EXPENSE' AS type, amount_cents, concept, payment_method, occurred_at, category, created_at,
                       NULL AS customer_name, supplier_name
                FROM expenses WHERE business_id = :business AND occurred_at >= :start AND occurred_at < :end
                """;
        String supplierPaymentsQuery = """
                SELECT id, 'SUPPLIER_PAYMENT' AS type, amount_cents, note AS concept, payment_method, occurred_at,
                       NULL AS category, created_at, NULL AS customer_name, supplier_name
                FROM supplier_payments WHERE business_id = :business AND occurred_at >= :start AND occurred_at < :end
                """;
        List<String> parts = new java.util.ArrayList<>();
        if (income) {
            parts.add(salesQuery);
            parts.add(paymentsQuery);
        }
        if (expenses) {
            parts.add(expensesQuery);
            parts.add(supplierPaymentsQuery);
        }
        String union = String.join(" UNION ALL ", parts);
        return jdbc.sql("SELECT * FROM (" + union + ") m ORDER BY occurred_at DESC, created_at DESC, id")
                .param("business", businessId.toString())
                .param("start", Timestamp.from(period.start()))
                .param("end", Timestamp.from(period.end()))
                .query((rs, n) -> new MovementRow(
                        UUID.fromString(rs.getString("id")),
                        MovementType.valueOf(rs.getString("type")),
                        rs.getLong("amount_cents"),
                        rs.getString("concept"),
                        PaymentMethod.valueOf(rs.getString("payment_method")),
                        instant(rs, "occurred_at"),
                        rs.getString("category") == null ? null : ExpenseCategory.valueOf(rs.getString("category")),
                        rs.getString("customer_name"),
                        rs.getString("supplier_name")))
                .list();
    }

    /** "2 × Pan, 1 × Leche" for every sale and purchase with product lines in the period. */
    Map<UUID, String> lineSummaries(UUID businessId, Period period) {
        Map<UUID, String> summaries = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT i.sale_id AS owner_id, i.product_name, i.quantity_milli, i.position
                        FROM sale_items i JOIN sales s ON s.id = i.sale_id
                        WHERE s.business_id = :business AND s.occurred_at >= :start AND s.occurred_at < :end
                        UNION ALL
                        SELECT i.expense_id AS owner_id, i.product_name, i.quantity_milli, i.position
                        FROM expense_items i JOIN expenses e ON e.id = i.expense_id
                        WHERE e.business_id = :business AND e.occurred_at >= :start AND e.occurred_at < :end
                        ORDER BY owner_id, position
                        """)
                .param("business", businessId.toString())
                .param("start", Timestamp.from(period.start()))
                .param("end", Timestamp.from(period.end()))
                .query(rs -> {
                    summaries.merge(
                            UUID.fromString(rs.getString("owner_id")),
                            lineSummary(rs.getLong("quantity_milli"), rs.getString("product_name")),
                            (a, b) -> a + ", " + b);
                });
        return summaries;
    }

    static String lineSummary(long quantityMilli, String productName) {
        return Quantity.format(quantityMilli) + " × " + productName;
    }

    /** Paid sales only: a credit sale is not income until the customer pays (cash basis). */
    long sumPaidSales(UUID businessId, Period period) {
        return sum("SELECT COALESCE(SUM(total_cents), 0) FROM sales", "payment_method <> 'CREDIT'", businessId, period);
    }

    long sumPayments(UUID businessId, Period period) {
        return sum("SELECT COALESCE(SUM(amount_cents), 0) FROM customer_payments", "1 = 1", businessId, period);
    }

    /** Paid expenses only: a credit expense is not cash out until the supplier is paid. */
    long sumPaidExpenses(UUID businessId, Period period) {
        return sum(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM expenses",
                "payment_method <> 'CREDIT'",
                businessId,
                period);
    }

    long sumSupplierPayments(UUID businessId, Period period) {
        return sum("SELECT COALESCE(SUM(amount_cents), 0) FROM supplier_payments", "1 = 1", businessId, period);
    }

    private long sum(String select, String condition, UUID businessId, Period period) {
        return jdbc.sql(select + " WHERE " + condition
                        + " AND business_id = ? AND occurred_at >= ? AND occurred_at < ?")
                .params(businessId.toString(), Timestamp.from(period.start()), Timestamp.from(period.end()))
                .query(Long.class)
                .single();
    }

    static java.sql.Date sqlDate(java.time.LocalDate date) {
        return date == null ? null : java.sql.Date.valueOf(date);
    }

    static java.time.LocalDate localDate(java.sql.Date date) {
        return date == null ? null : date.toLocalDate();
    }

    static UUID uuidOrNull(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }
}
