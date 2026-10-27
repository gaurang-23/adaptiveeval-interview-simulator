Write-Host "Stopping Adaptive Technical Interview Simulator services on ports 8085, 8080, and 5173..." -ForegroundColor Yellow

foreach ($port in @(8085, 8080, 5173)) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    foreach ($c in $conns) {
        if ($c.OwningProcess -gt 0) {
            Write-Host "Stopping PID $($c.OwningProcess) listening on port $port"
            Stop-Process -Id $c.OwningProcess -Force -ErrorAction SilentlyContinue
        }
    }
}

Write-Host "Done." -ForegroundColor Green
