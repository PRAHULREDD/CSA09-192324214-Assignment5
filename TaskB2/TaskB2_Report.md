# Task B2 — Engineer an Adversarial Input Report (5 Marks)

## 1. SQL Injection Adversarial Experiment (`LoginSecurityDemo.java`)

| Input Case | `id` Input | `pwd` Input | Unsafe Concatenation Result | `PreparedStatement` Result |
| :--- | :--- | :--- | :--- | :--- |
| **Legitimate User** | `192324214` | `Rahul@2026` | `true` (Authenticated) | `true` (Authenticated) |
| **SQL Injection Payload** | `' OR '1'='1' -- ` | `wrong_password` | **`true` (UNAUTHORIZED BYPASS)** | **`false` (REJECTED)** |

### What This Reveals About the Original Design
- **Mixing Code and Data:** String concatenation (`"SELECT * FROM users WHERE id='" + id + "'..."`) allows untrusted user characters (`'`, `--`) to break out of the SQL string literal context and alter the query's Abstract Syntax Tree (AST).
- **Structural Separation:** `PreparedStatement` sends the SQL command skeleton (`SELECT id FROM users WHERE id = ? AND pwd = ?`) to MySQL first for compilation, then transmits parameters separately via the binary protocol so user input can never be interpreted as SQL keywords or operators.

---

## 2. Adversarial Bus-Pass File Test (`AdversarialFileStoreTest.java`)

| Line # | Crafted Adversarial Input | Fault Type | File Store Response |
| :---: | :--- | :--- | :--- |
| 1 | `P2001,192324214,Rahul Reddy,12,4500.0` | Valid control record | Loaded (`validPasses`) |
| 2 | `""` (empty line) | Blank line | Skipped & logged (`EMPTY/WHITESPACE LINE`) |
| 3 | `P2002,ONLY_TWO_COLUMNS` | Wrong field count (`2 != 5`) | Skipped & logged (`WRONG FIELD COUNT`) |
| 4 | `P2003,192311005,Sneha,14,FREE_PASS` | Non-numeric amount | Caught `NumberFormatException`, skipped & logged |
| 5 | `P2004,192311006,Vikram,ROUTE_A,4200.0` | Non-numeric route | Caught `NumberFormatException`, skipped & logged |
| 6 | `P2005,192311007,Ananya,9,-500.0` | Negative amount | Rejected by domain check (`amount <= 0`) |
| 7 | `P2006,192311008,Karthik,5,3900.0,EXTRA_COL` | Extra field (`6 != 5`) | Skipped & logged (`WRONG FIELD COUNT`) |
| 8 | `P2007,192311009,Priya,8,4100.0` | Valid trailing record | Loaded (`validPasses`) |

**What This Reveals:** Structural checks (`fields.length == 5`) alone are insufficient—for example, `P2003` has 5 fields yet contains `"FREE_PASS"` as the amount. Combining structural validation with strict type/range parsing ensures corrupt rows are quarantined while valid rows (`P2001`, `P2007`) remain accessible.

---

## Alternative Concept and Complexity Analysis

### Alternative: Quote Escaping or Regex Input Validation Before String Concatenation
Suppose instead of `PreparedStatement`, the application escapes single quotes (`id.replace("'", "''")`) or validates `id.matches("^[a-zA-Z0-9_]{1,30}$")` before building a dynamic SQL string:

1. **Why It Is Security-Wise Weaker:**
   - **Context Sensitivity & Encoding Bypass:** Manual escaping fails when parameters are unquoted numeric fields (`WHERE route = ` + input), `LIKE` wildcards (`%`, `_`), multi-byte character-set smuggling (`GBK` backslash consumption), or second-order SQL injection.
   - **Developer Error Prone:** Forgetting to escape even one parameter across dozens of queries re-opens full database compromise, whereas `PreparedStatement` enforces parameterization structurally.
2. **Time Cost Comparison per Query:**
   - **Regex + Dynamic SQL String:** $O(|s|)$ regex DFA matching on input of length $|s|$ **plus** full SQL lexical analysis, parsing, and query plan optimization on the database server for **every** execution ($O(L_{\text{sql}})$ server CPU per query).
   - **`PreparedStatement`:** $O(|s|)$ binary parameter serialization with **zero** regex backtracking risk, and the database reuses the cached pre-compiled execution plan across repeated calls ($O(1)$ plan lookup).
