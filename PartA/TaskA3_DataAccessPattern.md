# Task A3 — Discover the Data-Access Design Pattern (5 Marks)
**Bloom's Level:** K4 – Analyse  
**Scenario:** Refactoring duplicated JDBC code across `StudentDB`, `CourseDB`, and `MarksDB`.

---

## A3.1 — Identify the Common Pattern (2 Marks)

### Repeated Steps Across `StudentDB`, `CourseDB`, and `MarksDB`
1. **Connection Acquisition:** Obtaining a `Connection` from `DriverManager` or a `DataSource`.
2. **Statement Preparation & Execution:** Creating a `PreparedStatement` / `Statement`, executing `executeUpdate()` or `executeQuery()`.
3. **ResultSet Iteration Loop:** Advancing the cursor via `while (rs.next())` and collecting rows into a `List<T>`.
4. **Resource Cleanup:** Closing `ResultSet`, `PreparedStatement`, and `Connection` via `try-with-resources`.
5. **Exception Translation:** Catching `SQLException` and wrapping/propagating a data-access exception.

### Parts That Vary by Entity
1. **Table & Column Schema:** `student` vs `course` vs `marks`.
2. **SQL Query Templates:** `INSERT`, `SELECT`, `UPDATE`, `DELETE` strings.
3. **Parameter Binding:** Mapping entity getters (`s.getRegNo()`, `s.getName()`) to `?` placeholders.
4. **Row-to-Object Mapping (`RowMapper`):** Extracting typed columns (`rs.getString("reg_no")`, `rs.getInt("credits")`) to construct `Student`, `Course`, or `Marks` instances.

---

## A3.2 — Design a Generic Data-Access Interface (2 Marks)

### Generic `Dao<T, K>` Interface & `StudentDao` Implementation

```java
package edu.simats.csa09.dao;

import java.sql.*;
import java.util.*;

// 1. Generic DAO Contract
public interface Dao<T, K> {
    boolean insert(T entity) throws SQLException;
    Optional<T> findById(K id) throws SQLException;
    List<T> findAll() throws SQLException;
    boolean update(T entity) throws SQLException;
    boolean delete(K id) throws SQLException;
}

// 2. Domain Entity (POJO)
public class Student {
    private String regNo;
    private String name;
    private String department;
    private int semester;
    private double cgpa;

    public Student(String regNo, String name, String department, int semester, double cgpa) {
        this.regNo = regNo;
        this.name = name;
        this.department = department;
        this.semester = semester;
        this.cgpa = cgpa;
    }
    public String getRegNo() { return regNo; }
    public String getName() { return name; }
    public String getDepartment() { return department; }
    public int getSemester() { return semester; }
    public double getCgpa() { return cgpa; }
}

// 3. Concrete StudentDao Implementation
public class StudentDao implements Dao<Student, String> {
    private final String url;
    private final String user;
    private final String password;

    public StudentDao(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private Student mapRow(ResultSet rs) throws SQLException {
        return new Student(
            rs.getString("reg_no"),
            rs.getString("full_name"),
            rs.getString("dept_code"),
            rs.getInt("semester"),
            rs.getDouble("cgpa")
        );
    }

    @Override
    public boolean insert(Student s) throws SQLException {
        String sql = "INSERT INTO student (reg_no, full_name, dept_code, semester, cgpa) VALUES (?, ?, ?, ?, ?)";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, s.getRegNo());
            ps.setString(2, s.getName());
            ps.setString(3, s.getDepartment());
            ps.setInt(4, s.getSemester());
            ps.setDouble(5, s.getCgpa());
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public Optional<Student> findById(String regNo) throws SQLException {
        String sql = "SELECT reg_no, full_name, dept_code, semester, cgpa FROM student WHERE reg_no = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, regNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<Student> findAll() throws SQLException {
        String sql = "SELECT reg_no, full_name, dept_code, semester, cgpa FROM student";
        List<Student> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    @Override
    public boolean update(Student s) throws SQLException {
        String sql = "UPDATE student SET full_name = ?, dept_code = ?, semester = ?, cgpa = ? WHERE reg_no = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getDepartment());
            ps.setInt(3, s.getSemester());
            ps.setDouble(4, s.getCgpa());
            ps.setString(5, s.getRegNo());
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean delete(String regNo) throws SQLException {
        String sql = "DELETE FROM student WHERE reg_no = ?";
        try (Connection con = getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, regNo);
            return ps.executeUpdate() == 1;
        }
    }
}
```

---

## A3.3 — Map Objects to Tables (1 Mark)

| Java `Student` Field | Java Type | Relational `student` Column | SQL Type | Key / Constraint |
| :--- | :--- | :--- | :--- | :--- |
| `regNo` | `String` | `reg_no` | `VARCHAR(15)` | **PRIMARY KEY** (`NOT NULL`) |
| `name` | `String` | `full_name` | `VARCHAR(100)` | `NOT NULL` |
| `department` | `String` | `dept_code` | `VARCHAR(10)` | `NOT NULL` |
| `semester` | `int` | `semester` | `INT` | `CHECK (semester BETWEEN 1 AND 8)` |
| `cgpa` | `double` | `cgpa` | `DECIMAL(4,2)` | `CHECK (cgpa BETWEEN 0.00 AND 10.00)` |

---

## Alternative Concept and Complexity Analysis

### Alternative: Writing SQL Directly Inside Service or GUI Classes (Transaction Script / Smart UI)
If SQL queries are embedded directly inside Swing/JavaFX event handlers (`ActionListener`) and business services without a `Dao<T, K>` layer:

| Evaluation Criterion | Generic `Dao<T, K>` Pattern | Inline SQL in Service / GUI Classes |
| :--- | :--- | :--- |
| **Places Changed When a Column Is Renamed** | **$O(1)$ — 1 class (`StudentDao`)** | **$O(M)$ — every screen, report, and service** referencing that column ($M$ scattered files) |
| **Time Complexity per DB Operation** | $O(1)$ connection + $O(k)$ for $k$ returned rows | $O(1)$ connection + $O(k)$ for $k$ returned rows |
| **Space Complexity per DB Operation** | $O(k)$ to hold $k$ domain objects | $O(k)$ to hold $k$ domain objects |
| **Runtime Overhead Difference** | **Negligible (~1–2 nanoseconds)** for JVM `invokeinterface` dispatch vs millisecond-scale JDBC network I/O | Same JDBC network/SQL execution cost, but higher risk of leaked connections when UI handlers forget `close()` |
