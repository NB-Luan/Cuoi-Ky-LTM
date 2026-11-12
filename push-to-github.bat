@echo off
chcp 65001 >nul
title Day code len GitHub

cd /d "%~dp0"

echo ========================================================
echo               DAY CODE LEN GITHUB
echo ========================================================
echo Dang day code len: https://github.com/NB-Luan/LTM-TCP-Chat-App.git
echo.

git push -u origin main

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo [THANH CONG] Da day toan bo code len GitHub thanh cong!
    echo ========================================================
) else (
    echo.
    echo [CHUA THANH CONG]
    echo Neu ban chua tao repo tren web, vui long vao https://github.com/new
    echo tao repo ten la "LTM-TCP-Chat-App" roi chay lai file nay nhe!
)

echo.
pause
