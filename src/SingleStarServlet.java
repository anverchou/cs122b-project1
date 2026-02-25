import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

// Return information for each single star
@WebServlet("/singlestar")
public class SingleStarServlet extends HttpServlet {
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

    /* 1) Read starId from query param
     * 2) Validate it exists
     * 3) Query star core info
     * 4) Query movies the star appeared
     * 5) Write final JSON
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        PrintWriter out = response.getWriter();

        // Read and validate
        String starId = request.getParameter("id");
        if (starId == null || starId.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"status\":\"fail\",\"message\":\"Missing star id (expected ?id=...)\"}");
            out.close();
            return;
        }

        try {

            try (Connection connection = dataSource.getConnection()) {

                // 1) Star info
                String starQuery = "SELECT id, name, birthYear FROM stars WHERE id = ?";

                String name;
                Integer birthYear;

                try (PreparedStatement ps = connection.prepareStatement(starQuery)) {
                    ps.setString(1, starId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                            out.print("{\"status\":\"fail\",\"message\":\"Star not found\"}");
                            return; // finally will close writer
                        }
                        name = rs.getString("name");
                        birthYear = (Integer) rs.getObject("birthYear");
                    }
                }

                // 2) Movies the star acted in
                String moviesQuery =
                        "SELECT DISTINCT m.id, m.title, m.year " +
                                "FROM stars_in_movies sim " +
                                "JOIN movies m ON m.id = sim.movieId " +
                                "WHERE sim.starId = ? " +
                                "ORDER BY m.year DESC, m.title ASC";

                // 3) JSON response
                out.print("{");
                out.print("\"status\":\"success\",");
                out.print("\"id\":\"" + escapeJson(starId) + "\",");
                out.print("\"name\":\"" + escapeJson(name) + "\",");

                out.print("\"birthYear\":" + (birthYear == null ? "null" : birthYear) + ",");

                out.print("\"movies\":[");
                boolean first = true;
                try (PreparedStatement ps = connection.prepareStatement(moviesQuery)) {
                    ps.setString(1, starId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            if (!first) out.print(",");
                            first = false;

                            String mid = rs.getString("id");
                            String title = rs.getString("title");

                            out.print("{");
                            out.print("\"id\":\"" + escapeJson(mid) + "\",");
                            out.print("\"title\":\"" + escapeJson(title) + "\"");
                            out.print("}");
                        }
                    }
                }
                out.print("]");

                out.print("}");
            }

        } catch (Exception e) {
            request.getServletContext().log("SingleStarServlet error: ", e);

            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) msg = e.getClass().getSimpleName();

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"status\":\"fail\",\"message\":\"Exception in doGet: " + escapeJson(msg) + "\"}");
        } finally {
            out.close();
        }
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
