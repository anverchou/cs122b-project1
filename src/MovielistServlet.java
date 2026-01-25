import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

@WebServlet("/movielist")
public class MovielistServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String loginUser = "mytestuser";
        String loginPasswd = "password";
        String loginUrl = "jdbc:mysql://localhost:3306/moviedb";

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection connection = DriverManager.getConnection(loginUrl, loginUser, loginPasswd);
            Statement statement = connection.createStatement();

            String query =
                    "SELECT m.id, m.title, m.year, m.director, IFNULL(r.rating, NULL) AS rating, " +

                            // First three genres of movie
                            " ( " +
                            "   SELECT GROUP_CONCAT(t.name ORDER BY t.name SEPARATOR ', ') " +
                            "   FROM ( " +
                            "     SELECT DISTINCT g.name AS name " +
                            "     FROM genres_in_movies gim " +
                            "     JOIN genres g ON g.id = gim.genreId " +
                            "     WHERE gim.movieId = m.id " +
                            "     ORDER BY g.name " +
                            "     LIMIT 3 " +
                            "   ) AS t " +
                            " ) AS genres, " +

                            // First three stars of movie
                            " ( " +
                            "   SELECT GROUP_CONCAT(t2.pair SEPARATOR ', ') " +
                            "   FROM ( " +
                            "     SELECT DISTINCT CONCAT(s.id, ':', s.name) AS pair " +
                            "     FROM stars_in_movies sim " +
                            "     JOIN stars s ON s.id = sim.starId " +
                            "     WHERE sim.movieId = m.id " +
                            "     LIMIT 3 " +
                            "   ) AS t2 " +
                            " ) AS stars " +

                            "FROM movies m " +
                            // Add rating to query
                            "LEFT JOIN ratings r ON r.movieId = m.id " +
                            // Sort list by ratings
                            "ORDER BY (r.rating is NULL) ASC, r.rating DESC, m.title ASC " +
                            "LIMIT 20";

            ResultSet resultSet = statement.executeQuery(query);

            // Body text
            out.print("[");
            boolean first = true;

            while (resultSet.next()) {
                String movieId = resultSet.getString("id");
                String title = resultSet.getString("title");
                int year = resultSet.getInt("year");
                String director = resultSet.getString("director");
                String genres = resultSet.getString("genres");
                String stars = resultSet.getString("stars");

                // Set values to N/A if there is no value
                if (genres == null || genres.trim().isEmpty()) genres = "N/A";
                if (stars == null || stars.trim().isEmpty()) stars = "N/A";

                Object ratingObj = resultSet.getObject("rating");
                String rating = (ratingObj == null) ? "N/A" : String.valueOf(((Number) ratingObj).doubleValue());

                if (!first) out.print(",");
                first = false;

                out.print("{");
                out.print("\"id\":\"" + escapeHtml(movieId) + "\",");
                out.print("\"title\":\"" + escapeHtml(title) + "\",");
                out.print("\"year\":" + year + ",");
                out.print("\"director\":\"" + escapeHtml(director) + "\",");
                out.print("\"genres\":\"" + escapeHtml(genres) + "\",");
                out.print("\"stars\":\"" + escapeHtml(stars) + "\",");
                out.print("\"rating\":\"" + escapeHtml(rating) + "\"");
                out.print("}");
            }

            out.print("]");

            resultSet.close();
            statement.close();
            connection.close();

        } catch (Exception e) {
            request.getServletContext().log("Error: ", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            out.print("{\"error\":\"Exception in doGet: " + escapeHtml(e.getMessage()) + "\"}");
        }

        out.close();
    }

    // Hyperlink injection
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
