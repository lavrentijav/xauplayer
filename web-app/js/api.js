// API клиент для работы с сервером
class ApiClient {
    constructor(baseUrl = 'http://localhost:8000') {
        this.baseUrl = baseUrl;
        this.token = localStorage.getItem('access_token');
        this.refreshToken = localStorage.getItem('refresh_token');
        this.loggingEnabled = true; // Включить логирование
    }

    log(level, message, data = null) {
        if (!this.loggingEnabled) return;
        
        const timestamp = new Date().toISOString();
        const prefix = `[API ${timestamp}]`;
        
        switch (level) {
            case 'info':
                console.log(`%c${prefix} ${message}`, 'color: #2196F3', data || '');
                break;
            case 'success':
                console.log(`%c${prefix} ${message}`, 'color: #4CAF50', data || '');
                break;
            case 'error':
                console.error(`%c${prefix} ${message}`, 'color: #F44336', data || '');
                break;
            case 'warn':
                console.warn(`%c${prefix} ${message}`, 'color: #FF9800', data || '');
                break;
            default:
                console.log(`${prefix} ${message}`, data || '');
        }
    }

    setBaseUrl(url) {
        this.log('info', 'Setting base URL', { old: this.baseUrl, new: url });
        this.baseUrl = url;
        localStorage.setItem('server_url', url);
    }

    getBaseUrl() {
        const saved = localStorage.getItem('server_url');
        const url = saved || this.baseUrl;
        this.log('info', 'Getting base URL', { url });
        return url;
    }

    setTokens(accessToken, refreshToken) {
        this.token = accessToken;
        this.refreshToken = refreshToken;
        if (accessToken) {
            localStorage.setItem('access_token', accessToken);
        }
        if (refreshToken) {
            localStorage.setItem('refresh_token', refreshToken);
        }
    }

    clearTokens() {
        this.token = null;
        this.refreshToken = null;
        localStorage.removeItem('access_token');
        localStorage.removeItem('refresh_token');
    }

    async request(endpoint, options = {}) {
        const url = `${this.getBaseUrl()}${endpoint}`;
        const headers = {
            'Content-Type': 'application/json',
            ...options.headers
        };

        if (this.token) {
            headers['Authorization'] = `Bearer ${this.token.substring(0, 20)}...`;
        }

        this.log('info', `→ ${options.method || 'GET'} ${endpoint}`, {
            url: url,
            headers: { ...headers, Authorization: headers['Authorization'] ? 'Bearer ***' : undefined },
            body: options.body ? (options.body.length > 200 ? options.body.substring(0, 200) + '...' : options.body) : undefined
        });

        const startTime = Date.now();

        try {
            const response = await fetch(url, {
                ...options,
                headers: {
                    ...headers,
                    'Authorization': this.token ? `Bearer ${this.token}` : undefined
                }
            });

            const duration = Date.now() - startTime;
            const responseHeaders = {};
            response.headers.forEach((value, key) => {
                responseHeaders[key] = value;
            });

            this.log('info', `← ${response.status} ${response.statusText} (${duration}ms)`, {
                url: url,
                headers: responseHeaders,
                ok: response.ok
            });

            // Проверяем заголовки для автоматического обновления токена
            if (response.headers.get('X-Token-Expires-Soon') === 'true') {
                this.log('warn', 'Token expires soon, refreshing...');
                await this.refreshAccessToken();
            }

            if (response.status === 401) {
                this.log('error', 'Unauthorized (401)', {
                    'X-Token-Expired': response.headers.get('X-Token-Expired'),
                    'X-Token-Expires-At': response.headers.get('X-Token-Expires-At')
                });

                // Проверяем, истек ли токен
                if (response.headers.get('X-Token-Expired') === 'true') {
                    this.log('warn', 'Token expired, attempting refresh...');
                    const refreshed = await this.refreshAccessToken();
                    if (refreshed) {
                        this.log('success', 'Token refreshed, retrying request...');
                        // Повторяем запрос с новым токеном
                        const retryHeaders = {
                            ...headers,
                            'Authorization': `Bearer ${this.token}`
                        };
                        const retryResponse = await fetch(url, {
                            ...options,
                            headers: retryHeaders
                        });
                        
                        const retryDuration = Date.now() - startTime;
                        this.log('info', `← RETRY ${retryResponse.status} (${retryDuration}ms)`);
                        
                        if (!retryResponse.ok) {
                            const errorData = await retryResponse.json().catch(() => ({}));
                            this.log('error', 'Retry failed', errorData);
                            throw new Error(errorData.detail || `HTTP error! status: ${retryResponse.status}`);
                        }
                        const contentType = retryResponse.headers.get('content-type');
                        if (contentType && contentType.includes('application/json')) {
                            const data = await retryResponse.json();
                            this.log('success', 'Request successful after retry', { dataSize: JSON.stringify(data).length });
                            return data;
                        }
                        return null;
                    } else {
                        this.log('error', 'Failed to refresh token');
                    }
                }
                // Токен не удалось обновить, выходим из системы
                this.log('error', 'Clearing tokens and logging out');
                this.clearTokens();
                window.dispatchEvent(new CustomEvent('auth:logout'));
                throw new Error('Сессия истекла. Пожалуйста, войдите снова.');
            }

            if (!response.ok) {
                let errorData = {};
                try {
                    const text = await response.text();
                    if (text) {
                        errorData = JSON.parse(text);
                    }
                } catch (e) {
                    errorData = { detail: response.statusText };
                }
                
                this.log('error', `Request failed: ${response.status}`, {
                    status: response.status,
                    statusText: response.statusText,
                    error: errorData
                });
                
                throw new Error(errorData.detail || `HTTP error! status: ${response.status}`);
            }

            // Если ответ пустой, возвращаем null
            const contentType = response.headers.get('content-type');
            if (contentType && contentType.includes('application/json')) {
                const data = await response.json();
                this.log('success', 'Request successful', { 
                    dataSize: JSON.stringify(data).length,
                    hasData: data !== null && data !== undefined
                });
                return data;
            }
            
            this.log('info', 'Request successful (no JSON body)');
            return null;
        } catch (error) {
            const duration = Date.now() - startTime;
            
            // Определяем тип ошибки
            let errorMessage = error.message;
            let errorDetails = {
                error: error.message,
                stack: error.stack,
                url: url,
                duration: `${duration}ms`
            };

            // CORS ошибка
            if (error.message === 'Failed to fetch' || error.name === 'TypeError') {
                errorMessage = 'CORS ошибка: Сервер не разрешает запросы с этого домена. Проверьте настройки CORS на сервере.';
                errorDetails.corsError = true;
                errorDetails.origin = window.location.origin;
                errorDetails.target = url;
                errorDetails.solution = 'На сервере нужно добавить ваш домен в список разрешенных origins в настройках CORS';
            }

            // Сетевая ошибка
            if (error.message.includes('NetworkError') || error.message.includes('network')) {
                errorMessage = 'Сетевая ошибка: Не удалось подключиться к серверу. Проверьте доступность сервера и интернет-соединение.';
                errorDetails.networkError = true;
            }

            this.log('error', `Request failed after ${duration}ms`, errorDetails);
            
            // Создаем улучшенную ошибку
            const enhancedError = new Error(errorMessage);
            enhancedError.originalError = error;
            enhancedError.details = errorDetails;
            throw enhancedError;
        }
    }

