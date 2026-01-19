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
        String loginUser = "root";
        String loginPasswd = "Teehee1324!";
        String loginUrl = "jdbc:mysql://localhost:3306/moviedb";

        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println("<html><head><title>Fabflix</title></head>");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection connection = DriverManager.getConnection(loginUrl, loginUser, loginPasswd);
            Statement statement = connection.createStatement();

            String query =
                    "SELECT m.id, m.title, m.year, m.director, " +

                            // First three genres of movie
                            "       ( " +
                            "         SELECT GROUP_CONCAT(t.name ORDER BY t.name SEPARATOR ', ') " +
                            "         FROM ( " +
                            "           SELECT DISTINCT g.name AS name " +
                            "           FROM genres_in_movies gim " +
                            "           JOIN genres g ON g.id = gim.genreId " +
                            "           WHERE gim.movieId = m.id " +
                            "           ORDER BY g.name " +
                            "           LIMIT 3 " +
                            "         ) AS t " +
                            "       ) AS genres, " +

                            // First three stars of movie
                            "         SELECT GROUP_CONCAT(t2.name ORDER BY t2.name SEPARATOR ', ') " +
                            "         FROM ( " +
                            "           SELECT DISTINCT s.name AS name " +
                            "           FROM stars_in_movies sim " +
                            "           JOIN stars s ON s.id = sim.starId " +
                            "           WHERE sim.movieId = m.id " +
                            "           ORDER BY s.name " +
                            "           LIMIT 3 " +
                            "         ) AS t2 " +
                            "       ) AS stars " +

                            "FROM movies m " +
                            "LIMIT 20";

            ResultSet resultSet = statement.executeQuery(query);

            out.println("<body>");
            out.println("<h1>Movie List</h1>");
            out.println("<table border='1'>");

            out.println("<tr>");
            out.println("<th>Title</th>");
            out.println("<th>Year</th>");
            out.println("<th>Director</th>");
            out.println("<th>3 Genres</th>");
            out.println("<th>3 Stars</th>");
            out.println("</tr>");

            while (resultSet.next()) {
                String movieId = resultSet.getString("id");
                String title = resultSet.getString("title");
                int year = resultSet.getInt("year");
                String director = resultSet.getString("director");
                String genres = resultSet.getString("genres");
                String stars = resultSet.getString("stars");

                // Make the Movie names a hyperlink
                out.println("<tr>");
                out.println("<td><a href='single-movie?id=" + escapeHtml(movieId) + "'>"
                        + escapeHtml(title) + "</a></td>");
                out.println("<td>" + year + "</td>");
                out.println("<td>" + escapeHtml(director) + "</td>");
                out.println("<td>" + escapeHtml(genres) + "</td>");
                out.println("<td>" + escapeHtml(stars) + "</td>");
                out.println("</tr>");
            }

            out.println("</table>");
            out.println("</body>");

            resultSet.close();
            statement.close();
            connection.close();

        } catch (Exception e) {
            request.getServletContext().log("Error: ", e);
            out.println("<body><p>Exception in doGet: " + escapeHtml(e.getMessage()) + "</p></body>");
        }

        out.println("</html>");
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
