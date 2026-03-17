package movies;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

@WebServlet("/api/genres")
public class GenresServlet extends HttpServlet {
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
     * Handles GET requests to /api/genres.
     *
     * 1) Configure response headers (JSON + UTF-8).
     * 2) Connect to MySQL.
     * 3) Query all genres ordered alphabetically by name.
     * 4) Convert rows into JSON objects and return a JSON array.
     * 5) If anything fails, return HTTP 500 and a JSON error object.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Store each JSON object as a string to join them
        List<String> rows = new ArrayList<>();

        try {
            try (Connection conn = dataSource.getConnection()) {

                String sql = "SELECT id, name FROM genres ORDER BY name ASC";

                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {

                    // Convert each DB row into a JSON object
                    // {"id" : 1, "name": "Action"}
                    while (rs.next()) {
                        int id = rs.getInt("id");
                        String name = rs.getString("name");
                        rows.add("{\"id\":" + id + ",\"name\":\"" + escapeJson(name) + "\"}");
                    }
                }
            }
            // Write the final JSON array
            response.getWriter().write("[" + String.join(",", rows) + "]");

            // Debugging
        } catch (Exception e) {
            request.getServletContext().log("movies.GenresServlet error:", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    // Safely embed inside JSON string. Was having issues missing certrain entries
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}