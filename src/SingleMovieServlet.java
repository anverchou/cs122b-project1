import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

@WebServlet("/singlemovie")
public class SingleMovieServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String loginUser = "root";
        String loginPasswd = "Teehee1324!";
        String loginUrl = "jdbc:mysql://localhost:3306/moviedb";

        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        // Accept both id and movieId
        String movieId = request.getParameter("id");
        if (movieId == null || movieId.trim().isEmpty()) {
            movieId = request.getParameter("movieId");
        }

        out.println("<html><head><title>Single Movie</title></head><body>");
        // Return to movielist button
        out.println("<a href='movielist'>Back to Movie List</a><br/><br/>");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection connection = DriverManager.getConnection(loginUrl, loginUser, loginPasswd);

            // Movie info + rating
            String movieQuery =
                    "SELECT m.title, m.year, m.director, r.rating " +
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

            // Print out required information
            out.println("<h1>Movie: " + escapeHtml(title) + "</h1>");
            out.println("<p><b>Year:</b> " + year + "</p>");
            out.println("<p><b>Director:</b> " + escapeHtml(director) + "</p>");
            out.println("<p><b>Rating:</b> " + (rating == null ? "N/A" : rating) + "</p>");

            // All genres
            out.println("<h2>Genres</h2>");
            // Print into a list
            out.println("<ul>");

            // Genres query
            String genresQuery =
                    "SELECT g.name " +
                            "FROM genres_in_movies gim " +
                            "JOIN genres g ON g.id = gim.genreId " +
                            "WHERE gim.movieId = ? " +
                            "ORDER BY g.name";

            // Check if genres exist
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

            // Print depending if at least one genre exists or not
            if (!anyGenre) out.println("<li>N/A</li>");
            out.println("</ul>");

            // Print stars that are connected with the movie
            out.println("<h2>Stars</h2>");
            out.println("<ul>");

            // Stars query
            String starsQuery =
                    "SELECT s.id, s.name " +
                            "FROM stars_in_movies sim " +
                            "JOIN stars s ON s.id = sim.starId " +
                            "WHERE sim.movieId = ? " +
                            "ORDER BY s.name";

            // Check if stars exist
            boolean anyStar = false;

            try (PreparedStatement ps = connection.prepareStatement(starsQuery)) {
                ps.setString(1, movieId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        anyStar = true;
                        String starId = rs.getString("id");
                        String starName = rs.getString("name");

                        out.println("<li><a href='singlestar?id=" + escapeHtml(starId) + "'>"
                                + escapeHtml(starName) + "</a></li>");
                    }
                }
            }

            // Print out stars that correspond to the movie
            if (!anyStar) {
                out.println("<li>N/A</li>");
            }
            out.println("</ul>");
            connection.close();

        } catch (Exception e) {
            request.getServletContext().log("Error: ", e);
            out.println("<p>Exception in doGet: " + escapeHtml(e.getMessage()) + "</p>");
        }

        out.println("</body></html>");
        out.close();
    }

    // HTML injection from Movielist
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
