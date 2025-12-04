// Модуль авторизации
import ApiClient from './api.js';

class AuthManager {
    constructor(apiClient) {
        this.api = apiClient;
        this.setupEventListeners();
        this.updateServerInfo();
    }

    updateServerInfo() {
        const serverUrl = this.api.getBaseUrl();
        const serverInfoEl = document.getElementById('auth-server-url');
        if (serverInfoEl) {
            serverInfoEl.textContent = serverUrl || 'Не указан';
            if (!serverUrl || serverUrl === 'http://localhost:8000') {
                serverInfoEl.style.color = 'var(--text-secondary)';
            } else {
                serverInfoEl.style.color = 'var(--success)';
            }
        }
    }

    setupEventListeners() {
        // Переключение между логином и регистрацией
        document.getElementById('show-register')?.addEventListener('click', (e) => {
            e.preventDefault();
            this.showRegister();
        });

        document.getElementById('show-login')?.addEventListener('click', (e) => {
            e.preventDefault();
            this.showLogin();
        });

        // Кнопки входа и регистрации
        document.getElementById('login-btn')?.addEventListener('click', () => {
            this.handleLogin();
        });

        document.getElementById('register-btn')?.addEventListener('click', () => {
            this.handleRegister();
        });

        // Enter в формах
        ['login-email', 'login-password', 'register-email', 'register-password'].forEach(id => {
            document.getElementById(id)?.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') {
                    if (id.startsWith('login')) {
                        this.handleLogin();
                    } else {
                        this.handleRegister();
                    }
                }
            });
        });
    }

    showLogin() {
        document.getElementById('login-form').classList.add('active');
        document.getElementById('register-form').classList.remove('active');
        this.clearErrors();
    }

    showRegister() {
        document.getElementById('register-form').classList.add('active');
        document.getElementById('login-form').classList.remove('active');
        this.clearErrors();
    }

    clearErrors() {
        document.getElementById('login-error').classList.remove('show');
        document.getElementById('register-error').classList.remove('show');
    }

    showError(elementId, message) {
        const errorEl = document.getElementById(elementId);
        errorEl.textContent = message;
        errorEl.classList.add('show');
    }

    async handleLogin() {
        console.log('[AUTH] Starting login process...');
        
        const email = document.getElementById('login-email').value.trim();
        const password = document.getElementById('login-password').value;
        const deviceName = document.getElementById('login-device').value.trim() || 'Web Browser';
        const rememberMe = document.getElementById('remember-me').checked;

        console.log('[AUTH] Login form data:', { 
            email, 
            passwordLength: password.length, 
            deviceName,
            rememberMe 
        });

        // Проверяем наличие сервера
        const serverUrl = this.api.getBaseUrl();
        console.log('[AUTH] Server URL:', serverUrl);
        
        if (!serverUrl || serverUrl === 'http://localhost:8000') {
            console.warn('[AUTH] Server URL not configured');
            this.showError('login-error', 'Сначала укажите URL сервера в настройках');
            return;
        }

        if (!email || !password) {
            console.warn('[AUTH] Missing email or password');
            this.showError('login-error', 'Заполните все поля');
            return;
        }

        const loginBtn = document.getElementById('login-btn');
        loginBtn.disabled = true;
        loginBtn.textContent = 'Вход...';
        this.clearErrors();

        try {
            console.log('[AUTH] Calling API login...');
            const data = await this.api.login(email, password, deviceName);
            console.log('[AUTH] Login response:', { 
                hasAccessToken: !!data.access_token,
                hasRefreshToken: !!data.refresh_token,
                deviceId: data.device_id
            });
            
            if (data.access_token) {
                console.log('[AUTH] Login successful, calling onAuthSuccess...');
                if (!rememberMe) {
                    // Если не запоминать, не сохраняем токены
                    // Но они уже сохранены в api.login, так что очистим если нужно
                }
                this.onAuthSuccess();
            } else {
                console.error('[AUTH] No access token in response:', data);
                throw new Error('Не удалось получить токен');
            }
        } catch (error) {
            console.error('[AUTH] Login error:', {
                message: error.message,
                stack: error.stack,
                name: error.name,
                details: error.details
            });

            // Улучшенное сообщение об ошибке
            let errorMessage = error.message || 'Ошибка входа. Проверьте данные.';
            
            // CORS ошибка
            if (error.details?.corsError) {
                errorMessage = 'Ошибка CORS: Сервер не разрешает запросы с этого домена.\n\n' +
                    'Решение:\n' +
                    '1. На сервере API нужно настроить CORS\n' +
                    '2. Добавьте ваш домен в список разрешенных origins\n' +
                    '3. Или используйте прокси для обхода CORS';
            }
            
            // Сетевая ошибка
            if (error.details?.networkError) {
                errorMessage = 'Не удалось подключиться к серверу.\n\n' +
                    'Проверьте:\n' +
                    '1. Доступность сервера: ' + serverUrl + '\n' +
                    '2. Интернет-соединение\n' +
                    '3. Правильность URL сервера';
            }

            this.showError('login-error', errorMessage);
        } finally {
            loginBtn.disabled = false;
            loginBtn.textContent = 'Войти';
        }
    }

    async handleRegister() {
        const name = document.getElementById('register-name').value.trim();
        const email = document.getElementById('register-email').value.trim();
        const password = document.getElementById('register-password').value;

        // Проверяем наличие сервера
        const serverUrl = this.api.getBaseUrl();
        if (!serverUrl || serverUrl === 'http://localhost:8000') {
            this.showError('register-error', 'Сначала укажите URL сервера в настройках');
            return;
        }

        if (!name || !email || !password) {
            this.showError('register-error', 'Заполните все поля');
            return;
        }

        if (password.length < 6) {
            this.showError('register-error', 'Пароль должен быть не менее 6 символов');
            return;
        }

        const registerBtn = document.getElementById('register-btn');
        registerBtn.disabled = true;
        registerBtn.textContent = 'Регистрация...';
        this.clearErrors();

        try {
            await this.api.register(email, password, name);
            // После регистрации автоматически входим
            const loginData = await this.api.login(email, password, 'Web Browser');
            if (loginData.access_token) {
                this.onAuthSuccess();
            }
        } catch (error) {
            this.showError('register-error', error.message || 'Ошибка регистрации');
        } finally {
            registerBtn.disabled = false;
            registerBtn.textContent = 'Зарегистрироваться';
        }
    }

    onAuthSuccess() {
        // Переключаемся на главный экран
        document.getElementById('auth-screen').classList.remove('active');
        document.getElementById('main-screen').classList.add('active');
        
        // Запускаем приложение
        window.dispatchEvent(new CustomEvent('app:authenticated'));
    }

    async logout() {
        try {
            await this.api.logout();
        } catch (error) {
            console.error('Logout error:', error);
        }
        
        // Переключаемся на экран авторизации
        document.getElementById('main-screen').classList.remove('active');
        document.getElementById('auth-screen').classList.add('active');
        this.showLogin();
        
        // Очищаем форму
        document.getElementById('login-email').value = '';
        document.getElementById('login-password').value = '';
    }
}

export default AuthManager;

