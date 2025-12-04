// Главный модуль приложения
import ApiClient from './api.js';
import AuthManager from './auth.js';
import Player from './player.js';
import BooksManager from './books.js';
import ShortcutsManager from './shortcuts.js';
import ShortcutsHelp from './shortcuts-help.js';
import ServerSettings from './server-settings.js';

class App {
    constructor() {
        // Инициализируем API клиент
        const savedServerUrl = localStorage.getItem('server_url');
        this.api = new ApiClient(savedServerUrl || 'http://localhost:8000');
        
        // Восстанавливаем токены если есть
        const token = localStorage.getItem('access_token');
        if (token) {
            this.api.token = token;
            this.api.refreshToken = localStorage.getItem('refresh_token');
        }

        // Инициализируем менеджеры
        this.auth = new AuthManager(this.api);
        this.serverSettings = new ServerSettings(this.api);
        this.player = new Player(this.api);
        this.books = new BooksManager(this.api, this.player);
        this.shortcuts = null; // Инициализируем после авторизации
        
        // Делаем authManager доступным глобально для server-settings
        window.authManager = this.auth;

        this.setupEventListeners();
        this.checkAuth();
        this.loadSettings();
    }

    setupEventListeners() {
        // Навигация (нижняя панель для мобильных)
        document.querySelectorAll('.nav-item').forEach(item => {
            item.addEventListener('click', () => {
                const tab = item.dataset.tab;
                this.switchTab(tab);
            });
        });

        // Навигация (боковая панель для ПК)
        document.querySelectorAll('.sidebar-nav-item').forEach(item => {
            item.addEventListener('click', () => {
                const tab = item.dataset.tab;
                if (tab) {
                    this.switchTab(tab);
                }
            });
        });

        // Выход
        document.getElementById('logout-btn')?.addEventListener('click', () => {
            this.auth.logout();
        });

        document.getElementById('sidebar-logout-btn')?.addEventListener('click', () => {
            this.auth.logout();
        });

        // Мобильное меню
        const sidebar = document.querySelector('.sidebar');
        const overlay = document.getElementById('sidebar-overlay');
        
        document.getElementById('mobile-menu-btn')?.addEventListener('click', () => {
            sidebar?.classList.toggle('mobile-open');
            overlay?.classList.toggle('active');
        });

        // Закрытие мобильного меню при клике на overlay
        overlay?.addEventListener('click', () => {
            sidebar?.classList.remove('mobile-open');
            overlay?.classList.remove('active');
        });

        // Закрытие мобильного меню при клике вне его
        document.addEventListener('click', (e) => {
            const menuBtn = document.getElementById('mobile-menu-btn');
            if (sidebar && sidebar.classList.contains('mobile-open') && 
                !sidebar.contains(e.target) && 
                e.target !== menuBtn && 
                !menuBtn?.contains(e.target) &&
                e.target !== overlay) {
                sidebar.classList.remove('mobile-open');
                overlay?.classList.remove('active');
            }
        });

        // Событие успешной авторизации
        window.addEventListener('app:authenticated', () => {
            this.onAuthenticated();
        });

        // Событие выхода
        window.addEventListener('auth:logout', () => {
            this.auth.logout();
        });

        // Настройки сервера
        document.getElementById('save-server-btn')?.addEventListener('click', () => {
            this.saveServerUrl();
        });

        // Показать горячие клавиши
        document.getElementById('show-shortcuts-btn')?.addEventListener('click', () => {
            ShortcutsHelp.show();
        });

        // Мини-плеер клик для открытия полного плеера
        document.getElementById('mini-player')?.addEventListener('click', () => {
            this.switchTab('player');
        });

        // Закрытие модального окна
        document.getElementById('close-full-player')?.addEventListener('click', () => {
            document.getElementById('full-player-modal').classList.add('hidden');
        });
    }

    checkAuth() {
        if (this.api.token) {
            // Проверяем валидность токена
            this.api.getAccount()
                .then(() => {
                    // Токен валиден, показываем главный экран
                    document.getElementById('auth-screen').classList.remove('active');
                    document.getElementById('main-screen').classList.add('active');
                    this.onAuthenticated();
                })
                .catch(() => {
                    // Токен невалиден, показываем экран авторизации
                    this.api.clearTokens();
                    document.getElementById('auth-screen').classList.add('active');
                    document.getElementById('main-screen').classList.remove('active');
                });
        } else {
            // Нет токена, показываем экран авторизации
            document.getElementById('auth-screen').classList.add('active');
            document.getElementById('main-screen').classList.remove('active');
        }
    }

    async onAuthenticated() {
        // Инициализируем горячие клавиши
        if (!this.shortcuts) {
            this.shortcuts = new ShortcutsManager(this);
        }
        
        // Загружаем данные
        await this.loadAccountData();
        await this.books.loadBooks();
    }

