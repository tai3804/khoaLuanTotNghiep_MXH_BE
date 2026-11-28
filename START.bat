@echo off
chcp 65001 >nul
title KLTN Social - START
cd /d "%~dp0"

echo ==^> PostgreSQL + pgvector (cai tren may)
sc query postgresql-x64-18 | find "RUNNING" >nul
if errorlevel 1 (
  net start postgresql-x64-18 || echo [!] Chua bat duoc PostgreSQL. Hay mo bang quyen Administrator hoac bat dich vu postgresql-x64-18.
) else (
  echo     PostgreSQL dang chay.
)

echo ==^> Docker
docker info >nul 2>&1
if errorlevel 1 (
  echo     Dang mo Docker Desktop...
  start "" "%ProgramFiles%\Docker\Docker\Docker Desktop.exe"
  :waitdocker
  timeout /t 3 >nul
  docker info >nul 2>&1
  if errorlevel 1 goto waitdocker
)
echo     Docker dang chay.

echo ==^> Build va chay toan bo he thong (docker compose)
docker compose up -d --build
if errorlevel 1 (
  echo [!] docker compose up loi, xem thong bao phia tren.
  pause
  exit /b 1
)

echo.
docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
echo.
echo API Gateway  : http://localhost:8080
echo Eureka       : http://localhost:8761
echo Kafka UI     : http://localhost:8089
echo RedisInsight : http://localhost:5540
echo Zipkin       : http://localhost:9411
echo Xem log      : docker compose logs -f ^<ten-service^>
echo Dung tat ca  : STOP.bat
pause
