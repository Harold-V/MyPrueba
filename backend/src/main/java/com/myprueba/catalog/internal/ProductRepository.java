package com.myprueba.catalog.internal;

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

    private static final String SELECT = """
            SELECT p.id, p.name, p.price_cents, p.cost_cents, p.category_id, c.name AS category_name, p.barcode,
                   p.group_id, g.name AS group_name, p.variant
            FROM products p
            LEFT JOIN product_categories c ON c.id = p.category_id
            LEFT JOIN product_groups g ON g.id = p.group_id
            """;

    private static final RowMapper<ProductRow> MAPPER = (rs, n) -> new ProductRow(
            UUID.fromString(rs.getString("id")),
            rs.getString("name"),
            rs.getLong("price_cents"),
            rs.getObject("cost_cents") == null ? null : rs.getLong("cost_cents"),
            uuidOrNull(rs.getString("category_id")),
            rs.getString("category_name"),
            rs.getString("barcode"),
            uuidOrNull(rs.getString("group_id")),
            rs.getString("group_name"),
            rs.getString("variant"));

    /** What is stored for a product; its category and group names are joined when read. */
    record NewProduct(
            UUID id,
            String name,
            long priceCents,
            Long costCents,
            UUID categoryId,
            String barcode,
            UUID groupId,
            String variant) {}

    private final JdbcClient jdbc;

    ProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<ProductRow> findAll(UUID businessId) {
        return jdbc.sql(SELECT + " WHERE p.business_id = ? ORDER BY p.name_key, p.id")
                .param(businessId.toString())
                .query(MAPPER)
                .list();
    }

    List<ProductRow> findByGroup(UUID businessId, UUID groupId) {
        return jdbc.sql(SELECT + " WHERE p.business_id = ? AND p.group_id = ? ORDER BY p.name_key, p.id")
                .params(businessId.toString(), groupId.toString())
                .query(MAPPER)
                .list();
    }

    Optional<ProductRow> find(UUID businessId, UUID id) {
        return jdbc.sql(SELECT + " WHERE p.business_id = ? AND p.id = ?")
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

    boolean barcodeTaken(UUID businessId, String barcode, UUID exceptId) {
        return jdbc.sql("SELECT COUNT(*) FROM products WHERE business_id = ? AND barcode = ? AND id <> ?")
                        .params(businessId.toString(), barcode, exceptId == null ? "" : exceptId.toString())
                        .query(Long.class)
                        .single()
                > 0;
    }

    void insert(UUID businessId, NewProduct product, Instant now) {
        jdbc.sql("""
                        INSERT INTO products (id, business_id, name, name_key, price_cents, cost_cents, category_id,
                                              barcode, group_id, variant, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        product.id().toString(),
                        businessId.toString(),
                        product.name(),
                        ProductService.key(product.name()),
                        product.priceCents(),
                        product.costCents(),
                        string(product.categoryId()),
                        product.barcode(),
                        string(product.groupId()),
                        product.variant(),
                        Timestamp.from(now),
                        Timestamp.from(now))
                .update();
    }

    void update(UUID businessId, NewProduct product, Instant now) {
        jdbc.sql("""
                        UPDATE products SET name = ?, name_key = ?, price_cents = ?, cost_cents = ?, category_id = ?,
                                            barcode = ?, variant = ?, updated_at = ?
                        WHERE business_id = ? AND id = ?
                        """)
                .params(
                        product.name(),
                        ProductService.key(product.name()),
                        product.priceCents(),
                        product.costCents(),
                        string(product.categoryId()),
                        product.barcode(),
                        product.variant(),
                        Timestamp.from(now),
                        businessId.toString(),
                        product.id().toString())
                .update();
    }

    int delete(UUID businessId, UUID id) {
        return jdbc.sql("DELETE FROM products WHERE business_id = ? AND id = ?")
                .params(businessId.toString(), id.toString())
                .update();
    }

    private static String string(UUID id) {
        return id == null ? null : id.toString();
    }

    private static UUID uuidOrNull(String value) {
        return value == null ? null : UUID.fromString(value);
    }
}
