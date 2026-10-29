@echo off
REM Windows Batch script to build and run the Solari Support Ticket Assistant

setlocal

if not exist bin mkdir bin

echo [*] Compiling Java files...
javac -d bin src\*.java
if %ERRORLEVEL% neq 0 (
    echo [!] Compilation failed. Please make sure JDK (javac) is in your PATH.
    pause
    exit /b %ERRORLEVEL%
)

if "%1"=="test" (
    echo [*] Running automated tests...
    java -cp bin src.TestRunner
) else if "%1"=="cli" (
    echo [*] Running CLI batch evaluation...
    java -cp bin src.App
) else if "%1"=="benchmark" (
    echo [*] Running benchmark...
    java -cp bin src.App --benchmark
) else (
    echo [*] Starting Web UI Server on http://localhost:8088 ...
    echo [*] Open your browser and navigate to: http://localhost:8088
    java -cp bin src.WebServer --port 8088
)
