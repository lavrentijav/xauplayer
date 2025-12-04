// Модуль для настройки сервера API
import ApiClient from './api.js';
import UrlUtils from './url-utils.js';

class ServerSettings {
    constructor(apiClient) {
        this.api = apiClient;
        this.modal = document.getElementById('server-settings-modal');
        this.setupEventListeners();
    }

    setupEventListeners() {
        // Используем делегирование событий для надежности
        const handleClick = (e) => {
            const target = e.target.closest('#server-settings-btn, #sidebar-server-settings-btn, #auth-server-settings-btn');
            if (target) {
                e.preventDefault();
                e.stopPropagation();
                this.open();
            }
        };
        
        document.addEventListener('click', handleClick);

        // Также добавляем прямые обработчики для надежности
        const setupDirectHandlers = () => {
            const authBtn = document.getElementById('auth-server-settings-btn');
            const serverBtn = document.getElementById('server-settings-btn');
            const sidebarBtn = document.getElementById('sidebar-server-settings-btn');

            if (authBtn) {
                authBtn.addEventListener('click', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    console.log('Auth server settings button clicked');
                    this.open();
                });
            }

            if (serverBtn) {
                serverBtn.addEventListener('click', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    this.open();
                });
            }

            if (sidebarBtn) {
                sidebarBtn.addEventListener('click', (e) => {
                    e.preventDefault();
                    e.stopPropagation();
                    this.open();
                });
            }
        };

        // Пытаемся установить обработчики сразу и после загрузки DOM
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', setupDirectHandlers);
        } else {
            // Задержка для гарантии, что элементы в DOM
            setTimeout(setupDirectHandlers, 100);
        }

        // Кнопки закрытия
        document.getElementById('close-server-settings')?.addEventListener('click', () => {
            this.close();
        });

        document.getElementById('cancel-server-settings-btn')?.addEventListener('click', () => {
            this.close();
        });

        // Закрытие при клике вне модалки
        this.modal?.addEventListener('click', (e) => {
            if (e.target === this.modal) {
                this.close();
            }
        });

        // Проверка подключения
        document.getElementById('test-server-btn')?.addEventListener('click', () => {
            this.testConnection();
        });

        // Сохранение настроек
        document.getElementById('save-server-settings-btn')?.addEventListener('click', () => {
            this.save();
        });

        // Enter в поле ввода
        document.getElementById('server-url-input')?.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') {
                this.testConnection();
            }
        });
    }

    open() {
        if (!this.modal) {
            console.error('Modal element not found');
            return;
        }

        console.log('Opening server settings modal');

        // Загружаем текущий URL
        const currentUrl = this.api.getBaseUrl();
        const input = document.getElementById('server-url-input');
        if (input) {
            input.value = currentUrl;
        }

        // Сбрасываем статус
        this.resetStatus();

        // Показываем модалку
        this.modal.classList.remove('hidden');
        document.body.style.overflow = 'hidden';

        // Фокус на поле ввода
        setTimeout(() => {
            if (input) {
                input.focus();
                input.select();
            }
        }, 100);
    }

    close() {
        if (!this.modal) return;
        this.modal.classList.add('hidden');
        document.body.style.overflow = '';
    }

    resetStatus() {
        const indicator = document.getElementById('status-indicator');
        const statusText = document.getElementById('status-text');
        const testInfo = document.getElementById('server-test-info');

        if (indicator) {
            indicator.className = 'status-indicator';
        }
        if (statusText) {
            statusText.textContent = 'Не проверено';
        }
        if (testInfo) {
            testInfo.innerHTML = '';
            testInfo.classList.remove('show');
        }
    }

    async testConnection() {
        console.log('[SERVER-SETTINGS] Starting connection test...');
        
        const input = document.getElementById('server-url-input');
        if (!input) {
            console.error('[SERVER-SETTINGS] Input element not found');
            return;
        }

        let url = input.value.trim();
        console.log('[SERVER-SETTINGS] Input URL:', url);
        
        if (!url) {
            console.warn('[SERVER-SETTINGS] Empty URL');
            this.showStatus('error', 'Введите URL сервера');
            return;
        }

        // Нормализуем URL (добавляем порт по умолчанию если нужно)
        const originalUrl = url;
        url = UrlUtils.normalizeUrl(url);
        console.log('[SERVER-SETTINGS] Normalized URL:', { original: originalUrl, normalized: url });
        
        // Валидация URL
        if (!UrlUtils.isValidUrl(url)) {
            console.error('[SERVER-SETTINGS] Invalid URL format');
            this.showStatus('error', 'Неверный формат URL');
            return;
        }

        // Обновляем значение в поле ввода нормализованным URL
        input.value = url;

        const testBtn = document.getElementById('test-server-btn');
        const originalText = testBtn?.textContent;
        if (testBtn) {
            testBtn.disabled = true;
            testBtn.textContent = 'Проверка...';
        }

        this.showStatus('loading', 'Проверка подключения...');

        try {
            console.log('[SERVER-SETTINGS] Testing connection to:', url);
            
            // Пробуем выполнить health check
            const healthUrl = `${url}/health`;
            console.log('[SERVER-SETTINGS] Trying /health endpoint:', healthUrl);
            
            const startTime = Date.now();
            const response = await fetch(healthUrl, {
                method: 'GET',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            const duration = Date.now() - startTime;

            console.log('[SERVER-SETTINGS] Health check response:', {
                status: response.status,
                statusText: response.statusText,
                ok: response.ok,
                duration: `${duration}ms`,
                headers: Object.fromEntries(response.headers.entries())
            });

            if (response.ok) {
                const data = await response.json().catch(() => ({}));
                console.log('[SERVER-SETTINGS] Health check data:', data);
                this.showStatus('success', 'Подключение успешно!');
                this.showTestInfo('success', {
                    status: response.status,
                    statusText: response.statusText,
                    server: url,
                    health: data,
                    duration: `${duration}ms`
                });
            } else {
                console.warn('[SERVER-SETTINGS] Health check failed, trying /ping...');
                // Пробуем /ping как альтернативу
                const pingUrl = `${url}/ping`;
                const pingStartTime = Date.now();
                const pingResponse = await fetch(pingUrl, {
                    method: 'GET'
                });
                const pingDuration = Date.now() - pingStartTime;
                
                console.log('[SERVER-SETTINGS] Ping response:', {
                    status: pingResponse.status,
                    statusText: pingResponse.statusText,
                    ok: pingResponse.ok,
                    duration: `${pingDuration}ms`
                });
                
                if (pingResponse.ok) {
                    this.showStatus('success', 'Подключение успешно!');
                    this.showTestInfo('success', {
                        status: pingResponse.status,
                        statusText: pingResponse.statusText,
                        server: url,
                        duration: `${pingDuration}ms`
                    });
                } else {
                    const errorText = await response.text().catch(() => '');
                    console.error('[SERVER-SETTINGS] Both /health and /ping failed', {
                        healthStatus: response.status,
                        pingStatus: pingResponse.status,
                        errorText: errorText.substring(0, 200)
                    });
                    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                }
            }
        } catch (error) {
            let errorMessage = error.message;
            let errorDetails = {
                message: error.message,
                stack: error.stack,
                name: error.name,
                url: url
            };

            // CORS ошибка
            if (error.message === 'Failed to fetch' || error.name === 'TypeError') {
                errorMessage = 'CORS ошибка: Сервер не разрешает запросы';
                errorDetails.corsError = true;
                errorDetails.origin = window.location.origin;
                errorDetails.target = url;
            }

            // Сетевая ошибка
            if (error.message.includes('NetworkError') || error.message.includes('network')) {
                errorMessage = 'Сетевая ошибка: Не удалось подключиться к серверу';
                errorDetails.networkError = true;
            }

            console.error('[SERVER-SETTINGS] Connection test error:', errorDetails);
            
            let displayMessage = `Ошибка подключения: ${errorMessage}`;
            if (errorDetails.corsError) {
                displayMessage += '\n\nНа сервере нужно настроить CORS для разрешения запросов с вашего домена.';
            }

            this.showStatus('error', displayMessage);
            this.showTestInfo('error', {
                server: url,
                error: errorMessage,
                details: errorDetails.corsError ? 
                    'CORS ошибка: Сервер не разрешает запросы с ' + window.location.origin + 
                    '\n\nРешение: Настройте CORS на сервере API, добавив ваш домен в список разрешенных origins.' :
                    error.stack
            });
        } finally {
            if (testBtn) {
                testBtn.disabled = false;
                testBtn.textContent = originalText || 'Проверить подключение';
            }
        }
    }

    showStatus(type, message) {
        const indicator = document.getElementById('status-indicator');
        const statusText = document.getElementById('status-text');

        if (indicator) {
            indicator.className = `status-indicator status-${type}`;
        }
        if (statusText) {
            statusText.textContent = message;
        }
    }

    showTestInfo(type, data) {
        const testInfo = document.getElementById('server-test-info');
        if (!testInfo) return;

        testInfo.classList.add('show', `info-${type}`);

        let html = '<div class="test-info-content">';
        
        if (type === 'success') {
            html += '<h4>✓ Информация о подключении:</h4>';
            html += `<p><strong>Сервер:</strong> ${data.server}</p>`;
            html += `<p><strong>Статус:</strong> ${data.status} ${data.statusText}</p>`;
            if (data.health) {
                html += '<p><strong>Health Check:</strong> Сервер работает</p>';
            }
        } else {
            html += '<h4>✗ Ошибка подключения:</h4>';
            html += `<p><strong>Сервер:</strong> ${data.server}</p>`;
            html += `<p><strong>Ошибка:</strong> ${data.error}</p>`;
            html += '<p class="error-hint">Проверьте:</p>';
            html += '<ul>';
            html += '<li>Правильность URL сервера</li>';
            html += '<li>Доступность сервера в сети</li>';
            if (data.error && data.error.includes('CORS')) {
                html += '<li><strong>Настройки CORS на сервере</strong> - добавьте ваш домен в разрешенные origins</li>';
            } else {
                html += '<li>Настройки CORS на сервере</li>';
            }
            html += '<li>Наличие endpoints /health или /ping</li>';
            html += '</ul>';
            
            if (data.error && data.error.includes('CORS')) {
                html += '<div class="cors-help">';
                html += '<h5>Как настроить CORS на сервере:</h5>';
                html += '<p>В FastAPI добавьте в middleware:</p>';
                html += '<pre><code>app.add_middleware(\n';
                html += '  CORSMiddleware,\n';
                html += '  allow_origins=["http://localhost:8000", "ваш-домен"],\n';
                html += '  allow_credentials=True,\n';
                html += '  allow_methods=["*"],\n';
                html += '  allow_headers=["*"],\n';
                html += ')</code></pre>';
                html += '</div>';
            }
        }

        html += '</div>';
        testInfo.innerHTML = html;
    }

    save() {
        const input = document.getElementById('server-url-input');
        if (!input) return;

        let url = input.value.trim();
        if (!url) {
            alert('Введите URL сервера');
            return;
        }

        // Нормализуем URL (добавляем порт по умолчанию если нужно)
        url = UrlUtils.normalizeUrl(url);
        
        // Валидация URL
        if (!UrlUtils.isValidUrl(url)) {
            alert('Неверный формат URL');
            return;
        }

        // Обновляем значение в поле ввода нормализованным URL
        input.value = url;

        // Сохраняем настройки
        this.api.setBaseUrl(url);
        
        // Обновляем информацию о сервере на странице входа
        if (window.authManager) {
            window.authManager.updateServerInfo();
        }
        
        // Показываем сообщение
        const saveBtn = document.getElementById('save-server-settings-btn');
        const originalText = saveBtn?.textContent;
        if (saveBtn) {
            saveBtn.textContent = 'Сохранено!';
            saveBtn.disabled = true;
        }

        setTimeout(() => {
            if (saveBtn) {
                saveBtn.textContent = originalText || 'Сохранить';
                saveBtn.disabled = false;
            }
            this.close();
            
            // Если мы на странице входа, не перезагружаем страницу
            const authScreen = document.getElementById('auth-screen');
            if (authScreen && authScreen.classList.contains('active')) {
                // Просто закрываем модалку, страница входа остается
                return;
            }
            
            // Перезагружаем страницу для применения изменений только в основном приложении
            if (confirm('Настройки сохранены. Перезагрузить страницу для применения изменений?')) {
                window.location.reload();
            }
        }, 1000);
    }
}

export default ServerSettings;

