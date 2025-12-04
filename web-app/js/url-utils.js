// Утилиты для работы с URL и автоматического определения портов
class UrlUtils {
    /**
     * Нормализует URL, добавляя порт по умолчанию если не указан
     * @param {string} url - URL для нормализации
     * @returns {string} - Нормализованный URL
     */
    static normalizeUrl(url) {
        if (!url || typeof url !== 'string') {
            return '';
        }

        url = url.trim();

        // Если URL не начинается с http:// или https://, добавляем http://
        if (!url.match(/^https?:\/\//i)) {
            url = 'http://' + url;
        }

        try {
            const urlObj = new URL(url);
            const protocol = urlObj.protocol.toLowerCase();
            const hostname = urlObj.hostname.toLowerCase();
            const port = urlObj.port;

            // Если порт не указан, добавляем порт по умолчанию
            if (!port) {
                if (protocol === 'https:') {
                    // Для HTTPS порт 443 обычно не указывается (стандартный порт)
                    // Для localhost используем 8000, для остальных оставляем без порта
                    if (hostname === 'localhost' || hostname === '127.0.0.1') {
                        urlObj.port = '8000'; // По умолчанию для localhost
                    }
                    // Для остальных HTTPS (включая api.xau.fire-core.ru) оставляем без порта (стандартный 443)
                } else if (protocol === 'http:') {
                    // Для HTTP порт 80 обычно не указывается (стандартный порт)
                    // Для localhost используем 8000, для остальных оставляем без порта
                    if (hostname === 'localhost' || hostname === '127.0.0.1') {
                        urlObj.port = '8000'; // По умолчанию для localhost
                    }
                    // Для остальных HTTP оставляем без порта (стандартный 80)
                }
            }
            // Если порт указан явно (например :443 или :8000), оставляем как есть

            // Убираем trailing slash из pathname
            if (urlObj.pathname === '/') {
                urlObj.pathname = '';
            }

            return urlObj.toString().replace(/\/$/, '');
        } catch (e) {
            // Если URL невалидный, возвращаем как есть (будет ошибка при валидации)
            return url;
        }
    }

    /**
     * Валидирует URL
     * @param {string} url - URL для валидации
     * @returns {boolean} - true если URL валиден
     */
    static isValidUrl(url) {
        if (!url || typeof url !== 'string') {
            return false;
        }

        try {
            const normalized = this.normalizeUrl(url);
            new URL(normalized);
            return true;
        } catch (e) {
            return false;
        }
    }

    /**
     * Извлекает базовый URL без пути
     * @param {string} url - Полный URL
     * @returns {string} - Базовый URL
     */
    static getBaseUrl(url) {
        try {
            const urlObj = new URL(url);
            return `${urlObj.protocol}//${urlObj.host}`;
        } catch (e) {
            return url;
        }
    }
}

export default UrlUtils;

