import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/**
 * Dashboard operation: Insert a new star.
 *
 * Requires employee session.
 */
@WebServlet(name = "DashboardAddStarServlet", urlPatterns = "/api/dashboard/add-star")
public class DashboardAddStarServlet extends HttpServlet {
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

    /**
     * Handles POST requests to insert a new star.
     *
     *  1) Set JSON response headers.
     *  2) Ensure employee is logged in
     *  3) Read parameters "name" and optional "birthYear".
     *  4) Validate that "name" is non-empty.
     *  5) Connect to MySQL.
     *  7) Generate a new star id
     *  8) Insert into stars
     *  9) Return JSON
     */

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (request.getSession().getAttribute("employee") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"status\":\"fail\",\"message\":\"not logged in\"}");
            return;
        }

        // Read and sanitize input parameters
        String name = request.getParameter("name");
        String birthYearStr = request.getParameter("birthYear");
        if (name == null) name = "";
        name = name.trim();

        // Year is null if parsing fails
        Integer birthYear = null;
        if (birthYearStr != null && !birthYearStr.trim().isEmpty()) {
            try {
                birthYear = Integer.parseInt(birthYearStr.trim());
            } catch (NumberFormatException ignored) {
            }
        }

        // Validate star name
        if (name.isEmpty()) {
            response.getWriter().write("{\"status\":\"fail\",\"message\":\"Star name is required.\"}");
            return;
        }

        try {

            try (Connection conn = dataSource.getConnection()) {

                // Duplicate Detection
                int sameNameAndBirthYear = 0;
                String dupSql = "SELECT COUNT(*) AS c FROM stars " +
                        "WHERE name = ? AND ((birthYear IS NULL AND ? IS NULL) OR birthYear = ?)";
                try (PreparedStatement ps = conn.prepareStatement(dupSql)) {
                    ps.setString(1, name);
                    if (birthYear == null) {
                        ps.setNull(2, Types.INTEGER);
                        ps.setNull(3, Types.INTEGER);
                    } else {
                        ps.setInt(2, birthYear);
                        ps.setInt(3, birthYear);
                    }
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) sameNameAndBirthYear = rs.getInt("c");
                    }
                }

                // Generate new star id
                int nextNum = 1;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT IFNULL(MAX(CAST(SUBSTRING(id,3) AS UNSIGNED)), 0) + 1 AS nextNum FROM stars WHERE id LIKE 'nm%'");
                     ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        nextNum = rs.getInt("nextNum");
                    }
                }

                String newId = String.format("nm%07d", nextNum);

                // Insert new star record
                String insert = "INSERT INTO stars(id, name, birthYear) VALUES (?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    ps.setString(1, newId);
                    ps.setString(2, name);
                    if (birthYear == null) {
                        ps.setNull(3, Types.INTEGER);
                    } else {
                        ps.setInt(3, birthYear);
                    }
                    ps.executeUpdate();
                }

                String msg = "Star added. id=" + newId;
                if (sameNameAndBirthYear > 0) {
                    msg = "Star added (note: another star already exists with the same name and birth year). id=" + newId;
                }
                response.getWriter().write("{\"status\":\"success\",\"message\":\"" + escapeJson(msg) + "\"}");
            }

        } catch (Exception e) {
            request.getServletContext().log("Dashboard add-star error:", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"fail\",\"message\":\"Exception: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static String envOrDefault(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
