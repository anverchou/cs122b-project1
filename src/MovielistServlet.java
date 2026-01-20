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
        String loginUser = "testuser";
        String loginPasswd = "password";
        String loginUrl = "jdbc:mysql://localhost:3306/moviedb";

        // Header Text
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println("<!doctype html>");
        out.println("<html lang='en'>");
        out.println("<head>");
        out.println("<meta charset='utf-8'>");
        out.println("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        out.println("<title>Fabflix - Movie List</title>");
        out.println("<style>");

        // Theme of the page
        out.println("  :root { --bg:#0b1220; --card:#0f172a; --text:#e5e7eb; --muted:#9ca3af; --border:#1f2937; --link:#60a5fa; --linkhover:#93c5fd; }");

        // Styling of page
        out.println("  body { margin:0; font-family: system-ui,-apple-system,Segoe UI,Roboto,Arial,sans-serif; background: linear-gradient(180deg, #070b14, #0b1220); color: var(--text); }");

        // Center container
        out.println("  .container { max-width: 1100px; margin: 40px auto; padding: 0 16px; }");

        // Header row
        out.println("  .header { display:flex; align-items:flex-end; justify-content:space-between; gap:12px; margin-bottom:16px; }");

        // Title page
        out.println("  h1 { margin:0; font-size: 28px; letter-spacing: .2px; }");

        // Card container
        out.println("  .card { background: rgba(15, 23, 42, 0.75); border: 1px solid var(--border); border-radius: 14px; overflow:hidden; box-shadow: 0 12px 30px rgba(0,0,0,.35); }");

        // Table
        out.println("  table { width:100%; border-collapse: collapse; }");

        // Cell padding and borders
        out.println("  th, td { padding: 12px 12px; border-bottom: 1px solid var(--border); vertical-align: top; }");

        // Header cell styling
        out.println("  th { text-align:left; font-size: 12px; text-transform: uppercase; letter-spacing: .08em; color: var(--muted); background: rgba(255,255,255,.02); }");

        // Zebra striping
        out.println("  tr:nth-child(even) td { background: rgba(255,255,255,.015); }");

        // Hover row highlight
        out.println("  tr:hover td { background: rgba(96,165,250,.06); }");

        // Link styling
        out.println("  a { color: var(--link); text-decoration: none; }");
        out.println("  a:hover { color: var(--linkhover); text-decoration: underline; }");
        out.println("  .nowrap { white-space: nowrap; }");
        out.println("</style>");
        out.println("</head>");

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

            // Body Text
            out.println("<body>");
            out.println("  <div class='container'>");
            out.println("    <div class='header'>");
            out.println("      <div>");
            out.println("        <h1>Top 20 Movie List</h1>");
            out.println("      </div>");
            out.println("    </div>");

            out.println("    <div class='card'>");

            out.println("      <table>");
            out.println("        <thead>");
            out.println("          <tr>");
            out.println("            <th style='width:22%'>Title</th>");
            out.println("            <th class='nowrap' style='width:8%'>Year</th>");
            out.println("            <th style='width:18%'>Director</th>");
            out.println("            <th style='width:24%'>Genres</th>");
            out.println("            <th style='width:20%'>Stars</th>");
            out.println("            <th class='nowrap' style='width:8%'>Rating</th>");
            out.println("          </tr>");
            out.println("        </thead>");
            out.println("        <tbody>");

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

                Float ratingObj = (Float)resultSet.getObject("rating");
                String rating = (ratingObj == null) ? "N/A" : String.valueOf(ratingObj);

                // Make the Movie names a hyperlink
                out.println("<tr>");
                out.println("<td><a href='singlemovie?id=" + escapeHtml(movieId) + "'>"
                        + escapeHtml(title) + "</a></td>");
                out.println("<td>" + year + "</td>");
                out.println("<td>" + escapeHtml(director) + "</td>");
                out.println("<td>" + escapeHtml(genres) + "</td>");
                // Hyperlink stars
                out.println("<td>");
                if (stars == null || stars.trim().isEmpty()) {
                    out.println("N/A");
                } else {
                    // Formatting of stars in order
                    String[] pairs = stars.split(", ");
                    for (int i = 0; i < pairs.length; i++) {
                        String[] parts = pairs[i].split(":", 2);
                        String starId = parts[0];
                        String starName = (parts.length > 1) ? parts[1] : parts[0];

                        out.print("<a href='singlestar?id=" + escapeHtml(starId) + "'>"
                                + escapeHtml(starName) + "</a>");
                        if (i < pairs.length - 1) out.print(", ");
                    }
                }
                out.println("</td>");
                out.println("<td>" + escapeHtml(rating) + "</td>");
                out.println("</tr>");
            }
            out.println("        </tbody>");
            out.println("      </table>");
            out.println("    </div>");
            out.println("  </div>");
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
