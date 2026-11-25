import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

/** PAYMENT SERVICE - port 8083 */
public class PaymentService {
    static final AtomicInteger nextId = new AtomicInteger(5001);

    public static void main(String[] args) throws Exception {
        HttpServer s = HttpServer.create(new InetSocketAddress(8083), 0);

        // POST /pay   {"orderId":1001,"method":"UPI"}
        Common.route(s, "/pay", ex -> {
            String b = Common.body(ex);
            String orderId = Common.field(b, "orderId");

            // 1) Ask the Order Service for the real amount
            String order = Common.call("GET", "http://localhost:8082/order?id=" + orderId, null);
            if (order.contains("error")) { Common.send(ex, 404, "{\"error\":\"Order not found\"}"); return; }
            if (order.contains("\"paid\":true")) { Common.send(ex, 400, "{\"error\":\"Order already paid\"}"); return; }

            // 2) "Charge" the customer (simulated) and tell the Order Service it is paid
            Common.call("POST", "http://localhost:8082/order/paid?id=" + orderId, "");
            Common.send(ex, 200, "{\"status\":\"SUCCESS\",\"paymentId\":" + nextId.getAndIncrement()
                    + ",\"orderId\":" + orderId + ",\"amount\":" + Common.field(order, "total")
                    + ",\"method\":\"" + Common.field(b, "method") + "\"}");
        });

        s.start();
        System.out.println("Payment Service running on http://localhost:8083");
    }
}