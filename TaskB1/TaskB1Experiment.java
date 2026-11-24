import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Task B1 - Complete Experimental Investigation:
 * B1.1: Validation of add, find, and list (normal, missing ID, damaged line)
 * B1.3: Unbuffered vs Buffered line-counting benchmarks on 1 MB, 10 MB, and 50 MB files
 * B1.4: PassStore.find() growth from 1,000 to 100,000 records vs HashMap index alternative
 */
public class TaskB1Experiment {

    /** Extended list-by-route operation matching Task A1.2 design */
    public static List<String[]> listByRoute(Path file, int targetRoute) throws IOException {
        List<String[]> matches = new ArrayList<>();
        if (Files.notExists(file)) {
            return matches;
        }
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", -1);
                if (parts.length != 5) {
                    continue;
                }
                try {
                    int route = Integer.parseInt(parts[3].trim());
                    double amount = Double.parseDouble(parts[4].trim());
                    if (amount > 0 && route == targetRoute) {
                        matches.add(parts);
                    }
                } catch (NumberFormatException ignored) {
                    // Skip malformed numeric fields
                }
            }
        }
        return matches;
    }

    /** Unbuffered byte-by-byte line counter (Task A2.1 baseline) */
    public static long countLinesUnbuffered(Path file) throws IOException {
        long lines = 0;
        long bytes = 0;
        int b;
        int last = -1;
        try (InputStream in = new FileInputStream(file.toFile())) {
            while ((b = in.read()) != -1) {
                bytes++;
                if (b == '\n') {
                    lines++;
                }
                last = b;
            }
        }
        if (bytes > 0 && last != '\n') {
            lines++;
        }
        return lines;
    }

    /** Buffered 64 KB chunk line counter (Task A2.2 redesign) */
    public static long countLinesBuffered(Path file) throws IOException {
        long lines = 0;
        long bytes = 0;
        byte[] buf = new byte[65536];
        int n;
        int last = -1;
        try (InputStream in = new BufferedInputStream(new FileInputStream(file.toFile()), 65536)) {
            while ((n = in.read(buf)) != -1) {
                bytes += n;
                for (int i = 0; i < n; i++) {
                    if (buf[i] == '\n') {
                        lines++;
                    }
                }
                last = buf[n - 1];
            }
        }
        if (bytes > 0 && last != '\n') {
            lines++;
        }
        return lines;
    }

    private static Path generateLogFile(int targetMegabytes) throws IOException {
        Path tmp = Files.createTempFile("attendance_" + targetMegabytes + "MB_", ".log");
        long targetBytes = (long) targetMegabytes * 1024L * 1024L;
        String sampleLine = "2026-11-24T08:30:15Z,192324214,ROUTE-12,POONAMALLEE,BOARDED_OK\n";
        byte[] chunk = sampleLine.repeat(1024).getBytes(StandardCharsets.UTF_8); // ~64 KB block
        long written = 0;
        try (var out = Files.newOutputStream(tmp, StandardOpenOption.WRITE)) {
            while (written < targetBytes) {
                int len = (int) Math.min(chunk.length, targetBytes - written);
                out.write(chunk, 0, len);
                written += len;
            }
        }
        return tmp;
    }

    public static void main(String[] args) throws IOException {
        System.out.println("=== B1.1: Functional Validation (Normal, Missing ID, Damaged Line) ===");
        Path testFile = Files.createTempFile("b1_validation_", ".csv");
        PassStore store = new PassStore(testFile);
        store.add("P1001", "192324214", "Rahul Reddy", 12, 4500.0);
        Files.writeString(testFile, "P1002,DAMAGED_RECORD_ONLY_TWO_FIELDS\n", StandardOpenOption.APPEND);
        store.add("P1003", "192311003", "Divya", 7, 3800.0);
        store.add("P1004", "192311004", "Karthik", 12, 4500.0);

        System.out.println("Find P1003 (normal)  : " + Arrays.toString(store.find("P1003")));
        System.out.println("Find P9999 (missing) : " + Arrays.toString(store.find("P9999")));
        System.out.println("Find P1002 (damaged) : " + Arrays.toString(store.find("P1002")));
        System.out.println("List Route 12 count  : " + listByRoute(testFile, 12).size() + " passes");
        Files.deleteIfExists(testFile);

        System.out.println("\n=== B1.3: Unbuffered vs Buffered Line Counting (1 MB, 10 MB, 50 MB) ===");
        int[] sizesMB = {1, 10, 50};
        for (int mb : sizesMB) {
            Path logFile = generateLogFile(mb);

            long t0 = System.nanoTime();
            long cUnbuf = countLinesUnbuffered(logFile);
            double msUnbuf = (System.nanoTime() - t0) / 1_000_000.0;

            long t1 = System.nanoTime();
            long cBuf = countLinesBuffered(logFile);
            double msBuf = (System.nanoTime() - t1) / 1_000_000.0;

            System.out.printf("Size: %2d MB | Lines: %d | Unbuffered: %9.2f ms | Buffered: %6.2f ms | Speedup: %.1fx%n",
                    mb, cBuf, msUnbuf, msBuf, msUnbuf / Math.max(msBuf, 0.01));
            if (cUnbuf != cBuf) {
                throw new IllegalStateException("Line count mismatch!");
            }
            Files.deleteIfExists(logFile);
        }

        System.out.println("\n=== B1.4: Growth Scaling (1,000 to 100,000 records) & HashMap Alternative ===");
        int[] recordCounts = {1_000, 10_000, 50_000, 100_000};
        for (int n : recordCounts) {
            Path scaleFile = Files.createTempFile("b1_scale_" + n + "_", ".csv");
            try (BufferedWriter w = Files.newBufferedWriter(scaleFile, StandardCharsets.UTF_8)) {
                for (int i = 1; i <= n; i++) {
                    w.write(String.format("P%06d,1923%05d,Student_%d,%d,4500.0%n", i, i, i, (i % 25) + 1));
                }
            }
            PassStore scaleStore = new PassStore(scaleFile);
            String worstCaseId = String.format("P%06d", n); // Last record in file

            // Measure 100 lookups via file scan
            int queries = 100;
            long tScanStart = System.nanoTime();
            for (int q = 0; q < queries; q++) {
                scaleStore.find(worstCaseId);
            }
            double totalScanMs = (System.nanoTime() - tScanStart) / 1_000_000.0;

            // Alternative: Load once into HashMap<String, String[]>
            long tLoadStart = System.nanoTime();
            Map<String, String[]> index = new HashMap<>(n * 2);
            try (BufferedReader br = Files.newBufferedReader(scaleFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(",", -1);
                    if (parts.length == 5) {
                        index.put(parts[0], parts);
                    }
                }
            }
            double loadMs = (System.nanoTime() - tLoadStart) / 1_000_000.0;

            long tMapStart = System.nanoTime();
            for (int q = 0; q < queries; q++) {
                index.get(worstCaseId);
            }
            double mapQueryMs = (System.nanoTime() - tMapStart) / 1_000_000.0;

            System.out.printf("N=%6d | 100 File Scans: %8.2f ms (%.3f ms/query) | HashMap Load: %6.2f ms + 100 Queries: %.4f ms%n",
                    n, totalScanMs, totalScanMs / queries, loadMs, mapQueryMs);
            Files.deleteIfExists(scaleFile);
        }
    }
}
