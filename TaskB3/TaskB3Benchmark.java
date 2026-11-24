import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Task B3 - Complete Experimental Investigation:
 * B3.1: Insert 10 records and inspect ResultSetMetaData
 * B3.2: Benchmark Statement vs PreparedStatement vs Batch at 1,000, 10,000, 100,000 rows
 * B3.3: Auto-commit ON vs Single Transaction on mid-way invalid record failure
 */
public class TaskB3Benchmark {

    private static List<String[]> generatePasses(int count, String prefix) {
        List<String[]> list = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            list.add(new String[]{
                prefix + String.format("%06d", i),
                "1923" + String.format("%05d", i % 100000),
                "Student_" + i,
                String.valueOf((i % 25) + 1),
                "4500.00"
            });
        }
        return list;
    }

    private static void truncateTable(Connection con) throws SQLException {
        try (Statement st = con.createStatement()) {
            st.executeUpdate("TRUNCATE TABLE bus_pass");
        }
    }

    private static long countRows(Connection con) throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM bus_pass")) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    /** (a) Plain Statement insert loop */
    private static double insertWithStatement(Connection con, List<String[]> passes) throws SQLException {
        long t0 = System.nanoTime();
        try (Statement st = con.createStatement()) {
            for (String[] p : passes) {
                String sql = String.format(
                    "INSERT INTO bus_pass (pass_id, reg_no, name, route_no, amount) VALUES ('%s','%s','%s',%d,%s)",
                    p[0], p[1], p[2], Integer.parseInt(p[3]), p[4]
                );
                st.executeUpdate(sql);
            }
        }
        return (System.nanoTime() - t0) / 1_000_000.0;
    }

    /** (b) Individual PreparedStatement executeUpdate loop */
    private static double insertWithPreparedStatement(Connection con, List<String[]> passes) throws SQLException {
        String sql = "INSERT INTO bus_pass (pass_id, reg_no, name, route_no, amount) VALUES (?, ?, ?, ?, ?)";
        long t0 = System.nanoTime();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (String[] p : passes) {
                ps.setString(1, p[0]);
                ps.setString(2, p[1]);
                ps.setString(3, p[2]);
                ps.setInt(4, Integer.parseInt(p[3]));
                ps.setDouble(5, Double.parseDouble(p[4]));
                ps.executeUpdate();
            }
        }
        return (System.nanoTime() - t0) / 1_000_000.0;
    }

    /** (c) Batched PreparedStatement via PassLoader.loadAll() */
    private static double insertWithBatch(Connection con, List<String[]> passes) throws SQLException {
        long t0 = System.nanoTime();
        PassLoader.loadAll(con, passes);
        return (System.nanoTime() - t0) / 1_000_000.0;
    }

    public static void main(String[] args) {
        try (Connection con = DBConfig.getConnection()) {
            // ----------------------------------------------------------------
            // B3.1: Correctness on 10 records + ResultSetMetaData inspection
            // ----------------------------------------------------------------
            System.out.println("=== B3.1: Small Data Correctness & ResultSetMetaData ===");
            truncateTable(con);
            PassLoader.loadAll(con, generatePasses(10, "SM"));

            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery("SELECT * FROM bus_pass ORDER BY pass_id")) {
                ResultSetMetaData meta = rs.getMetaData();
                int cols = meta.getColumnCount();
                System.out.println("Column Count: " + cols);
                for (int c = 1; c <= cols; c++) {
                    System.out.printf("  Col %d: %-12s | SQL Type: %-10s | Precision: %d%n",
                            c, meta.getColumnName(c), meta.getColumnTypeName(c), meta.getPrecision(c));
                }
                int rowCount = 0;
                while (rs.next()) {
                    rowCount++;
                    if (rowCount <= 3) {
                        System.out.printf("  Sample Row %d: %s, %s, %s, Route %d, Rs. %.2f%n",
                                rowCount, rs.getString(1), rs.getString(2), rs.getString(3),
                                rs.getInt(4), rs.getDouble(5));
                    }
                }
                System.out.println("Total Verified Rows: " + rowCount);
            }

            // ----------------------------------------------------------------
            // B3.2: Scaling Benchmark (1,000, 10,000, 100,000 rows)
            // ----------------------------------------------------------------
            System.out.println("\n=== B3.2: Statement vs PreparedStatement vs Batch Scaling ===");
            int[] sizes = {1_000, 10_000, 100_000};
            for (int size : sizes) {
                List<String[]> data = generatePasses(size, "BM");

                truncateTable(con);
                double tStmt = insertWithStatement(con, data);

                truncateTable(con);
                double tPrep = insertWithPreparedStatement(con, data);

                truncateTable(con);
                double tBatch = insertWithBatch(con, data);

                System.out.printf("Rows: %6d | Statement: %9.2f ms | PreparedStmt: %9.2f ms | Batch(1000): %7.2f ms%n",
                        size, tStmt, tPrep, tBatch);
            }

            // ----------------------------------------------------------------
            // B3.3: Auto-commit ON vs Single Transaction with Mid-Way Failure
            // ----------------------------------------------------------------
            System.out.println("\n=== B3.3: Half-Way Failure (Auto-Commit ON vs Transactional PassLoader) ===");
            List<String[]> faultyList = generatePasses(2_500, "TX");
            // Inject invalid amount at index 1,250 (half-way through 2,500 rows)
            faultyList.get(1250)[4] = "INVALID_AMOUNT";

            // Case 1: Auto-Commit ON
            truncateTable(con);
            con.setAutoCommit(true);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO bus_pass (pass_id, reg_no, name, route_no, amount) VALUES (?, ?, ?, ?, ?)")) {
                for (String[] p : faultyList) {
                    ps.setString(1, p[0]);
                    ps.setString(2, p[1]);
                    ps.setString(3, p[2]);
                    ps.setInt(4, Integer.parseInt(p[3]));
                    ps.setDouble(5, Double.parseDouble(p[4])); // Throws NumberFormatException at row 1,251
                    ps.executeUpdate();
                }
            } catch (RuntimeException ex) {
                System.out.println("Auto-Commit ON failed at row 1,251 (" + ex.getClass().getSimpleName() + ")");
                System.out.println("  -> Rows remaining in bus_pass table: " + countRows(con) + " (PARTIAL CORRUPT STATE!)");
            }

            // Case 2: Single Transaction via PassLoader.loadAll()
            truncateTable(con);
            try {
                PassLoader.loadAll(con, faultyList);
            } catch (RuntimeException ex) {
                System.out.println("Transactional PassLoader failed at row 1,251 (" + ex.getClass().getSimpleName() + ")");
                System.out.println("  -> Rows remaining in bus_pass table: " + countRows(con) + " (CLEAN ROLLBACK!)");
            }

        } catch (SQLException e) {
            System.err.println("JDBC Error: " + e.getMessage());
        }
    }
}
