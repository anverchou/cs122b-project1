import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;

/*
 * Handles user login
 * Validates the submitted email/password
 * On successful login, stores a session attribute that marks the user as logged in.
 */
@WebServlet(name = "LoginServlet", urlPatterns = "/api/login")
public class LoginServlet extends HttpServlet {
    /*
     *
     * 1) Read email/password from request parameters.
     * 2) Query DB for a customer with that email.
     * 3) If not found -> fail.
     * 4) If found, compare passwords -> fail if mismatch.
     * 5) If match -> set session attribute "user" and return success JSON.
     */

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String loginUser = "root";
        String loginPasswd = "Teehee1324!";
        String loginUrl = "jdbc:mysql://localhost:3306/moviedb";

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Read form parameters
        String email = request.getParameter("email");
        String password = request.getParameter("password");

        if (email == null) email = "";
        if (password == null) password = "";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Open Database connection
            try (Connection conn = DriverManager.getConnection(loginUrl, loginUser, loginPasswd)) {

                // 1) Check if user exists
                String q1 = "SELECT id, password FROM customers WHERE email = ?";
                Integer customerId = null;
                String dbPassword = null;

                try (PreparedStatement ps = conn.prepareStatement(q1)) {
                    ps.setString(1, email);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            customerId = rs.getInt("id");
                            dbPassword = rs.getString("password");
                        }
                    }
                }

                // If no match email, user does not exist
                if (customerId == null) {
                    response.getWriter().write("{\"status\":\"fail\",\"message\":\"user " + escapeJson(email) + " doesn't exist\"}");
                    return;
                }

                // 2) Validate password
                if (dbPassword == null || !dbPassword.equals(password)) {
                    response.getWriter().write("{\"status\":\"fail\",\"message\":\"incorrect password\"}");
                    return;
                }

                // 3) Successful login
                request.getSession().setAttribute("user", customerId);
                response.getWriter().write("{\"status\":\"success\",\"message\":\"success\"}");
            }

            // Log errors
        } catch (Exception e) {
            request.getServletContext().log("Login error: ", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"fail\",\"message\":\"Exception: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
