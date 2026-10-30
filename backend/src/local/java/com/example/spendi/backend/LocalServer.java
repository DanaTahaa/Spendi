package com.example.spendi.backend;

import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/** Development-only persistent PostgreSQL + API. Not included in the production distribution. */
public final class LocalServer {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath().normalize(); Files.createDirectories(root);
        Path credentials=root.resolve("connection.json");
        String token;
        if(Files.exists(credentials)) token=(String)Json.read(Files.readString(credentials),Map.class).get("token");
        else { byte[] bytes=new byte[32]; new SecureRandom().nextBytes(bytes); token=HexFormat.of().formatHex(bytes); }
        if(token==null || token.length()<32) throw new IllegalStateException("Invalid local credential file");
        final byte[] expected=token.getBytes(StandardCharsets.UTF_8);
        var stopped=new CountDownLatch(1);
        var finished=new CountDownLatch(1);
        try(var postgres=EmbeddedPostgres.builder().setPort(15432).setDataDirectory(root.resolve("postgres-data").toFile())
                .setCleanDataDirectory(false).setServerConfig("listen_addresses","127.0.0.1").start()) {
            try(var connection=postgres.getPostgresDatabase().getConnection();var statement=connection.createStatement()) {
                try(var found=statement.executeQuery("select 1 from pg_database where datname='spendi'")) {
                    if(!found.next()) try(var create=connection.createStatement()) { create.execute("CREATE DATABASE spendi"); }
                }
            }
            HikariConfig pool=new HikariConfig(); pool.setJdbcUrl(postgres.getJdbcUrl("postgres","spendi"));
            pool.setUsername("postgres"); pool.setMaximumPoolSize(4); pool.setConnectionTimeout(5000);
            try(var source=new HikariDataSource(pool)) {
                Main.migrate(source);
                Authenticator auth=supplied -> {
                    if(supplied==null || !MessageDigest.isEqual(expected,supplied.getBytes(StandardCharsets.UTF_8))) throw Authenticator.unauthorized();
                    return "local-demo";
                };
                try(var server=new ApiServer("127.0.0.1",8080,new FinancialRepository(source),auth)) {
                    String instance=UUID.randomUUID().toString();
                    HttpServer control=HttpServer.create(new InetSocketAddress("127.0.0.1",18080),4);
                    control.createContext("/",exchange -> {
                        String authorization=exchange.getRequestHeaders().getFirst("Authorization");
                        if(authorization==null || !authorization.startsWith("Bearer ")
                                || !MessageDigest.isEqual(expected,authorization.substring(7).getBytes(StandardCharsets.UTF_8))) {
                            exchange.sendResponseHeaders(401,-1); exchange.close(); return;
                        }
                        String path=exchange.getRequestURI().getPath();
                        if("/status".equals(path) && "GET".equals(exchange.getRequestMethod())) {
                            byte[] bytes=Json.write(Map.of("instance",instance)).getBytes(StandardCharsets.UTF_8);
                            exchange.getResponseHeaders().set("Content-Type","application/json");
                            exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes);
                        } else if("/stop".equals(path) && "POST".equals(exchange.getRequestMethod())) {
                            exchange.sendResponseHeaders(204,-1); stopped.countDown();
                        } else exchange.sendResponseHeaders(404,-1);
                        exchange.close();
                    });
                    Thread shutdown=new Thread(() -> {
                        stopped.countDown();
                        try { finished.await(15,java.util.concurrent.TimeUnit.SECONDS); }
                        catch(InterruptedException exception) { Thread.currentThread().interrupt(); }
                    }); Runtime.getRuntime().addShutdownHook(shutdown);
                    try {
                        server.start(); control.start();
                        Map<String,Object> settings=Map.of("baseUrl","http://127.0.0.1:8080","emulatorUrl","http://10.0.2.2:8080",
                                "token",token,"instance",instance,"controlUrl","http://127.0.0.1:18080","pid",ProcessHandle.current().pid());
                        Path temporary=root.resolve("connection.json.tmp"); Files.writeString(temporary,Json.write(settings));
                        Files.move(temporary,credentials,StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("Local Spendi API ready at http://127.0.0.1:8080; credentials are in "+credentials);
                        stopped.await();
                    } finally {
                        control.stop(1);
                        try { Runtime.getRuntime().removeShutdownHook(shutdown); } catch(IllegalStateException ignored) { }
                    }
                }
            }
        } finally { finished.countDown(); }
    }
}
