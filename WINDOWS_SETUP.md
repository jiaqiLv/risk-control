# Windows 环境设置指南

## 方式 1: 使用 IntelliJ IDEA (推荐，最简单)

### 步骤：

1. **安装 IntelliJ IDEA**
   - 下载社区版（免费）: https://www.jetbrains.com/idea/download/
   - 或使用已安装的 IDEA

2. **打开项目**
   ```
   File → Open → 选择项目根目录 (C:\JaredLyu\OnTheWay\risk-control)
   ```
   IDEA 会自动识别 Gradle 项目并开始导入

3. **等待依赖下载**
   - IDEA 会自动下载所有依赖
   - 右下角会显示进度
   - 首次导入可能需要 5-10 分钟

4. **构建项目**
   - 点击右侧 `Gradle` 面板
   - 展开 `Tasks → build`
   - 双击 `build` 任务

5. **运行服务**
   - 在 `graph-service/src/main/java/.../GraphServiceApplication.java`
   - 右键点击类名 → `Run 'GraphServiceApplication'`
   - 重复上述步骤运行 `feature-service` 和 `risk-gateway`

## 方式 2: 手动安装 Gradle

### 1. 安装 JDK 21

```powershell
# 使用 Winget
winget install Oracle.JDK.21

# 或手动下载
# https://www.oracle.com/java/technologies/downloads/#java21
```

设置环境变量：
```powershell
setx JAVA_HOME "C:\Program Files\Java\jdk-21"
setx PATH "%PATH%;%JAVA_HOME%\bin"
```

验证安装：
```powershell
java -version
```

### 2. 安装 Gradle 8.5+

#### 选项 A: 使用 Scoop (PowerShell)

```powershell
# 安装 Scoop
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
Invoke-RestMethod -Uri https://get.scoop.sh | Invoke-Expression

# 安装 Gradle
scoop install gradle
```

#### 选项 B: 使用 Chocolatey

```powershell
# 安装 Chocolatey
Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

# 安装 Gradle
choco install gradle
```

#### 选项 C: 手动安装

1. 下载 Gradle: https://gradle.org/releases/
2. 解压到 `C:\Gradle\gradle-8.5`
3. 添加到 PATH:
   ```powershell
   setx PATH "%PATH%;C:\Gradle\gradle-8.5\bin"
   ```
4. 重新打开 PowerShell

验证安装：
```powershell
gradle --version
```

## 方式 3: 使用 Gradle Wrapper (无需全局安装 Gradle)

1. **下载 Gradle Wrapper JAR**

```powershell
# 创建临时目录
mkdir temp
cd temp

# 下载 Gradle Wrapper
Invoke-WebRequest -Uri "https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar" -OutFile "gradle-wrapper.jar"

# 移动到正确位置
move gradle-wrapper.jar ..\gradle\wrapper\
cd ..
rmdir temp
```

2. **使用 gradlew.bat 编译**

```powershell
# 编译项目
.\gradlew.bat clean build -x test
```

## 启动步骤 (Windows)

### 1. 启动 Docker Desktop

确保 Docker Desktop 正在运行

### 2. 启动基础设施

```powershell
docker-compose up -d
```

验证：
```powershell
docker-compose ps
```

### 3. 初始化数据库 (可选，首次启动时自动执行)

```powershell
docker exec -it risk-control-postgres psql -U postgres -d risk_control -f /docker-entrypoint-initdb.d/init.sql
```

### 4. 编译项目

```powershell
# 使用 Gradle
gradle clean build -x test

# 或使用 Gradle Wrapper
.\gradlew.bat clean build -x test

# 或使用构建脚本
.\build.bat
```

### 5. 启动服务

打开 3 个 PowerShell 或 CMD 窗口：

**窗口 1 - Graph Service:**
```powershell
cd graph-service
gradlew bootRun
```

**窗口 2 - Feature Service:**
```powershell
cd feature-service
gradlew bootRun
```

**窗口 3 - Risk Gateway:**
```powershell
cd risk-gateway
gradlew bootRun
```

或使用自动启动脚本：
```powershell
.\start-services.bat
```

### 6. 验证服务

```powershell
# 检查健康状态
curl http://localhost:8091/actuator/health
curl http://localhost:8092/actuator/health
curl http://localhost:8080/actuator/health
```

## 测试 API

```powershell
# 发起风险评分请求
curl -X POST http://localhost:8080/api/v1/risk/score ^
  -H "Content-Type: application/json" ^
  -d "{\""eventId\"":\""evt_test_001\"",\""subjectType\"":\""user\"",\""subjectId\"":\""user_12345\"",\""transactionAmount\"":1500.00}"
```

或使用测试脚本：
```powershell
.\test-api.ps1
```

## 常见问题

### Q1: PowerShell 提示执行策略错误

```powershell
# 临时允许脚本执行
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope Process
```

### Q2: Docker Desktop 未启动

- 打开 Docker Desktop 应用程序
- 等待鲸鱼图标显示为运行状态

### Q3: 端口被占用

```powershell
# 查找占用端口的进程
netstat -ano | findstr :8080
netstat -ano | findstr :9091
netstat -ano | findstr :9092

# 结束进程 (PID 为最后一列的数字)
taskkill /PID <进程ID> /F
```

### Q4: 内存不足

在 `gradle.properties` 中调整：
```properties
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m
```

### Q5: 防火墙阻止

允许 Java 和 Gradle 通过防火墙：
```
Windows Defender 防火墙 → 允许应用通过防火墙
→ 勾选 "Java(TM) Platform SE binary"
→ 勾选 "Gradle"
```

## 推荐工具

- **IntelliJ IDEA** - Java IDE
- **Docker Desktop** - 容器管理
- **PowerShell 7** - 现代化的命令行
- **Windows Terminal** - 更好的终端体验
- **Postman** - API 测试工具

## 下一步

- 查看 `README.md` 了解完整文档
- 查看 `PROJECT_STRUCTURE.md` 了解项目结构
- 查看 `test-api.ps1` 了解 API 测试示例
