import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

@WebServlet(name = "PlaceOrderServlet", urlPatterns = "/api/place-order")
public class PlaceOrderServlet extends HttpServlet {
 /**
    * 1) Require a logged-in user
    * 2) Validates payment info against the `creditcards` table.
    * 3) Inserts purchase rows into sals
    * 4) Builds a "last order" summary and stores it in session
    * 5) Clears the shopping cart in session after a successful checkout.
  */

    // Session keys
    private static final String CART_KEY = "shopping_cart";
    private static final String LAST_ORDER_KEY = "last_order";

    private static final String loginUser = "mytestuser";
    private static final String loginPasswd = "password";
    private static final String loginUrl = "jdbc:mysql://localhost:3306/moviedb";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        // Authetnicate user
        HttpSession session = request.getSession(false);
        Integer customerId = (session == null) ? null : (Integer) session.getAttribute("user");
        if (customerId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print("{\"status\":\"fail\",\"message\":\"Not logged in.\"}");
            out.close();
            return;
        }

        // Rad and validate fields
        String firstName = trimOrNull(request.getParameter("firstName"));
        String lastName = trimOrNull(request.getParameter("lastName"));
        String cardNumber = trimOrNull(request.getParameter("cardNumber"));
        // UI collects expiration as month/year (YYYY-MM). We also accept full date (YYYY-MM-DD) for robustness.
        String expiration = trimOrNull(request.getParameter("expiration"));

        // Make fields required
        if (firstName == null || lastName == null || cardNumber == null || expiration == null) {
            out.print("{\"status\":\"fail\",\"message\":\"Please fill out all payment fields.\"}");
            out.close();
            return;
        }

        // Normalize expiration
        String expYearMonth = null;
        java.sql.Date expDate = null;
        if (expiration.matches("\\d{4}-\\d{2}")) {
            expYearMonth = expiration;
        } else {
            try {
                expDate = java.sql.Date.valueOf(expiration);
            } catch (Exception e) {
                out.print("{\"status\":\"fail\",\"message\":\"Expiration must be YYYY-MM (month/year) or YYYY-MM-DD.\"}");
                out.close();
                return;
            }
        }

        // Load cart
        @SuppressWarnings("unchecked")
        Map<String, CartServlet.CartItem> cart = (session == null) ? null : (Map<String, CartServlet.CartItem>) session.getAttribute(CART_KEY);
        if (cart == null || cart.isEmpty()) {
            out.print("{\"status\":\"fail\",\"message\":\"Your cart is empty.\"}");
            out.close();
            return;
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"fail\",\"message\":\"JDBC driver not found.\"}");
            out.close();
            return;
        }

        try (Connection conn = DriverManager.getConnection(loginUrl, loginUser, loginPasswd)) {
            conn.setAutoCommit(false);

            // 1) Validate payment info against creditcards table
            if (!isValidCard(conn, cardNumber, firstName, lastName, expYearMonth, expDate)) {
                conn.rollback();
                out.print("{\"status\":\"fail\",\"message\":\"Payment information does not match our records.\"}");
                return;
            }

            // 2) Insert sales rows
            String insertSql = "INSERT INTO sales(customerId, movieId, saleDate) VALUES(?, ?, CURDATE())";
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                for (CartServlet.CartItem item : cart.values()) {
                    if (item == null || item.quantity <= 0) continue;
                    for (int i = 0; i < item.quantity; i++) {
                        ps.setInt(1, customerId);
                        ps.setString(2, item.movieId);
                        ps.addBatch();
                    }
                }
                ps.executeBatch();
            }

            conn.commit();

            // 3) Build last order snapshot for confirmation page
            BigDecimal total = BigDecimal.ZERO;
            List<Map<String, Object>> orderItems = new ArrayList<>();
            for (CartServlet.CartItem item : cart.values()) {
                if (item == null || item.quantity <= 0) continue;
                BigDecimal unit = item.price;
                BigDecimal subtotal = unit.multiply(BigDecimal.valueOf(item.quantity)).setScale(2, RoundingMode.HALF_UP);
                total = total.add(subtotal);

                Map<String, Object> one = new LinkedHashMap<>();
                one.put("movieId", item.movieId);
                one.put("title", item.title);
                one.put("price", unit.setScale(2, RoundingMode.HALF_UP).toPlainString());
                one.put("quantity", item.quantity);
                one.put("subtotal", subtotal.toPlainString());
                orderItems.add(one);
            }

            Map<String, Object> lastOrder = new LinkedHashMap<>();
            lastOrder.put("orderDate", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()));
            lastOrder.put("total", total.setScale(2, RoundingMode.HALF_UP).toPlainString());
            lastOrder.put("items", orderItems);
            session.setAttribute(LAST_ORDER_KEY, lastOrder);

            // clear cart
            cart.clear();
            session.setAttribute(CART_KEY, cart);

            out.print("{\"status\":\"success\"}");

            // DEbug
        } catch (Exception e) {
            request.getServletContext().log("PlaceOrderServlet error:", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"fail\",\"message\":\"Exception: " + escapeJson(e.getMessage()) + "\"}");
        } finally {
            out.close();
        }
    }

    // Validate card against table
    private static boolean isValidCard(Connection conn, String cardNumber, String firstName, String lastName, String expYearMonth, java.sql.Date expDate) throws SQLException {
        final String sql;
        final boolean byMonth;
        if (expYearMonth != null) {
            byMonth = true;
            sql = "SELECT 1 FROM creditcards " +
                    "WHERE id = ? AND firstName = ? AND lastName = ? " +
                    "AND DATE_FORMAT(expiration, '%Y-%m') = ?";
        } else {
            byMonth = false;
            sql = "SELECT 1 FROM creditcards " +
                    "WHERE id = ? AND firstName = ? AND lastName = ? AND expiration = ?";
        }

        // Match by month/year format
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cardNumber);
            ps.setString(2, firstName);
            ps.setString(3, lastName);
            if (byMonth) ps.setString(4, expYearMonth);
            else ps.setDate(4, expDate);

            // Match by month/day/year format
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

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
