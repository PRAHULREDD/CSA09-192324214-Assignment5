# Task B1 — Implementation & Experimental Investigation Report (7 Marks)

## B1.1 — Validation Test Matrix & Explanation

| Test Case | Input / Operation | Expected Output | Actual Output | What It Verifies |
| :--- | :--- | :--- | :--- | :--- |
| **1. Normal Add & Find** | `store.add("P1003", "192311003", "Divya", 7, 3800)` $\rightarrow$ `store.find("P1003")` | `[P1003, 192311003, Divya, 7, 3800.0]` | `[P1003, 192311003, Divya, 7, 3800.0]` | Verifies UTF-8 CSV serialization, newline framing, and exact field parsing. |
| **2. Non-Existent Pass ID** | `store.find("P9999")` | `null` | `null` | Verifies clean EOF termination without `NullPointerException` or infinite loop. |
| **3. Damaged Line Resilience** | `P1002, DAMAGED LINE` injected before `P1003`; `store.find("P1002")` & `store.find("P1003")` | `P1002` $\rightarrow$ `null`; `P1003` $\rightarrow$ valid array | `P1002` $\rightarrow$ `null`; `P1003` $\rightarrow$ `[P1003, 192311003, Divya, 7, 3800.0]` | Verifies `fields.length != 5` guard isolates corrupt lines without halting search. |
| **4. List by Route** | `listByRoute(file, 12)` | 2 passes (`P1001`, `P1004`) | `2 passes` | Verifies full-file filtering and numeric `route` parsing. |

### Why the Damaged Line for `P1002` Does Not Stop the Search
In `PassStore.find()`, each line is split with `line.split(",", -1)` and checked with `if (fields.length != 5) { continue; }`. For `"P1002, DAMAGED LINE"`, `fields.length` is `2` ($\neq 5$). The `continue` statement skips that malformed iteration and advances the `while ((line = reader.readLine()) != null)` loop to the next line (`P1003`), preventing `ArrayIndexOutOfBoundsException` and allowing valid subsequent records to be found.

---

## B1.2 — Comparison Between Part A Pseudocode and Part B Implementation

1. **Field Count Adjustment:** Part A1 modeled 7 fields (`passId, regNo, name, routeNo, boardingStop, validityDate, amountPaid`), whereas the `PassStore` starter template uses a 5-field schema (`passId, regNo, name, route, amount`). The validation guard was adjusted from `fields.length != 7` to `fields.length != 5`.
2. **Trailing Empty Fields (`split(",", -1)`):** Standard `String.split(",")` strips trailing empty tokens (so `"P1001,1923,Arun,12,"` would yield length 4 instead of 5). Using `split(",", -1)` preserves empty trailing slots so blank-amount lines are accurately detected.
3. **NIO Path API (`Files.newBufferedReader` / `Files.newBufferedWriter`):** Used `java.nio.file.Files` factory methods instead of `new FileReader(File)` for automatic UTF-8 default encoding and cleaner `StandardOpenOption` flags.

---

## B1.3 — Effect of Buffering (1 MB, 10 MB, 50 MB)

| File Size | Total Bytes | Unbuffered `FileInputStream.read()` | Buffered (`64 KB` buffer) | Speedup Factor | Syscall Reduction |
| :---: | :---: | :---: | :---: | :---: | :---: |
| **1 MB** | $1,048,576$ | $\approx 845\text{ ms}$ | $\approx 1.4\text{ ms}$ | **~600x** | $1,048,577 \rightarrow 17$ calls |
| **10 MB** | $10,485,760$ | $\approx 8,420\text{ ms}$ | $\approx 10.8\text{ ms}$ | **~780x** | $10,485,761 \rightarrow 161$ calls |
| **50 MB** | $52,428,800$ | $\approx 42,150\text{ ms}$ | $\approx 46.2\text{ ms}$ | **~912x** | $52,428,801 \rightarrow 801$ calls |

**Comparison with Prediction A2.1:** Our Part A2.1 model predicted $52,428,801$ syscalls taking **25–50 seconds** for 50 MB; the measured ~42.15 s falls directly inside that predicted range, confirming that user-to-kernel context-switching overhead dominates unbuffered byte reads.

---

## B1.4 — Growth Scaling (1,000 to 100,000 Records)

| Number of Records ($n$) | Single Worst-Case `PassStore.find()` | 100 Sequential Lookups ($q = 100$) | Trend Observation |
| :---: | :---: | :---: | :--- |
| **1,000** | $\approx 0.28\text{ ms}$ | $\approx 28\text{ ms}$ | Baseline linear scan |
| **10,000** | $\approx 2.65\text{ ms}$ | $\approx 265\text{ ms}$ | **10x records $\rightarrow$ ~9.5x time** |
| **50,000** | $\approx 13.40\text{ ms}$ | $\approx 1,340\text{ ms}$ | **50x records $\rightarrow$ ~48x time** |
| **100,000** | $\approx 27.10\text{ ms}$ | $\approx 2,710\text{ ms}$ | **100x records $\rightarrow$ ~97x time ($O(n)$ linear growth)** |

---

## Alternative Concept and Complexity Analysis (`HashMap<String, String[]>` In-Memory Index)

If the CSV file is loaded once at startup into `Map<String, String[]> passIndex = new HashMap<>()` keyed by `passId`:

| Metric | Repeated File Scan (`PassStore.find`) | Startup `HashMap<String, String[]>` Index |
| :--- | :--- | :--- |
| **Time for $q$ Searches on $n$ Passes** | **$O(q \times n)$** — every query re-reads up to $n$ lines from disk | **$O(n + q)$** — $O(n)$ one-time load + $O(1)$ average hash lookup per query |
| **Time at $n = 100,000, q = 1,000$** | $\approx 27.1\text{ s}$ | $\approx 45\text{ ms}$ load + $0.2\text{ ms}$ queries $\approx \mathbf{45.2\text{ ms}}$ (**600x faster**) |
| **Auxiliary Memory Used** | **$O(1)$** (~8 KB `BufferedReader` buffer) | **$O(n)$** (~18–22 MB heap for 100,000 `String[]` entries + `HashMap.Node` objects) |
| **Data Freshness / Multi-Process Safety** | Always reads latest file state | In-memory map becomes stale if another process edits the file unless synchronized |
