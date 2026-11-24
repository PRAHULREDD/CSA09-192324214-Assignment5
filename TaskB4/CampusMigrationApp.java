import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Task B4 - Final Engineering Decision & Multi-Campus Migration:
 * B4.1: Reads legacy CSV pass file, validates records, batch-loads into MySQL atomically,
 *       and writes a failure report for rejected records.
 * B4.2: Inspects server and driver environment using DatabaseMetaData.
 */
public class CampusMigrationApp {

    private static final int BATCH_SIZE = 1000;

    public static void inspectDatabaseMetadata(Connection con) throws SQLException {
        DatabaseMetaData meta = con.getMetaData();
        System.out.println("=== B4.2: DatabaseMetaData Environment Inspection ===");
        System.out.println("Database Product : " + meta.getDatabaseProductName()
                + " " + meta.getDatabaseProductVersion());
        System.out.println("JDBC Driver      : " + meta.getDriverName()
                + " (" + meta.getDriverVersion() + ")");
        System.out.println("JDBC Spec Version: " + meta.getJDBCMajorVersion()
                + "." + meta.getJDBCMinorVersion());
        System.out.println("Supports Batch   : " + meta.supportsBatchUpdates());
        System.out.println("Supports Txns    : " + meta.supportsTransactions());

        System.out.print("Tables Present   : ");
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = meta.getTables(con.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        System.out.println(tables);
    }

    public static void migratePasses(Connection con, Path csvInput, Path failureReportOut)
            throws IOException, SQLException {
        List<String> failureReport = new ArrayList<>();
        List<String[]> validRows = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(csvInput, StandardCharsets.UTF_8)) {
            String line;
            int lineNum = 0;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.isBlank() || (lineNum == 1 && line.startsWith("pass_id,"))) {
                    continue; // Skip header or blank lines
                }
                String[] cols = line.split(",", -1);
                if (cols.length != 8) {
                    failureReport.add("Line " + lineNum + " | Expected 8 columns, got " + cols.length + " | Raw: " + line);
                    continue;
                }
                try {
                    int route = Integer.parseInt(cols[4].trim());
                    LocalDate validUntil = LocalDate.parse(cols[6].trim());
                    double amount = Double.parseDouble(cols[7].trim());
                    if (route <= 0 || amount <= 0) {
                        failureReport.add("Line " + lineNum + " | Route/Amount must be positive | Raw: " + line);
                        continue;
                    }
                    validRows.add(new String[]{
                        cols[0].trim(), cols[1].trim(), cols[2].trim(), cols[3].trim(),
                        String.valueOf(route), cols[5].trim(), validUntil.toString(), String.valueOf(amount)
                    });
                } catch (NumberFormatException | DateTimeParseException ex) {
                    failureReport.add("Line " + lineNum + " | Parse error (" + ex.getClass().getSimpleName() + ") | Raw: " + line);
                }
            }
        }

        // Write failure report file
        try (BufferedWriter writer = Files.newBufferedWriter(failureReportOut, StandardCharsets.UTF_8)) {
            writer.write("=== Multi-Campus Bus Pass Migration Failure Report ===\n");
            writer.write("Source File: " + csvInput.toAbsolutePath() + "\n");
            writer.write("Total Rejected Records: " + failureReport.size() + "\n\n");
            for (String entry : failureReport) {
                writer.write(entry);
                writer.newLine();
            }
        }

        // Atomic batch load into MySQL
        String upsertSql = "INSERT INTO campus_bus_pass "
                + "(pass_id, campus_code, reg_no, name, route_no, boarding_stop, validity_date, amount) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE route_no = VALUES(route_no), amount = VALUES(amount)";

        boolean prevAutoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        int inserted = 0;
        try (PreparedStatement ps = con.prepareStatement(upsertSql)) {
            for (String[] r : validRows) {
                ps.setString(1, r[0]);
                ps.setString(2, r[1]);
                ps.setString(3, r[2]);
                ps.setString(4, r[3]);
                ps.setInt(5, Integer.parseInt(r[4]));
                ps.setString(6, r[5]);
                ps.setDate(7, Date.valueOf(r[6]));
                ps.setDouble(8, Double.parseDouble(r[7]));
                ps.addBatch();
                inserted++;
                if (inserted % BATCH_SIZE == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
            con.commit();
        } catch (SQLException | RuntimeException ex) {
            con.rollback();
            throw ex;
        } finally {
            con.setAutoCommit(prevAutoCommit);
        }

        System.out.println("\n=== B4.1: Migration Summary ===");
        System.out.println("Valid Records Migrated : " + inserted);
        System.out.println("Failed Records Logged  : " + failureReport.size()
                + " -> saved to " + failureReportOut);
        for (String err : failureReport) {
            System.out.println("  [FAILED] " + err);
        }
    }

    public static void main(String[] args) throws Exception {
        Path inputCsv = Path.of(args.length > 0 ? args[0] : "sample_passes_input.csv");
        Path failureReport = Path.of("migration_failure_report.txt");

        try (Connection con = DBConfig.getConnection()) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("TRUNCATE TABLE campus_bus_pass");
            }
            inspectDatabaseMetadata(con);
            migratePasses(con, inputCsv, failureReport);
        } catch (SQLException e) {
            System.err.println("Database connection failed: " + e.getMessage());
        }
    }
}
