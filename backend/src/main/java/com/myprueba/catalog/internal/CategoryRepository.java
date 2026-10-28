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
class CategoryRepository {

    private static final RowMapper<CategoryResponse> MAPPER =
            (rs, n) -> new CategoryResponse(UUID.fromString(rs.getString("id")), rs.getString("name"));

    private final JdbcClient jdbc;

    CategoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<CategoryResponse> findAll(UUID businessId) {
        return jdbc.sql("SELECT id, name FROM product_categories WHERE business_id = ? ORDER BY name_key, id")
                .param(businessId.toString())
                .query(MAPPER)
                .list();
    }

    Optional<CategoryResponse> find(UUID businessId, UUID id) {
        return jdbc.sql("SELECT id, name FROM product_categories WHERE business_id = ? AND id = ?")
                .params(businessId.toString(), id.toString())
                .query(MAPPER)
                .optional();
    }

    Optional<CategoryResponse> findByName(UUID businessId, String nameKey) {
        return jdbc.sql("SELECT id, name FROM product_categories WHERE business_id = ? AND name_key = ?")
                .params(businessId.toString(), nameKey)
                .query(MAPPER)
                .optional();
    }

    void insert(UUID businessId, CategoryResponse category, Instant now) {
        jdbc.sql("""
                        INSERT INTO product_categories (id, business_id, name, name_key, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        category.id().toString(),
                        businessId.toString(),
                        category.name(),
                        ProductService.key(category.name()),
                        Timestamp.from(now),
                        Timestamp.from(now))
                .update();
    }

    void update(UUID businessId, CategoryResponse category, Instant now) {
        jdbc.sql(
                        "UPDATE product_categories SET name = ?, name_key = ?, updated_at = ? WHERE business_id = ? AND id = ?")
                .params(
                        category.name(),
                        ProductService.key(category.name()),
                        Timestamp.from(now),
                        businessId.toString(),
                        category.id().toString())
                .update();
    }

    int delete(UUID businessId, UUID id) {
        return jdbc.sql("DELETE FROM product_categories WHERE business_id = ? AND id = ?")
                .params(businessId.toString(), id.toString())
                .update();
    }
}
