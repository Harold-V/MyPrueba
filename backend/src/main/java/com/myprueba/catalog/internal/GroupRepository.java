package com.myprueba.catalog.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Product groups: the common name and category of a product's variants. */
@Repository
class GroupRepository {

    record Group(UUID id, String name, UUID categoryId) {}

    private final JdbcClient jdbc;

    GroupRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Group> find(UUID businessId, UUID id) {
        return jdbc.sql("SELECT id, name, category_id FROM product_groups WHERE business_id = ? AND id = ?")
                .params(businessId.toString(), id.toString())
                .query((rs, n) -> new Group(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("name"),
                        rs.getString("category_id") == null ? null : UUID.fromString(rs.getString("category_id"))))
                .optional();
    }

    void insert(UUID businessId, Group group, Instant now) {
        jdbc.sql("""
                        INSERT INTO product_groups (id, business_id, name, category_id, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        group.id().toString(),
                        businessId.toString(),
                        group.name(),
                        group.categoryId() == null ? null : group.categoryId().toString(),
                        Timestamp.from(now),
                        Timestamp.from(now))
                .update();
    }

    void update(UUID businessId, Group group, Instant now) {
        jdbc.sql("UPDATE product_groups SET name = ?, category_id = ?, updated_at = ? WHERE business_id = ? AND id = ?")
                .params(
                        group.name(),
                        group.categoryId() == null ? null : group.categoryId().toString(),
                        Timestamp.from(now),
                        businessId.toString(),
                        group.id().toString())
                .update();
    }

    void delete(UUID businessId, UUID id) {
        jdbc.sql("DELETE FROM products WHERE business_id = ? AND group_id = ?")
                .params(businessId.toString(), id.toString())
                .update();
        jdbc.sql("DELETE FROM product_groups WHERE business_id = ? AND id = ?")
                .params(businessId.toString(), id.toString())
                .update();
    }
}
