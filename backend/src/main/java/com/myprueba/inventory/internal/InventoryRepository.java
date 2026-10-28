package com.myprueba.inventory.internal;

import com.myprueba.shared.Quantity;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class InventoryRepository {

    record Stock(UUID productId, boolean tracked, long quantityMilli, Long minMilli) {}

    private final JdbcClient jdbc;

    InventoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Map<UUID, Stock> findAll(UUID businessId) {
        return jdbc
                .sql("SELECT product_id, tracked, quantity_milli, min_milli FROM stock_items WHERE business_id = ?")
                .param(businessId.toString())
                .query((rs, n) -> new Stock(
                        UUID.fromString(rs.getString("product_id")),
                        rs.getBoolean("tracked"),
                        rs.getLong("quantity_milli"),
                        rs.getObject("min_milli") == null ? null : rs.getLong("min_milli")))
                .list()
                .stream()
                .collect(Collectors.toMap(Stock::productId, s -> s));
    }

    Optional<Stock> find(UUID businessId, UUID productId) {
        return jdbc.sql("""
                        SELECT product_id, tracked, quantity_milli, min_milli FROM stock_items
                        WHERE business_id = ? AND product_id = ?
                        """)
                .params(businessId.toString(), productId.toString())
                .query((rs, n) -> new Stock(
                        productId,
                        rs.getBoolean("tracked"),
                        rs.getLong("quantity_milli"),
                        rs.getObject("min_milli") == null ? null : rs.getLong("min_milli")))
                .optional();
    }

    void save(UUID businessId, Stock stock, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE stock_items SET tracked = ?, quantity_milli = ?, min_milli = ?, updated_at = ?
                        WHERE business_id = ? AND product_id = ?
                        """)
                .params(
                        stock.tracked(),
                        stock.quantityMilli(),
                        stock.minMilli(),
                        Timestamp.from(now),
                        businessId.toString(),
                        stock.productId().toString())
                .update();
        if (updated == 0) {
            jdbc.sql("""
                            INSERT INTO stock_items (product_id, business_id, tracked, quantity_milli, min_milli, updated_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """)
                    .params(
                            stock.productId().toString(),
                            businessId.toString(),
                            stock.tracked(),
                            stock.quantityMilli(),
                            stock.minMilli(),
                            Timestamp.from(now))
                    .update();
        }
    }

    void addMovement(
            UUID businessId,
            UUID productId,
            MovementType type,
            long changeMilli,
            long stockAfterMilli,
            String note,
            UUID saleId,
            Instant at) {
        long seq = jdbc.sql("SELECT COALESCE(MAX(seq), 0) + 1 FROM stock_movements WHERE product_id = ?")
                .param(productId.toString())
                .query(Long.class)
                .single();
        jdbc.sql("""
                        INSERT INTO stock_movements (id, product_id, business_id, seq, type, change_milli,
                                                     stock_after_milli, note, sale_id, occurred_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        UUID.randomUUID().toString(),
                        productId.toString(),
                        businessId.toString(),
                        seq,
                        type.name(),
                        changeMilli,
                        stockAfterMilli,
                        note,
                        saleId == null ? null : saleId.toString(),
                        Timestamp.from(at))
                .update();
    }

    List<StockMovement> movements(UUID businessId, UUID productId, int limit) {
        return jdbc.sql("""
                        SELECT type, change_milli, stock_after_milli, note, occurred_at FROM stock_movements
                        WHERE business_id = ? AND product_id = ? ORDER BY seq DESC LIMIT ?
                        """)
                .params(businessId.toString(), productId.toString(), limit)
                .query((rs, n) -> new StockMovement(
                        MovementType.valueOf(rs.getString("type")),
                        Quantity.format(rs.getLong("change_milli")),
                        Quantity.format(rs.getLong("stock_after_milli")),
                        rs.getString("note"),
                        rs.getTimestamp("occurred_at").toInstant()))
                .list();
    }
}
