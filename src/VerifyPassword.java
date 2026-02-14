import org.jasypt.util.password.StrongPasswordEncryptor;
import java.sql.*;

// Verifies a user's email/password against the encrypted
public class VerifyPassword {
    /**
     *  1) Read DB connection
     *  2) Load MySQL JDBC driver.
     *  3) Connect to database.
     *  4) Query for the stored encrypted password by email.
     *  5) If no row found -> return false.
     */
    public static void main(String[] args) throws Exception {

        System.out.println(verifyCredentials("a@email.com", "a2"));
        System.out.println(verifyCredentials("a@email.com", "a3"));

    }

    private static boolean verifyCredentials(String email, String password) throws Exception {

        String loginUrl = envOrDefault("DB_URL", "jdbc:mysql://localhost:3306/moviedb");
        String loginUser = envOrDefault("DB_USER", "mytestuser");
        String loginPasswd = envOrDefault("DB_PASSWORD", "password");

        Class.forName("com.mysql.cj.jdbc.Driver");

        // Establish database connection
        try (Connection connection = DriverManager.getConnection(loginUrl, loginUser, loginPasswd)) {
            // Bind email parameter
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT password FROM customers WHERE email = ?")) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    // Credentials are invalid if no matching customer
                    if (!rs.next()) return false;

                    // Read encrupye password from database
                    String encryptedPassword = rs.getString("password");
                    // Verify raw password
                    return new StrongPasswordEncryptor().checkPassword(password, encryptedPassword);
                }
            }
        }
    }

    private static String envOrDefault(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
