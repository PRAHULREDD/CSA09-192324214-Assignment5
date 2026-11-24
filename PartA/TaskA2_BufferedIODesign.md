# Task A2 — Don't Read What You Don't Need (5 Marks)
**Bloom's Level:** K4 – Analyse  
**Scenario:** Counting lines in a **50 MB** attendance log using unbuffered `FileInputStream.read()`.

---

## A2.1 — Predict the Cost (2 Marks)

- **File Size:** $50\text{ MB} = 50 \times 1024 \times 1024 = \mathbf{52,428,800\text{ bytes}}$.
- **Predicted `read()` Calls:** Exactly **$52,428,801$ calls** ($52,428,800$ calls returning 1 byte each + $1$ final call returning `-1` for EOF).
- **Why It Is Slow:**
  1. **System Call & Context-Switch Overhead:** Every `FileInputStream.read()` invokes a native JNI method (`read0`), which triggers a user-mode to kernel-mode transition (`read()` syscall) and back. Executing 52.4 million syscalls costs roughly $0.5\text{–}1.0\,\mu\text{s}$ per call in CPU context switching, totaling **25 to 50 seconds** even when the file is already cached in OS RAM.
  2. **Poor CPU Cache Utilization:** Reading 1 byte per call prevents SIMD/vectorized scanning and causes repeated stack frame creation and lock acquisition inside native I/O wrappers.

---

## A2.2 — Redesign Using Buffering (2 Marks)

### Redesigned Strategy
Wrap `FileInputStream` in a `BufferedInputStream` (or read directly into a `byte[]` buffer of size $B = 8192\text{ bytes}$ or $65536\text{ bytes}$) and count `'\n'` (`0x0A`) bytes in memory.

### Pseudocode (SIMATS Engineering Standard)

```text
ALGORITHM CountLinesBuffered(filePath)
INPUT:  filePath (String)
OUTPUT: lineCount (Long)
BEGIN
    CONST BUFFER_SIZE <- 65536   // 64 KB buffer
    buffer <- ByteArray[BUFFER_SIZE]
    lineCount <- 0
    totalBytes <- 0
    lastByte <- -1

    OPEN fis <- FileInputStream(filePath)
    WHILE (bytesRead <- ReadBytes(fis, buffer, 0, BUFFER_SIZE)) != -1 DO
        totalBytes <- totalBytes + bytesRead
        FOR i <- 0 TO bytesRead - 1 DO
            IF buffer[i] == 10 THEN    // ASCII 10 = '\n'
                lineCount <- lineCount + 1
            END IF
        END FOR
        lastByte <- buffer[bytesRead - 1]
    END WHILE
    CLOSE fis

    // Handle non-empty file whose final line has no trailing newline
    IF totalBytes > 0 AND lastByte != 10 THEN
        lineCount <- lineCount + 1
    END IF

    RETURN lineCount
END
```

### Effect on Operating System Calls
- With an **8 KB (`8,192` bytes)** buffer: $\lceil 52,428,800 / 8,192 \rceil + 1 = \mathbf{6,401\text{ syscalls}}$ (**8,192x reduction**).
- With a **64 KB (`65,536` bytes)** buffer: $\lceil 52,428,800 / 65,536 \rceil + 1 = \mathbf{801\text{ syscalls}}$ (**65,454x reduction**).
- The OS kernel transfers 64 KB contiguous blocks directly into user-space memory per transition, reducing execution time from tens of seconds down to **~15–40 milliseconds**.

---

## A2.3 — When Buffering Does Not Help (1 Mark)

Adding a stream buffer gives little or no benefit in the following situations:
1. **Random-Access Seeks Smaller Than Buffer Size:** When using `RandomAccessFile` (or repeatedly seeking to scattered offsets) to read 16 bytes at random positions, pre-fetching an 8 KB block wastes 99.8% of the read bytes because the next `seek()` discards the buffer and triggers another disk read.
2. **In-Memory Streams (`ByteArrayInputStream` / `StringReader`):** Data already resides in JVM heap memory with zero kernel syscalls; adding `BufferedInputStream` only adds extra array copying (`System.arraycopy`) overhead.
3. **Very Small Files Read Once (< Buffer Size):** For a 500-byte config file, both unbuffered block read and buffered read perform 1 syscall.

---

## Alternative Concept and Complexity Analysis

### Alternative: `Files.readAllLines(Path)`
`List<String> lines = Files.readAllLines(Path.of("attendance.log"), StandardCharsets.UTF_8);`  
`long count = lines.size();`

### Time and Space Complexity Comparison (for $N = 50\text{ MB}$ File)

| Dimension | Buffered Streaming (`byte[]` / `BufferedInputStream`) | `Files.readAllLines(Path)` |
| :--- | :--- | :--- |
| **Time Complexity** | $O(N)$ — single linear scan over $N$ bytes | $O(N)$ — reads $N$ bytes, decodes UTF-8, allocates millions of `String` objects |
| **Auxiliary Space Complexity** | **$O(B) = O(1)$** — fixed **8 KB to 64 KB** buffer regardless of file size | **$O(N)$** — holds every line simultaneously in a `List<String>` |
| **Actual JVM Heap Used (50 MB file)** | **~64 KB** (`0.06 MB`) | **~110–160 MB** (UTF-8 $\rightarrow$ `String` headers + `byte[]` backing arrays + `Object[]` references) |
| **Scalability to 2 GB Log File** | Succeeds effortlessly in ~0.5 s using 64 KB RAM | Crashes with `java.lang.OutOfMemoryError: Java heap space` |

### When Each Approach Is Appropriate
- **Use Buffered Streaming:** For large files ($\ge 1\text{ MB}$), log analytics, aggregations (line counts, sums, filtering), or memory-constrained containers where streaming line-by-line avoids heap pressure.
- **Use `Files.readAllLines()`:** Only for small configuration/lookup files ($< 1\text{ MB}$) where random indexing (`lines.get(i)`), multi-pass traversal, or sorting all lines in memory is required.
