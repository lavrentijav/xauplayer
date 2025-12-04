// Модуль для горячих клавиш
import ShortcutsHelp from './shortcuts-help.js';

class ShortcutsManager {
    constructor(app) {
        this.app = app;
        this.setupShortcuts();
    }

    showHelp() {
        ShortcutsHelp.show();
    }

    setupShortcuts() {
        document.addEventListener('keydown', (e) => {
            // Игнорируем если пользователь вводит текст
            if (this.isInputFocused()) {
                return;
            }

            // Ctrl/Cmd + клавиша
            if (e.ctrlKey || e.metaKey) {
                switch (e.key) {
                    case 'q':
                        e.preventDefault();
                        this.app.auth.logout();
                        break;
                    case '1':
                        e.preventDefault();
                        this.app.switchTab('books');
                        break;
                    case '2':
                        e.preventDefault();
                        this.app.switchTab('player');
                        break;
                    case '3':
                        e.preventDefault();
                        this.app.switchTab('account');
                        break;
                    case '4':
                        e.preventDefault();
                        this.app.switchTab('settings');
                        break;
                    case 'k':
                        e.preventDefault();
                        this.focusSearch();
                        break;
                }
            }

            // Показ справки по горячим клавишам
            if (e.key === '?' && !e.ctrlKey && !e.metaKey && !e.shiftKey) {
                e.preventDefault();
                this.showHelp();
            }

            // Простые клавиши (без модификаторов)
            switch (e.key) {
                case ' ':
                    e.preventDefault();
                    this.togglePlayPause();
                    break;
                case 'ArrowLeft':
                    e.preventDefault();
                    this.seekBackward();
                    break;
                case 'ArrowRight':
                    e.preventDefault();
                    this.seekForward();
                    break;
                case 'ArrowUp':
                    e.preventDefault();
                    this.previousChapter();
                    break;
                case 'ArrowDown':
                    e.preventDefault();
                    this.nextChapter();
                    break;
                case 'f':
                    if (!e.ctrlKey && !e.metaKey) {
                        e.preventDefault();
                        this.toggleFullscreen();
                    }
                    break;
            }
        });
    }

    isInputFocused() {
        const activeElement = document.activeElement;
        return activeElement && (
            activeElement.tagName === 'INPUT' ||
            activeElement.tagName === 'TEXTAREA' ||
            activeElement.isContentEditable
        );
    }

    focusSearch() {
        const searchInput = document.getElementById('book-search');
        if (searchInput) {
            searchInput.focus();
            searchInput.select();
        }
    }

    togglePlayPause() {
        const player = this.app.player;
        if (player && player.audio) {
            player.togglePlayPause();
        }
    }

    seekBackward() {
        const player = this.app.player;
        if (player && player.audio) {
            const newTime = Math.max(0, player.audio.currentTime - 10);
            player.seekTo(newTime);
        }
    }

    seekForward() {
        const player = this.app.player;
        if (player && player.audio) {
            const duration = player.audio.duration || 0;
            const newTime = Math.min(duration, player.audio.currentTime + 10);
            player.seekTo(newTime);
        }
    }

    previousChapter() {
        const player = this.app.player;
        if (player) {
            player.previousChapter();
        }
    }

    nextChapter() {
        const player = this.app.player;
        if (player) {
            player.nextChapter();
        }
    }

    toggleFullscreen() {
        // Переключаемся на вкладку плеера
        this.app.switchTab('player');
    }
}

export default ShortcutsManager;

