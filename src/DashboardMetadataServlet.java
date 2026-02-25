import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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

@WebServlet(name = "DashboardMetadataServlet", urlPatterns = "/api/dashboard/metadata")
public class DashboardMetadataServlet extends HttpServlet {
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

            try (Connection conn = dataSource.getConnection()) {

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
