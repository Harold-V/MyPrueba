package com.myprueba.catalog.internal;

import com.myprueba.catalog.ProductView;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ProductRepository {

    private static final RowMapper<ProductView> MAPPER = (rs, n) ->
            new ProductView(UUID.fromString(rs.getString("id")), rs.getString("name"), rs.getLong("price_cents"));

    private final JdbcClient jdbc;

    ProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<ProductView> findAll(UUID businessId) {
        return jdbc.sql("SELECT id, name, price_cents FROM products WHERE business_id = ? ORDER BY name_key, id")
                .param(businessId.toString())
                .query(MAPPER)
                .list();
    }

    Optional<ProductView> find(UUID businessId, UUID id) {
        return jdbc.sql("SELECT id, name, price_cents FROM products WHERE business_id = ? AND id = ?")
                .params(businessId.toString(), id.toString())
                .query(MAPPER)
                .optional();
    }

    boolean nameTaken(UUID businessId, String nameKey, UUID exceptId) {
        return jdbc.sql("SELECT COUNT(*) FROM products WHERE business_id = ? AND name_key = ? AND id <> ?")
                        .params(businessId.toString(), nameKey, exceptId == null ? "" : exceptId.toString())
                        .query(Long.class)
                        .single()
                > 0;
    }

    void insert(UUID businessId, UUID id, String name, String nameKey, long priceCents, Instant now) {
        jdbc.sql("""
                        INSERT INTO products (id, business_id, name, name_key, price_cents, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id.toString(),
                        businessId.toString(),
                        name,
                        nameKey,
                        priceCents,
                        Timestamp.from(now),
                        Timestamp.from(now))
                .update();
    }

    int update(UUID businessId, UUID id, String name, String nameKey, long priceCents, Instant now) {
        return jdbc.sql("""
                        UPDATE products SET name = ?, name_key = ?, price_cents = ?, updated_at = ?
                        WHERE business_id = ? AND id = ?
                        """)
                .params(name, nameKey, priceCents, Timestamp.from(now), businessId.toString(), id.toString())
                .update();
    }

    int delete(UUID businessId, UUID id) {
        return jdbc.sql("DELETE FROM products WHERE business_id = ? AND id = ?")
                .params(businessId.toString(), id.toString())
                .update();
    }
}
