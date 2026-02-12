import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;

@WebServlet(name = "DashboardMetadataServlet", urlPatterns = "/api/dashboard/metadata")
public class DashboardMetadataServlet extends HttpServlet {
    /**
     * Handles GET requests to return schema metadata (tables and columns).
     *
     *  1) Set JSON response headers.
     *  2) Ensure employee is logged in.
     *  3) Connect to MySQL.
     *  4) Query information_schema.columns for the current database
     *  5) Group rows by table_name and build JSON structure:
     *     tables[] -> { name, columns[] -> { name, type } }
     *  6) Return JSON.
     */

    private static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/moviedb";
    private static final String DEFAULT_DB_USER = "root";
    private static final String DEFAULT_DB_PASS = "Teehee1324!";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (request.getSession().getAttribute("employee") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"status\":\"fail\",\"message\":\"not logged in\"}");
            return;
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            try (Connection conn = DriverManager.getConnection(
                    envOrDefault("DB_URL", DEFAULT_DB_URL),
                    envOrDefault("DB_USER", DEFAULT_DB_USER),
                    envOrDefault("DB_PASSWORD", DEFAULT_DB_PASS))) {

                // Pull metadata
                String sql = "SELECT table_name, column_name, column_type " +
                        "FROM information_schema.columns " +
                        "WHERE table_schema = DATABASE() " +
                        "ORDER BY table_name, ordinal_position";

                // Hold table objects
                JsonArray tables = new JsonArray();

                String curTable = null;
                JsonObject tableObj = null;
                JsonArray cols = null;

                try (PreparedStatement ps = conn.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {

                    while (rs.next()) {
                        String tableName = rs.getString("table_name");
                        String colName = rs.getString("column_name");
                        String colType = rs.getString("column_type");

                        // Start new table group when table name changes
                        if (curTable == null || !curTable.equals(tableName)) {
                            curTable = tableName;
                            tableObj = new JsonObject();
                            tableObj.addProperty("name", tableName);
                            cols = new JsonArray();
                            tableObj.add("columns", cols);
                            tables.add(tableObj);
                        }

                        // Add column metadata to the columns array
                        JsonObject c = new JsonObject();
                        c.addProperty("name", colName);
                        c.addProperty("type", colType);
                        cols.add(c);
                    }
                }

                JsonObject out = new JsonObject();
                out.addProperty("status", "success");
                out.add("tables", tables);

                response.getWriter().write(out.toString());
            }

        } catch (Exception e) {
            request.getServletContext().log("Dashboard metadata error:", e);
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
