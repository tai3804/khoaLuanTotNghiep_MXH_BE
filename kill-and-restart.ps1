<#
.SYNOPSIS
    Dừng toàn bộ các microservices Java (giải phóng các cổng) và tùy chọn khởi động lại.
#>
param(
    [switch]$Restart
)

$ports = @(8761, 8080, 8081, 8082, 8083, 8084, 8085, 8086, 8087, 8088, 8089, 8090, 8091)

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "   DỪNG TẤT CẢ CÁC SPRING BOOT SERVICES   " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

$killedCount = 0
foreach ($port in $ports) {
    $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($connections) {
        foreach ($conn in $connections) {
            $processId = $conn.OwningProcess
            if ($processId -gt 0) {
                try {
                    $proc = Get-Process -Id $processId -ErrorAction Stop
                    Write-Host "[KILL] Dang tat process $($proc.ProcessName) (PID: $processId) chiem port $port..." -ForegroundColor Yellow
                    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
                    $killedCount++
                } catch {
                    # Process might already be stopped
                }
            }
        }
    }
}

if ($killedCount -eq 0) {
    Write-Host "[OK] Khong co service Java nao dang chiem cac cong tren." -ForegroundColor Green
} else {
    Write-Host "[OK] Da giai phong thanh cong tat ca cac cong!" -ForegroundColor Green
}

if ($Restart) {
    Write-Host "`nDang khoi dong lai tat ca services..." -ForegroundColor Cyan
    & "$PSScriptRoot\start-all-server.ps1"
}
