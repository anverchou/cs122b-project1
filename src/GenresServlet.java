import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/api/genres")
public class GenresServlet extends HttpServlet {/**
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
        String loginUser = "root";
        String loginPasswd = "Teehee1324!";
        String loginUrl = "jdbc:mysql://localhost:3306/moviedb";

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Store each JSON object as a string to join them
        List<String> rows = new ArrayList<>();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(loginUrl, loginUser, loginPasswd);
                 PreparedStatement ps = conn.prepareStatement("SELECT id, name FROM genres ORDER BY name");
                 ResultSet rs = ps.executeQuery()) {

                // Convert each DB row into a JSON object
                // {"id" : 1, "name": "Action"}
                while (rs.next()) {
                    int id = rs.getInt("id");
                    String name = rs.getString("name");
                    rows.add("{\"id\":" + id + ",\"name\":\"" + escapeJson(name) + "\"}");
                }
            }
            // Write the final JSON array
            response.getWriter().write("[" + String.join(",", rows) + "]");

            // Debugging
        } catch (Exception e) {
            request.getServletContext().log("GenresServlet error:", e);
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
