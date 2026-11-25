import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** LOGIN SERVICE - port 8081 */
public class LoginService {
    static final Map<String, String> users = new ConcurrentHashMap<>();    // username -> password
    static final Map<String, String> tokens = new ConcurrentHashMap<>();   // token -> username

    public static void main(String[] args) throws Exception {
        users.put("john", "1234");   // demo account, you can delete this line
        HttpServer s = HttpServer.create(new InetSocketAddress(8081), 0);

        // POST /register   {"username":"sapna","password":"abc"}
        Common.route(s, "/register", ex -> {
            String b = Common.body(ex);
            String u = Common.field(b, "username"), p = Common.field(b, "password");
            if (u == null || u.isBlank() || p == null || p.isBlank()) {
                Common.send(ex, 400, "{\"error\":\"Enter a username and password\"}");
            } else if (users.putIfAbsent(u.trim(), p) != null) {
                Common.send(ex, 409, "{\"error\":\"That username is already taken\"}");
            } else {
                Common.send(ex, 200, "{\"status\":\"created\"}");
            }
        });

        // POST /login   {"username":"john","password":"1234"}
        Common.route(s, "/login", ex -> {
            String b = Common.body(ex);
            String u = Common.field(b, "username"), p = Common.field(b, "password");
            if (u != null && p != null && p.equals(users.get(u))) {
                String token = UUID.randomUUID().toString();
                tokens.put(token, u);
                Common.send(ex, 200, "{\"token\":\"" + token + "\",\"username\":\"" + u + "\"}");
            } else {
                Common.send(ex, 401, "{\"error\":\"Wrong username or password\"}");
            }
        });

        // GET /validate?token=xxx   (used by the Order Service)
        Common.route(s, "/validate", ex -> {
            String user = tokens.get(Common.query(ex, "token"));
            Common.send(ex, user != null ? 200 : 401,
                    user != null ? "{\"valid\":true,\"username\":\"" + user + "\"}" : "{\"valid\":false}");
        });

        s.start();
        System.out.println("Login Service running on http://localhost:8081");
    }
}