import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;


/**
 * 1) Read query parameters.
 * 2) Build SQL SELECT statement + optional WHERE conditions.
 * 3) Bind parameters in PreparedStatement.
 * 4) Execute query.
 * 5) Stream results out as a JSON array.
 * 6) Return HTTP 500 with a JSON error object.
 */
@WebServlet("/movielist")
public class MovielistServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String loginUser = "root";
        String loginPasswd = "Teehee1324!";
        String loginUrl = "jdbc:mysql://localhost:3306/moviedb";

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        // Read optional filters from query string
        String title = trimOrNull(request.getParameter("title"));
        String yearStr = trimOrNull(request.getParameter("year"));
        String director = trimOrNull(request.getParameter("director"));
        String star = trimOrNull(request.getParameter("star"));

        String genreIdStr = trimOrNull(request.getParameter("genreId"));
        String startsWith = trimOrNull(request.getParameter("startsWith"));

        // Filtering for query string
        boolean hasFilter = (title != null || yearStr != null || director != null || star != null || genreIdStr != null || startsWith != null);

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            try (Connection connection = DriverManager.getConnection(loginUrl, loginUser, loginPasswd)) {

                StringBuilder sql = new StringBuilder();
                sql.append("SELECT m.id, m.title, m.year, m.director, r.rating AS rating, ");

                // Build the base queries
                // First three genres
                sql.append(" ( ");
                sql.append("   SELECT GROUP_CONCAT(t.name ORDER BY t.name SEPARATOR ', ') ");
                sql.append("   FROM ( ");
                sql.append("     SELECT DISTINCT g.name AS name ");
                sql.append("     FROM genres_in_movies gim ");
                sql.append("     JOIN genres g ON g.id = gim.genreId ");
                sql.append("     WHERE gim.movieId = m.id ");
                sql.append("     ORDER BY g.name ");
                sql.append("     LIMIT 3 ");
                sql.append("   ) AS t ");
                sql.append(" ) AS genres, ");

                // First three stars
                sql.append(" ( ");
                sql.append("   SELECT GROUP_CONCAT(t2.pair SEPARATOR ', ') ");
                sql.append("   FROM ( ");
                sql.append("     SELECT DISTINCT CONCAT(s.id, ':', s.name) AS pair ");
                sql.append("     FROM stars_in_movies sim ");
                sql.append("     JOIN stars s ON s.id = sim.starId ");
                sql.append("     WHERE sim.movieId = m.id ");
                sql.append("     LIMIT 3 ");
                sql.append("   ) AS t2 ");
                sql.append(" ) AS stars ");

                sql.append("FROM movies m ");
                sql.append("LEFT JOIN ratings r ON r.movieId = m.id ");

                // Build WHERE caluse and parameter list
                List<String> where = new ArrayList<>();
                List<Object> params = new ArrayList<>();

                // title substring match
                if (title != null) {
                    where.add("LOWER(m.title) LIKE CONCAT('%', LOWER(?), '%')");
                    params.add(title);
                }

                // director substring match
                if (director != null) {
                    where.add("LOWER(m.director) LIKE CONCAT('%', LOWER(?), '%')");
                    params.add(director);
                }

                // year exact match
                if (yearStr != null) {
                    try {
                        int y = Integer.parseInt(yearStr);
                        where.add("m.year = ?");
                        params.add(y);
                    } catch (NumberFormatException nfe) {
                        // invalid year
                        out.print("[]");
                        return;
                    }
                }

                // star substring match
                if (star != null) {
                    where.add(
                            "EXISTS (" +
                                    "  SELECT 1 " +
                                    "  FROM stars_in_movies sim " +
                                    "  JOIN stars s ON s.id = sim.starId " +
                                    "  WHERE sim.movieId = m.id " +
                                    "    AND LOWER(s.name) LIKE CONCAT('%', LOWER(?), '%')" +
                                    ")"
                    );
                    params.add(star);
                }

                // browse by genreId
                if (genreIdStr != null) {
                    try {
                        int gid = Integer.parseInt(genreIdStr);
                        where.add(
                                "EXISTS (" +
                                        "  SELECT 1 " +
                                        "  FROM genres_in_movies gim " +
                                        "  WHERE gim.movieId = m.id AND gim.genreId = ?" +
                                        ")"
                        );
                        params.add(gid);
                    } catch (NumberFormatException nfe) {
                        out.print("[]");
                        return;
                    }
                }

                // browse by startsWith for letter and number
                if (startsWith != null) {
                    if (startsWith.equals("*")) {
                        where.add("m.title REGEXP '^[^0-9A-Za-z]'");
                    } else {
                        String ch = startsWith.substring(0, 1);
                        where.add("LOWER(m.title) LIKE CONCAT(LOWER(?), '%')");
                        params.add(ch);
                    }
                }

                if (!where.isEmpty()) {
                    sql.append(" WHERE ").append(String.join(" AND ", where));
                }

                // Keep your rating sort
                sql.append(" ORDER BY (r.rating IS NULL) ASC, r.rating DESC, m.title ASC ");

                // Only default page shows top 20
                if (!hasFilter) {
                    sql.append(" LIMIT 20 ");
                }

                // Prepare statement, bind parameters, excute query
                try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        Object p = params.get(i);
                        if (p instanceof Integer) ps.setInt(i + 1, (Integer) p);
                        else ps.setString(i + 1, String.valueOf(p));
                    }

                    // Results as JSON
                    try (ResultSet resultSet = ps.executeQuery()) {

                        out.print("[");
                        boolean first = true;

                        while (resultSet.next()) {
                            String movieId = resultSet.getString("id");
                            String t = resultSet.getString("title");
                            int year = resultSet.getInt("year");
                            String dir = resultSet.getString("director");
                            String genres = resultSet.getString("genres");
                            String stars = resultSet.getString("stars");

                            // Suberqies that are null/empty become "N/A/"
                            if (genres == null || genres.trim().isEmpty()) genres = "N/A";
                            if (stars == null || stars.trim().isEmpty()) stars = "N/A";

                            Object ratingObj = resultSet.getObject("rating");
                            String rating = (ratingObj == null) ? "N/A"
                                    : String.valueOf(((Number) ratingObj).doubleValue());

                            if (!first) out.print(",");
                            first = false;

                            // Build JSON object
                            out.print("{");
                            out.print("\"id\":\"" + escapeHtml(movieId) + "\",");
                            out.print("\"title\":\"" + escapeHtml(t) + "\",");
                            out.print("\"year\":" + year + ",");
                            out.print("\"director\":\"" + escapeHtml(dir) + "\",");
                            out.print("\"genres\":\"" + escapeHtml(genres) + "\",");
                            out.print("\"stars\":\"" + escapeHtml(stars) + "\",");
                            out.print("\"rating\":\"" + escapeHtml(rating) + "\"");
                            out.print("}");
                        }

                        out.print("]");
                    }
                }
            }

            // Debuggin
        } catch (Exception e) {
            request.getServletContext().log("Error: ", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\":\"Exception in doGet: " + escapeHtml(e.getMessage()) + "\"}");
        } finally {
            out.close();
        }
    }

    private static String trimOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
