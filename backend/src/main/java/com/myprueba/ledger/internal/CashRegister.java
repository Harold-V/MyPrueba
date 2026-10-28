package com.myprueba.ledger.internal;

import com.myprueba.accounts.CurrentBusiness;
import com.myprueba.shared.ConflictException;
import com.myprueba.shared.Money;
import com.myprueba.shared.Text;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

record OpenRegisterRequest(String openingAmount) {}

record CloseRegisterRequest(String countedAmount, String note) {}

/**
 * A cash register session. While open, {@code expected} is the opening float plus the cash that came
 * in minus the cash that went out; once closed it also keeps what was counted and the difference
 * (positive = surplus, negative = shortage).
 */
record CashSession(
        UUID id,
        Instant openedAt,
        String opening,
        String cashSales,
        String cashPayments,
        String cashExpenses,
        String cashSupplierPayments,
        String expected,
        Instant closedAt,
        String counted,
        String difference,
        String note) {}

record CashRegisterResponse(CashSession open, CashSession last) {}

@Repository
class CashRegisterRepository {

    /** A stored session; the cash flows are computed, not stored. */
    record Row(
            UUID id,
            Instant openedAt,
            long openingCents,
            Instant closedAt,
            Long expectedCents,
            Long countedCents,
            Long differenceCents,
            String note) {}

    record Flows(long sales, long payments, long expenses, long supplierPayments) {

        long expected(long opening) {
            return opening + sales + payments - expenses - supplierPayments;
        }
    }

    private static final String COLUMNS =
            "id, opened_at, opening_cents, closed_at, expected_cents, counted_cents, difference_cents, note";

    private final JdbcClient jdbc;

    CashRegisterRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Row> findOpen(UUID businessId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM cash_sessions WHERE business_id = ? AND closed_at IS NULL")
                .param(businessId.toString())
                .query(CashRegisterRepository::row)
                .optional();
    }

    List<Row> closed(UUID businessId, int limit) {
        return jdbc.sql("SELECT " + COLUMNS
                        + " FROM cash_sessions WHERE business_id = ? AND closed_at IS NOT NULL"
                        + " ORDER BY closed_at DESC, id LIMIT ?")
                .params(businessId.toString(), limit)
                .query(CashRegisterRepository::row)
                .list();
    }

    void open(UUID businessId, UUID id, long openingCents, Instant now) {
        jdbc.sql("INSERT INTO cash_sessions (id, business_id, opened_at, opening_cents) VALUES (?, ?, ?, ?)")
                .params(id.toString(), businessId.toString(), Timestamp.from(now), openingCents)
                .update();
    }

    void close(UUID businessId, UUID id, Instant now, long expected, long counted, String note) {
        jdbc.sql("""
                        UPDATE cash_sessions SET closed_at = ?, expected_cents = ?, counted_cents = ?,
                                                 difference_cents = ?, note = ?
                        WHERE business_id = ? AND id = ?
                        """)
                .params(
                        Timestamp.from(now),
                        expected,
                        counted,
                        counted - expected,
                        note,
                        businessId.toString(),
                        id.toString())
                .update();
    }

    /**
     * Cash that moved while the register was open: entries <em>recorded</em> in [from, to], since
     * that is when the money went into or out of the drawer, whatever date they carry.
     */
    Flows flows(UUID businessId, Instant from, Instant to) {
        return new Flows(
                sum("SELECT COALESCE(SUM(total_cents), 0) FROM sales", businessId, from, to),
                sum("SELECT COALESCE(SUM(amount_cents), 0) FROM customer_payments", businessId, from, to),
                sum("SELECT COALESCE(SUM(amount_cents), 0) FROM expenses", businessId, from, to),
                sum("SELECT COALESCE(SUM(amount_cents), 0) FROM supplier_payments", businessId, from, to));
    }

    private long sum(String select, UUID businessId, Instant from, Instant to) {
        return jdbc.sql(select
                        + " WHERE business_id = ? AND payment_method = 'CASH' AND created_at >= ? AND created_at <= ?")
                .params(businessId.toString(), Timestamp.from(from), Timestamp.from(to))
                .query(Long.class)
                .single();
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column) == null ? null : rs.getLong(column);
    }

    private static Row row(ResultSet rs, int n) throws SQLException {
        Timestamp closed = rs.getTimestamp("closed_at");
        return new Row(
                UUID.fromString(rs.getString("id")),
                rs.getTimestamp("opened_at").toInstant(),
                rs.getLong("opening_cents"),
                closed == null ? null : closed.toInstant(),
                nullableLong(rs, "expected_cents"),
                nullableLong(rs, "counted_cents"),
                nullableLong(rs, "difference_cents"),
                rs.getString("note"));
    }
}

