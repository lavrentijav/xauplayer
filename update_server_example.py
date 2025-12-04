#!/usr/bin/env python3
"""
Простой сервер для обновлений XAuPlayer

Использование:
1. Поместите APK файл в папку 'apk/' с именем 'app-release.apk'
2. Запустите сервер: python update_server_example.py
3. В настройках приложения укажите URL: http://localhost:8000
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import os
from urllib.parse import urlparse, parse_qs

# Конфигурация
APK_DIR = r"app\\release"
APK_FILENAME = "app-release.apk"
PORT = 8000

# Информация о новой версии (обновите эти значения)
NEW_VERSION_CODE = 11  # Должен быть больше текущего versionCode приложения
NEW_VERSION_NAME = "1.1.11"
UPDATE_DESCRIPTION = "Исправления и улучшения"
MANDATORY_UPDATE = False


class UpdateHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        parsed_path = urlparse(self.path)
        path = parsed_path.path
        query_params = parse_qs(parsed_path.query)
        
        # Обработка запроса проверки обновлений
        if path == "/check":
            version_code = int(query_params.get("versionCode", [0])[0])
            package_name = query_params.get("packageName", [""])[0]
            
            print(f"Проверка обновлений: versionCode={version_code}, packageName={package_name}")
            
            # Проверяем, есть ли обновление
            if version_code < NEW_VERSION_CODE:
                # Формируем URL для скачивания APK
                # Используем хост из заголовка запроса или localhost
                host = self.headers.get("Host", f"localhost:{PORT}")
                download_url = f"http://{host}/download"
                
                response_data = {
                    "updateAvailable": True,
                    "versionCode": NEW_VERSION_CODE,
                    "versionName": NEW_VERSION_NAME,
                    "downloadUrl": download_url,
                    "description": UPDATE_DESCRIPTION,
                    "mandatory": MANDATORY_UPDATE
                }
                
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(json.dumps(response_data).encode())
                print(f"Обновление доступно: версия {NEW_VERSION_NAME}")
            else:
                # Обновление не требуется
                response_data = {
                    "updateAvailable": False
                }
                
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                self.wfile.write(json.dumps(response_data).encode())
                print("Обновление не требуется")
        
        # Обработка запроса скачивания APK
        elif path == "/download":
            apk_path = os.path.join(APK_DIR, APK_FILENAME)
            
            if not os.path.exists(apk_path):
                self.send_response(404)
                self.end_headers()
                self.wfile.write(b"APK file not found")
                print(f"Ошибка: APK файл не найден: {apk_path}")
                return
            
            # Отправляем APK файл
            file_size = os.path.getsize(apk_path)
            
            self.send_response(200)
            self.send_header("Content-Type", "application/vnd.android.package-archive")
            self.send_header("Content-Length", str(file_size))
            self.send_header("Content-Disposition", f'attachment; filename="{APK_FILENAME}"')
            self.end_headers()
            
            with open(apk_path, "rb") as f:
                self.wfile.write(f.read())
            
            print(f"APK отправлен: {APK_FILENAME} ({file_size} bytes)")
        
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not Found")
    
    def log_message(self, format, *args):
        # Переопределяем логирование для более читаемого вывода
        print(f"[{self.address_string()}] {format % args}")


def main():
    # Проверяем наличие APK файла
    apk_path = os.path.join(APK_DIR, APK_FILENAME)
    if not os.path.exists(apk_path):
        print(f"⚠️  ВНИМАНИЕ: APK файл не найден: {apk_path}")
        print(f"   Создайте папку '{APK_DIR}' и поместите туда файл '{APK_FILENAME}'")
        print(f"   Или измените APK_DIR и APK_FILENAME в скрипте")
        return
    
    # Создаем и запускаем сервер
    server_address = ("", PORT)
    httpd = HTTPServer(server_address, UpdateHandler)
    
    print("=" * 60)
    print("Сервер обновлений XAuPlayer запущен")
    print("=" * 60)
    print(f"URL сервера: http://localhost:{PORT}")
    print(f"APK файл: {apk_path}")
    print(f"Версия: {NEW_VERSION_NAME} (code: {NEW_VERSION_CODE})")
    print("=" * 60)
    print("\nДля остановки нажмите Ctrl+C\n")
    
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n\nСервер остановлен")
        httpd.server_close()


if __name__ == "__main__":
    main()

