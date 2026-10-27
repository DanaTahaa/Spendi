package com.example.spendi.backend;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

public final class Main {
    public static void main(String[] args) throws Exception {
        Configuration configuration = Configuration.from(System.getenv());
        HikariConfig pool = new HikariConfig();
        pool.setJdbcUrl(configuration.dbUrl());
        pool.setUsername(configuration.dbUser());
        pool.setPassword(configuration.dbPassword());
        pool.setMaximumPoolSize(10);
        pool.setConnectionTimeout(5000);
        pool.setValidationTimeout(2000);
        HikariDataSource source = new HikariDataSource(pool);
        try {
            migrate(source);
            var server = new ApiServer(configuration.host(), configuration.port(), new FinancialRepository(source), configuration.authenticator());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> { server.close(); source.close(); }));
            server.start();
            System.out.println("Spendi API listening on " + configuration.host() + ":" + server.port());
        } catch (Exception exception) { source.close(); throw exception; }
    }

    public static void migrate(javax.sql.DataSource source) {
        Flyway.configure().dataSource(source).cleanDisabled(true).locations("classpath:db/migration").load().migrate();
    }
}
