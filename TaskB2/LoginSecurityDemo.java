import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Task B2 - Part 1: SQL Injection Adversarial Demonstration
 * Compares vulnerable string concatenation (Task A5.2) against PreparedStatement.
 */
public class LoginSecurityDemo {

    /** VULNERABLE: String concatenation allows SQL syntax injection */
    public static boolean unsafeLogin(Connection con, String id, String pwd) throws SQLException {
        String sql = "SELECT * FROM users WHERE id='" + id + "' AND pwd='" + pwd + "'";
        System.out.println("  [Unsafe SQL Executed] -> " + sql);
        try (Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next();
        }
    }

    /** SAFE: Parameterized query binds user input strictly as literal data */
    public static boolean safeLogin(Connection con, String id, String pwd) throws SQLException {
        String sql = "SELECT id FROM users WHERE id = ? AND pwd = ?";
        System.out.println("  [Safe PreparedStatement] -> " + sql + " | params=[" + id + ", " + pwd + "]");
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, id);
            ps.setString(2, pwd);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void main(String[] args) {
        String validId = "192324214";
        String validPwd = "Rahul@2026";
        String attackId = "' OR '1'='1' -- ";
        String wrongPwd = "wrong_password";

        try (Connection con = DBConfig.getConnection()) {
            System.out.println("=== Test 1: Legitimate Credentials ===");
            System.out.println("Unsafe Login Result : " + unsafeLogin(con, validId, validPwd));
            System.out.println("Safe Login Result   : " + safeLogin(con, validId, validPwd));

            System.out.println("\n=== Test 2: Adversarial SQL Injection Payload ===");
            boolean unsafeAccepted = unsafeLogin(con, attackId, wrongPwd);
            boolean safeAccepted = safeLogin(con, attackId, wrongPwd);

            System.out.println("\nSummary:");
            System.out.println("  Unsafe version accepted invalid login? -> " + unsafeAccepted + " (BREACH!)");
            System.out.println("  PreparedStatement version accepted?    -> " + safeAccepted + " (BLOCKED!)");
        } catch (SQLException e) {
            System.err.println("Database connection error: " + e.getMessage());
            System.err.println("Ensure MySQL is running and sql/setup_all.sql has been imported.");
        }
    }
}
