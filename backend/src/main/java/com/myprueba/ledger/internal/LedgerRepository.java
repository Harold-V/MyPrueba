package com.myprueba.ledger.internal;

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

    void insertSale(UUID businessId, UUID id, SaleData sale, Instant now) {
        jdbc.sql("""
                        INSERT INTO sales (id, business_id, kind, total_cents, concept, payment_method, occurred_at, note,
                                           customer_id, customer_name, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id.toString(),
                        businessId.toString(),
                        sale.kind().name(),
                        sale.totalCents(),
                        sale.concept(),
                        sale.paymentMethod().name(),
                        Timestamp.from(sale.occurredAt()),
                        sale.note(),
                        sale.customerId() == null ? null : sale.customerId().toString(),
                        sale.customerName(),
                        Timestamp.from(now),
                        Timestamp.from(now))
                .update();
        insertLines(id, sale.lines());
    }

    int updateSale(UUID businessId, UUID id, SaleData sale, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE sales SET kind = ?, total_cents = ?, concept = ?, payment_method = ?, occurred_at = ?,
                                         note = ?, customer_id = ?, customer_name = ?, updated_at = ?
                        WHERE business_id = ? AND id = ?
                        """)
                .params(
                        sale.kind().name(),
                        sale.totalCents(),
                        sale.concept(),
                        sale.paymentMethod().name(),
                        Timestamp.from(sale.occurredAt()),
                        sale.note(),
                        sale.customerId() == null ? null : sale.customerId().toString(),
                        sale.customerName(),
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
                                                    line_total_cents, position)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """)
                    .params(
                            UUID.randomUUID().toString(),
                            saleId.toString(),
                            line.productId() == null ? null : line.productId().toString(),
                            line.productName(),
                            line.unitPriceCents(),
                            line.quantityMilli(),
                            line.lineTotalCents(),
                            i)
                    .update();
        }
    }

    Optional<SaleData> findSale(UUID businessId, UUID id) {
        return jdbc.sql("""
                        SELECT kind, total_cents, concept, payment_method, occurred_at, note, customer_id, customer_name
                        FROM sales WHERE business_id = ? AND id = ?
                        """)
                .params(businessId.toString(), id.toString())
                .query((rs, n) -> new SaleData(
                        SaleKind.valueOf(rs.getString("kind")),
                        rs.getLong("total_cents"),
                        List.of(),
                        rs.getString("concept"),
                        PaymentMethod.valueOf(rs.getString("payment_method")),
                        instant(rs, "occurred_at"),
                        rs.getString("note"),
                        uuidOrNull(rs.getString("customer_id")),
                        rs.getString("customer_name")))
                .optional()
                .map(sale -> new SaleData(
                        sale.kind(),
                        sale.totalCents(),
                        findLines(id),
                        sale.concept(),
                        sale.paymentMethod(),
                        sale.occurredAt(),
                        sale.note(),
                        sale.customerId(),
                        sale.customerName()));
    }

    private List<SaleLine> findLines(UUID saleId) {
        return jdbc.sql("""
                        SELECT product_id, product_name, unit_price_cents, quantity_milli, line_total_cents
                        FROM sale_items WHERE sale_id = ? ORDER BY position
                        """)
                .param(saleId.toString())
                .query((rs, n) -> new SaleLine(
                        rs.getString("product_id") == null ? null : UUID.fromString(rs.getString("product_id")),
                        rs.getString("product_name"),
                        rs.getLong("unit_price_cents"),
                        rs.getLong("quantity_milli"),
                        rs.getLong("line_total_cents")))
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
                                              note, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                        Timestamp.from(now),
                        Timestamp.from(now))
                .update();
    }

    int updateExpense(UUID businessId, UUID id, ExpenseData expense, Instant now) {
        return jdbc.sql("""
                        UPDATE expenses SET amount_cents = ?, category = ?, concept = ?, payment_method = ?,
                                            occurred_at = ?, note = ?, updated_at = ?
                        WHERE business_id = ? AND id = ?
                        """)
                .params(
                        expense.amountCents(),
                        expense.category().name(),
                        expense.concept(),
                        expense.paymentMethod().name(),
                        Timestamp.from(expense.occurredAt()),
                        expense.note(),
                        Timestamp.from(now),
                        businessId.toString(),
                        id.toString())
                .update();
    }

    Optional<ExpenseData> findExpense(UUID businessId, UUID id) {
        return jdbc.sql("""
                        SELECT amount_cents, category, concept, payment_method, occurred_at, note
                        FROM expenses WHERE business_id = ? AND id = ?
                        """)
                .params(businessId.toString(), id.toString())
                .query((rs, n) -> new ExpenseData(
                        rs.getLong("amount_cents"),
                        ExpenseCategory.valueOf(rs.getString("category")),
                        rs.getString("concept"),
                        PaymentMethod.valueOf(rs.getString("payment_method")),
                        instant(rs, "occurred_at"),
                        rs.getString("note")))
                .optional();
    }

    int deleteExpense(UUID businessId, UUID id) {
        return jdbc.sql("DELETE FROM expenses WHERE business_id = ? AND id = ?")
                .params(businessId.toString(), id.toString())
                .update();
    }

    // --- movements & balance -----------------------------------------------------------------

    /** Sales and customer payments ({@code income}) and/or expenses in the period, newest first. */
    List<MovementRow> movements(UUID businessId, Period period, boolean income, boolean expenses) {
        String salesQuery = """
                SELECT id, 'SALE' AS type, total_cents AS amount_cents, concept, payment_method, occurred_at,
                       NULL AS category, created_at, customer_name
                FROM sales WHERE business_id = :business AND occurred_at >= :start AND occurred_at < :end
                """;
        String paymentsQuery = """
                SELECT id, 'PAYMENT' AS type, amount_cents, note AS concept, payment_method, occurred_at,
                       NULL AS category, created_at, customer_name
                FROM customer_payments WHERE business_id = :business AND occurred_at >= :start AND occurred_at < :end
                """;
        String expensesQuery = """
                SELECT id, 'EXPENSE' AS type, amount_cents, concept, payment_method, occurred_at, category, created_at,
                       NULL AS customer_name
                FROM expenses WHERE business_id = :business AND occurred_at >= :start AND occurred_at < :end
                """;
        List<String> parts = new java.util.ArrayList<>();
        if (income) {
            parts.add(salesQuery);
            parts.add(paymentsQuery);
        }
        if (expenses) {
            parts.add(expensesQuery);
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
                        rs.getString("customer_name")))
                .list();
    }

    /** Item lines of every sale in the period, grouped by sale id and in position order. */
    Map<UUID, List<SaleLine>> linesInPeriod(UUID businessId, Period period) {
        Map<UUID, List<SaleLine>> lines = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT i.sale_id, i.product_id, i.product_name, i.unit_price_cents, i.quantity_milli,
                               i.line_total_cents
                        FROM sale_items i JOIN sales s ON s.id = i.sale_id
                        WHERE s.business_id = ? AND s.occurred_at >= ? AND s.occurred_at < ?
                        ORDER BY i.sale_id, i.position
                        """)
                .params(businessId.toString(), Timestamp.from(period.start()), Timestamp.from(period.end()))
                .query(rs -> {
                    lines.computeIfAbsent(UUID.fromString(rs.getString("sale_id")), k -> new java.util.ArrayList<>())
                            .add(new SaleLine(
                                    null,
                                    rs.getString("product_name"),
                                    rs.getLong("unit_price_cents"),
                                    rs.getLong("quantity_milli"),
                                    rs.getLong("line_total_cents")));
                });
        return lines;
    }

    /** Paid sales only: a credit sale is not income until the customer pays (cash basis). */
    long sumPaidSales(UUID businessId, Period period) {
        return sum("SELECT COALESCE(SUM(total_cents), 0) FROM sales", "payment_method <> 'CREDIT'", businessId, period);
    }

    long sumPayments(UUID businessId, Period period) {
        return sum("SELECT COALESCE(SUM(amount_cents), 0) FROM customer_payments", "1 = 1", businessId, period);
    }

    long sumExpenses(UUID businessId, Period period) {
        return sum("SELECT COALESCE(SUM(amount_cents), 0) FROM expenses", "1 = 1", businessId, period);
    }

    private long sum(String select, String condition, UUID businessId, Period period) {
        return jdbc.sql(select + " WHERE " + condition
                        + " AND business_id = ? AND occurred_at >= ? AND occurred_at < ?")
                .params(businessId.toString(), Timestamp.from(period.start()), Timestamp.from(period.end()))
                .query(Long.class)
                .single();
    }

    static UUID uuidOrNull(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }
}
