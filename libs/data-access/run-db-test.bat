@echo off
REM ========================================
REM 数据库连接测试脚本
REM ========================================

echo.
echo ========================================
echo 数据库连接测试
echo ========================================
echo.

cd /d "%~dp0"

echo [1/2] 检查数据库连接...
echo.

REM 检查环境变量
if defined DB_USERNAME (
    echo 数据库用户名: %DB_USERNAME%
) else (
    echo 数据库用户名: postgres (默认)
)

if defined DB_PASSWORD (
    echo 数据库密码: *** (已设置)
) else (
    echo 数据库密码: postgres (默认)
)

echo.
echo [2/2] 运行测试...
echo.

mvn test -Dtest=QuickConnectionTest

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo ✓ 测试通过！
    echo ========================================
) else (
    echo.
    echo ========================================
    echo ✗ 测试失败！
    echo ========================================
    echo.
    echo 请检查：
    echo 1. PostgreSQL 数据库是否正在运行？
    echo 2. 数据库 'riskcontrol' 是否已创建？
    echo 3. 用户名和密码是否正确？
    echo.
    echo 使用以下命令连接数据库检查：
    echo   psql -U postgres -h localhost -d riskcontrol
    echo.
)

pause