/** One open cash register per business; selling does not require one. */
@Service
class CashRegisterService {

    static final int HISTORY = 30;

    private final CashRegisterRepository registers;
    private final Clock clock;

    CashRegisterService(CashRegisterRepository registers, Clock clock) {
        this.registers = registers;
        this.clock = clock;
    }

    CashRegisterResponse current(UUID businessId) {
        CashSession open =
                registers.findOpen(businessId).map(row -> view(businessId, row)).orElse(null);
        CashSession last = registers.closed(businessId, 1).stream()
                .findFirst()
                .map(row -> view(businessId, row))
                .orElse(null);
        return new CashRegisterResponse(open, last);
    }

    List<CashSession> history(UUID businessId) {
        return registers.closed(businessId, HISTORY).stream()
                .map(row -> view(businessId, row))
                .toList();
    }

    @Transactional
    CashSession open(UUID businessId, OpenRegisterRequest request) {
        long opening = nonNegative("openingAmount", request.openingAmount());
        if (registers.findOpen(businessId).isPresent()) {
            throw new ConflictException("cash_register_open");
        }
        UUID id = UUID.randomUUID();
        registers.open(businessId, id, opening, clock.instant());
        return view(businessId, registers.findOpen(businessId).orElseThrow());
    }

    @Transactional
    CashSession close(UUID businessId, CloseRegisterRequest request) {
        long counted = nonNegative("countedAmount", request.countedAmount());
        String note = Text.optional("note", request.note(), 500);
        CashRegisterRepository.Row open =
                registers.findOpen(businessId).orElseThrow(() -> new ConflictException("cash_register_closed"));
        Instant now = clock.instant();
        long expected = registers.flows(businessId, open.openedAt(), now).expected(open.openingCents());
        registers.close(businessId, open.id(), now, expected, counted, note);
        return view(businessId, registers.closed(businessId, 1).getFirst());
    }

    private CashSession view(UUID businessId, CashRegisterRepository.Row row) {
        Instant until = row.closedAt() != null ? row.closedAt() : clock.instant();
        CashRegisterRepository.Flows flows = registers.flows(businessId, row.openedAt(), until);
        long expected = row.expectedCents() != null ? row.expectedCents() : flows.expected(row.openingCents());
        return new CashSession(
                row.id(),
                row.openedAt(),
                Money.format(row.openingCents()),
                Money.format(flows.sales()),
                Money.format(flows.payments()),
                Money.format(flows.expenses()),
                Money.format(flows.supplierPayments()),
                Money.format(expected),
                row.closedAt(),
                row.countedCents() == null ? null : Money.format(row.countedCents()),
                row.differenceCents() == null ? null : Money.format(row.differenceCents()),
                row.note());
    }

    /** A cash amount that may be zero (an empty drawer); otherwise parsed like any amount. */
    private static long nonNegative(String field, String raw) {
        if (raw != null && raw.strip().matches("0+(\\.0{1,2})?")) {
            return 0;
        }
        return Money.parseCents(field, raw);
    }
}

@RestController
class CashRegisterController {

    private final CashRegisterService registers;
    private final CurrentBusiness business;

    CashRegisterController(CashRegisterService registers, CurrentBusiness business) {
        this.registers = registers;
        this.business = business;
    }

    @GetMapping("/api/cash-register")
    CashRegisterResponse current() {
        return registers.current(business.id());
    }

    @GetMapping("/api/cash-register/history")
    List<CashSession> history() {
        return registers.history(business.id());
    }

    @PostMapping(path = "/api/cash-register/open", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    CashSession open(@RequestBody OpenRegisterRequest request) {
        return registers.open(business.id(), request);
    }

    @PostMapping(path = "/api/cash-register/close", consumes = MediaType.APPLICATION_JSON_VALUE)
    CashSession close(@RequestBody CloseRegisterRequest request) {
        return registers.close(business.id(), request);
    }
}
