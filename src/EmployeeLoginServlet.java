import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.*;

import org.jasypt.util.password.StrongPasswordEncryptor;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

@WebServlet(name = "EmployeeLoginServlet", urlPatterns = "/api/employee-login")
public class EmployeeLoginServlet extends HttpServlet {
    private DataSource dataSource;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            dataSource = (DataSource) new InitialContext().lookup("java:comp/env/jdbc/moviedbWrite");
        } catch (NamingException e) {
            throw new ServletException("Cannot retrieve java:comp/env/jdbc/moviedbWrite", e);
        }
    }

    /**
     * 1) Set JSON response headers.
     * 2) Read email/password parameters
     * 3) Connect to MySQL.
     * 4) Look up employee by email in `employees` table:
     * 5) If no row -> fail: "employee not found"
     * 6) Verify password
     * 7) If password valid:
     * - set session attributes
     * - return success JSON
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Read crentials
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        if (email == null) email = "";
        if (password == null) password = "";

        // reCAPTCHA token
        String gRecaptchaResponse = request.getParameter("g-recaptcha-response");

        if (gRecaptchaResponse == null || gRecaptchaResponse.isBlank()) {
            response.getWriter().write("{\"status\":\"fail\",\"message\":\"Please complete the reCAPTCHA.\"}");
            return;
        }

        try {
            RecaptchaVerifyUtils.verify(gRecaptchaResponse);
        } catch (Exception e) {
            response.getWriter().write(
                    "{\"status\":\"fail\",\"message\":\"reCAPTCHA verification failed. Please try again.\"}"
            );
            return;
        }

        try {

            try (Connection conn = dataSource.getConnection()) {

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

                StrongPasswordEncryptor encryptor = new StrongPasswordEncryptor();
                boolean ok = false;
                boolean plaintextMatched = false;

                // Verify password with Jaspy
                try {
                    ok = encryptor.checkPassword(password, dbPassword);
                } catch (Exception ignored) {
                    ok = false;
                }
                if (!ok && dbPassword.equals(password)) {
                    ok = true;
                    plaintextMatched = true;
                }

                // Reject invalid password
                if (!ok) {
                    response.getWriter().write("{\"status\":\"fail\",\"message\":\"incorrect password\"}");
                    return;
                }

                // Mark employee as logged in on success
                request.getSession().setAttribute("employee", email);
                if (fullname != null) request.getSession().setAttribute("employee_name", fullname);

                if (plaintextMatched) {
                    String encrypted = encryptor.encryptPassword(password);
                    try (PreparedStatement ups = conn.prepareStatement("UPDATE employees SET password = ? WHERE email = ?")) {
                        ups.setString(1, encrypted);
                        ups.setString(2, email);
                        ups.executeUpdate();
                    }
                }

                response.getWriter().write("{\"status\":\"success\",\"message\":\"success\"}");
            }

        } catch (Exception e) {
            request.getServletContext().log("Employee login error: ", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            // Avoid blank exception messages
            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) msg = e.getClass().getSimpleName();

            response.getWriter().write("{\"status\":\"fail\",\"message\":\"Exception: " + escapeJson(msg) + "\"}");
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
