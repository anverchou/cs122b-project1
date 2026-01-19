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
        String loginPasswd = "pasword";
        String loginUrl = "jdbc:mysql://localhost:3306/moviedb";

        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        out.println("<html><head><title>Fabflix</title></head>");

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection connection = DriverManager.getConnection(loginUrl, loginUser, loginPasswd);
            Statement statement = connection.createStatement();

            String query = "SELECT * FROM movies LIMIT 20";

            ResultSet resultSet = statement.executeQuery(query);

            out.println("<body>");
            out.println("<h1>Top 20 Rated Movies</h1>");
            out.println("<table border='1'>");

            out.println("<tr>");
            out.println("<th>title</th>");
            out.println("<th>year</th>");
            out.println("<th>director</th>");
//            out.println("<th>genres (first 3)</th>");
//            out.println("<th>stars (first 3)</th>");
//            out.println("<th>rating</th>");
            out.println("</tr>");

            while (resultSet.next()) {
                String title = resultSet.getString("title");
                int year = resultSet.getInt("year");
                String director = resultSet.getString("director");
//                String genres3 = resultSet.getString("genres3");
//                String stars3 = resultSet.getString("stars3");
//                float rating = resultSet.getFloat("rating");

                out.println("<tr>");
                out.println("<td>" + title + "</td>");
                out.println("<td>" + year + "</td>");
                out.println("<td>" + director + "</td>");
//                out.println("<td>" + (genres3 == null ? "" : genres3) + "</td>");
//                out.println("<td>" + (stars3 == null ? "" : stars3) + "</td>");
//                out.println("<td>" + rating + "</td>");
                out.println("</tr>");
            }

            out.println("</table>");
            out.println("</body>");

            resultSet.close();
            statement.close();
            connection.close();

        } catch (Exception e) {
            request.getServletContext().log("Error: ", e);
            out.println("<body><p>Exception in doGet: " + e.getMessage() + "</p></body>");
        }

        out.println("</html>");
        out.close();
    }
}
