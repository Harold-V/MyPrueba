package com.myprueba.ledger.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Supplier payments and debts: debt = Σ credit expenses − Σ supplier payments, per supplier. */
@Repository
class PayablesRepository {

    /** One row per credit expense (+) and per supplier payment (−), with its supplier. */
    private static final String ENTRIES = """
            SELECT supplier_id, amount_cents AS amount FROM expenses
            WHERE business_id = :business AND payment_method = 'CREDIT' AND supplier_id IS NOT NULL
            UNION ALL
            SELECT supplier_id, -amount_cents AS amount FROM supplier_payments
            WHERE business_id = :business AND supplier_id IS NOT NULL
            """;

    private final JdbcClient jdbc;

    PayablesRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Non-zero debts by supplier, largest first. */
    Map<UUID, Long> debts(UUID businessId) {
        Map<UUID, Long> debts = new LinkedHashMap<>();
        jdbc.sql("SELECT supplier_id, SUM(amount) AS debt FROM (" + ENTRIES + ") d"
                        + " GROUP BY supplier_id HAVING SUM(amount) <> 0 ORDER BY debt DESC, supplier_id")
                .param("business", businessId.toString())
                .query(rs -> {
                    debts.put(UUID.fromString(rs.getString("supplier_id")), rs.getLong("debt"));
                });
        return debts;
    }

    long debt(UUID businessId, UUID supplierId) {
        return jdbc.sql("SELECT COALESCE(SUM(amount), 0) FROM (" + ENTRIES + ") d WHERE supplier_id = :supplier")
                .param("business", businessId.toString())
                .param("supplier", supplierId.toString())
                .query(Long.class)
                .single();
    }

    long totalDebt(UUID businessId) {
        return jdbc.sql("SELECT COALESCE(SUM(amount), 0) FROM (" + ENTRIES + ") d")
                .param("business", businessId.toString())
                .query(Long.class)
                .single();
    }

    void insertPayment(UUID businessId, UUID id, SupplierPaymentData payment, Instant now) {
        jdbc.sql("""
                        INSERT INTO supplier_payments (id, business_id, supplier_id, supplier_name, amount_cents,
                                                       payment_method, occurred_at, note, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id.toString(),
                        businessId.toString(),
                        payment.supplierId().toString(),
                        payment.supplierName(),
                        payment.amountCents(),
                        payment.paymentMethod().name(),
                        Timestamp.from(payment.occurredAt()),
                        payment.note(),
                        Timestamp.from(now),
                        Timestamp.from(now))
                .update();
    }

    void updatePayment(UUID businessId, UUID id, SupplierPaymentData payment, Instant now) {
        jdbc.sql("""
                        UPDATE supplier_payments SET supplier_id = ?, supplier_name = ?, amount_cents = ?,
                               payment_method = ?, occurred_at = ?, note = ?, updated_at = ?
                        WHERE business_id = ? AND id = ?
                        """)
                .params(
                        payment.supplierId().toString(),
                        payment.supplierName(),
                        payment.amountCents(),
                        payment.paymentMethod().name(),
                        Timestamp.from(payment.occurredAt()),
                        payment.note(),
                        Timestamp.from(now),
                        businessId.toString(),
                        id.toString())
                .update();
    }

    Optional<SupplierPaymentData> findPayment(UUID businessId, UUID id) {
        return jdbc.sql("""
                        SELECT supplier_id, supplier_name, amount_cents, payment_method, occurred_at, note
                        FROM supplier_payments WHERE business_id = ? AND id = ?
                        """)
                .params(businessId.toString(), id.toString())
                .query((rs, n) -> new SupplierPaymentData(
                        LedgerRepository.uuidOrNull(rs.getString("supplier_id")),
                        rs.getString("supplier_name"),
                        rs.getLong("amount_cents"),
                        PaymentMethod.valueOf(rs.getString("payment_method")),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("note")))
                .optional();
    }

    int deletePayment(UUID businessId, UUID id) {
        return jdbc.sql("DELETE FROM supplier_payments WHERE business_id = ? AND id = ?")
                .params(businessId.toString(), id.toString())
                .update();
    }

    /** Credit expenses with one supplier, oldest first. */
    List<Settlement.Owed> creditExpenses(UUID businessId, UUID supplierId) {
        return jdbc.sql("""
                        SELECT amount_cents, occurred_at, due_date FROM expenses
                        WHERE business_id = ? AND supplier_id = ? AND payment_method = 'CREDIT'
                        ORDER BY occurred_at, created_at, id
                        """)
                .params(businessId.toString(), supplierId.toString())
                .query((rs, n) -> new Settlement.Owed(
                        rs.getLong("amount_cents"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        LedgerRepository.localDate(rs.getDate("due_date"))))
                .list();
    }

    record Purchase(UUID id, boolean credit, long totalCents, String concept, Instant occurredAt) {}

    /** Every expense with one supplier, newest first. */
    List<Purchase> purchases(UUID businessId, UUID supplierId) {
        return jdbc.sql("""
                        SELECT id, payment_method, amount_cents, concept, occurred_at FROM expenses
                        WHERE business_id = ? AND supplier_id = ?
                        ORDER BY occurred_at DESC, created_at DESC, id
                        """)
                .params(businessId.toString(), supplierId.toString())
                .query((rs, n) -> new Purchase(
                        UUID.fromString(rs.getString("id")),
                        "CREDIT".equals(rs.getString("payment_method")),
                        rs.getLong("amount_cents"),
                        rs.getString("concept"),
                        rs.getTimestamp("occurred_at").toInstant()))
                .list();
    }

    /** "10 × Gaseosa, 2 × Leche" for each expense with the supplier that has product lines. */
    Map<UUID, String> itemSummaries(UUID businessId, UUID supplierId) {
        Map<UUID, String> summaries = new HashMap<>();
        jdbc.sql("""
                        SELECT i.expense_id, i.product_name, i.quantity_milli FROM expense_items i
                        JOIN expenses e ON e.id = i.expense_id
                        WHERE e.business_id = ? AND e.supplier_id = ?
                        ORDER BY i.expense_id, i.position
                        """).params(businessId.toString(), supplierId.toString()).query(rs -> {
            summaries.merge(
                    UUID.fromString(rs.getString("expense_id")),
                    LedgerRepository.lineSummary(rs.getLong("quantity_milli"), rs.getString("product_name")),
                    (a, b) -> a + ", " + b);
        });
        return summaries;
    }

    /** Credit expenses and payments with one supplier, newest first. */
    List<MovementRow> account(UUID businessId, UUID supplierId) {
        return jdbc.sql("""
                        SELECT * FROM (
                            SELECT id, 'EXPENSE' AS type, amount_cents, concept, occurred_at, created_at
                            FROM expenses WHERE business_id = :business AND supplier_id = :supplier
                                            AND payment_method = 'CREDIT'
                            UNION ALL
                            SELECT id, 'SUPPLIER_PAYMENT' AS type, amount_cents, note AS concept, occurred_at, created_at
                            FROM supplier_payments WHERE business_id = :business AND supplier_id = :supplier
                        ) a ORDER BY occurred_at DESC, created_at DESC, id
                        """)
                .param("business", businessId.toString())
                .param("supplier", supplierId.toString())
                .query((rs, n) -> new MovementRow(
                        UUID.fromString(rs.getString("id")),
                        MovementType.valueOf(rs.getString("type")),
                        rs.getLong("amount_cents"),
                        rs.getString("concept"),
                        null,
                        rs.getTimestamp("occurred_at").toInstant(),
                        null,
                        null,
                        null))
                .list();
    }
}
