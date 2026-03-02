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
import java.util.*;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

// Create movie price on demand and assign price to each movieId when added to cart
@WebServlet(name = "CartServlet", urlPatterns = "/api/cart")
public class CartServlet extends HttpServlet {
    private DataSource dataSource;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            dataSource = (DataSource) new InitialContext().lookup("java:comp/env/jdbc/moviedbWrite");
        } catch (NamingException e) {
            throw new ServletException("Cannot retrieve java:comp/env/jdbc/moviedbWrite", e);
        }
    }

    private static final String CART_KEY = "shopping_cart";

    // Create price table for movies
    private static final String PRICE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS movie_prices (" +
                    "movieId VARCHAR(10) NOT NULL PRIMARY KEY," +
                    "price DECIMAL(10,2) NOT NULL" +
                    ")";

    public static class CartItem {
        String movieId;
        String title;
        BigDecimal price;
        int quantity;

        CartItem(String movieId, String title, BigDecimal price, int quantity) {
            this.movieId = movieId;
            this.title = title;
            this.price = price;
            this.quantity = quantity;
        }

        // Get total for each movie (price * quantity)
        BigDecimal subtotal() {
            return price.multiply(BigDecimal.valueOf(quantity));
        }
    }

    /* GET
     * 1) Validate user is logged in (
     * 2) Read cart from session
     * 3) Compute totals
     * 4) Return JSON with items[] and total
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        try {
            // Require login
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("user") == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print("{\"error\":\"not logged in\"}");
                return;
            }

            // Read the cart from the session
            Map<String, CartItem> cart = getCart(session);

            // Add all subtotals for cart total
            BigDecimal total = BigDecimal.ZERO;
            for (CartItem it : cart.values()) total = total.add(it.subtotal());

            // Build the JSON
            out.print("{");
            out.print("\"items\":[");

            boolean first = true;
            for (CartItem it : cart.values()) {
                if (!first) out.print(",");
                first = false;

                out.print("{");
                out.print("\"id\":\"");
                out.print(escapeJson(it.movieId));
                out.print("\",");

                out.print("\"title\":\"");
                out.print(escapeJson(it.title));
                out.print("\",");

                out.print("\"price\":");
                out.print(it.price.setScale(2, RoundingMode.HALF_UP));
                out.print(",");

                out.print("\"quantity\":");
                out.print(it.quantity);
                out.print(",");

                out.print("\"subtotal\":");
                out.print(it.subtotal().setScale(2, RoundingMode.HALF_UP));
                out.print("}");
            }

            out.print("],");
            out.print("\"total\":");
            out.print(total.setScale(2, RoundingMode.HALF_UP));
            out.print("}");

            // Debug
        } catch (Exception e) {
            request.getServletContext().log("CartServlet error:", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"fail\",\"message\":\"");
            out.print(escapeJson(e.getMessage()));
            out.print("\"}");
        } finally {
            out.close();
        }
    }

    /* POST
     * 1) Validate user is logged in
     * 2) Read action + params
     * 3) Mutate cart in session:
     *      - clear: remove all
     *      - delete: remove movieId
     *      - add/inc/dec: +/- 1 quantity
     * 4) Return small JSON status
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();
        try {
            // Require login
            HttpSession session = request.getSession(false);
            if (session == null || session.getAttribute("user") == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                out.print("{\"status\":\"fail\",\"message\":\"not logged in\"}");
                return;
            }

            // Read actions from user
            String action = trimOrNull(request.getParameter("action"));
            if (action == null) action = "add";
            action = action.toLowerCase(Locale.ROOT);

            Map<String, CartItem> cart = getCart(session);

            // Clear cart
            if ("clear".equals(action)) {
                cart.clear();
                out.print("{\"status\":\"success\",\"message\":\"cart cleared\"}");
                return;
            }

            String movieId = trimOrNull(request.getParameter("movieId"));
            if (movieId == null) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                out.print("{\"status\":\"fail\",\"message\":\"missing movieId\"}");
                return;
            }

            // Require a movie id for action
            if ("delete".equals(action)) {
                cart.remove(movieId);
                out.print("{\"status\":\"success\",\"message\":\"removed\"}");
                return;
            }

            // add/inc/dec
            int delta = ("dec".equals(action)) ? -1 : 1;

            CartItem existing = cart.get(movieId);
            if (existing == null) {
                if (delta < 0) {
                    out.print("{\"status\":\"success\",\"message\":\"not in cart\"}");
                    return;
                }

                // fetch title and price
                try (Connection conn = dataSource.getConnection()) {
                    ensurePriceTable(conn);

                    // Validate the existence of movie and title
                    String title = fetchMovieTitle(conn, movieId);
                    if (title == null) {
                        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                        out.print("{\"status\":\"fail\",\"message\":\"unknown movie id\"}");
                        return;
                    }

                    // Get price
                    BigDecimal price = getOrCreatePrice(conn, movieId);
                    existing = new CartItem(movieId, title, price, 0);
                    cart.put(movieId, existing);
                }
            }

            // Remove item if quantity goes to 0
            existing.quantity += delta;
            if (existing.quantity <= 0) {
                cart.remove(movieId);
            }

            out.print("{\"status\":\"success\",\"message\":\"updated\"}");

            // DEbug
        } catch (Exception e) {
            request.getServletContext().log("CartServlet POST error:", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"fail\",\"message\":\"");
            out.print(escapeJson(e.getMessage()));
            out.print("\"}");
        } finally {
            out.close();
        }
    }

    // Helper functions
    @SuppressWarnings("unchecked")
    private Map<String, CartItem> getCart(HttpSession session) {
        Object obj = session.getAttribute(CART_KEY);
        if (obj instanceof Map) {
            return (Map<String, CartItem>) obj;
        }
        Map<String, CartItem> cart = new LinkedHashMap<>();
        session.setAttribute(CART_KEY, cart);
        return cart;
    }

    private static void ensurePriceTable(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(PRICE_TABLE_SQL)) {
            ps.executeUpdate();
        }
    }

    private static String fetchMovieTitle(Connection conn, String movieId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT title FROM movies WHERE id = ?")) {
            ps.setString(1, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("title");
            }
        }
        return null;
    }

    private static BigDecimal getOrCreatePrice(Connection conn, String movieId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT price FROM movie_prices WHERE movieId = ?")) {
            ps.setString(1, movieId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("price").setScale(2, RoundingMode.HALF_UP);
                }
            }
        }

        BigDecimal price = randomPrice();
        try (PreparedStatement ins = conn.prepareStatement(
                "INSERT INTO movie_prices(movieId, price) VALUES(?, ?)")) {
            ins.setString(1, movieId);
            ins.setBigDecimal(2, price);
            ins.executeUpdate();
        } catch (SQLException dup) {
            try (PreparedStatement ps = conn.prepareStatement("SELECT price FROM movie_prices WHERE movieId = ?")) {
                ps.setString(1, movieId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getBigDecimal("price").setScale(2, RoundingMode.HALF_UP);
                }
            }
        }
        return price;
    }

    private static BigDecimal randomPrice() {
        Random r = new Random();
        int cents = 399 + r.nextInt(1601);
        return BigDecimal.valueOf(cents).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
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
