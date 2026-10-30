package com.example.spendi.client;

import com.sun.net.httpserver.HttpServer;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class ApiClientTest {
    private HttpServer server;
    private String base;
    private String response;
    private int status=200;
    private final List<String> keys=new ArrayList<>();
    private final List<String> auth=new ArrayList<>();
    private static final String ENTRY="{\"id\":\"7f7c1177-f02c-4d4a-9949-486926327d21\",\"amount\":\"25.50\",\"currency\":\"USD\",\"type\":\"EXPENSE\",\"category\":\"Food\",\"date\":\"2027-07-15\",\"note\":\"Lunch\",\"version\":1}";
    @Before public void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange -> {
            keys.add(exchange.getRequestHeaders().getFirst("Idempotency-Key")); auth.add(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getRequestBody().close();
            if(status==302) exchange.getResponseHeaders().set("Location",base+"/redirect-target");
            byte[] bytes=response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");
            exchange.sendResponseHeaders(status,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start(); base="http://127.0.0.1:"+server.getAddress().getPort(); response=ENTRY;
    }
    @After public void stop() { server.stop(0); }
    private ApiClient client() { return new ApiClient(base,"private-test-token",true); }
    @Test public void releaseModeRejectsCleartextAndUnsafeEndpoints() {
        assertThrows(IllegalArgumentException.class,()->new ApiClient(base,"token",false));
        assertThrows(IllegalArgumentException.class,()->new ApiClient("http://example.com","token",true));
        assertThrows(IllegalArgumentException.class,()->new ApiClient("https://user:password@example.com","token",false));
        assertThrows(IllegalArgumentException.class,()->new ApiClient("https://example.com?token=secret","token",false));
        assertThrows(IllegalArgumentException.class,()->new ApiClient("https://example.com","bad\ntoken",false));
    }
    @Test public void createAndRetrySendTheSameIdempotencyKeyAndBearerHeader() throws Exception {
        ApiClient.Entry input=new ApiClient.Entry(null,"25.50","EXPENSE","Food","2027-07-15","Lunch",0);
        assertEquals("25.50",client().save(input,"same-key").amount);
        client().save(input,"same-key");
        assertEquals(List.of("same-key","same-key"),keys);
        assertEquals(List.of("Bearer private-test-token","Bearer private-test-token"),auth);
    }
    @Test public void unauthorizedAndConflictsAreNotConvertedIntoEmptySuccess() {
        status=401; response="{\"code\":\"unauthorized\"}";
        assertEquals(401,assertThrows(ApiException.class,()->client().get("7f7c1177-f02c-4d4a-9949-486926327d21")).status);
        status=409; response="{\"code\":\"version_conflict\"}";
        assertEquals("version_conflict",assertThrows(ApiException.class,()->client().save(new ApiClient.Entry(null,"1","EXPENSE","Food","2027-07-15","",0),"key")).code);
    }
    @Test public void redirectsAreRejectedWithoutForwardingCredentials() {
        status=302; response="";
        assertEquals(302,assertThrows(ApiException.class,()->client().get("7f7c1177-f02c-4d4a-9949-486926327d21")).status);
        assertEquals(1,auth.size());
    }
    @Test public void malformedAndOversizedResponsesFailExplicitly() {
        response="not-json";
        assertThrows(java.io.IOException.class,()->client().list("2027-07"));
        response="x".repeat(1_048_577);
        assertThrows(java.io.IOException.class,()->client().list("2027-07"));
    }
    @Test public void fullPagesAreFetchedInsteadOfSilentlyTruncatingTheMonth() throws Exception {
        server.removeContext("/");
        server.createContext("/",exchange -> {
            String items=exchange.getRequestURI().getQuery().contains("offset=0")?String.join(",",java.util.Collections.nCopies(100,ENTRY)):ENTRY;
            byte[] bytes=("{\"items\":["+items+"],\"limit\":100,\"offset\":0}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        assertEquals(101,client().list("2027-07").size());
    }
    @Test public void onlyMissingBudgetReturnsNull() throws Exception {
        status=404; response="{\"code\":\"not_found\"}";
        assertNull(client().budget("2027-07"));
        status=401;
        assertThrows(ApiException.class,()->client().budget("2027-07"));
    }
    @Test public void budgetAndSummaryUseExactMoneyStrings() throws Exception {
        response="{\"month\":\"2027-07\",\"amount\":\"100.00\",\"currency\":\"USD\",\"version\":2}";
        assertEquals(2,client().saveBudget("2027-07","100.00",1).version);
        response="{\"currency\":\"USD\",\"income\":\"0.00\",\"expenses\":\"184467440737095516.14\",\"balance\":\"-184467440737095516.14\",\"budget\":null,\"remaining\":null}";
        ApiClient.Summary summary=client().summary("2027-07");
        assertEquals("184467440737095516.14",summary.expenses); assertNull(summary.budget);
    }
}
