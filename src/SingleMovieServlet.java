import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

@WebServlet("/singlemovie")
public class SingleMovieServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {

        String loginUser = "root";
        String loginPasswd = "Teehee1324!";
        String loginUrl = "jdbc:mysql://localhost:3306";

        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        String movieId = request.getParameter("id");

        out.println("<html><head><title>Single Movie</title></head><body>");
        out.println("<a href='movielist'>Back to Movie List</a><br/><br/>");

        if (movieId == null || movieId.trim().isEmpty()) {
            out.println("<p>Missing required parameter: id</p>");
            out.println("</body></html>");
            out.close();
            return;
        }

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection connection = DriverManager.getConnection(loginUrl, loginUser, loginPasswd);

            // 1) Movie basic info + rating (rating may not exist -> LEFT JOIN)
            String movieQuery =
                    "SELECT m.id, m.title, m.year, m.director, r.rating " +
                            "FROM movies m " +
                            "LEFT JOIN ratings r ON r.movieId = m.id " +
                            "WHERE m.id = ?";

            String title = null;
            Integer year = null;
            String director = null;
            Float rating = null;

            try (PreparedStatement ps = connection.prepareStatement(movieQuery)) {
                ps.setString(1, movieId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        title = rs.getString("title");
                        year = (Integer) rs.getObject("year");
                        director = rs.getString("director");
                        rating = (Float) rs.getObject("rating"); // can be null
                    }
                }
            }

            if (title == null) {
                out.println("<p>Movie not found for id: " + escapeHtml(movieId) + "</p>");
                out.println("</body></html>");
                connection.close();
                out.close();
                return;
            }

            out.println("<h1>" + escapeHtml(title) + "</h1>");
            out.println("<p><b>Year:</b> " + year + "</p>");
            out.println("<p><b>Director:</b> " + escapeHtml(director) + "</p>");
            out.println("<p><b>Rating:</b> " + (rating == null ? "N/A" : rating) + "</p>");

            // 2) All genres
            String genresQuery =
                    "SELECT g.name " +
                            "FROM genres_in_movies gim " +
                            "JOIN genres g ON g.id = gim.genreId " +
                            "WHERE gim.movieId = ? " +
                            "ORDER BY g.name";

            out.println("<h2>Genres</h2>");
            out.println("<ul>");
            boolean anyGenre = false;
            try (PreparedStatement ps = connection.prepareStatement(genresQuery)) {
                ps.setString(1, movieId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        anyGenre = true;
                        out.println("<li>" + escapeHtml(rs.getString("name")) + "</li>");
                    }
                }
            }
            if (!anyGenre) out.println("<li>N/A</li>");
            out.println("</ul>");

            // 3) All stars (hyperlinked)
            // NOTE: This links to /single-star?id=... (you’ll implement that servlet next).
            String starsQuery =
                    "SELECT s.id, s.name, s.birthYear " +
                            "FROM stars_in_movies sim " +
                            "JOIN stars s ON s.id = sim.starId " +
                            "WHERE sim.movieId = ? " +
                            "ORDER BY s.name";

            out.println("<h2>Stars</h2>");
            out.println("<ul>");
            boolean anyStar = false;
            try (PreparedStatement ps = connection.prepareStatement(starsQuery)) {
                ps.setString(1, movieId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        anyStar = true;
                        String starId = rs.getString("id");
                        String starName = rs.getString("name");

                        out.println("<li><a href='single-star?id=" + escapeHtml(starId) + "'>"
                                + escapeHtml(starName) + "</a></li>");
                    }
                }
            }
            if (!anyStar) out.println("<li>N/A</li>");
            out.println("</ul>");

            connection.close();

        } catch (Exception e) {
            request.getServletContext().log("Error: ", e);
            out.println("<p>Exception in doGet: " + escapeHtml(e.getMessage()) + "</p>");
        }

        out.println("</body></html>");
        out.close();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
