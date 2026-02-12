import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;

import org.jasypt.util.password.StrongPasswordEncryptor;

@WebServlet(name = "EmployeeLoginServlet", urlPatterns = "/api/employee-login")
public class EmployeeLoginServlet extends HttpServlet {

    /**
     *  1) Set JSON response headers.
     *  2) Read email/password parameters
     *  3) Connect to MySQL.
     *  4) Look up employee by email in `employees` table:
     *      SELECT password, fullname FROM employees WHERE email = ?
     *  5) If no row -> fail: "employee not found"
     *  6) Verify password
     *  7) If password valid:
     *      - set session attributes
     *      - return success JSON
     */

 private static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/moviedb";
    private static final String DEFAULT_DB_USER = "root";
    private static final String DEFAULT_DB_PASS = "Teehee1324!";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Read crentials
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        if (email == null) email = "";
        if (password == null) password = "";

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");

            try (Connection conn = DriverManager.getConnection(
                    envOrDefault("DB_URL", DEFAULT_DB_URL),
                    envOrDefault("DB_USER", DEFAULT_DB_USER),
                    envOrDefault("DB_PASSWORD", DEFAULT_DB_PASS))) {

                // Fetch encrypyed password
                String sql = "SELECT password, fullname FROM employees WHERE email = ?";
                String dbPassword = null;
                String fullname = null;

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, email);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            dbPassword = rs.getString("password");
                            fullname = rs.getString("fullname");
                        }
                    }
                }

                if (dbPassword == null) {
                    response.getWriter().write("{\"status\":\"fail\",\"message\":\"employee not found\"}");
                    return;
                }

                // Verify password with Jaspy
                boolean ok = new StrongPasswordEncryptor().checkPassword(password, dbPassword);
                if (!ok && dbPassword.equals(password)) {
                    ok = true;
                }

                // Reject invalid password
                if (!ok) {
                    response.getWriter().write("{\"status\":\"fail\",\"message\":\"incorrect password\"}");
                    return;
                }

                // Mark employee as logged in on success
                request.getSession().setAttribute("employee", email);
                if (fullname != null) request.getSession().setAttribute("employee_name", fullname);

                response.getWriter().write("{\"status\":\"success\",\"message\":\"success\"}");
            }

        } catch (Exception e) {
            request.getServletContext().log("Employee login error: ", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"status\":\"fail\",\"message\":\"Exception: " + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static String envOrDefault(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
