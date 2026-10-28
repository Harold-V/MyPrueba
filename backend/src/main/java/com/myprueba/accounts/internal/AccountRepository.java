package com.myprueba.accounts.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AccountRepository {

    record UserRow(UUID id, String email, String passwordHash) {}

    private final JdbcClient jdbc;

    AccountRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    boolean emailExists(String email) {
        return jdbc.sql("SELECT COUNT(*) FROM users WHERE email = ?")
                        .param(email)
                        .query(Long.class)
                        .single()
                > 0;
    }

    void insertUser(UUID id, String email, String passwordHash, Instant now) {
        jdbc.sql("INSERT INTO users (id, email, password_hash, created_at) VALUES (?, ?, ?, ?)")
                .params(id.toString(), email, passwordHash, Timestamp.from(now))
                .update();
    }

    Optional<UserRow> findUserByEmail(String email) {
        return jdbc.sql("SELECT id, email, password_hash FROM users WHERE email = ?")
                .param(email)
                .query((rs, n) -> new UserRow(
                        UUID.fromString(rs.getString("id")), rs.getString("email"), rs.getString("password_hash")))
                .optional();
    }

    Optional<UserView> findUser(UUID userId) {
        return jdbc.sql("SELECT id, email FROM users WHERE id = ?")
                .param(userId.toString())
                .query((rs, n) -> new UserView(UUID.fromString(rs.getString("id")), rs.getString("email")))
                .optional();
    }

    void insertBusiness(
            UUID id,
            UUID ownerId,
            String name,
            BusinessCategory category,
            String currency,
            String timeZone,
            Instant now) {
        jdbc.sql("""
                        INSERT INTO businesses (id, owner_user_id, name, category, currency, time_zone, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(
                        id.toString(),
                        ownerId.toString(),
                        name,
                        category == null ? null : category.name(),
                        currency,
                        timeZone,
                        Timestamp.from(now),
                        Timestamp.from(now))
                .update();
    }

    Optional<BusinessView> findBusinessByOwner(UUID ownerId) {
        return findBusiness("owner_user_id", ownerId);
    }

    Optional<BusinessView> findBusiness(UUID businessId) {
        return findBusiness("id", businessId);
    }

    private Optional<BusinessView> findBusiness(String column, UUID value) {
        return jdbc.sql(
                        "SELECT id, name, category, currency, time_zone, document, phone, address FROM businesses WHERE "
                                + column + " = ?")
                .param(value.toString())
                .query((rs, n) -> new BusinessView(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("name"),
                        rs.getString("category") == null ? null : BusinessCategory.valueOf(rs.getString("category")),
                        rs.getString("currency"),
                        rs.getString("time_zone"),
                        rs.getString("document"),
                        rs.getString("phone"),
                        rs.getString("address")))
                .optional();
    }

    void updateBusiness(
            UUID businessId,
            String name,
            BusinessCategory category,
            String document,
            String phone,
            String address,
            Instant now) {
        jdbc.sql("""
                        UPDATE businesses SET name = ?, category = ?, document = ?, phone = ?, address = ?, updated_at = ?
                        WHERE id = ?
                        """)
                .params(
                        name,
                        category == null ? null : category.name(),
                        document,
                        phone,
                        address,
                        Timestamp.from(now),
                        businessId.toString())
                .update();
    }
}
