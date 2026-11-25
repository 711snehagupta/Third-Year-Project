import com.sun.net.httpserver.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.*;

/** Small helpers shared by all three services (no external libraries needed). */
public class Common {
    static final HttpClient client = HttpClient.newHttpClient();

    interface Handler { void handle(HttpExchange ex) throws Exception; }

    /** Register a route; adds CORS + error handling so every service stays tiny. */
    static void route(HttpServer server, String path, Handler h) {
        server.createContext(path, ex -> {
            try {
                if (ex.getRequestMethod().equals("OPTIONS")) { send(ex, 200, "{}"); return; }
                h.handle(ex);
            } catch (Exception e) {
                send(ex, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        });
    }

    static void send(HttpExchange ex, int code, String json) throws IOException {
        var h = ex.getResponseHeaders();
        h.add("Access-Control-Allow-Origin", "*");
        h.add("Access-Control-Allow-Headers", "Content-Type");
        h.add("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        h.add("Content-Type", "application/json");
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    static String body(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    /** Read a simple value from JSON text: "key": "value" or "key": 123 */
    static String field(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*\"?([^\",}\\]]*)\"?").matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** Read a query parameter: /order?id=5 -> query(ex, "id") = "5" */
    static String query(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) return null;
        for (String p : q.split("&")) {
            String[] kv = p.split("=", 2);
            if (kv[0].equals(key) && kv.length == 2) return kv[1];
        }
        return null;
    }

    /** Service-to-service call (e.g. Order -> Login). */
    static String call(String method, String url, String json) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(url));
        if (method.equals("POST")) b.POST(HttpRequest.BodyPublishers.ofString(json == null ? "" : json));
        else b.GET();
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString()).body();
    }
}