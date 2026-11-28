@echo off
chcp 65001 >nul
title KLTN Social - STOP
cd /d "%~dp0"
echo ==^> Dung toan bo container (PostgreSQL tren may van chay)
docker compose down
pause
