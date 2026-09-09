<#
.SYNOPSIS
    Khởi động tự động toàn bộ hạ tầng Docker và các Spring Boot Microservices.
#>

$root = $PSScriptRoot

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  KHỞI ĐỘNG HỆ THỐNG KLTN SOCIAL BACKEND  " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. Kiểm tra & Khởi chạy Docker Compose (Postgres, Redis, Kafka)
Write-Host "`n[1/3] Kiem tra Ha tang Docker (Postgres, Redis, Kafka)..." -ForegroundColor Yellow
try {
    docker compose -f "$root\docker-compose.yaml" up -d
    Write-Host "[OK] Ha tang Docker dang chay san sang!" -ForegroundColor Green
} catch {
    Write-Host "[CANH BAO] Khong the goi Docker Compose. Hay dam bao Docker Desktop dang bat!" -ForegroundColor Red
}

# 2. Khởi chạy Eureka Server (Service Discovery) trước
Write-Host "`n[2/3] Dang khoi dong Eureka Server (Port 8761)..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle = 'EUREKA-SERVER (8761)'; cd '$root\eureka-server'; mvn spring-boot:run"

# Chờ Eureka Server sẵn sàng (tối đa 30s)
Write-Host "Dang doi Eureka Server san sang..." -NoNewline
for ($i = 0; $i -lt 15; $i++) {
    Start-Sleep -Seconds 1
    Write-Host "." -NoNewline
}
Write-Host " [OK]" -ForegroundColor Green

# 3. Khởi chạy API Gateway và các Microservices
Write-Host "`n[3/3] Dang khoi dong API Gateway va cac Microservices..." -ForegroundColor Yellow

$services = @(
    @{ Name = "api-gateway"; Title = "API-GATEWAY (8080)" },
    @{ Name = "auth-service"; Title = "AUTH-SERVICE (8081)" },
    @{ Name = "user-service"; Title = "USER-SERVICE (8085)" },
    @{ Name = "post-service"; Title = "POST-SERVICE (8082)" },
    @{ Name = "feed-service"; Title = "FEED-SERVICE (8088)" },
    @{ Name = "notification-service"; Title = "NOTIFICATION-SERVICE (8083)" },
    @{ Name = "media-service"; Title = "MEDIA-SERVICE (8084)" },
    @{ Name = "chat-service"; Title = "CHAT-SERVICE (8087)" }
)

foreach ($s in $services) {
    $svcPath = Join-Path $root $s.Name
    if (Test-Path $svcPath) {
        Write-Host "  -> Dang mo cua so: $($s.Title)..." -ForegroundColor Cyan
        Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$Host.UI.RawUI.WindowTitle = '$($s.Title)'; cd '$svcPath'; mvn spring-boot:run"
        Start-Sleep -Seconds 2
    }
}

Write-Host "`n========================================================" -ForegroundColor Green
Write-Host " [THANH CONG] Toan bo cac services da duoc khoi dong!" -ForegroundColor Green
Write-Host "  - Eureka Server : http://localhost:8761" -ForegroundColor White
Write-Host "  - API Gateway   : http://localhost:8080" -ForegroundColor White
Write-Host "  - Redis Insight : http://localhost:5540" -ForegroundColor White
Write-Host "  - Kafka UI      : http://localhost:8089" -ForegroundColor White
Write-Host "  - Frontend App  : http://localhost:5173" -ForegroundColor White
Write-Host "========================================================" -ForegroundColor Green
