@echo off
REM Orchestrator Service Startup Script

echo ========================================
echo   Starting Orchestrator Service...
echo ========================================
echo.

cd /d %~dp0

echo Compiling...
call mvn clean compile -q
if %ERRORLEVEL% neq 0 (
    echo Compilation failed!
    exit /b 1
)

echo Starting service on port 8081...
echo.
echo Service will be available at:
echo   - http://localhost:8081/api/v1/health
echo   - http://localhost:8081/api/v1/info
echo.
echo Press Ctrl+C to stop the service
echo.

call mvn spring-boot:run
