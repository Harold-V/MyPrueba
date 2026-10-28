package com.myprueba.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** V9 numbers existing sales per business, in the order they were recorded, with no discounts. */
class V9MigrationTest {

    @Test
    void existingSalesAreNumberedPerBusiness() throws Exception {
        Path db = Files.createTempFile("v9-migration", ".db");
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + db + "?foreign_keys=true");
        Flyway.configure().dataSource(dataSource).target("8").load().migrate();

        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("""
                        INSERT INTO users (id, email, password_hash, created_at)
                        VALUES ('u1', 'a@b.co', 'x', 0), ('u2', 'c@d.co', 'x', 0)
                        """).update();
        jdbc.sql("""
                        INSERT INTO businesses (id, owner_user_id, name, currency, time_zone, created_at, updated_at)
                        VALUES ('b1', 'u1', 'Uno', 'COP', 'America/Bogota', 0, 0),
                               ('b2', 'u2', 'Dos', 'COP', 'America/Bogota', 0, 0)
                        """).update();
        // Inserted out of order: numbers follow created_at, not insertion or id.
        jdbc.sql("""
                        INSERT INTO sales (id, business_id, kind, total_cents, payment_method, occurred_at,
                                           created_at, updated_at)
                        VALUES ('s-c', 'b1', 'FREE', 300, 'CASH', 0, 3000, 0),
                               ('s-a', 'b1', 'FREE', 100, 'CASH', 0, 1000, 0),
                               ('s-x', 'b2', 'FREE', 900, 'CASH', 0, 2000, 0),
                               ('s-b', 'b1', 'FREE', 200, 'CASH', 0, 2000, 0)
                        """).update();

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(jdbc.sql("SELECT id FROM sales WHERE business_id = 'b1' ORDER BY number")
                        .query(String.class)
                        .list())
                .isEqualTo(List.of("s-a", "s-b", "s-c"));
        assertThat(jdbc.sql("SELECT number FROM sales WHERE id = 's-x'")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM sales WHERE subtotal_cents = total_cents AND discount_cents = 0")
                        .query(Long.class)
                        .single())
                .isEqualTo(4);
        Files.deleteIfExists(db);
    }
}
