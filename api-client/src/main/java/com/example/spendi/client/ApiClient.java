package com.example.spendi.client;

import com.example.spendi.core.AmountParser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Java 17 / Android API 26 compatible HTTP client. Call only from an IO executor. */
public final class ApiClient {
    public static final class Entry {
        public final String id, amount, type, category, date, note;
        public final long version;
        public Entry(String id, String amount, String type, String category, String date, String note, long version) {
            this.id=id; this.amount=amount; this.type=type; this.category=category; this.date=date; this.note=note; this.version=version;
        }
    }
    public static final class Budget {
        public final String month, amount;
        public final long version;
        public Budget(String month, String amount, long version) { this.month=month; this.amount=amount; this.version=version; }
    }
    public static final class Summary {
        public final String income,expenses,balance,budget,remaining;
        private Summary(JSONObject json) throws JSONException {
            if(!"USD".equals(json.getString("currency"))) throw new JSONException("Unsupported currency");
            income=json.getString("income"); expenses=json.getString("expenses"); balance=json.getString("balance");
            budget=json.isNull("budget")?null:json.getString("budget"); remaining=json.isNull("remaining")?null:json.getString("remaining");
            for(String value:new String[]{income,expenses,balance}) if(!value.matches("-?[0-9]+\\.[0-9]{2}")) throw new JSONException("Invalid money");
            if((budget==null)!=(remaining==null)) throw new JSONException("Invalid budget summary");
            if(budget!=null) {
                try { AmountParser.parsePositiveMinor(budget); } catch(IllegalArgumentException exception) { throw new JSONException("Invalid budget"); }
                if(!remaining.matches("-?[0-9]+\\.[0-9]{2}")) throw new JSONException("Invalid money");
            }
        }
    }
    private final String baseUrl, token;
    public ApiClient(String baseUrl, String token, boolean allowLocalHttp) {
        URI uri = URI.create(baseUrl == null ? "" : baseUrl.trim());
        String host = uri.getHost();
        boolean localHttp = allowLocalHttp && "http".equals(uri.getScheme())
                && ("10.0.2.2".equals(host) || "127.0.0.1".equals(host) || "localhost".equals(host));
        if (host == null || (!"https".equals(uri.getScheme()) && !localHttp)
                || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null
                || (uri.getPath()!=null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("Use an HTTPS server URL, or a loopback/emulator HTTP URL in debug builds");
        }
        if (token==null || token.trim().isEmpty() || token.length()>8192 || token.contains("\n") || token.contains("\r")) {
            throw new IllegalArgumentException("An access token is required");
        }
        this.baseUrl=uri.toString().replaceAll("/$", ""); this.token=token.trim();
    }

    public List<Entry> list(String month) throws IOException {
        List<Entry> entries = new ArrayList<>();
        for (int offset=0; offset<=10000; offset+=100) {
            JSONObject page=request("GET", "/v1/transactions?month="+URLEncoder.encode(month,"UTF-8")+"&limit=100&offset="+offset, null, null);
            try {
                JSONArray items=page.getJSONArray("items");
                if(items.length()>100) throw new JSONException("Invalid page size");
                for(int i=0;i<items.length();i++) entries.add(entry(items.getJSONObject(i)));
                if(items.length()<100) return entries;
            } catch(JSONException | IllegalArgumentException exception) { throw invalid(); }
        }
        throw new IOException("This month exceeds the supported listing size; totals were not truncated");
    }
    public Entry get(String id) throws IOException { return parseEntry(request("GET", path(id), null, null)); }
    public Entry save(Entry entry, String key) throws IOException {
        try {
            JSONObject body=new JSONObject().put("amount",entry.amount).put("type",entry.type).put("category",entry.category)
                    .put("date",entry.date).put("note",entry.note);
            if(entry.id!=null) body.put("version",entry.version);
            return parseEntry(request(entry.id==null?"POST":"PUT",entry.id==null?"/v1/transactions":path(entry.id),body,entry.id==null?key:null));
        } catch(JSONException exception) { throw invalid(); }
    }
    public void delete(String id,long version) throws IOException { request("DELETE",path(id)+"?version="+version,null,null); }
    public Budget budget(String month) throws IOException {
        try { return parseBudget(request("GET","/v1/budgets/"+URLEncoder.encode(month,"UTF-8"),null,null)); }
        catch(ApiException exception) { if(exception.status==404) return null; throw exception; }
    }
    public Budget saveBudget(String month,String amount,long version) throws IOException {
        try {
            JSONObject body=new JSONObject().put("amount",amount);
            if(version>0) body.put("version",version);
            return parseBudget(request("PUT","/v1/budgets/"+URLEncoder.encode(month,"UTF-8"),body,null));
        } catch(JSONException exception) { throw invalid(); }
    }
    public Summary summary(String month) throws IOException {
        try { return new Summary(request("GET","/v1/summaries/"+URLEncoder.encode(month,"UTF-8"),null,null)); }
        catch(JSONException exception) { throw invalid(); }
    }
    private static String path(String id) { return "/v1/transactions/"+UUID.fromString(id); }
    private static Entry parseEntry(JSONObject json) throws IOException {
        try { return entry(json); } catch(JSONException | IllegalArgumentException exception) { throw invalid(); }
    }
    private static Entry entry(JSONObject json) throws JSONException {
        if(!"USD".equals(json.getString("currency"))) throw new JSONException("Unsupported currency");
        String amount=json.getString("amount"); AmountParser.parsePositiveMinor(amount);
        String type=json.getString("type");
        try { java.time.LocalDate.parse(json.getString("date")); }
        catch(java.time.DateTimeException exception) { throw new JSONException("Invalid date"); }
        if(!"INCOME".equals(type) && !"EXPENSE".equals(type)) throw new JSONException("Invalid type");
        return new Entry(UUID.fromString(json.getString("id")).toString(),amount,type,json.getString("category"),
                json.getString("date"),json.getString("note"),version(json));
    }
    private static Budget parseBudget(JSONObject json) throws IOException {
        try {
            if(!"USD".equals(json.getString("currency"))) throw new JSONException("Unsupported currency");
            String amount=json.getString("amount"); AmountParser.parsePositiveMinor(amount);
            return new Budget(json.getString("month"),amount,version(json));
        } catch(JSONException | IllegalArgumentException exception) { throw invalid(); }
    }
    private static long version(JSONObject json) throws JSONException {
        long value=Long.parseLong(json.get("version").toString());
        if(value<=0) throw new JSONException("Invalid version");
        return value;
    }
    private JSONObject request(String method,String path,JSONObject body,String key) throws IOException {
        HttpURLConnection connection=(HttpURLConnection)new URL(baseUrl+path).openConnection();
        try {
            connection.setConnectTimeout(4000); connection.setReadTimeout(8000); connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method); connection.setRequestProperty("Authorization","Bearer "+token);
            connection.setRequestProperty("Accept","application/json");
            if(key!=null) connection.setRequestProperty("Idempotency-Key",key);
            if(body!=null) {
                byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setDoOutput(true); connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                try(var output=connection.getOutputStream()) { output.write(bytes); }
            }
            int status=connection.getResponseCode();
            if(status>=300 && status<400) throw new ApiException(status,"redirect_rejected");
            if(status==204) return new JSONObject();
            String response=read(status>=400?connection.getErrorStream():connection.getInputStream());
            if(status>=400) {
                String code="request_failed";
                try { code=new JSONObject(response).optString("code",code); } catch(JSONException ignored) { }
                throw new ApiException(status,code);
            }
            try { return new JSONObject(response); } catch(JSONException exception) { throw invalid(); }
        } finally { connection.disconnect(); }
    }
    private static String read(InputStream stream) throws IOException {
        if(stream==null) return "";
        try(InputStream input=stream; ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] bytes=new byte[4096]; int size;
            while((size=input.read(bytes))!=-1) {
                if(output.size()+size>1_048_576) throw new IOException("API response exceeds the supported size");
                output.write(bytes,0,size);
            }
            return new String(output.toByteArray(),StandardCharsets.UTF_8);
        }
    }
    private static IOException invalid() { return new IOException("Invalid API response"); }
}
