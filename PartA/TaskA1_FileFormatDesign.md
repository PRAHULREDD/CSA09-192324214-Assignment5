# Task A1 — Design a File Format (7 Marks)
**Bloom's Level:** K4 – Analyse  
**Scenario:** University Transport Office Bus-Pass Records (~8,000 passes/year).

---

## A1.1 — Represent the Record (2 Marks)

### (a) Delimited Text File Layout (`bus_passes.csv`)
- **Encoding:** UTF-8 (`StandardCharsets.UTF_8`)
- **Record Separator:** LF (`\n`)
- **Field Delimiter:** Comma (`,`) adhering to **RFC 4180** CSV quoting rules.
- **Field Order (7 fields per record):**
  1. `passId` (String, e.g., `P20260001`)
  2. `regNo` (String, e.g., `192324214`)
  3. `name` (String, e.g., `"Reddy, Rahul"`)
  4. `routeNo` (Integer, e.g., `12`)
  5. `boardingStop` (String, e.g., `Poonamallee`)
  6. `validityDate` (ISO-8601 Date `YYYY-MM-DD`, e.g., `2027-04-30`)
  7. `amountPaid` (Decimal `0.00`, e.g., `4500.00`)

**Handling a Name Containing the Delimiter (`,`):**  
Any text field containing a comma (`,`), double quote (`"`), or newline is enclosed in double quotes (`"`), and internal double quotes are doubled (`""`).  
*Example:* `P20260001,192324214,"Reddy, P. Rahul",12,Poonamallee,2027-04-30,4500.00`  
When parsing, a state-machine CSV tokenizer ignores commas while inside an open quote (`inQuotes == true`).

---

### (b) Binary File Layout (`bus_passes.dat` via `DataOutputStream`)
Using fixed-width UTF-8 / primitive fields ensures deterministic record size (**112 bytes per record**) and eliminates delimiter ambiguity altogether:

| Field # | Field Name | Java `DataOutputStream` Write Method | Storage Format | Size (Bytes) |
| :--- | :--- | :--- | :--- | :---: |
| 1 | `passId` | `write(byte[12])` | Fixed 12-byte ASCII/UTF-8 (null-padded) | 12 |
| 2 | `regNo` | `write(byte[12])` | Fixed 12-byte ASCII/UTF-8 (null-padded) | 12 |
| 3 | `name` | `write(byte[40])` | Fixed 40-byte UTF-8 (null-padded) | 40 |
| 4 | `routeNo` | `writeInt(int)` | 32-bit big-endian signed integer | 4 |
| 5 | `boardingStop` | `write(byte[32])` | Fixed 32-byte UTF-8 (null-padded) | 32 |
| 6 | `validityDate` | `writeInt(int)` | Packed integer `YYYYMMDD` (e.g., `20270430`) | 4 |
| 7 | `amountPaid` | `writeDouble(double)` | 64-bit IEEE 754 floating-point | 8 |
| **Total** | | | **Fixed Record Size** | **112 Bytes** |

*(Note: Because `name` is stored as raw UTF-8 bytes inside a dedicated 40-byte slot rather than separated by a delimiter character, commas or quotes in `name` require no escaping.)*

---

## A1.2 — Design the Operations (3 Marks)

### Stream Class Selection & Justification
- **Write (`addPass`):** `BufferedWriter` wrapping `OutputStreamWriter` / `FileWriter(file, StandardCharsets.UTF_8, true)` (or `Files.newBufferedWriter(..., CREATE, APPEND)`).
  - *Why:* Character streams automatically handle UTF-8 encoding, and `BufferedWriter` batches character writes into an 8 KB memory buffer before invoking OS `write()` system calls, avoiding per-field kernel transitions.
- **Read (`findPassById`, `listPassesByRoute`):** `BufferedReader` wrapping `FileReader(file, StandardCharsets.UTF_8)`.
  - *Why:* `BufferedReader.readLine()` reads 8 KB chunks into user space and extracts full lines efficiently without one-byte-at-a-time overhead. `FileInputStream`/`FileOutputStream` operate on raw bytes and lack `readLine()` or character set handling, making them unsuitable for text files.

### Pseudocode (SIMATS Engineering Standard)

