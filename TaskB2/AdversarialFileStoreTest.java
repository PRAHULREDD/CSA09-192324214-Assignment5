import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Task B2 - Part 2: Adversarial / Damaged Bus-Pass File Testing
 * Tests wrong field counts, non-numeric amounts/routes, negative amounts, and empty lines.
 */
public class AdversarialFileStoreTest {

    public static class ValidationResult {
        public final List<String[]> validPasses = new ArrayList<>();
        public final List<String> rejectedLines = new ArrayList<>();
    }

    /** Hardened loader that validates field count, route integer, and positive numeric amount */
    public static ValidationResult loadAndValidate(Path file) throws IOException {
        ValidationResult result = new ValidationResult();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    result.rejectedLines.add("Line " + lineNo + ": [EMPTY/WHITESPACE LINE]");
                    continue;
                }
                String[] fields = line.split(",", -1);
                if (fields.length != 5) {
                    result.rejectedLines.add("Line " + lineNo + ": [WRONG FIELD COUNT=" + fields.length + "] -> " + line);
                    continue;
                }
                try {
                    int route = Integer.parseInt(fields[3].trim());
                    double amount = Double.parseDouble(fields[4].trim());
                    if (route <= 0 || amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
                        result.rejectedLines.add("Line " + lineNo + ": [OUT-OF-RANGE ROUTE/AMOUNT] -> " + line);
                        continue;
                    }
                    result.validPasses.add(fields);
                } catch (NumberFormatException ex) {
                    result.rejectedLines.add("Line " + lineNo + ": [NON-NUMERIC FIELD] -> " + line);
                }
            }
        }
        return result;
    }

    public static void main(String[] args) throws IOException {
        Path damagedFile = Files.createTempFile("adversarial_passes_", ".csv");
        List<String> craftedRows = List.of(
            "P2001,192324214,Rahul Reddy,12,4500.0",          // Valid
            "",                                                // Empty line
            "P2002,ONLY_TWO_COLUMNS",                          // Wrong field count (2)
            "P2003,192311005,Sneha,14,FREE_PASS",              // Non-numeric amount
            "P2004,192311006,Vikram,ROUTE_A,4200.0",           // Non-numeric route
            "P2005,192311007,Ananya,9,-500.0",                 // Negative amount
            "P2006,192311008,Karthik,5,3900.0,EXTRA_COL",      // Extra column (6)
            "P2007,192311009,Priya,8,4100.0"                   // Valid
        );
        Files.write(damagedFile, craftedRows, StandardCharsets.UTF_8);

        System.out.println("=== Adversarial Bus-Pass File Store Test ===");
        ValidationResult vr = loadAndValidate(damagedFile);

        System.out.println("\nValid Passes Loaded (" + vr.validPasses.size() + "):");
        for (String[] p : vr.validPasses) {
            System.out.println("  OK -> " + Arrays.toString(p));
        }

        System.out.println("\nDamaged Lines Quarantined (" + vr.rejectedLines.size() + "):");
        for (String err : vr.rejectedLines) {
            System.out.println("  REJECTED -> " + err);
        }

        Files.deleteIfExists(damagedFile);
    }
}
