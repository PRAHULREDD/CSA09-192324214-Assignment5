import java.io.Console;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class DBConfig {
    public static final String URL = System.getenv().getOrDefault(
            "DB_URL",
            "jdbc:mysql://localhost:3306/csa09_assign5?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
    );
    public static final String USER = System.getenv().getOrDefault("DB_USER", "root");
    private static String resolvedPass = System.getenv("DB_PASS");

    public static Connection getConnection() throws SQLException {
        if (resolvedPass != null) {
            return DriverManager.getConnection(URL, USER, resolvedPass);
        }

        // Try common local MySQL passwords automatically first
        List<String> candidates = new ArrayList<>(List.of("", "root", "1234", "123456", "admin", "password", "Root@1234"));
        SQLException lastEx = null;
        for (String candidate : candidates) {
            try {
                Connection con = DriverManager.getConnection(URL, USER, candidate);
                resolvedPass = candidate;
                return con;
            } catch (SQLException ex) {
                lastEx = ex;
                if (ex.getErrorCode() != 1045) {
                    throw ex; // Not an auth error (e.g., server down or DB missing)
                }
            }
        }

        // Prompt interactively if none of the defaults matched
        Console console = System.console();
        if (console != null) {
            char[] pwdChars = console.readPassword("Enter MySQL password for '%s': ", USER);
            resolvedPass = new String(pwdChars);
        } else {
            System.out.print("Enter MySQL password for '" + USER + "': ");
            Scanner sc = new Scanner(System.in);
            resolvedPass = sc.nextLine();
        }
        return DriverManager.getConnection(URL, USER, resolvedPass);
    }
}
