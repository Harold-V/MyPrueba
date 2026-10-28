package com.myprueba.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** V5 converts data written under the old rules into the Treinta-aligned ones. */
class V5MigrationTest {

    @Test
    void legacyDataIsConverted() throws Exception {
        Path db = Files.createTempFile("v5-migration", ".db");
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + db + "?foreign_keys=true");
        Flyway.configure().dataSource(dataSource).target("4").load().migrate();

        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("INSERT INTO users (id, email, password_hash, created_at) VALUES ('u1', 'a@b.co', 'x', 0)")
                .update();
        jdbc.sql("""
                        INSERT INTO businesses (id, owner_user_id, name, category, currency, time_zone, created_at, updated_at)
                        VALUES ('b1', 'u1', 'Tienda', 'GROCERY', 'COP', 'America/Bogota', 0, 0)
                        """).update();
        jdbc.sql("""
                        INSERT INTO products (id, business_id, name, name_key, price_cents, created_at, updated_at)
                        VALUES ('p1', 'b1', 'Pan', 'pan', 500, 0, 0), ('p2', 'b1', 'Leche', 'leche', 900, 0, 0)
                        """).update();
        jdbc.sql("""
                        INSERT INTO stock_items (product_id, business_id, tracked, quantity_milli, min_milli, updated_at)
                        VALUES ('p1', 'b1', FALSE, 7000, NULL, 0)
                        """).update();
        jdbc.sql("""
                        INSERT INTO expenses (id, business_id, amount_cents, category, payment_method, occurred_at,
                                              created_at, updated_at)
                        VALUES ('e1', 'b1', 100, 'TAXES', 'CASH', 0, 0, 0)
                        """).update();

        Flyway.configure().dataSource(dataSource).load().migrate();

        assertThat(jdbc.sql("SELECT category FROM businesses")
                        .query(String.class)
                        .single())
                .isEqualTo("LOCAL_MARKET");
        assertThat(jdbc.sql("SELECT category FROM expenses").query(String.class).single())
                .isEqualTo("OTHER");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM stock_items WHERE tracked = TRUE")
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
        assertThat(jdbc.sql("SELECT quantity_milli FROM stock_items WHERE product_id = 'p1'")
                        .query(Long.class)
                        .single())
                .isEqualTo(7000);
        assertThat(jdbc.sql("SELECT allow_negative_stock FROM businesses")
                        .query(Boolean.class)
                        .single())
                .isTrue();
        Files.deleteIfExists(db);
    }
}