    switchTab(tabName) {
        // Закрываем мобильное меню
        const sidebar = document.querySelector('.sidebar');
        const overlay = document.getElementById('sidebar-overlay');
        sidebar?.classList.remove('mobile-open');
        overlay?.classList.remove('active');

        // Обновляем активные вкладки
        document.querySelectorAll('.tab-content').forEach(tab => {
            tab.classList.remove('active');
        });
        document.querySelectorAll('.nav-item').forEach(item => {
            item.classList.remove('active');
        });
        document.querySelectorAll('.sidebar-nav-item').forEach(item => {
            item.classList.remove('active');
        });

        // Показываем выбранную вкладку
        const tabContent = document.getElementById(`${tabName}-tab`);
        const navItem = document.querySelector(`.nav-item[data-tab="${tabName}"]`);
        const sidebarItem = document.querySelector(`.sidebar-nav-item[data-tab="${tabName}"]`);
        
        if (tabContent) {
            tabContent.classList.add('active');
        }
        if (navItem) {
            navItem.classList.add('active');
        }
        if (sidebarItem) {
            sidebarItem.classList.add('active');
        }

        // Обновляем заголовок
        const titles = {
            books: 'Книги',
            player: 'Плеер',
            account: 'Аккаунт',
            settings: 'Настройки'
        };
        const pageTitle = document.getElementById('page-title');
        if (pageTitle) {
            pageTitle.textContent = titles[tabName] || 'XAuPlayer';
        }

        // Если переключились на плеер, обновляем UI
        if (tabName === 'player') {
            this.player.updateFullPlayerUI();
        }

        // Если переключились на аккаунт, обновляем данные
        if (tabName === 'account') {
            this.loadAccountData();
        }
    }

    async loadAccountData() {
        try {
            const account = await this.api.getAccount();
            const devices = await this.api.getDevices();

            // Обновляем информацию об аккаунте
            const accountName = document.getElementById('account-name');
            const accountEmail = document.getElementById('account-email');
            
            if (accountName) accountName.textContent = account.name || account.email;
            if (accountEmail) accountEmail.textContent = account.email;

            // Загружаем статистику
            try {
                const [listening, completed, wanted] = await Promise.all([
                    this.api.getBooksByStatus('listening', 0, 1),
                    this.api.getBooksByStatus('completed', 0, 1),
                    this.api.getBooksByStatus('wanted', 0, 1)
                ]);

                const statListening = document.getElementById('stat-listening');
                const statCompleted = document.getElementById('stat-completed');
                const statWanted = document.getElementById('stat-wanted');

                // TODO: API должен возвращать общее количество, пока используем длину массива
                if (statListening) statListening.textContent = listening?.length || 0;
                if (statCompleted) statCompleted.textContent = completed?.length || 0;
                if (statWanted) statWanted.textContent = wanted?.length || 0;
            } catch (error) {
                console.error('Error loading stats:', error);
            }

            // Обновляем список устройств
            const devicesList = document.getElementById('devices-list');
            if (devicesList && devices) {
                devicesList.innerHTML = devices.map(device => `
                    <div class="device-item">
                        <div class="device-info">
                            <div class="device-name">${device.name || 'Неизвестное устройство'}</div>
                            <div class="device-details">
                                Последняя активность: ${device.last_activity ? new Date(device.last_activity).toLocaleString('ru-RU') : 'Никогда'}
                            </div>
                        </div>
                        <div class="device-actions">
                            ${device.is_current ? 
                                '<span style="color: var(--success); font-size: 0.85rem;">Текущее</span>' :
                                `<button class="btn btn-secondary" style="padding: 6px 12px; font-size: 0.85rem;" 
                                         onclick="app.deleteDevice(${device.id})">Удалить</button>`
                            }
                        </div>
                    </div>
                `).join('');
            }
        } catch (error) {
            console.error('Error loading account data:', error);
        }
    }

    async deleteDevice(deviceId) {
        if (!confirm('Удалить это устройство?')) {
            return;
        }

        try {
            await this.api.deleteDevice(deviceId);
            await this.loadAccountData();
        } catch (error) {
            alert('Ошибка удаления устройства: ' + error.message);
        }
    }

    loadSettings() {
        const serverUrl = document.getElementById('server-url');
        const defaultSpeed = document.getElementById('default-speed');

        if (serverUrl) {
            const saved = localStorage.getItem('server_url');
            serverUrl.value = saved || 'http://localhost:8000';
        }

        if (defaultSpeed) {
            const saved = localStorage.getItem('default_speed');
            defaultSpeed.value = saved || '1';
        }
    }

    saveServerUrl() {
        const serverUrlInput = document.getElementById('server-url');
        if (serverUrlInput) {
            const url = serverUrlInput.value.trim();
            if (url) {
                this.api.setBaseUrl(url);
                alert('URL сервера сохранен. Перезагрузите страницу для применения изменений.');
            }
        }
    }
}

// Инициализируем приложение
const app = new App();

// Делаем app доступным глобально для обработчиков событий
window.app = app;

