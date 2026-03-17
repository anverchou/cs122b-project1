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

@WebServlet(name = "LoginServlet", urlPatterns = "/api/login")
public class LoginServlet extends HttpServlet {
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

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String email = request.getParameter("email");
        String password = request.getParameter("password");

        if (email == null) email = "";
        if (password == null) password = "";

        System.out.println("[LoginService] Login request for: " + email);

        try {
            try (Connection conn = dataSource.getConnection()) {

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

                if (customerId == null) {
                    response.getWriter().write("{\"status\":\"fail\",\"message\":\"user " + escapeJson(email) + " doesn't exist\"}");
                    return;
                }

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

                // Store user in Redis session instead of Tomcat session
                String sessionId = request.getSession().getId();
                RedisUtil.setSessionAttribute(sessionId, "user", String.valueOf(customerId));

                System.out.println("[LoginService] Login success for user ID: " + customerId + ", sessionId: " + sessionId);

                response.getWriter().write("{\"status\":\"success\",\"message\":\"success\"}");
            }

        } catch (Exception e) {
            request.getServletContext().log("Login error: ", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) msg = e.getClass().getSimpleName();

            response.getWriter().write("{\"status\":\"fail\",\"message\":\"Exception: " + escapeJson(msg) + "\"}");
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}