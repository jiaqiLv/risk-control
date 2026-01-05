@echo off
REM Windows 构建脚本 - 需要先安装 Gradle

echo =========================================
echo   实时图欺诈检测系统 - 构建脚本
echo =========================================
echo.

REM 检查 JAVA_HOME
if "%JAVA_HOME%"=="" (
    echo [错误] JAVA_HOME 环境变量未设置
    echo 请设置 JAVA_HOME 指向 JDK 21 安装目录
    echo 例如: set JAVA_HOME=C:\Program Files\Java\jdk-21
    pause
    exit /b 1
)

echo [✓] JAVA_HOME: %JAVA_HOME%
echo.

REM 检查 Java 版本
"%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /i "version"
echo.

REM 检查 Gradle
where gradle >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [错误] 未找到 Gradle
    echo.
    echo 请安装 Gradle 8.5+:
    echo 方式 1: 使用 SDKMAN (推荐)
    echo   下载: https://sdkman.io/install
    echo   运行: sdk install gradle 8.5.0
    echo.
    echo 方式 2: 手动安装
    echo   1. 下载: https://gradle.org/releases/
    echo   2. 解压到: C:\Gradle\gradle-8.5
    echo   3. 添加到 PATH: set PATH=%PATH%;C:\Gradle\gradle-8.5\bin
    echo.
    echo 方式 3: 使用 IntelliJ IDEA (最简单)
    echo   1. 打开 IntelliJ IDEA
    echo   2. File -^> Open -^> 选择此项目根目录
    echo   3. IDEA 会自动识别并导入 Gradle 项目
    echo   4. 点击 IDEA 右侧 Gradle 面板 -^> Tasks -^> build -^> Run
    echo.
    pause
    exit /b 1
)

echo [✓] Gradle 已安装
gradle --version
echo.

echo 开始构建项目...
echo.

REM 构建项目
gradle clean build -x test

if %ERRORLEVEL% equ 0 (
    echo.
    echo =========================================
    echo   构建成功！
    echo =========================================
    echo.
    echo 下一步:
    echo   1. 启动基础设施: docker-compose up -d
    echo   2. 启动服务（3 个终端）:
    echo      终端 1: cd graph-service ^&^& gradle bootRun
    echo      终端 2: cd feature-service ^&^& gradle bootRun
    echo      终端 3: cd risk-gateway ^&^& gradle bootRun
    echo.
) else (
    echo.
    echo =========================================
    echo   构建失败
    echo =========================================
    echo.
    echo 请检查上面的错误信息
    echo.
)

pause
