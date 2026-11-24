# Task B4 — Final Engineering Decision Report (5 Marks)

## B4.1 — Migration Execution & Validation Failure Report

Running `CampusMigrationApp` on `sample_passes_input.csv` (10 data rows across `MAIN`, `CITY`, `NORTH`, `SOUTH`, `WEST`, `EAST` campuses) validates every row before batch insertion:
- **Valid Records Migrated:** `7` rows inserted atomically into `campus_bus_pass`.
- **Failed Records Quarantined (`3` rows written to `migration_failure_report.txt`):**
  1. `Line 6`: `P2026-005` — `NumberFormatException` on `amount = INVALID_FEE`
  2. `Line 8`: `P2026-007` — `Expected 8 columns, got 2` (`CORRUPT_ROW_MISSING_COLUMNS`)
  3. `Line 9`: `P2026-008` — `Route/Amount must be positive` (`amount = -150.00`)

---

## B4.2 — `DatabaseMetaData` Output & Portability Value

### Sample Output
```text
Database Product : MySQL 8.0.36
JDBC Driver      : MySQL Connector/J (mysql-connector-j-8.3.0)
JDBC Spec Version: 4.3
Supports Batch   : true
Supports Txns    : true
Tables Present   : [bus_pass, campus_bus_pass, users]
```

### Why `DatabaseMetaData` Is Essential When Moving to Another Server
1. **Schema Pre-Flight Verification:** `meta.getTables(...)` verifies that required tables (`campus_bus_pass`) exist on the target server before running batch jobs, failing fast with a clear diagnostic rather than crashing mid-migration.
2. **Feature & Dialect Adaptation:** `supportsBatchUpdates()` and `supportsTransactions()` confirm at runtime that the target database engine (e.g., InnoDB vs MyISAM or a different SQL vendor) supports ACID rollback and JDBC batching.
3. **Version Auditing:** Recording `getDatabaseProductVersion()` and `getDriverVersion()` in startup logs diagnoses subtle SQL/timezone/SSL behavior differences between staging and production servers.

---

## B4.3 — Evidence-Based Production Decision (12 Campuses, 800,000 Passes/Year)

### Decision: **Use a Centralized Relational Database (MySQL / PostgreSQL via JDBC)**

| Requirement | Text Files (CSV) | Object Serialization (`.ser`) | Relational Database (MySQL + JDBC) |
| :--- | :--- | :--- | :--- |
| **Scale ($800,000$ passes/yr)** | $O(n)$ scan takes ~220 ms/query (`Task B1.4` grew linearly to 27 ms at 100k) | Rewrites entire multi-MB object graph on every new pass ($O(n)$ write) | **$O(\log n)$ B-Tree index lookup** (`PRIMARY KEY` / `idx_campus_route`) in **< 1 ms** |
| **Concurrent Multi-Campus Counters** | **Unsafe** — simultaneous appends interleave bytes or require coarse OS file locks | **Unsafe** — last writer overwrites entire file, losing concurrent passes | **Row-level locking + MVCC** allows dozens of counters to issue passes simultaneously |
| **Data Integrity & Crash Safety** | Vulnerable to partial line writes and malformed strings (`Task B2`) | Fragile across class changes (`InvalidClassException`, `Task A4.2`) | **ACID transactions (`commit`/`rollback`)** + `CHECK`/`PRIMARY KEY` constraints (`Task B3.3`) |

---

## Alternative Concept and Complexity Analysis

### Alternative: Each of the 12 Campuses Keeps Its Own Serialized File (`campus_i.ser`)

| Dimension | Shared Relational Database (12 Campuses) | 12 Separate Serialized `.ser` Files |
| :--- | :--- | :--- |
| **Time to Search All Passes (University-Wide Lookup)** | **$O(\log N)$** via global B-Tree index on `pass_id` or `reg_no` (< 1 ms for $N = 800,000$) | **$O(12 \times (N/12)) = O(N)$** — must deserialize all 12 files into memory and scan every object (~400–800 ms) |
| **Space Used ($N = 800,000$ passes)** | $\approx 95\text{ MB}$ (compact row pages + B-Tree indexes, zero duplicate class headers) | $\approx 12 \times 5.2\text{ MB} \approx \mathbf{62.4\text{ MB}}$ on disk, **plus ~320 MB RAM** when all 12 graphs are loaded |
| **Handling Concurrent Counters** | Native ACID row-level locks & connection pooling (`HikariCP`); zero lost updates | **Severe contention:** Two counters at the same campus cannot concurrently serialize to `campus_1.ser` without distributed file locks and full-file rewrites on every single pass |
