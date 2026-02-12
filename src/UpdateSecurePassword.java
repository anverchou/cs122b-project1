import org.jasypt.util.password.PasswordEncryptor;
import org.jasypt.util.password.StrongPasswordEncryptor;

import java.sql.*;

// Eencrypt existing plain-text passwords in moviedb.customers.
public class UpdateSecurePassword {
    /**
     *  1) Read DB connection
     *  2) Load MySQL JDBC driver.
     *  3) Connect to DB.
     *  4) SELECT id, password from customers
     *  5) Print final count.
    */
    public static void main(String[] args) throws Exception {

        String loginUrl = envOrDefault("DB_URL", "jdbc:mysql://localhost:3306/moviedb");
        String loginUser = envOrDefault("DB_USER", "root");
        String loginPasswd = envOrDefault("DB_PASSWORD", "Teehee1324!");

        Class.forName("com.mysql.cj.jdbc.Driver");

        try (Connection connection = DriverManager.getConnection(loginUrl, loginUser, loginPasswd)) {

            // Read existing passwords
            try (PreparedStatement selectPs = connection.prepareStatement("SELECT id, password FROM customers")) {
                try (ResultSet rs = selectPs.executeQuery()) {

                    PasswordEncryptor encryptor = new StrongPasswordEncryptor();

                    String updateSql = "UPDATE customers SET password = ? WHERE id = ?";
                    int count = 0;

                    System.out.println("Encrypting and updating passwords (this may take a while)...");

                    try (PreparedStatement updatePs = connection.prepareStatement(updateSql)) {
                        while (rs.next()) {
                            int id = rs.getInt("id");
                            String plainPassword = rs.getString("password");

                            // Encrypt/hash password
                            String encryptedPassword = encryptor.encryptPassword(plainPassword);

                            // Queue update for customer
                            updatePs.setString(1, encryptedPassword);
                            updatePs.setInt(2, id);
                            updatePs.addBatch();

                            // Execute in chunks
                            if (++count % 500 == 0) {
                                updatePs.executeBatch();
                                System.out.println("...updated " + count + " customers");
                            }
                        }
                        updatePs.executeBatch();
                    }

                    System.out.println("Updated " + count + " customers.");
                }
            }
        }
    }

    private static String envOrDefault(String key, String def) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? def : v;
    }
}
