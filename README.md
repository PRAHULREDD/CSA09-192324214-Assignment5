# CSA09 - Programming in Java | Assignment 5 (CO5)
## Data Persistence Engineering — Files and Databases

- **Student Name:** Pulicharla Rahul Reddy
- **Register Number:** 192324214
- **Course:** CSA09 – Programming in Java (CO5: Java I/O, Serialization & JDBC)
- **Institution:** Saveetha Institute of Medical and Technical Sciences (SIMATS Engineering)
- **Java Version:** OpenJDK 17.0.x (`javac 17` / `java 17`)
- **Database & Driver:** MySQL 8.0+ with MySQL Connector/J (`mysql-connector-j-8.x.jar`)

---

## Repository Structure

```text
CSA09-192324214-Assignment5/
├── README.md
├── sql/
│   └── setup_all.sql                  # Complete MySQL schema & seed script for B2, B3, B4
├── PartA/
│   ├── TaskA1_FileFormatDesign.md     # Record layouts, SIMATS pseudocode, error recovery, RAF analysis
│   ├── TaskA2_BufferedIODesign.md     # Syscall prediction, buffered I/O redesign, Files.readAllLines analysis
│   ├── TaskA3_DataAccessPattern.md    # Generic Dao<T, K>, StudentDao, ORM mapping, inline SQL analysis
│   ├── TaskA4_SerializationDesign.md  # transient fields, object graph, serialVersionUID, CSV/JSON analysis
│   └── TaskA5_TransactionsAndInjection.md # ACID wallet transfer, SQL injection & PreparedStatement
├── TaskB1/
│   ├── PassStore.java                 # Completed "Complete the Code" file store (TODO 1)
│   ├── TaskB1Experiment.java          # Validation suite, 1MB/10MB/50MB buffer benchmark, 1k-100k growth test
│   └── TaskB1_Report.md               # Experimental results, complexity comparison & HashMap analysis
├── TaskB2/
│   ├── DBConfig.java                  # Shared JDBC connection helper
│   ├── LoginSecurityDemo.java         # Unsafe concatenation vs PreparedStatement SQLi demonstration
│   ├── AdversarialFileStoreTest.java  # Damaged CSV resilience testing (bad fields, NaN amount, empty lines)
│   └── TaskB2_Report.md               # Adversarial test logs & regex/escaping alternative analysis
├── TaskB3/
│   ├── DBConfig.java                  # Shared JDBC connection helper
│   ├── PassLoader.java                # Completed "Complete the Code" batch loader (TODO 1-3)
│   ├── TaskB3Benchmark.java           # ResultSetMetaData check, Statement vs Prepared vs Batch, Rollback test
│   └── TaskB3_Report.md               # Benchmark measurements, rollback proof & LOAD DATA analysis
└── TaskB4/
    ├── DBConfig.java                  # Shared JDBC connection helper
    ├── sample_passes_input.csv        # Multi-campus input dataset with valid & malformed rows
    ├── CampusMigrationApp.java        # Batch transactional migration + failure report + DatabaseMetaData
    └── TaskB4_Report.md               # Metadata output, 12-campus architectural decision & analysis
```

---

## Prerequisites & Environment Setup

1. **Verify Java 17+:**
   ```bash
   java -version
   javac -version
   ```
2. **Initialize MySQL Database (for Tasks B2, B3, and B4):**
   ```bash
   mysql -u root -p < sql/setup_all.sql
   ```
3. **Configure JDBC Credentials (Optional — defaults match standard local MySQL):**
   ```bash
   export DB_URL="jdbc:mysql://localhost:3306/csa09_assign5?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
   export DB_USER="root"
   export DB_PASS="your_mysql_password"
   ```
   *(Place `mysql-connector-j-8.x.jar` in your classpath or `./lib/` folder.)*

---

## Steps to Compile and Run Each Task

### Task B1 — Instrumented File Store & Buffered Line Counter (No DB Required)
```bash
cd TaskB1
javac PassStore.java TaskB1Experiment.java
java PassStore
java TaskB1Experiment
```

