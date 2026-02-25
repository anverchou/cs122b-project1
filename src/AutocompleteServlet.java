import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;


// Return JSON of up to 10 entries
@WebServlet(name = "AutocompleteServlet", urlPatterns = "/api/autocomplete")
public class AutocompleteServlet extends HttpServlet {
    private DataSource dataSource;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            dataSource = (DataSource) new InitialContext().lookup("java:comp/env/jdbc/moviedb");
        } catch (NamingException e) {
            throw new ServletException("Cannot retrieve java:comp/env/jdbc/moviedb", e);
        }
    }


    /**
     * 1) Read and trim parameter
     * 2) If query is < 3 characters, return an empty JSON array.
     * 3) Build WHERE clauses and parameter list
     * 4) Execute SQL with PreparedStatement and return up to 10 results
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String rawQuery = request.getParameter("query");
        String q = rawQuery == null ? "" : rawQuery.trim();

        // Don't search for 1-2 chars
        if (q.length() < 3) {
            response.getWriter().write("[]");
            return;
        }

        List<String> where = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        addFullTextTitleFilter(where, params, q);

        if (where.isEmpty()) {
            response.getWriter().write("[]");
            return;
        }

        String whereSql = " WHERE " + String.join(" AND ", where);

        // Query returns id/title and orders by rating first.
        String sql =
                "SELECT m.id, m.title " +
                        "FROM movies m " +
                        "LEFT JOIN ratings r ON r.movieId = m.id " +
                        whereSql +
                        " ORDER BY COALESCE(r.rating, 0) DESC, m.title ASC " +
                        "LIMIT 10";

        // Pre-build JSON entries
        List<String> rows = new ArrayList<>();

        try {

            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                bindParams(ps, params);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        String title = rs.getString("title");
                        rows.add("{\"value\":\"" + escapeJson(title) + "\",\"data\":{\"movieId\":\"" + escapeJson(id) + "\"}}"
                        );
                    }
                }
            }

            response.getWriter().write("[" + String.join(",", rows) + "]");
        } catch (Exception e) {
            request.getServletContext().log("AutocompleteServlet error:", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // Bind a List of parameters for preparedstatement
    private static void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        int idx = 1;
        for (Object p : params) {
            if (p instanceof Integer) ps.setInt(idx++, (Integer) p);
            else ps.setString(idx++, String.valueOf(p));
        }
    }

    // Full-text
    private static void addFullTextTitleFilter(List<String> where, List<Object> params, String rawQuery) {
        if (rawQuery == null) return;

        List<String> tokens = tokenizeQuery(rawQuery);
        if (tokens.isEmpty()) return;

        StringBuilder booleanQuery = new StringBuilder();
        List<String> shortTokenRegex = new ArrayList<>();

        for (String tok : tokens) {
            if (tok.length() >= 3) {
                // Require token
                booleanQuery.append('+').append(tok).append('*').append(' ');
            } else {
                // Prefix-like match for very short tokens
                shortTokenRegex.add("(^|[^0-9a-z])" + tok);
            }
        }

        String bq = booleanQuery.toString().trim();
        if (!bq.isEmpty()) {
            where.add("MATCH(m.title) AGAINST (? IN BOOLEAN MODE)");
            params.add(bq);
        }

        // Add a REGEXP clause per short token
        for (String pattern : shortTokenRegex) {
            where.add("LOWER(m.title) REGEXP ?");
            params.add(pattern);
        }
    }

    // Convert the raw query into a list of searchable tokens.
    private static List<String> tokenizeQuery(String raw) {
        String query = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (query.isEmpty()) return Collections.emptyList();

        String[] parts = query.split("\\s+");
        ArrayList<String> out = new ArrayList<>();
        for (String p : parts) {
            if (p == null) continue;

            String cleaned = p.replaceAll("[^a-z0-9]", "");
            if (cleaned.isEmpty()) continue;

            out.add(cleaned);
            if (out.size() >= 10) break;
        }
        return out;
    }

    static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}