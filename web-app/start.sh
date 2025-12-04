#!/bin/bash

echo "========================================"
echo "Запуск XAuPlayer Web Server"
echo "========================================"
echo ""

cd "$(dirname "$0")"

PORT=8000
MAX_ATTEMPTS=5
ATTEMPT=1

# Функция для проверки доступности порта
check_port() {
    if command -v lsof &> /dev/null; then
        lsof -i :$1 > /dev/null 2>&1
    elif command -v netstat &> /dev/null; then
        netstat -an | grep ":$1 " > /dev/null 2>&1
    else
        return 1
    fi
}

# Пробуем найти свободный порт
while [ $ATTEMPT -le $MAX_ATTEMPTS ]; do
    if check_port $PORT; then
        echo "Порт $PORT занят, пробую следующий..."
        PORT=$((PORT + 1))
        ATTEMPT=$((ATTEMPT + 1))
    else
        break
    fi
done

# Пробуем Python
if command -v python3 &> /dev/null; then
    echo "Запускаю сервер на порту $PORT..."
    python3 -m http.server $PORT
elif command -v python &> /dev/null; then
    echo "Запускаю сервер на порту $PORT..."
    python -m http.server $PORT
elif command -v node &> /dev/null; then
    echo "Python не найден, используем Node.js..."
    echo "Запускаю сервер на порту $PORT..."
    npx http-server -p $PORT
else
    echo "Ошибка: Python или Node.js не найдены!"
    echo ""
    echo "Установите один из них:"
    echo "- Python: https://www.python.org/"
    echo "- Node.js: https://nodejs.org/"
    exit 1
fi

