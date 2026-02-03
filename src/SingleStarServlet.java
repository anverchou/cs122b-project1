import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

// Return information for each single star
@WebServlet("/singlestar")
public class SingleStarServlet extends HttpServlet {
    /* 1) Read starId from query param
     * 2) Validate it exists
     * 3) Query star core info
     * 4) Query movies the star appeared
     * 5) Write final JSON
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String loginUser = "admin";
        String loginPasswd = "password";
        String loginUrl = "jdbc:mysql://localhost:3306/moviedb";

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        // Read and validate
        String starId = request.getParameter("id");
        if (starId == null || starId.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\":\"Missing star id (expected ?id=...)\"}");
            out.close();
            return;
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            try (Connection connection = DriverManager.getConnection(loginUrl, loginUser, loginPasswd)) {

                // 1) Star info
                String starQuery = "SELECT id, name, birthYear FROM stars WHERE id = ?";

                String name;
                Integer birthYear;

                try (PreparedStatement ps = connection.prepareStatement(starQuery)) {
                    ps.setString(1, starId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                            out.print("{\"error\":\"Star not found\"}");
                            return;
                        }
                        name = rs.getString("name");
                        birthYear = (Integer) rs.getObject("birthYear"); // may be null
                    }
                }

                // 2) Movies the star acted in
                // Requirement: sorted by year DESC then title ASC
                String moviesQuery =
                        "SELECT DISTINCT m.id, m.title " +
                                "FROM stars_in_movies sim " +
                                "JOIN movies m ON m.id = sim.movieId " +
                                "WHERE sim.starId = ? " +
                                "ORDER BY m.year DESC, m.title ASC";

                StringBuilder moviesSb = new StringBuilder();
                try (PreparedStatement ps = connection.prepareStatement(moviesQuery)) {
                    ps.setString(1, starId);
                    try (ResultSet rs = ps.executeQuery()) {
                        boolean first = true;
                        while (rs.next()) {
                            if (!first) moviesSb.append(", ");
                            first = false;
                            String mid = rs.getString("id");
                            String title = rs.getString("title");
                            moviesSb.append(mid).append(":").append(title);
                        }
                    }
                }

                String movies = moviesSb.length() == 0 ? "N/A" : moviesSb.toString();

                // 3) JSON response
                out.print("{");
                out.print("\"id\":\"" + escapeJson(starId) + "\",");
                out.print("\"name\":\"" + escapeJson(name) + "\",");

                // birthYear should be avnumber or null
                out.print("\"birthYear\":" + (birthYear == null ? "null" : birthYear) + ",");

                out.print("\"movies\":\"" + escapeJson(movies) + "\"");
                out.print("}");
            }

            // Debugging
        } catch (Exception e) {
            request.getServletContext().log("SingleStarServlet error: ", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\":\"Exception in doGet: " + escapeJson(e.getMessage()) + "\"}");
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