### Task B2 — Adversarial Inputs (SQL Injection & Damaged File Store)
```bash
cd TaskB2
javac -cp ".:../lib/*" DBConfig.java LoginSecurityDemo.java AdversarialFileStoreTest.java
java -cp ".:../lib/*" AdversarialFileStoreTest
java -cp ".:../lib/*" LoginSecurityDemo
```
*(On Windows PowerShell/CMD, replace `:` with `;` in `-cp ".;../lib/*"`.)*

### Task B3 — Statement vs PreparedStatement vs Batch & Transaction Rollback
```bash
cd TaskB3
javac -cp ".:../lib/*" DBConfig.java PassLoader.java TaskB3Benchmark.java
java -cp ".:../lib/*" TaskB3Benchmark
```

### Task B4 — Multi-Campus Batch Migration & DatabaseMetaData Inspection
```bash
cd TaskB4
javac -cp ".:../lib/*" DBConfig.java CampusMigrationApp.java
java -cp ".:../lib/*" CampusMigrationApp sample_passes_input.csv
```

---

## Summary of Results by Task

| Task | Focus Area | Key Outcome / Result |
| :--- | :--- | :--- |
| **A1** | Bus-Pass File Format Design | Designed RFC-4180 escaped CSV and fixed-field binary (`DataOutputStream`) layouts; justified `BufferedReader`/`BufferedWriter`; compared $O(n)$ scan vs $O(1)$ `RandomAccessFile` seek. |
| **A2** | Buffered vs Unbuffered I/O | Predicted $52,428,800$ `read()` syscalls for 50 MB vs $6,400$ syscalls with an 8 KB buffer (~8,192x reduction); analyzed $O(1)$ buffer memory vs $O(N)$ `Files.readAllLines()`. |
| **A3** | Generic DAO Pattern | Abstracted JDBC boilerplate into `Dao<T, K>` implemented by `StudentDao`; mapped `Student` fields to `student` table (`reg_no` PK). |
| **A4** | Object Serialization Design | Marked `Connection` and `password` as `transient`; analyzed object graph traversal and `serialVersionUID` compatibility for schema evolution. |
| **A5** | ACID Transactions & SQLi | Designed `setAutoCommit(false)` + `commit()`/`rollback()` wallet transfer and parameterized `PreparedStatement` login check. |
| **B1** | File Store & Buffer Benchmarks | `PassStore` skips malformed line `P1002` and outputs `[P1003, 192311003, Divya, 7, 3800.0]` and `null`. Buffered reader cuts 50 MB line-count time from ~42 s to ~45 ms (~930x speedup). Linear $O(n)$ search growth verified from 1k to 100k records. |
| **B2** | Adversarial Input Testing | Crafted `' OR '1'='1' -- ` bypasses unsafe string-concatenated login, while `PreparedStatement` rejects it as a literal string. Damaged CSV rows (wrong field count, NaN amount, empty lines) are isolated without halting valid lookups. |
| **B3** | JDBC Batching & Rollback | Batching 1,000 rows/flush reduces 100,000 inserts from ~68 s (`Statement`) to ~0.65 s (~100x faster). Injecting an invalid amount mid-batch triggers `con.rollback()`, leaving `0` partial rows in `bus_pass`. |
| **B4** | 12-Campus Migration & Decision | Migrated valid passes in atomic batches while logging malformed rows to `migration_failure_report.txt`; inspected server/driver via `DatabaseMetaData`; selected MySQL RDBMS over files/serialization for concurrent multi-counter safety. |

---

## Progressive Git Commit Workflow

```bash
git init
git branch -M main
git add README.md sql/
git commit -m "chore: initialize repository structure and MySQL schema"

git add PartA/
git commit -m "docs(part-a): complete persistence design, SIMATS pseudocode, and complexity analysis (A1-A5)"

git add TaskB1/
git commit -m "feat(task-b1): implement PassStore TODO 1 and buffered I/O + growth benchmarks"

git add TaskB2/
git commit -m "feat(task-b2): implement SQL injection and malformed CSV adversarial test harnesses"

git add TaskB3/
git commit -m "feat(task-b3): implement PassLoader batching, JDBC benchmarks, and rollback verification"

git add TaskB4/
git commit -m "feat(task-b4): implement multi-campus batch migration, DatabaseMetaData inspection, and decision report"
```
