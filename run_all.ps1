# ============================================================================
# CSA09 Assignment 5 - One-Click PowerShell Build, Schema Setup & Execution
# ============================================================================
$ErrorActionPreference = "Stop"
$Root = $PSScriptRoot

Write-Host "=== 1. Locating MySQL CLI and Connector/J JAR ===" -ForegroundColor Cyan
$mysql = Get-ChildItem "C:\Program Files\MySQL", "C:\xampp\mysql\bin", "C:\wamp64\bin\mysql" -Recurse -Filter "mysql.exe" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
$jar = Get-ChildItem "$Root", "$HOME\Downloads", "C:\Program Files (x86)\MySQL", "C:\Program Files\MySQL", "$HOME\.m2" -Recurse -Filter "mysql-connector-j*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName

if (-not $jar) {
    $jar = Get-ChildItem "$Root", "$HOME\Downloads" -Recurse -Filter "mysql-connector-java*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName
}

Write-Host "MySQL CLI : $mysql"
Write-Host "JDBC JAR  : $jar"

if (-not $jar) {
    Write-Host "ERROR: mysql-connector-j*.jar not found! Place it inside $Root\lib\ and re-run." -ForegroundColor Red
    exit 1
}

# Prompt once for MySQL root password if DB_PASS is not already set
if (-not $env:DB_PASS) {
    $secPwd = Read-Host "Enter your MySQL 'root' password (press Enter if blank)" -AsSecureString
    $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secPwd)
    $env:DB_PASS = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
}

if ($mysql) {
    Write-Host "`n=== 2. Initializing MySQL Database (sql/setup_all.sql) ===" -ForegroundColor Cyan
    $sqlContent = Get-Content "$Root\sql\setup_all.sql" -Raw
    if ([string]::IsNullOrEmpty($env:DB_PASS)) {
        $sqlContent | & $mysql -u root
    } else {
        $sqlContent | & $mysql -u root "-p$($env:DB_PASS)"
    }
    Write-Host "Database 'csa09_assign5' ready." -ForegroundColor Green
}

Write-Host "`n=== 3. Running Task B1 (PassStore & File Benchmarks) ===" -ForegroundColor Cyan
Push-Location "$Root\TaskB1"
javac PassStore.java TaskB1Experiment.java
java PassStore
java TaskB1Experiment
Pop-Location

Write-Host "`n=== 4. Running Task B2 (Adversarial SQLi & Damaged CSV) ===" -ForegroundColor Cyan
Push-Location "$Root\TaskB2"
javac -cp ".;$jar" DBConfig.java LoginSecurityDemo.java AdversarialFileStoreTest.java
java -cp ".;$jar" AdversarialFileStoreTest
java -cp ".;$jar" LoginSecurityDemo
Pop-Location

Write-Host "`n=== 5. Running Task B3 (Statement vs PreparedStatement vs Batch) ===" -ForegroundColor Cyan
Push-Location "$Root\TaskB3"
javac -cp ".;$jar" DBConfig.java PassLoader.java TaskB3Benchmark.java
java -cp ".;$jar" TaskB3Benchmark
Pop-Location

Write-Host "`n=== 6. Running Task B4 (12-Campus Migration & DatabaseMetaData) ===" -ForegroundColor Cyan
Push-Location "$Root\TaskB4"
javac -cp ".;$jar" DBConfig.java CampusMigrationApp.java
java -cp ".;$jar" CampusMigrationApp sample_passes_input.csv
Pop-Location

Write-Host "`nALL TASKS COMPLETED SUCCESSFULLY!" -ForegroundColor Green
