import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

@WebServlet("/movielist")
public class MovielistServlet extends HttpServlet {

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String loginUser = "root";
        String loginPasswd = "password";
        String loginUrl = "jdbc:mysql://localhost:3306/moviedb";

        response.setContentType("text/html");
        PrintWriter out = response.getWriter();

        out.println("<html><head><title>Fabflix</title></head>");

        try {
            // Recommended driver for MySQL Connector/J 8+
            Class.forName("com.mysql.cj.jdbc.Driver");

            Connection connection = DriverManager.getConnection(loginUrl, loginUser, loginPasswd);
            Statement statement = connection.createStatement();

            // Only select the columns you want
            String query = "SELECT * FROM movies LIMIT 20";
            ResultSet resultSet = statement.executeQuery(query);

            out.println("<body>");
            out.println("<h1>Movie List</h1>");

            out.println("<table border='1'>");
            out.println("<tr>");
            out.println("<th>movieId</th>");
            out.println("<th>title</th>");
            out.println("<th>year</th>");
            out.println("</tr>");

            while (resultSet.next()) {
                String movieId = resultSet.getString("id");
                String title = resultSet.getString("title");
                int year = resultSet.getInt("year");

                out.println("<tr>");
                out.println("<td>" + movieId + "</td>");
                out.println("<td>" + title + "</td>");
                out.println("<td>" + year + "</td>");
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
