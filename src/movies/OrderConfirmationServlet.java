package movies;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

@WebServlet(name = "movies.OrderConfirmationServlet", urlPatterns = "/api/order-confirmation")
public class OrderConfirmationServlet extends HttpServlet {
    /*
     * 1) Verify the user is logged in
     * 2) Read "last_order" from the session
     * 3) If missing, return 404
     */
    private static final String LAST_ORDER_KEY = "last_order";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        // Verify log in
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("user") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"status\":\"fail\",\"message\":\"Not logged in\"}");
            return;
        }

        // Read last successful order summary
        @SuppressWarnings("unchecked")
        Map<String, Object> last = (Map<String, Object>) session.getAttribute(LAST_ORDER_KEY);

        // USer has not checked out if null
        if (last == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            out.print("{\"status\":\"fail\",\"message\":\"No recent order found.\"}");
            return;
        }

        // Return order JSON
        out.print("{\"status\":\"success\",\"order\":");
        out.print(mapToJson(last));
        out.print("}");
    }

    // Convert Map to JSON string for variables
    private static String mapToJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(e.getKey())).append("\":");
            sb.append(toJsonValue(e.getValue()));
        }
        sb.append("}");
        return sb.toString();
    }

    // Convert Java objects to JSON values
    @SuppressWarnings("unchecked")
    private static String toJsonValue(Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return String.valueOf(v);
        if (v instanceof String) return "\"" + escapeJson((String) v) + "\"";
        if (v instanceof Map) return mapToJson((Map<String, Object>) v);
        if (v instanceof Iterable) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object it : (Iterable<?>) v) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJsonValue(it));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(String.valueOf(v)) + "\"";
    }

    // Allow to safely insert into JSON
    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
}
