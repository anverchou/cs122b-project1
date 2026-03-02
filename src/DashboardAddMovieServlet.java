import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.sql.*;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/**
 * Dashboard operation: Add a new movie using the stored procedure add_movie.
 *
 * Requires employee session.
 */
@WebServlet(name = "DashboardAddMovieServlet", urlPatterns = "/api/dashboard/add-movie")
public class DashboardAddMovieServlet extends HttpServlet {
    private DataSource dataSource;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            // Task 4 (master/slave): stored procedure writes multiple tables, so this servlet must use the WRITE pool
            dataSource = (DataSource) new InitialContext().lookup("java:comp/env/jdbc/moviedbWrite");
        } catch (NamingException e) {
            throw new ServletException("Cannot retrieve java:comp/env/jdbc/moviedbWrite", e);
        }
    }

    /**
     * Handles POST requests to add a movie using the stored procedure add_movie.
     *
     *  1) Set JSON response headers.
     *  2) Ensure employee is logged in
     *  3) Read & validate required parameters
     *  5) Call stored procedure add_movie
     *  6) Collect any intermediate messages produced as result sets.
     *  7) Read out parameter message.
     *  8) Return JSON
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // Access controls for authetnicated employees
        if (request.getSession().getAttribute("employee") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"status\":\"fail\",\"message\":\"not logged in\"}");
            return;
        }

        // Read parameters
        String title = trimOrEmpty(request.getParameter("title"));
        String yearStr = trimOrEmpty(request.getParameter("year"));
        String director = trimOrEmpty(request.getParameter("director"));
        String starName = trimOrEmpty(request.getParameter("star"));
        String genreName = trimOrEmpty(request.getParameter("genre"));

        Integer year = null;
        // Year stays null if validation fails
        try {
            if (!yearStr.isEmpty()) year = Integer.parseInt(yearStr);
        } catch (NumberFormatException ignored) {}

        // Validate required inputs
        if (title.isEmpty() || director.isEmpty() || year == null || starName.isEmpty() || genreName.isEmpty()) {
            response.getWriter().write("{\"status\":\"fail\",\"message\":\"Missing required fields (title, year, director, star, genre).\"}");
            return;
        }

        try {

            try (Connection conn = dataSource.getConnection()) {

                // Call stored procedure
                try (CallableStatement cs = conn.prepareCall("{CALL add_movie(?, ?, ?, ?, ?, ?)}")) {
                    cs.setString(1, title);
                    cs.setInt(2, year);
                    cs.setString(3, director);
                    cs.setString(4, starName);
                    cs.setString(5, genreName);
                    cs.registerOutParameter(6, Types.VARCHAR);

                    List<String> messages = new ArrayList<>();

                    boolean hasResultSet = cs.execute();
                    // Read result set
                    while (true) {
                        if (hasResultSet) {
                            try (ResultSet rs = cs.getResultSet()) {
                                while (rs != null && rs.next()) {
                                    String m = rs.getString(1);
                                    if (m != null) messages.add(m);
                                }
                            }
                        } else {
                            int uc = cs.getUpdateCount();
                            if (uc == -1) break;
                        }
                        hasResultSet = cs.getMoreResults();
                    }

                    // Read OUT parameter summary
                    String summary = cs.getString(6);
                    if (summary == null) summary = "";

                    boolean success = summary.toUpperCase().startsWith("SUCCESS") || summary.toLowerCase().contains("movie added");

                    response.getWriter().write("{");
                    response.getWriter().write("\"status\":\"" + (success ? "success" : "fail") + "\"");
                    response.getWriter().write(",\"message\":\"" + escapeJson(summary) + "\"");

                    // Include messages from stored procedure
                    response.getWriter().write(",\"messages\":[");
                    for (int i = 0; i < messages.size(); i++) {
                        if (i > 0) response.getWriter().write(",");
                        response.getWriter().write("\"" + escapeJson(messages.get(i)) + "\"");
                    }
                    response.getWriter().write("]}");
                }
            }

        } catch (Exception e) {
            request.getServletContext().log("Dashboard add-movie error:", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"fail\",\"message\":\"Exception: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
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
