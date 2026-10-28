package com.myprueba.suppliers.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SupplierRepository {

    private static final RowMapper<SupplierResponse> MAPPER = (rs, n) -> new SupplierResponse(
            UUID.fromString(rs.getString("id")),
            rs.getString("name"),
            rs.getString("phone"),
            rs.getString("document"),
            rs.getString("note"));

    private final JdbcClient jdbc;

    SupplierRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<SupplierResponse> findAll(UUID businessId) {
        return jdbc.sql(
                        "SELECT id, name, phone, document, note FROM suppliers WHERE business_id = ? ORDER BY name_key, id")
                .param(businessId.toString())
                .query(MAPPER)
                .list();
    }

    Optional<SupplierResponse> find(UUID businessId, UUID id) {
        return jdbc.sql("SELECT id, name, phone, document, note FROM suppliers WHERE business_id = ? AND id = ?")
                .params(businessId.toString(), id.toString())
                .query(MAPPER)
                .optional();
    }

    void insert(UUID businessId, SupplierResponse supplier, Instant now) {
        jdbc.sql("""
                        INSERT INTO suppliers (id, business_id, name, name_key, phone, document, note, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        supplier.id().toString(),
                        businessId.toString(),
                        supplier.name(),
                        SupplierService.key(supplier.name()),
                        supplier.phone(),
                        supplier.document(),
                        supplier.note(),
                        Timestamp.from(now),
                        Timestamp.from(now))
                .update();
    }

    void update(UUID businessId, SupplierResponse supplier, Instant now) {
        jdbc.sql("""
                        UPDATE suppliers SET name = ?, name_key = ?, phone = ?, document = ?, note = ?, updated_at = ?
                        WHERE business_id = ? AND id = ?
                        """)
                .params(
                        supplier.name(),
                        SupplierService.key(supplier.name()),
                        supplier.phone(),
                        supplier.document(),
                        supplier.note(),
                        Timestamp.from(now),
                        businessId.toString(),
                        supplier.id().toString())
                .update();
    }

    void delete(UUID businessId, UUID id) {
        jdbc.sql("DELETE FROM suppliers WHERE business_id = ? AND id = ?")
                .params(businessId.toString(), id.toString())
                .update();
    }
}
