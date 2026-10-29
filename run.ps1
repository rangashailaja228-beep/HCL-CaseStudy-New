# PowerShell script to compile and run the Solari Support Ticket Assistant
param(
    [string]$Mode = "ui"
)

if (!(Test-Path "bin")) {
    New-Item -ItemType Directory -Path "bin" | Out-Null
}

Write-Host "[*] Compiling Java source files..." -ForegroundColor Cyan
javac -d bin (Get-ChildItem -Path src -Filter *.java | ForEach-Object { $_.FullName })

if ($LASTEXITCODE -ne 0) {
    Write-Host "[!] Compilation failed. Ensure 'javac' is installed and in PATH." -ForegroundColor Red
    exit 1
}

Write-Host "[+] Compilation succeeded!" -ForegroundColor Green

if ($Mode -eq "test") {
    Write-Host "[*] Running Test Suite..." -ForegroundColor Cyan
    java -cp bin src.TestRunner
} elseif ($Mode -eq "cli") {
    Write-Host "[*] Running CLI Batch Evaluation..." -ForegroundColor Cyan
    java -cp bin src.App
} elseif ($Mode -eq "benchmark") {
    Write-Host "[*] Running Benchmark..." -ForegroundColor Cyan
    java -cp bin src.App --benchmark
} else {
    Write-Host "[*] Starting Web UI Server on http://localhost:8088 ..." -ForegroundColor Green
    Write-Host "[*] Open your browser and go to: http://localhost:8088" -ForegroundColor Yellow
    java -cp bin src.WebServer --port 8088
}
