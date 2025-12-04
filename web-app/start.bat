@echo off
chcp 65001 >nul
echo ========================================
echo Запуск XAuPlayer Web Server
echo ========================================
echo.

cd /d "%~dp0"

REM Пробуем разные порты
set PORT=8000
set MAX_ATTEMPTS=5
set ATTEMPT=1

:try_port
echo Попытка запуска на порту %PORT%...
python -m http.server %PORT% 2>nul
if errorlevel 1 (
    if %ATTEMPT% LSS %MAX_ATTEMPTS% (
        set /a PORT+=1
        set /a ATTEMPT+=1
        echo Порт занят, пробую следующий...
        goto try_port
    )
    echo.
    echo Ошибка запуска сервера!
    echo.
    echo Возможные причины:
    echo 1. Python не найден - установите с https://www.python.org/
    echo 2. Все порты заняты - закройте другие приложения
    echo 3. Нет прав доступа - запустите от имени администратора
    echo.
    echo Альтернативные варианты:
    echo - Node.js: npx http-server -p 8000
    echo - Другой порт: python -m http.server 3000
    echo.
    pause
    exit /b 1
)

REM Если дошли сюда, сервер запущен
echo.
echo ========================================
echo Сервер запущен!
echo.
echo Приложение доступно по адресу:
echo http://localhost:%PORT%
echo.
echo Нажмите Ctrl+C для остановки сервера
echo ========================================
echo.
pause

