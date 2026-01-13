@echo off
REM Orchestrator Service Test Script

set BASE_URL=http://localhost:8081

echo ========================================
echo   Orchestrator Service Test Script
echo ========================================
echo.

REM Test 1: Health Check
echo Test 1: Health Check
echo GET /api/v1/health
curl -s "%BASE_URL%/api/v1/health"
echo.
echo.

REM Test 2: Service Info
echo Test 2: Service Info
echo GET /api/v1/info
curl -s "%BASE_URL%/api/v1/info"
echo.
echo.

REM Test 3: Normal Amount Transaction (APPROVE expected)
echo Test 3: Normal Amount Transaction (APPROVE expected)
echo POST /api/v1/evaluate
curl -s -X POST "%BASE_URL%/api/v1/evaluate" ^
  -H "Content-Type: application/json" ^
  -d "{\"transactionId\":\"test001\",\"userId\":\"user001\",\"eventTimestamp\":1736608800000,\"amount\":299.99,\"currency\":\"USD\",\"productCd\":\"W\",\"channel\":\"online\",\"attributes\":{\"card1\":12345,\"addr1\":100}}"
echo.
echo.

REM Test 4: High Amount Transaction (REVIEW expected)
echo Test 4: High Amount Transaction (REVIEW expected)
echo POST /api/v1/evaluate
curl -s -X POST "%BASE_URL%/api/v1/evaluate" ^
  -H "Content-Type: application/json" ^
  -d "{\"transactionId\":\"test002\",\"userId\":\"user002\",\"eventTimestamp\":1736608800000,\"amount\":1500.00,\"currency\":\"USD\",\"productCd\":\"W\",\"channel\":\"online\",\"attributes\":{\"card1\":67890,\"addr1\":200}}"
echo.
echo.

REM Test 5: Very High Amount Transaction (REJECT expected)
echo Test 5: Very High Amount Transaction (REJECT expected)
echo POST /api/v1/evaluate
curl -s -X POST "%BASE_URL%/api/v1/evaluate" ^
  -H "Content-Type: application/json" ^
  -d "{\"transactionId\":\"test003\",\"userId\":\"user003\",\"eventTimestamp\":1736608800000,\"amount\":6000.00,\"currency\":\"USD\",\"productCd\":\"W\",\"channel\":\"online\",\"attributes\":{\"card1\":11111,\"addr1\":300}}"
echo.
echo.

echo ========================================
echo   Tests Completed
echo ========================================
echo.
echo Note: Tests are running in MOCK mode (no downstream services required)
echo To enable real mode, set mock-mode: false in application.yml
echo.
pause
