@echo off
chcp 65001 >nul
title Day code len GitHub

cd /d "%~dp0"

echo ========================================================
echo               DAY CODE LEN GITHUB
echo ========================================================
echo Dang day code len: https://github.com/NB-Luan/LTM-TCP-Chat-App.git
echo.

git push origin main

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================================
    echo [THANH CONG] Da day toan bo code len GitHub thanh cong!
    echo ========================================================
) else (
    echo.
    echo [LOI] Co loi xay ra khi day code len GitHub.
)

echo.
pause
