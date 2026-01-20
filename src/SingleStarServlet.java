import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;

@WebServlet("/singlestar")
public class SingleStarServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String loginUser = "testuser";
        String loginPasswd = "password";
        String loginUrl = "jdbc:mysql://localhost:3306/moviedb";

        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        String starId = request.getParameter("id");

        // Return to Movie List button
        out.println("<html><head><title>Single Star</title></head><body>");
        out.println("<a href='movielist'>Back to Movie List</a><br/><br/>");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection connection = DriverManager.getConnection(loginUrl, loginUser, loginPasswd);

            // Get star info
            String starQuery = "SELECT name, birthYear FROM stars WHERE id = ?";
            // Set values to null first
            String name = null;
            Integer birthYear = null;

            try (PreparedStatement ps = connection.prepareStatement(starQuery)) {
                ps.setString(1, starId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        name = rs.getString("name");
                        birthYear = (Integer) rs.getObject("birthYear"); // may be null
                    }
                }
            }

            // Display the actor's information
            out.println("<h1>" + escapeHtml(name) + "</h1>");
            // Display birthYear or N/A if not applicable
            out.println("<p><b>Year of Birth:</b> " + (birthYear == null ? "N/A" : birthYear) + "</p>");

            // Display all movies the actor has starred in
            out.println("<h2>Movies</h2>");
            out.println("<ul>");

            String moviesQuery =
                    "SELECT m.id, m.title, m.year " +
                            "FROM stars_in_movies sim " +
                            "JOIN movies m ON m.id = sim.movieId " +
                            "WHERE sim.starId = ? " +
                            "ORDER BY m.title";

            boolean anyMovie = false;
            try (PreparedStatement ps = connection.prepareStatement(moviesQuery)) {
                ps.setString(1, starId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        anyMovie = true;
                        String movieId = rs.getString("id");
                        String title = rs.getString("title");
                        out.println("<li><a href='singlemovie?id=" + escapeHtml(movieId) + "'>"
                                + escapeHtml(title) + "</a></li>");
                    }
                }
            }

            // Return list of movies the actor has starred in or N/A
            if (!anyMovie) out.println("<li>N/A</li>");
            out.println("</ul>");

            connection.close();

        } catch (Exception e) {
            request.getServletContext().log("Error: ", e);
            out.println("<p>Exception: " + escapeHtml(e.getMessage()) + "</p>");
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
