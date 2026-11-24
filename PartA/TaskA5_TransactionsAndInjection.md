# Task A5 — Design for Correctness and Safety (4 Marks)
**Bloom's Level:** K4 – Analyse  
**Scenario:** Wallet transfer atomicity and SQL injection prevention in a login check.

---

## A5.1 — Transaction Design (2 Marks)

### Pseudocode (SIMATS Engineering Standard)

```text
ALGORITHM TransferWalletFunds(con, studentRegNo, amount)
INPUT:  con (JDBC Connection), studentRegNo (String), amount (Decimal, e.g., 500.00)
OUTPUT: Boolean status (TRUE if committed, FALSE if rolled back)
BEGIN
    IF amount <= 0 THEN
        THROW IllegalArgumentException("Transfer amount must be positive")
    END IF

    oldAutoCommit <- GetAutoCommit(con)
    TRY
        SetAutoCommit(con, FALSE)   // Begin atomic transaction boundary

        // 1. Lock row and verify sufficient hostel wallet balance
        PREPARE checkStmt <- "SELECT hostel_balance FROM wallets WHERE reg_no = ? FOR UPDATE"
        BIND checkStmt[1] <- studentRegNo
        rs <- ExecuteQuery(checkStmt)
        IF NOT Next(rs) OR GetDecimal(rs, "hostel_balance") < amount THEN
            Rollback(con)
            RETURN FALSE
        END IF

        // 2. Debit ₹500 from Hostel Wallet
        PREPARE debitStmt <- "UPDATE wallets SET hostel_balance = hostel_balance - ? WHERE reg_no = ?"
        BIND debitStmt[1] <- amount
        BIND debitStmt[2] <- studentRegNo
        rowsDebited <- ExecuteUpdate(debitStmt)

        // 3. Credit ₹500 to Canteen Wallet
        PREPARE creditStmt <- "UPDATE wallets SET canteen_balance = canteen_balance + ? WHERE reg_no = ?"
        BIND creditStmt[1] <- amount
        BIND creditStmt[2] <- studentRegNo
        rowsCredited <- ExecuteUpdate(creditStmt)

        // 4. Verify both updates succeeded before committing
        IF rowsDebited == 1 AND rowsCredited == 1 THEN
            Commit(con)             // Make both changes permanent atomically
            RETURN TRUE
        ELSE
            Rollback(con)
            RETURN FALSE
        END IF
    CATCH (SQLException OR RuntimeException ex)
        Rollback(con)               // Undo partial debit if crash/error occurs mid-way
        THROW ex
    FINALLY
        SetAutoCommit(con, oldAutoCommit)
    END TRY
END
```

---

## A5.2 — Injection Analysis (2 Marks)

### Vulnerability in the Original Query
Original code:
```java
String sql = "SELECT * FROM users WHERE id='" + id + "' AND pwd='" + pwd + "'";
```

### Crafted Adversarial Input
- **Input `id`:** `' OR '1'='1' -- `
- **Input `pwd`:** `anything`

### Resulting SQL Sent to Database
```sql
SELECT * FROM users WHERE id='' OR '1'='1' -- ' AND pwd='anything'
```
- `' OR '1'='1'` turns the `WHERE` predicate into a tautology (`TRUE` for every row in `users`).
- `-- ` comments out the remainder of the SQL statement (`' AND pwd='anything'`), completely bypassing password verification and logging the attacker in as the first user in the table (often an administrator).

### Safe Redesign Using `PreparedStatement`
```java
public boolean authenticateUser(Connection con, String id, String pwd) throws SQLException {
    String sql = "SELECT pwd FROM users WHERE id = ?";
    try (PreparedStatement ps = con.prepareStatement(sql)) {
        ps.setString(1, id);   // Bound strictly as a data literal, never SQL syntax
        try (ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                return false;
            }
            String storedPwd = rs.getString("pwd");
            return storedPwd != null && storedPwd.equals(pwd);
        }
    }
}
```
*Why it is safe:* The database pre-compiles the SQL parse tree (`SELECT pwd FROM users WHERE id = ?`) before receiving parameter values. The adversarial string `' OR '1'='1' -- ` is treated strictly as a literal user ID value, which matches no row and returns `false`.

---

## Alternative Concept and Complexity Analysis

### Alternative: Executing Two Separate Auto-Commit Statements
If `con.setAutoCommit(true)` is left enabled and the two `UPDATE` statements run independently:

| Evaluation Aspect | Single Explicit Transaction (`setAutoCommit(false)`) | Two Separate Auto-Commit Statements |
| :--- | :--- | :--- |
| **Outcome on Crash Between Updates** | **Atomic Rollback:** `con.rollback()` reverts the ₹500 hostel debit; total system money is conserved ($\Delta = ₹0$). | **Data Corruption (Money Destroyed):** Statement 1 (`hostel_balance - 500`) is already committed to disk; Statement 2 never runs. The student loses ₹500 permanently. |
| **Time Cost ($T_{\text{commit}}$)** | **$1\times$ transaction log `fsync`** + 2 query round-trips ($T_{\text{total}} \approx 2 T_{\text{net}} + 1 T_{\text{fsync}}$). **Faster** because the redo/WAL log is flushed to disk only once. | **$2\times$ transaction log `fsync`s** + 2 query round-trips ($T_{\text{total}} \approx 2 T_{\text{net}} + 2 T_{\text{fsync}}$). **Slower** due to double disk durability flushes. |
| **Data Correctness Risk** | **Zero** (ACID Atomicity & Isolation preserved). | **Unacceptable** for financial/ledger operations; violates conservation of funds. |
