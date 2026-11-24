# Task A4 — What Should Be Serialized? (4 Marks)
**Bloom's Level:** K4 – Analyse  
**Scenario:** Serializing a `Hostel` object containing `List<Room>`, each containing `List<Resident>`.

---

## A4.1 — Decide What to Persist (2 Marks)

### Fields Marked `transient` and Justification
1. **`private transient Connection dbConnection;` (Mandatory):**
   - *Reason:* `java.sql.Connection` represents an active OS network socket and database session state. It does **not** implement `java.io.Serializable`. Attempting to serialize `Resident` without marking `dbConnection` as `transient` immediately throws `java.io.NotSerializableException: com.mysql.cj.jdbc.ConnectionImpl`. Even if it could be serialized, a socket handle is meaningless after JVM restart.
2. **`private transient String password;` (Security Requirement):**
   - *Reason:* Java serialization writes `String` objects in **cleartext UTF-8** inside the binary `.ser` stream. Persisting raw passwords to disk exposes credentials to anyone who inspects the file with `strings` or a hex editor. Passwords should either be stored as salted cryptographic hashes (`passwordHash`) or excluded via `transient` and re-authenticated from the database.

### What Happens to the Object Graph During Serialization
- All three classes (`Hostel`, `Room`, `Resident`) must implement `java.io.Serializable`.
- When `ObjectOutputStream.writeObject(hostel)` is called, the JVM performs a **depth-first reachability traversal** starting from `hostel`:
  1. Writes `Hostel` metadata and fields, then traverses `List<Room>` (`ArrayList` implements `Serializable`).
  2. For each `Room`, writes its fields and traverses its `List<Resident>`.
  3. For each `Resident`, serializes `name` and `registerNumber`, while skipping `transient` fields (`password` and `dbConnection`).
- **Cycle & Shared Reference Handling:** `ObjectOutputStream` maintains a handle table (`wire handle`) of already-written object identities. If two `Room` objects or back-references point to the same `Hostel` instance, subsequent occurrences are written as compact 5-byte back-references (`TC_REFERENCE`) rather than infinite loops or duplicate copies.
- Upon deserialization (`readObject()`), `transient` reference fields (`dbConnection`, `password`) are initialized to **`null`** (and primitive `transient` fields to `0`/`false`).

---

## A4.2 — Plan for Change (2 Marks)

### Scenario: Adding `private String phoneNumber;` to `Resident` After Files Were Saved

1. **Without an Explicit `serialVersionUID`:**
   - The JVM computes a 64-bit SHA-1 hash (`serialVersionUID`) from the class name, modifiers, interfaces, fields, and method signatures.
   - Adding `phoneNumber` changes the computed hash of `Resident`.
   - When reading an older `.ser` file, `ObjectInputStream` compares the stream's `serialVersionUID` against the new `Resident` class's `serialVersionUID`, detects a mismatch, and throws:
     `java.io.InvalidClassException: Resident; local class incompatible: stream classdesc serialVersionUID = ..., local class serialVersionUID = ...`
2. **With an Explicit `private static final long serialVersionUID = 1L;`:**
   - Both the saved `.ser` file and the updated `Resident` class share `serialVersionUID = 1L`.
   - `ObjectInputStream` treats adding a field as a **compatible change**: it populates `name` and `registerNumber` from the old stream and initializes the newly added `phoneNumber` field to its default value (`null`).

---

## Alternative Concept and Complexity Analysis

### Alternative: Saving Hostel Data as CSV or JSON Text Files
Instead of `ObjectOutputStream`, the `Hostel` hierarchy can be exported as structured JSON (`hostel.json`) or normalized CSV (`residents.csv`).

| Metric | Java Binary Serialization (`.ser`) | JSON Text File (`hostel.json`) | Flat CSV File (`residents.csv`) |
| :--- | :--- | :--- | :--- |
| **File Size ($n = 2,000$ residents)** | Medium (~140 KB; includes binary class descriptors & type tags) | Largest (~260 KB; repeats `"name":`, `"regNo":` keys per object) | **Smallest (~95 KB; single header row + raw values)** |
| **Time to Save / Load $n$ Residents** | $O(n)$ — uses reflection and deep object-graph handle tracking | $O(n)$ — fast streaming token parsing (Jackson/Gson) | **$O(n)$ — fastest** (minimal string splitting/joining, no reflection) |
| **Coping with a New Field (`phoneNumber`)** | Requires explicit `serialVersionUID`; fragile across package renames or field type changes | **Graceful** — missing JSON property maps cleanly to `null` without version IDs | **Graceful** — parser checks `cols.length`; missing trailing column defaults to `null` |
| **Cross-Language Portability** | **Java-only** (tightly coupled to JVM bytecode classes) | **Universal** (readable by Python, JS, SQL, humans) | **Universal** (Excel, SQL `LOAD DATA`, Python, Java) |
