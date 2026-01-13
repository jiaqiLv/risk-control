@echo off
REM Test Python gRPC Service Connection
REM This script tests the connection to the real Python inference service

set BASE_URL=http://localhost:8081

echo ========================================
echo   Testing Python gRPC Connection
echo ========================================
echo.
echo Python Service: 10.60.38.173:49094
echo.

echo Test 1: Small Amount Transaction (expect APPROVE)
echo POST /api/v1/evaluate
curl -s -X POST "%BASE_URL%/api/v1/evaluate" ^
  -H "Content-Type: application/json" ^
  -d "{\"transactionId\":\"test_python_001\",\"userId\":\"user_001\",\"eventTimestamp\":1736608800000,\"amount\":299.99,\"currency\":\"USD\",\"productCd\":\"W\",\"channel\":\"online\",\"attributes\":{\"card1\":12345,\"addr1\":100,\"deviceInfo\":\"iPhone 12\"}}" ^
  | jq .
echo.
echo.

echo Test 2: Medium Amount Transaction (expect REVIEW)
echo POST /api/v1/evaluate
curl -s -X POST "%BASE_URL%/api/v1/evaluate" ^
  -H "Content-Type: application/json" ^
  -d "{\"transactionId\":\"test_python_002\",\"userId\":\"user_002\",\"eventTimestamp\":1736608800000,\"amount\":2500.00,\"currency\":\"USD\",\"productCd\":\"W\",\"channel\":\"online\",\"attributes\":{\"card1\":67890,\"addr1\":200,\"deviceInfo\":\"Samsung S21\"}}" ^
  | jq .
echo.
echo.

echo Test 3: High Amount Transaction (expect REJECT)
echo POST /api/v1/evaluate
curl -s -X POST "%BASE_URL%/api/v1/evaluate" ^
  -H "Content-Type: application/json" ^
  -d "{\"transactionId\":\"test_python_003\",\"userId\":\"user_003\",\"eventTimestamp\":1736608800000,\"amount\":8000.00,\"currency\":\"USD\",\"productCd\":\"W\",\"channel\":\"online\",\"attributes\":{\"card1\":11111,\"addr1\":300,\"deviceInfo\":\"Unknown Device\"}}" ^
  | jq .
echo.
echo.

echo Test 4: Cold Start User (new user)
echo POST /api/v1/evaluate
curl -s -X POST "%BASE_URL%/api/v1/evaluate" ^
  -H "Content-Type: application/json" ^
  -d "{\"transactionId\":\"test_coldstart_001\",\"userId\":\"COLD_START_new_user_001\",\"eventTimestamp\":1736608800000,\"amount\":1500.00,\"currency\":\"USD\",\"productCd\":\"W\",\"channel\":\"online\",\"attributes\":{\"card1\":99999,\"addr1\":999,\"deviceInfo\":\"New Device Registration\"}}" ^
  | jq .
echo.
echo.

echo ========================================
echo   Tests Completed
echo ========================================
echo.
echo Check the logs for detailed gRPC communication:
echo   logs/orchestrator-service.log
echo.
pause
