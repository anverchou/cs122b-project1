import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

/**
 * Returns detailed information for a single movie as JSON, including:
 *   - core movie fields (id, title, year, director)
 *   - rating (nullable)
 *   - all genres (comma-separated string)
 * all stars (comma-separated "id:name" pairs)
 */
@WebServlet("/singlemovie")
public class SingleMovieServlet extends HttpServlet {
     /* 1) Read movieId from query param
      * 2) Validate it exists
      * 3) Query movie core info + rating
      * 4) Query all genres
      * 5) Query all stars
      * 6) Write final JSON object
      */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String loginUser = "root";
        String loginPasswd = "Teehee1324!";
        String loginUrl = "jdbc:mysql://localhost:3306/moviedb";

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        // Rad and valid
        String movieId = request.getParameter("id");
        if (movieId == null || movieId.trim().isEmpty()) {
            movieId = request.getParameter("movieId");
        }
        // Retur 400 is missing
        if (movieId == null || movieId.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            out.print("{\"error\":\"Missing movie id (expected ?id=...)\"}");
            out.close();
            return;
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            try (Connection connection = DriverManager.getConnection(loginUrl, loginUser, loginPasswd)) {

                // 1) Movie core info + rating
                String movieQuery =
                        "SELECT m.id, m.title, m.year, m.director, r.rating " +
                                "FROM movies m " +
                                "LEFT JOIN ratings r ON r.movieId = m.id " +
                                "WHERE m.id = ?";

                String title;
                Integer year;
                String director;
                Double rating;

                try (PreparedStatement ps = connection.prepareStatement(movieQuery)) {
                    ps.setString(1, movieId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            // Movie id is not found
                            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                            out.print("{\"error\":\"Movie not found\"}");
                            return;
                        }
                        title = rs.getString("title");
                        year = (Integer) rs.getObject("year");
                        director = rs.getString("director");
                        Number r = (Number) rs.getObject("rating");
                        rating = (r == null) ? null : r.doubleValue();
                    }
                }

                // 2) All genres
                String genresQuery =
                        "SELECT DISTINCT g.name " +
                                "FROM genres_in_movies gim " +
                                "JOIN genres g ON g.id = gim.genreId " +
                                "WHERE gim.movieId = ? " +
                                "ORDER BY g.name";

                StringBuilder genresSb = new StringBuilder();
                try (PreparedStatement ps = connection.prepareStatement(genresQuery)) {
                    ps.setString(1, movieId);
                    try (ResultSet rs = ps.executeQuery()) {
                        boolean first = true;
                        while (rs.next()) {
                            if (!first) genresSb.append(", ");
                            first = false;
                            genresSb.append(rs.getString("name"));
                        }
                    }
                }
                String genres = genresSb.length() == 0 ? "N/A" : genresSb.toString();

                // 3) All stars as
                String starsQuery =
                        "SELECT DISTINCT s.id, s.name " +
                                "FROM stars_in_movies sim " +
                                "JOIN stars s ON s.id = sim.starId " +
                                "WHERE sim.movieId = ? " +
                                "ORDER BY s.name";

                StringBuilder starsSb = new StringBuilder();
                try (PreparedStatement ps = connection.prepareStatement(starsQuery)) {
                    ps.setString(1, movieId);
                    try (ResultSet rs = ps.executeQuery()) {
                        boolean first = true;
                        while (rs.next()) {
                            if (!first) starsSb.append(", ");
                            first = false;
                            String sid = rs.getString("id");
                            String sname = rs.getString("name");
                            starsSb.append(sid).append(":").append(sname);
                        }
                    }
                }
                String stars = starsSb.length() == 0 ? "N/A" : starsSb.toString();

                // 4) JSON response
                out.print("{");
                out.print("\"id\":\"" + escapeJson(movieId) + "\",");
                out.print("\"title\":\"" + escapeJson(title) + "\",");

                // year number or null
                out.print("\"year\":" + (year == null ? "null" : year) + ",");

                out.print("\"director\":\"" + escapeJson(director) + "\",");

                out.print("\"genres\":\"" + escapeJson(genres) + "\",");
                out.print("\"stars\":\"" + escapeJson(stars) + "\",");

                // rating number or null
                out.print("\"rating\":" + (rating == null ? "null" : rating));

                out.print("}");
            }

            // Debugging
        } catch (Exception e) {
            request.getServletContext().log("SingleMovieServlet error: ", e);
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
