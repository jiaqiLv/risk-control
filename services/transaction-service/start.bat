@echo off
echo ====================================
echo Starting Transaction Service...
echo ====================================
echo.

cd /d %~dp0
mvn spring-boot:run

pause
