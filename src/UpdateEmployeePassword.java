import org.jasypt.util.password.PasswordEncryptor;
import org.jasypt.util.password.StrongPasswordEncryptor;

import java.sql.*;
public class UpdateEmployeePassword {
    /**  1) Read DB connection settings
     *  2) Load MySQL
     *  3) Connect to DB.
     *  4) Encrypt and update plain-text passwords
     */
    public static void main(String[] args) throws Exception {

        String loginUrl = envOrDefault("DB_URL", "jdbc:mysql://localhost:3306/moviedb");
        String loginUser = envOrDefault("DB_USER", "mytestuser");
        String loginPasswd = envOrDefault("DB_PASSWORD", "password");

        Class.forName("com.mysql.cj.jdbc.Driver");

        try (Connection connection = DriverManager.getConnection(loginUrl, loginUser, loginPasswd)) {

            PasswordEncryptor encryptor = new StrongPasswordEncryptor();

            // Read existing employee passwords
            try (PreparedStatement selectPs = connection.prepareStatement(
                    "SELECT email, password FROM employees")) {

                try (ResultSet rs = selectPs.executeQuery()) {

                    String updateSql = "UPDATE employees SET password = ? WHERE email = ?";
                    int count = 0;

                    System.out.println("Encrypting and updating employee passwords...");

                    try (PreparedStatement updatePs = connection.prepareStatement(updateSql)) {
                        while (rs.next()) {
                            String email = rs.getString("email");
                            String plainPassword = rs.getString("password");

                            // Only encrypt if it still looks like a short plain-text password.
                            // > 20 to account for old plain-text
                            if (plainPassword == null || plainPassword.length() > 20) {
                                continue;
                            }

                            // Encrypt/hash the password
                            String encryptedPassword = encryptor.encryptPassword(plainPassword);

                            // Update queue for employee
                            updatePs.setString(1, encryptedPassword);
                            updatePs.setString(2, email);
                            updatePs.addBatch();
                            count++;
                        }
                        updatePs.executeBatch();
                    }
                }
            }
        }
    }

    private static String envOrDefault(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
