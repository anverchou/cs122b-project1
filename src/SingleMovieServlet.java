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

/**
 * Returns detailed information for a single movie as JSON, including:
 *   - core movie fields (id, title, year, director)
 *   - rating (nullable)
 *   - all genres (comma-separated string)
 * all stars (comma-separated "id:name" pairs)
 *
 * NOTE: genres/stars are returned as JSON arrays of objects {id, name}
 * so the frontend can hyperlink each genre/star cleanly.
 */
@WebServlet("/singlemovie")
public class SingleMovieServlet extends HttpServlet {
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

    /* 1) Read movieId from query param
     * 2) Validate it exists
     * 3) Query movie core info + rating
     * 4) Query all genres
     * 5) Query all stars
     * 6) Write final JSON object
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

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

            try (Connection connection = dataSource.getConnection()) {

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
                // Requirement: sorted by alphabetical order + hyperlinkable (need genre id)
                // Output format: "id:name, id:name, ..."
                //
                // New output in JSON:
                //   "genres": [ { "id": 1, "name": "Action" }, ... ]
                String genresQuery =
                        "SELECT DISTINCT g.id, g.name " +
                                "FROM genres_in_movies gim " +
                                "JOIN genres g ON g.id = gim.genreId " +
                                "WHERE gim.movieId = ? " +
                                "ORDER BY g.name";

                // 3) All stars as
                // Requirement: sorted by number of movies played DESC, tie by name ASC
                //
                // New output in JSON:
                //   "stars": [ { "id": "nm123", "name": "Some Star" }, ... ]
                //
                // Use a correlated subquery for movieCount to avoid join-multiplication issues.
                String starsQuery =
                        "SELECT s.id, s.name, " +
                                "  (SELECT COUNT(DISTINCT sim2.movieId) " +
                                "   FROM stars_in_movies sim2 " +
                                "   WHERE sim2.starId = s.id) AS movieCount " +
                                "FROM stars_in_movies sim " +
                                "JOIN stars s ON s.id = sim.starId " +
                                "WHERE sim.movieId = ? " +
                                "GROUP BY s.id, s.name " +
                                "ORDER BY movieCount DESC, s.name ASC";

                // 4) JSON response
                out.print("{");

                out.print("\"id\":\"");
                out.print(escapeJson(movieId));
                out.print("\",");

                out.print("\"title\":\"");
                out.print(escapeJson(title));
                out.print("\",");

                // year number or null
                out.print("\"year\":");
                out.print(year == null ? "null" : String.valueOf(year));
                out.print(",");

                out.print("\"director\":\"");
                out.print(escapeJson(director));
                out.print("\",");

                // rating number or null
                out.print("\"rating\":");
                out.print(rating == null ? "null" : String.valueOf(rating));
                out.print(",");

                // ---- genres array ----
                out.print("\"genres\":[");
                boolean firstGenre = true;
                try (PreparedStatement ps = connection.prepareStatement(genresQuery)) {
                    ps.setString(1, movieId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            if (!firstGenre) out.print(",");
                            firstGenre = false;

                            int gid = rs.getInt("id");
                            String gname = rs.getString("name");

                            out.print("{\"id\":");
                            out.print(gid);
                            out.print(",\"name\":\"");
                            out.print(escapeJson(gname));
                            out.print("\"}");
                        }
                    }
                }
                out.print("],");

                // ---- stars array ----
                out.print("\"stars\":[");
                boolean firstStar = true;
                try (PreparedStatement ps = connection.prepareStatement(starsQuery)) {
                    ps.setString(1, movieId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            if (!firstStar) out.print(",");
                            firstStar = false;

                            String sid = rs.getString("id");
                            String sname = rs.getString("name");

                            out.print("{\"id\":\"");
                            out.print(escapeJson(sid));
                            out.print("\",\"name\":\"");
                            out.print(escapeJson(sname));
                            out.print("\"}");
                        }
                    }
                }
                out.print("]");

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