```text
ALGORITHM AddPass(filePath, passRecord)
INPUT:  filePath (String), passRecord (Record of 7 fields)
OUTPUT: Boolean status (TRUE if written)
BEGIN
    escapedName <- EscapeCSV(passRecord.name)
    escapedStop <- EscapeCSV(passRecord.boardingStop)
    line <- passRecord.passId + "," + passRecord.regNo + "," + escapedName + "," +
            ToString(passRecord.routeNo) + "," + escapedStop + "," +
            passRecord.validityDate + "," + FormatDecimal(passRecord.amountPaid)
    OPEN writer <- BufferedWriter(FileWriter(filePath, UTF_8, APPEND=TRUE))
    WRITE line TO writer
    WRITE NEWLINE TO writer
    FLUSH AND CLOSE writer
    RETURN TRUE
END

ALGORITHM FindPassById(filePath, targetPassId)
INPUT:  filePath (String), targetPassId (String)
OUTPUT: Matching passRecord or NULL
BEGIN
    IF NOT FileExists(filePath) THEN
        RETURN NULL
    END IF
    OPEN reader <- BufferedReader(FileReader(filePath, UTF_8))
    WHILE (line <- ReadLine(reader)) != NULL DO
        fields <- ParseCSVLine(line)
        IF Length(fields) != 7 OR NOT IsValidRecord(fields) THEN
            LOG_WARNING("Skipping damaged line: " + line)
            CONTINUE
        END IF
        IF fields[0] == targetPassId THEN
            CLOSE reader
            RETURN MapToRecord(fields)
        END IF
    END WHILE
    CLOSE reader
    RETURN NULL
END

ALGORITHM ListPassesByRoute(filePath, targetRoute)
INPUT:  filePath (String), targetRoute (Integer)
OUTPUT: List of matching passRecords
BEGIN
    resultList <- EmptyList()
    IF NOT FileExists(filePath) THEN
        RETURN resultList
    END IF
    OPEN reader <- BufferedReader(FileReader(filePath, UTF_8))
    WHILE (line <- ReadLine(reader)) != NULL DO
        fields <- ParseCSVLine(line)
        IF Length(fields) != 7 OR NOT IsValidRecord(fields) THEN
            CONTINUE
        END IF
        IF ToInteger(fields[3]) == targetRoute THEN
            APPEND MapToRecord(fields) TO resultList
        END IF
    END WHILE
    CLOSE reader
    RETURN resultList
END
```

---

## A1.3 — Design for Damaged Data (2 Marks)

1. **Damaged Line (Corrupt fields, missing columns, non-numeric route/amount):**
   - *Detection:* Check `fields.length == 7`, verify `routeNo` parses as a positive `int`, `amountPaid` parses as a positive `double`, and `validityDate` matches `YYYY-MM-DD`.
   - *Handling:* Catch `NumberFormatException` / `DateTimeParseException`, log the corrupt line number and content to an audit/error log, and `continue` scanning the remaining lines instead of crashing with an unhandled runtime exception.
2. **Missing File (`bus_passes.csv` does not exist yet):**
   - *Detection:* Check `Files.notExists(path)` before read operations; catch `NoSuchFileException` / `FileNotFoundException`.
   - *Handling:* For `findPassById` and `listPassesByRoute`, return `null` or an empty list gracefully. For `addPass`, open with `StandardOpenOption.CREATE` so the file is initialized automatically on the first pass issuance.
3. **Incomplete Write Caused by Power Failure:**
   - *Detection:* A power cut mid-append leaves a truncated trailing line without a terminating `\n` or with fewer than 7 fields (or mismatched CRC32 field).
   - *Handling:*
     - During reads, the trailing incomplete line fails validation (`fields.length < 7`) and is quarantined/ignored rather than corrupting valid earlier records.
     - During bulk updates/rewrites, write to a temporary file (`bus_passes.tmp`), call `FileChannel.force(true)` (`fsync`) to flush OS page caches to disk, and atomically replace the target via `Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)`.

---

## Alternative Concept and Complexity Analysis

### Alternative: Fixed-Length Records in `RandomAccessFile`
If all 8,000 passes are stored in a `RandomAccessFile` using the 112-byte fixed record layout (`recordSize = 112`), record index $i$ ($0$-based) is accessed directly via `raf.seek((long) i * 112)` and read in a single 112-byte read.

### Complexity & Space Comparison

| Metric | Delimited Text File (`BufferedReader`) | `RandomAccessFile` (Fixed 112-byte Records) |
| :--- | :--- | :--- |
| **Find by ID (by record index / direct offset)** | $O(n)$ sequential scan | **$O(1)$** via `seek(i * recordSize)` *(or $O(\log n)$ binary search if sorted by `passId`)* |
| **Find by ID (unsorted arbitrary `passId`)** | $O(n)$ sequential line scan | $O(n)$ sequential block scan (unless accompanied by an ID-to-offset index) |
| **List all passes on a route** | $O(n)$ | $O(n)$ |
| **Storage Size (8,000 records)** | $\approx 55 \text{ bytes/rec} \times 8,000 \approx \mathbf{440\text{ KB}}$ | $112 \text{ bytes/rec} \times 8,000 = \mathbf{896\text{ KB}}$ |
| **Wasted Padding Space** | **$0\text{ bytes}$** (variable-length fields) | $\approx (112 - 55) \times 8,000 \approx \mathbf{456\text{ KB}}$ (**50.9% internal fragmentation**) |

**Engineering Verdict:** At 8,000 passes/year (~440 KB), `RandomAccessFile` wastes ~51% of disk space due to fixed-width padding on short names/stops, whereas a sequential `BufferedReader` scan completes in $< 5\text{ ms}$. `RandomAccessFile` only pays off when direct index-based in-place updates are frequent.
