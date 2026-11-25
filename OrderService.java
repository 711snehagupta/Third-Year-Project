import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.*;

/** ORDER SERVICE - port 8082 */
public class OrderService {
    record Item(int id, String name, String emoji, int price) {}
    static class Order { int id; String user; int total; boolean paid; }

    static final List<Item> MENU = List.of(
        new Item(1, "Butter Chicken", "🍛", 320), new Item(2, "Paneer Tikka", "🧆", 240),
        new Item(3, "Veg Biryani", "🍚", 200),    new Item(4, "Garlic Naan", "🫓", 60),
        new Item(5, "Masala Dosa", "🥞", 140),    new Item(6, "Mango Lassi", "🥭", 90));

    static final Map<Integer, Order> orders = new ConcurrentHashMap<>();
    static final AtomicInteger nextId = new AtomicInteger(1001);

    static String json(Order o) {
        return "{\"orderId\":" + o.id + ",\"user\":\"" + o.user + "\",\"total\":" + o.total + ",\"paid\":" + o.paid + "}";
    }

    public static void main(String[] args) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(8082), 0);

        // GET /menu
        Common.route(s, "/menu", ex -> {
            StringBuilder sb = new StringBuilder("[");
            for (Item i : MENU) {
                if (sb.length() > 1) sb.append(",");
                sb.append("{\"id\":").append(i.id()).append(",\"name\":\"").append(i.name())
                  .append("\",\"emoji\":\"").append(i.emoji()).append("\",\"price\":").append(i.price()).append("}");
            }
            Common.send(ex, 200, sb.append("]").toString());
        });

        // POST /order   {"token":"...","items":[{"id":1,"qty":2},{"id":4,"qty":3}]}
        Common.route(s, "/order", ex -> {
            if (ex.getRequestMethod().equals("GET")) {                       // GET /order?id=1001
                Order o = orders.get(Integer.parseInt(Common.query(ex, "id")));
                Common.send(ex, o == null ? 404 : 200, o == null ? "{\"error\":\"Order not found\"}" : json(o));
                return;
            }
            String b = Common.body(ex);
            // 1) Ask the Login Service if this person is logged in
            String check = Common.call("GET", "http://localhost:8081/validate?token=" + Common.field(b, "token"), null);
            if (!check.contains("true")) { Common.send(ex, 401, "{\"error\":\"Please login first\"}"); return; }

            // 2) Calculate the total from the menu (price is never trusted from the browser)
            int total = 0;
            Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)\\s*,\\s*\"qty\"\\s*:\\s*(\\d+)").matcher(b);
            while (m.find()) {
                int id = Integer.parseInt(m.group(1)), qty = Integer.parseInt(m.group(2));
                total += MENU.stream().filter(i -> i.id() == id).mapToInt(Item::price).sum() * qty;
            }
            if (total == 0) { Common.send(ex, 400, "{\"error\":\"Cart is empty\"}"); return; }

            Order o = new Order();
            o.id = nextId.getAndIncrement(); o.user = Common.field(check, "username"); o.total = total;
            orders.put(o.id, o);
            Common.send(ex, 200, json(o));
        });

        // POST /order/paid?id=1001   (called by the Payment Service)
        Common.route(s, "/order/paid", ex -> {
            Order o = orders.get(Integer.parseInt(Common.query(ex, "id")));
            if (o == null) { Common.send(ex, 404, "{\"error\":\"Order not found\"}"); return; }
            o.paid = true;
            Common.send(ex, 200, json(o));
        });

        s.start();
        System.out.println("Order Service running on http://localhost:8082");
    }
}