    async refreshAccessToken() {
        if (!this.refreshToken) {
            this.log('warn', 'No refresh token available');
            return false;
        }

        this.log('info', 'Refreshing access token...');
        const startTime = Date.now();

        try {
            const response = await fetch(`${this.getBaseUrl()}/auth/refresh`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    refresh_token: this.refreshToken
                })
            });

            const duration = Date.now() - startTime;
            this.log('info', `Token refresh response: ${response.status} (${duration}ms)`);

            if (response.ok) {
                const data = await response.json();
                this.setTokens(data.access_token, data.refresh_token || this.refreshToken);
                this.log('success', 'Token refreshed successfully');
                return true;
            } else {
                const errorData = await response.json().catch(() => ({}));
                this.log('error', 'Token refresh failed', errorData);
            }
        } catch (error) {
            this.log('error', 'Token refresh error', {
                message: error.message,
                stack: error.stack
            });
        }
        return false;
    }


    // Аутентификация
    async register(email, password, name) {
        this.log('info', 'Registering new user', { email, name: name ? '***' : undefined });
        const data = await this.request('/auth/register', {
            method: 'POST',
            body: JSON.stringify({ email, password, name })
        });
        this.log('success', 'Registration successful', { userId: data.id });
        return data;
    }

    async login(email, password, deviceName = 'Web Browser') {
        this.log('info', 'Attempting login', { email, deviceName });
        try {
            const data = await this.request('/auth/login', {
                method: 'POST',
                body: JSON.stringify({ email, password, device_name: deviceName })
            });
            if (data.access_token) {
                this.setTokens(data.access_token, data.refresh_token);
                this.log('success', 'Login successful', { 
                    hasAccessToken: !!data.access_token,
                    hasRefreshToken: !!data.refresh_token,
                    deviceId: data.device_id
                });
            } else {
                this.log('error', 'Login response missing access_token', data);
            }
            return data;
        } catch (error) {
            this.log('error', 'Login failed', {
                message: error.message,
                email: email
            });
            throw error;
        }
    }

    async logout() {
        if (this.refreshToken) {
            try {
                await this.request(`/auth/logout?refresh_token=${this.refreshToken}`, {
                    method: 'POST'
                });
            } catch (error) {
                console.error('Logout error:', error);
            }
        }
        this.clearTokens();
    }

    // Книги
    async getBooks(offset = 0, limit = 100, search = '') {
        const params = new URLSearchParams({
            offset: offset.toString(),
            limit: limit.toString()
        });
        if (search) {
            params.append('search', search);
        }
        return await this.request(`/books?${params}`);
    }

    async getBook(bookId) {
        return await this.request(`/books/${bookId}`);
    }

    async getBookChapters(bookId) {
        return await this.request(`/books/${bookId}/chapters`);
    }

    getBookCoverUrl(bookId) {
        return `${this.getBaseUrl()}/covers/books/${bookId}`;
    }

    getStreamUrl(bookId, chapterId) {
        // Используем новый endpoint согласно API_GUIDE.md
        return `${this.getBaseUrl()}/books/${bookId}/stream/${chapterId}`;
    }

    // Статусы
    async getBookStatus(bookId) {
        return await this.request(`/status/books/${bookId}`);
    }

    async setBookStatus(bookId, status) {
        return await this.request(`/status/books/${bookId}`, {
            method: 'PUT',
            body: JSON.stringify({ status })
        });
    }

    async deleteBookStatus(bookId) {
        return await this.request(`/status/books/${bookId}`, {
            method: 'DELETE'
        });
    }

    async getBooksByStatus(status, offset = 0, limit = 100) {
        const params = new URLSearchParams({
            status: status,
            offset: offset.toString(),
            limit: limit.toString()
        });
        return await this.request(`/status/books?${params}`);
    }

    async getTags(language = 'ru') {
        return await this.request(`/tags?language=${language}`);
    }

    // Прогресс
    async updateProgress(bookId, chapterId, positionMs, playbackSpeed = 1.0) {
        return await this.request('/progress/update', {
            method: 'POST',
            body: JSON.stringify({
                book_id: bookId,
                chapter_id: chapterId,
                position_ms: positionMs,
                playback_speed: playbackSpeed,
                last_update: new Date().toISOString()
            })
        });
    }

    async syncProgress() {
        return await this.request('/progress/sync');
    }

    // Аккаунт
    async getAccount() {
        return await this.request('/account');
    }

    async getDevices() {
        return await this.request('/account/devices');
    }

    async deleteDevice(deviceId) {
        return await this.request(`/account/device/${deviceId}`, {
            method: 'DELETE'
        });
    }

    // Серии
    async getSeriesList(offset = 0, limit = 100, search = '') {
        const params = new URLSearchParams({
            offset: offset.toString(),
            limit: limit.toString()
        });
        if (search) {
            params.append('search', search);
        }
        return await this.request(`/series?${params}`);
    }

    async getSeries(seriesId) {
        return await this.request(`/series/${seriesId}`);
    }

    async getSeriesBooks(seriesId, search = '') {
        const params = new URLSearchParams();
        if (search) {
            params.append('search', search);
        }
        const query = params.toString();
        return await this.request(`/series/${seriesId}/books${query ? '?' + query : ''}`);
    }

    getSeriesCoverUrl(seriesId) {
        return `${this.getBaseUrl()}/covers/series/${seriesId}`;
    }

    // Статусы серий
    async getSeriesStatus(seriesId) {
        return await this.request(`/status/series/${seriesId}`);
    }

    async setSeriesStatus(seriesId, status) {
        return await this.request(`/status/series/${seriesId}`, {
            method: 'PUT',
            body: JSON.stringify({ status })
        });
    }

    async getSeriesByStatus(status, offset = 0, limit = 100) {
        const params = new URLSearchParams({
            status: status,
            offset: offset.toString(),
            limit: limit.toString()
        });
        return await this.request(`/status/series?${params}`);
    }

    // Статистика
    async getStatistics(year, month = null, startDate = null, endDate = null) {
        const params = new URLSearchParams();
        if (year && month) {
            params.append('year', year.toString());
            params.append('month', month.toString());
        } else if (year) {
            params.append('year', year.toString());
        } else if (startDate && endDate) {
            params.append('start_date', startDate);
            params.append('end_date', endDate);
        }
        return await this.request(`/statistics?${params}`);
    }

    // Активность
    async getActivity() {
        return await this.request('/account/activity');
    }

    // Заметки
    async getNotes(bookId) {
        return await this.request(`/notes/${bookId}`);
    }

    async createNote(bookId, text, timestamp) {
        return await this.request(`/notes/${bookId}`, {
            method: 'POST',
            body: JSON.stringify({ text, timestamp })
        });
    }
}

// Экспорт для использования в других модулях
export default ApiClient;


