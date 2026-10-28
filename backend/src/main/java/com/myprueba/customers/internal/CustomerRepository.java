package com.myprueba.customers.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CustomerRepository {

    private static final RowMapper<CustomerResponse> MAPPER = (rs, n) -> new CustomerResponse(
            UUID.fromString(rs.getString("id")), rs.getString("name"), rs.getString("phone"), rs.getString("note"));

    private final JdbcClient jdbc;

    CustomerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    List<CustomerResponse> findAll(UUID businessId) {
        return jdbc.sql("SELECT id, name, phone, note FROM customers WHERE business_id = ? ORDER BY name_key, id")
                .param(businessId.toString())
                .query(MAPPER)
                .list();
    }

    Optional<CustomerResponse> find(UUID businessId, UUID id) {
        return jdbc.sql("SELECT id, name, phone, note FROM customers WHERE business_id = ? AND id = ?")
                .params(businessId.toString(), id.toString())
                .query(MAPPER)
                .optional();
    }

    void insert(UUID businessId, CustomerResponse customer, Instant now) {
        jdbc.sql("""
                        INSERT INTO customers (id, business_id, name, name_key, phone, note, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        customer.id().toString(),
                        businessId.toString(),
                        customer.name(),
                        CustomerService.key(customer.name()),
                        customer.phone(),
                        customer.note(),
                        Timestamp.from(now),
                        Timestamp.from(now))
                .update();
    }

    void update(UUID businessId, CustomerResponse customer, Instant now) {
        jdbc.sql("""
                        UPDATE customers SET name = ?, name_key = ?, phone = ?, note = ?, updated_at = ?
                        WHERE business_id = ? AND id = ?
                        """)
                .params(
                        customer.name(),
                        CustomerService.key(customer.name()),
                        customer.phone(),
                        customer.note(),
                        Timestamp.from(now),
                        businessId.toString(),
                        customer.id().toString())
                .update();
    }

    void delete(UUID businessId, UUID id) {
        jdbc.sql("DELETE FROM customers WHERE business_id = ? AND id = ?")
                .params(businessId.toString(), id.toString())
                .update();
    }
}
