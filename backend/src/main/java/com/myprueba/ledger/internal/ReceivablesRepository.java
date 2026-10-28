package com.myprueba.ledger.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Customer payments and debts: debt = Σ credit sales − Σ payments, per customer. */
@Repository
class ReceivablesRepository {

    /** One row per credit sale (+) and per payment (−), with its customer. */
    private static final String ENTRIES = """
            SELECT customer_id, total_cents AS amount FROM sales
            WHERE business_id = :business AND payment_method = 'CREDIT' AND customer_id IS NOT NULL
            UNION ALL
            SELECT customer_id, -amount_cents AS amount FROM customer_payments
            WHERE business_id = :business AND customer_id IS NOT NULL
            """;

    private final JdbcClient jdbc;

    ReceivablesRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Non-zero debts by customer, largest first. */
    Map<UUID, Long> debts(UUID businessId) {
        Map<UUID, Long> debts = new LinkedHashMap<>();
        jdbc.sql("SELECT customer_id, SUM(amount) AS debt FROM (" + ENTRIES + ") d"
                        + " GROUP BY customer_id HAVING SUM(amount) <> 0 ORDER BY debt DESC, customer_id")
                .param("business", businessId.toString())
                .query(rs -> {
                    debts.put(UUID.fromString(rs.getString("customer_id")), rs.getLong("debt"));
                });
        return debts;
    }

    long debt(UUID businessId, UUID customerId) {
        return jdbc.sql("SELECT COALESCE(SUM(amount), 0) FROM (" + ENTRIES + ") d WHERE customer_id = :customer")
                .param("business", businessId.toString())
                .param("customer", customerId.toString())
                .query(Long.class)
                .single();
    }

    long totalDebt(UUID businessId) {
        return jdbc.sql("SELECT COALESCE(SUM(amount), 0) FROM (" + ENTRIES + ") d")
                .param("business", businessId.toString())
                .query(Long.class)
                .single();
    }

    void insertPayment(UUID businessId, UUID id, PaymentData payment, Instant now) {
        jdbc.sql("""
                        INSERT INTO customer_payments (id, business_id, customer_id, customer_name, amount_cents,
                                                       payment_method, occurred_at, note, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id.toString(),
                        businessId.toString(),
                        payment.customerId().toString(),
                        payment.customerName(),
                        payment.amountCents(),
                        payment.paymentMethod().name(),
                        Timestamp.from(payment.occurredAt()),
                        payment.note(),
                        Timestamp.from(now),
                        Timestamp.from(now))
                .update();
    }

    void updatePayment(UUID businessId, UUID id, PaymentData payment, Instant now) {
        jdbc.sql("""
                        UPDATE customer_payments SET customer_id = ?, customer_name = ?, amount_cents = ?,
                               payment_method = ?, occurred_at = ?, note = ?, updated_at = ?
                        WHERE business_id = ? AND id = ?
                        """)
                .params(
                        payment.customerId().toString(),
                        payment.customerName(),
                        payment.amountCents(),
                        payment.paymentMethod().name(),
                        Timestamp.from(payment.occurredAt()),
                        payment.note(),
                        Timestamp.from(now),
                        businessId.toString(),
                        id.toString())
                .update();
    }

    Optional<PaymentData> findPayment(UUID businessId, UUID id) {
        return jdbc.sql("""
                        SELECT customer_id, customer_name, amount_cents, payment_method, occurred_at, note
                        FROM customer_payments WHERE business_id = ? AND id = ?
                        """)
                .params(businessId.toString(), id.toString())
                .query((rs, n) -> new PaymentData(
                        LedgerRepository.uuidOrNull(rs.getString("customer_id")),
                        rs.getString("customer_name"),
                        rs.getLong("amount_cents"),
                        PaymentMethod.valueOf(rs.getString("payment_method")),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("note")))
                .optional();
    }

    int deletePayment(UUID businessId, UUID id) {
        return jdbc.sql("DELETE FROM customer_payments WHERE business_id = ? AND id = ?")
                .params(businessId.toString(), id.toString())
                .update();
    }

    record CreditSale(long totalCents, Instant occurredAt, java.time.LocalDate dueDate) {}

    /** Credit sales of one customer, oldest first. */
    List<CreditSale> creditSales(UUID businessId, UUID customerId) {
        return jdbc.sql("""
                        SELECT total_cents, occurred_at, due_date FROM sales
                        WHERE business_id = ? AND customer_id = ? AND payment_method = 'CREDIT'
                        ORDER BY occurred_at, created_at, id
                        """)
                .params(businessId.toString(), customerId.toString())
                .query((rs, n) -> new CreditSale(
                        rs.getLong("total_cents"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        LedgerRepository.localDate(rs.getDate("due_date"))))
                .list();
    }

    /** Credit sales and payments of one customer, newest first. */
    List<MovementRow> account(UUID businessId, UUID customerId) {
        return jdbc.sql("""
                        SELECT * FROM (
                            SELECT id, 'SALE' AS type, total_cents AS amount_cents, concept, occurred_at, created_at
                            FROM sales WHERE business_id = :business AND customer_id = :customer
                                         AND payment_method = 'CREDIT'
                            UNION ALL
                            SELECT id, 'PAYMENT' AS type, amount_cents, note AS concept, occurred_at, created_at
                            FROM customer_payments WHERE business_id = :business AND customer_id = :customer
                        ) a ORDER BY occurred_at DESC, created_at DESC, id
                        """)
                .param("business", businessId.toString())
                .param("customer", customerId.toString())
                .query((rs, n) -> new MovementRow(
                        UUID.fromString(rs.getString("id")),
                        MovementType.valueOf(rs.getString("type")),
                        rs.getLong("amount_cents"),
                        rs.getString("concept"),
                        null,
                        rs.getTimestamp("occurred_at").toInstant(),
                        null,
                        null))
                .list();
    }
}
