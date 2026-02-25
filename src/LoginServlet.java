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

/*
 * Handles user login
 * Validates the submitted email/password
 * On successful login, stores a session attribute that marks the user as logged in.
 */
@WebServlet(name = "LoginServlet", urlPatterns = "/api/login")
public class LoginServlet extends HttpServlet {
    private DataSource dataSource;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        try {
            dataSource = (DataSource) new InitialContext().lookup("java:comp/env/jdbc/moviedb");
        } catch (NamingException e) {
            throw new ServletException("Cannot retrieve java:comp/env/jdbc/moviedb", e);
        }
    }

    /*
     *
     * 1) Read email/password from request parameters.
     * 2) Query DB for a customer with that email.
     * 3) If not found -> fail.
     * 4) If found, compare passwords -> fail if mismatch.
     * 5) If match -> set session attribute "user" and return success JSON.
     */

    // Keep defaults, but allow env vars to override (handy on AWS)

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // Read form parameters
        String email = request.getParameter("email");
        String password = request.getParameter("password");

        // reCAPTCHA token
        String gRecaptchaResponse = request.getParameter("g-recaptcha-response");

        if (email == null) email = "";
        if (password == null) password = "";

        try {
            // Verify reCAPTCHA
            try {
                RecaptchaVerifyUtils.verify(gRecaptchaResponse);
            } catch (Exception e) {
                response.getWriter().write(
                        "{\"status\":\"fail\",\"message\":\"reCAPTCHA verification failed. Please try again.\"}"
                );
                return;
            }

            // Open Database connection
            try (Connection conn = dataSource.getConnection()) {

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
                StrongPasswordEncryptor encryptor = new StrongPasswordEncryptor();
                boolean passwordOk = false;
                boolean plaintextMatched = false;

                if (dbPassword != null) {
                    try {
                        passwordOk = encryptor.checkPassword(password, dbPassword);
                    } catch (Exception ignored) {
                        passwordOk = false;
                    }

                    if (!passwordOk && dbPassword.equals(password)) {
                        passwordOk = true;
                        plaintextMatched = true;
                    }
                }

                // Incorrect password
                if (!passwordOk) {
                    response.getWriter().write("{\"status\":\"fail\",\"message\":\"incorrect password\"}");
                    return;
                }

                if (plaintextMatched) {
                    String encrypted = encryptor.encryptPassword(password);
                    try (PreparedStatement ups = conn.prepareStatement("UPDATE customers SET password = ? WHERE id = ?")) {
                        ups.setString(1, encrypted);
                        ups.setInt(2, customerId);
                        ups.executeUpdate();
                    }
                }

                // 3) Successful login
                request.getSession().setAttribute("user", customerId);
                response.getWriter().write("{\"status\":\"success\",\"message\":\"success\"}");
            }

            // Log errors
        } catch (Exception e) {
            request.getServletContext().log("Login error: ", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

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
        return s.replace("\\", "\\\\").replace("\"", "\\");
    }
}
