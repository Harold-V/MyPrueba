package com.myprueba.accounts.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Opaque session tokens: only their SHA-256 hash is stored, so logging out really ends them. */
@Service
class SessionService {

    static final Duration IDLE_TIMEOUT = Duration.ofDays(7);
    private static final Duration TOUCH_INTERVAL = Duration.ofHours(1);

    private final JdbcClient jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    SessionService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    String open(UUID userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        jdbc.sql("""
                        INSERT INTO sessions (token_hash, user_id, expires_at, last_seen_at, created_at)
                        VALUES (?, ?, ?, ?, ?)
                        """)
                .params(
                        hash(token),
                        userId.toString(),
                        Timestamp.from(now.plus(IDLE_TIMEOUT)),
                        Timestamp.from(now),
                        Timestamp.from(now))
                .update();
        return token;
    }

    Optional<OwnerPrincipal> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = hash(token);
        Instant now = clock.instant();
        record Row(UUID userId, UUID businessId, ZoneId zone, Instant lastSeen) {}
        Optional<Row> row = jdbc.sql("""
                        SELECT s.user_id, s.last_seen_at, b.id AS business_id, b.time_zone
                        FROM sessions s JOIN businesses b ON b.owner_user_id = s.user_id
                        WHERE s.token_hash = ? AND s.expires_at > ?
                        """)
                .params(tokenHash, Timestamp.from(now))
                .query((rs, n) -> new Row(
                        UUID.fromString(rs.getString("user_id")),
                        UUID.fromString(rs.getString("business_id")),
                        ZoneId.of(rs.getString("time_zone")),
                        rs.getTimestamp("last_seen_at").toInstant()))
                .optional();
        row.filter(r -> r.lastSeen().plus(TOUCH_INTERVAL).isBefore(now))
                .ifPresent(r -> jdbc.sql("UPDATE sessions SET last_seen_at = ?, expires_at = ? WHERE token_hash = ?")
                        .params(Timestamp.from(now), Timestamp.from(now.plus(IDLE_TIMEOUT)), tokenHash)
                        .update());
        return row.map(r -> new OwnerPrincipal(r.userId(), r.businessId(), r.zone()));
    }

    void close(String token) {
        if (token != null && !token.isBlank()) {
            jdbc.sql("DELETE FROM sessions WHERE token_hash = ?")
                    .param(hash(token))
                    .update();
        }
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
