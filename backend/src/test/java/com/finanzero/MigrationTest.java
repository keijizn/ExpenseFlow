package com.finanzero;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class MigrationTest {
    @Test void upgradingLegacySchemaPreservesBalancesAndInvalidatesOldSessions() throws Exception {
        String url = "jdbc:h2:mem:legacy;MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("1").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            sql.execute("alter table debt drop column account_id");
            sql.execute("insert into app_user (id,name,email,password_hash,verified,role,auth_token) values (1,'Legacy','legacy@example.test','unused',true,'USER','old-token')");
            sql.execute("insert into wallet_account (name,balance,card_limit,owner_id) values ('Conta',123.45,500,1)");
        }
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("select balance from wallet_account")) { rows.next(); assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("123.45"); }
            try (var rows = sql.executeQuery("select auth_token from app_user")) { rows.next(); assertThat(rows.getString(1)).isNull(); }
            sql.executeQuery("select account_id from debt").close();
        }
    }
}
