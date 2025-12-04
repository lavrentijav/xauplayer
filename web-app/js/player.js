// Модуль плеера
import ApiClient from './api.js';

class Player {
    constructor(apiClient) {
        this.api = apiClient;
        this.audio = null;
        this.currentBook = null;
        this.currentChapter = null;
        this.chapters = [];
        this.currentChapterIndex = -1;
        this.isPlaying = false;
        this.currentPosition = 0;
        this.playbackSpeed = parseFloat(localStorage.getItem('default_speed') || '1.0');
        this.updateInterval = null;
        this.setupEventListeners();
    }

    setupEventListeners() {
        // Кнопки управления
        document.getElementById('mini-play-pause')?.addEventListener('click', () => {
            this.togglePlayPause();
        });

        // Обновление прогресса
        if (this.audio) {
            this.audio.addEventListener('timeupdate', () => {
                this.updateProgress();
            });

            this.audio.addEventListener('ended', () => {
                this.onChapterEnd();
            });

            this.audio.addEventListener('loadedmetadata', () => {
                this.updateUI();
            });
        }

        // Обработка изменения скорости
        document.addEventListener('speed:change', (e) => {
            this.setSpeed(e.detail.speed);
        });
    }

    async loadBook(book) {
        try {
            this.currentBook = book;
            this.chapters = await this.api.getBookChapters(book.id);
            
            // Загружаем сохраненный прогресс
            const progress = await this.api.syncProgress();
            const bookProgress = progress?.progress?.find(p => p.book_id === book.id);
            
            if (bookProgress) {
                this.currentChapterIndex = this.chapters.findIndex(c => c.id === bookProgress.chapter_id);
                if (this.currentChapterIndex === -1) {
                    this.currentChapterIndex = 0;
                }
                this.currentPosition = bookProgress.position_ms || 0;
            } else {
                this.currentChapterIndex = 0;
                this.currentPosition = 0;
            }

            await this.loadChapter(this.currentChapterIndex);
            this.updatePlayerUI();
            this.showMiniPlayer();
        } catch (error) {
            console.error('Error loading book:', error);
            alert('Ошибка загрузки книги: ' + error.message);
        }
    }

    async loadAudioWithAuth() {
        try {
            const streamUrl = this.api.getStreamUrl(this.currentBook.id, this.currentChapter.id);
            
            // Загружаем аудио через fetch с авторизацией
            const response = await fetch(streamUrl, {
                headers: {
                    'Authorization': `Bearer ${this.api.token}`
                }
            });

            if (!response.ok) {
                throw new Error(`Failed to load audio: ${response.status}`);
            }

            // Создаем blob из ответа
            const blob = await response.blob();
            const blobUrl = URL.createObjectURL(blob);

            // Создаем аудио элемент с blob URL
            this.audio = new Audio(blobUrl);
            this.audio.preload = 'auto';
            this.audio.playbackRate = this.playbackSpeed;

            // Устанавливаем позицию если есть сохраненный прогресс
            if (this.currentPosition > 0) {
                this.audio.addEventListener('loadedmetadata', () => {
                    this.audio.currentTime = this.currentPosition / 1000;
                }, { once: true });
            }

            // Обновляем слушатели событий
            this.audio.addEventListener('timeupdate', () => {
                this.updateProgress();
            });

            this.audio.addEventListener('ended', () => {
                this.onChapterEnd();
                // Освобождаем blob URL после окончания
                URL.revokeObjectURL(blobUrl);
            });

            this.audio.addEventListener('loadedmetadata', () => {
                this.updateUI();
            });

            this.updatePlayerUI();
        } catch (error) {
            console.error('Error loading audio:', error);
            throw error;
        }
    }

    async loadChapter(index) {
        if (index < 0 || index >= this.chapters.length) {
            return;
        }

        this.currentChapterIndex = index;
        this.currentChapter = this.chapters[index];

        // Останавливаем текущее воспроизведение и освобождаем blob URL
        if (this.audio) {
            this.audio.pause();
            if (this.audio.src.startsWith('blob:')) {
                URL.revokeObjectURL(this.audio.src);
            }
            this.audio = null;
        }

        // Загружаем аудио через fetch с авторизацией и создаем blob URL
        await this.loadAudioWithAuth();
    }

    async play() {
        if (!this.audio) {
            if (this.currentBook) {
                await this.loadChapter(this.currentChapterIndex);
            } else {
                return;
            }
        }

        try {
            await this.audio.play();
            this.isPlaying = true;
            this.updatePlayPauseButton();
            this.startProgressUpdates();
        } catch (error) {
            console.error('Play error:', error);
        }
    }

    pause() {
        if (this.audio) {
            this.audio.pause();
            this.isPlaying = false;
            this.updatePlayPauseButton();
            this.stopProgressUpdates();
            this.saveProgress();
        }
    }

    togglePlayPause() {
        if (this.isPlaying) {
            this.pause();
        } else {
            this.play();
        }
    }

    async previousChapter() {
        if (this.currentChapterIndex > 0) {
            this.currentPosition = 0;
            await this.loadChapter(this.currentChapterIndex - 1);
            await this.play();
        }
    }

    async nextChapter() {
        if (this.currentChapterIndex < this.chapters.length - 1) {
            this.currentPosition = 0;
            await this.loadChapter(this.currentChapterIndex + 1);
            await this.play();
        }
    }

    onChapterEnd() {
        // Автоматически переходим к следующей главе
        if (this.currentChapterIndex < this.chapters.length - 1) {
            this.nextChapter();
        } else {
            this.pause();
        }
    }

    setSpeed(speed) {
        this.playbackSpeed = speed;
        if (this.audio) {
            this.audio.playbackRate = speed;
        }
        localStorage.setItem('default_speed', speed.toString());
        this.updateSpeedButtons();
    }

    seekTo(position) {
        if (this.audio) {
            this.audio.currentTime = position;
            this.currentPosition = position * 1000;
            this.updateProgress();
        }
    }

    updateProgress() {
        if (!this.audio) return;

        this.currentPosition = this.audio.currentTime * 1000;
        const duration = this.audio.duration * 1000 || 0;
        const progress = duration > 0 ? (this.currentPosition / duration) * 100 : 0;

        // Обновляем прогресс-бар
        const progressFill = document.getElementById('mini-progress-fill');
        if (progressFill) {
            progressFill.style.width = `${progress}%`;
        }

        // Обновляем время в полном плеере
        const currentTimeEl = document.getElementById('current-time');
        const durationEl = document.getElementById('duration');
        if (currentTimeEl) {
            currentTimeEl.textContent = this.formatTime(this.currentPosition / 1000);
        }
        if (durationEl) {
            durationEl.textContent = this.formatTime(this.audio.duration || 0);
        }
    }

    startProgressUpdates() {
        this.stopProgressUpdates();
        this.updateInterval = setInterval(() => {
            this.saveProgress();
        }, 5000); // Сохраняем каждые 5 секунд
    }

    stopProgressUpdates() {
        if (this.updateInterval) {
            clearInterval(this.updateInterval);
            this.updateInterval = null;
        }
    }

    async saveProgress() {
        if (!this.currentBook || !this.currentChapter || !this.audio) {
            return;
        }

        try {
            await this.api.updateProgress(
                this.currentBook.id,
                this.currentChapter.id,
                Math.floor(this.currentPosition),
                this.playbackSpeed
            );
        } catch (error) {
            console.error('Error saving progress:', error);
        }
    }

    updatePlayPauseButton() {
        const playIcon = document.querySelector('.play-icon');
        const pauseIcon = document.querySelector('.pause-icon');
        
        if (playIcon && pauseIcon) {
            if (this.isPlaying) {
                playIcon.classList.add('hidden');
                pauseIcon.classList.remove('hidden');
            } else {
                playIcon.classList.remove('hidden');
                pauseIcon.classList.add('hidden');
            }
        }
    }

    updateSpeedButtons() {
        document.querySelectorAll('.speed-btn').forEach(btn => {
            if (parseFloat(btn.dataset.speed) === this.playbackSpeed) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });
    }

    updatePlayerUI() {
        if (!this.currentBook || !this.currentChapter) return;

        // Обновляем мини-плеер
        const miniTitle = document.getElementById('mini-player-title');
        const miniChapter = document.getElementById('mini-player-chapter');
        const miniCover = document.getElementById('mini-player-cover');

        if (miniTitle) miniTitle.textContent = this.currentBook.title;
        if (miniChapter) miniChapter.textContent = this.currentChapter.title || `Глава ${this.currentChapterIndex + 1}`;
        if (miniCover) {
            miniCover.src = this.api.getBookCoverUrl(this.currentBook.id);
            miniCover.onerror = () => {
                miniCover.style.display = 'none';
            };
        }

        // Обновляем полный плеер
        this.updateFullPlayerUI();
    }

    updateFullPlayerUI() {
        if (!this.currentBook || !this.currentChapter) return;

        const playerContent = document.getElementById('player-content');
        if (!playerContent) return;

        const coverUrl = this.api.getBookCoverUrl(this.currentBook.id);
        const duration = this.audio?.duration || 0;

        playerContent.innerHTML = `
            <div class="player-view">
                <img src="${coverUrl}" alt="${this.currentBook.title}" class="player-cover-large" 
                     onerror="this.style.display='none'">
                <div class="player-info">
                    <div class="player-book-title">${this.currentBook.title}</div>
                    <div class="player-book-author">${this.currentBook.author || ''}</div>
                    <div class="player-chapter-title">${this.currentChapter.title || `Глава ${this.currentChapterIndex + 1}`}</div>
                </div>
                <div class="player-controls">
                    <button class="control-btn" id="prev-chapter-btn">
                        <svg viewBox="0 0 24 24" fill="currentColor">
                            <path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/>
                        </svg>
                    </button>
                    <button class="control-btn play-pause" id="full-play-pause-btn">
                        <svg class="play-icon" viewBox="0 0 24 24" fill="currentColor">
                            <path d="M8 5v14l11-7z"/>
                        </svg>
                        <svg class="pause-icon hidden" viewBox="0 0 24 24" fill="currentColor">
                            <path d="M6 4h4v16H6zm8 0h4v16h-4z"/>
                        </svg>
                    </button>
                    <button class="control-btn" id="next-chapter-btn">
                        <svg viewBox="0 0 24 24" fill="currentColor">
                            <path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/>
                        </svg>
                    </button>
                </div>
                <div class="progress-container">
                    <div class="progress-bar" id="full-progress-bar">
                        <div class="progress-fill" id="full-progress-fill"></div>
                    </div>
                    <div class="progress-time">
                        <span id="current-time">${this.formatTime(this.currentPosition / 1000)}</span>
                        <span id="duration">${this.formatTime(duration)}</span>
                    </div>
                </div>
                <div class="speed-control">
                    <span>Скорость:</span>
                    ${[0.5, 0.75, 1, 1.25, 1.5, 1.75, 2].map(speed => 
                        `<button class="speed-btn ${speed === this.playbackSpeed ? 'active' : ''}" 
                                 data-speed="${speed}">${speed}x</button>`
                    ).join('')}
                </div>
                <div class="chapters-list">
                    <h3>Главы</h3>
                    <div id="chapters-list-content">
                        ${this.chapters.map((chapter, index) => `
                            <div class="chapter-item ${index === this.currentChapterIndex ? 'active' : ''}" 
                                 data-chapter-index="${index}">
                                <span class="chapter-item-title">${chapter.title || `Глава ${index + 1}`}</span>
                                <span class="chapter-item-duration">${this.formatTime(chapter.duration || 0)}</span>
                            </div>
                        `).join('')}
                    </div>
                </div>
            </div>
        `;

        // Настраиваем обработчики событий для полного плеера
        document.getElementById('full-play-pause-btn')?.addEventListener('click', () => {
            this.togglePlayPause();
        });

        document.getElementById('prev-chapter-btn')?.addEventListener('click', () => {
            this.previousChapter();
        });

        document.getElementById('next-chapter-btn')?.addEventListener('click', () => {
            this.nextChapter();
        });

        // Обработка клика по прогресс-бару
        const progressBar = document.getElementById('full-progress-bar');
        if (progressBar) {
            progressBar.addEventListener('click', (e) => {
                const rect = progressBar.getBoundingClientRect();
                const percent = (e.clientX - rect.left) / rect.width;
                const newTime = percent * (this.audio?.duration || 0);
                this.seekTo(newTime);
            });
        }

        // Обработка выбора главы
        document.querySelectorAll('.chapter-item').forEach(item => {
            item.addEventListener('click', () => {
                const index = parseInt(item.dataset.chapterIndex);
                this.loadChapter(index);
                this.play();
            });
        });

        // Обработка изменения скорости
        document.querySelectorAll('.speed-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const speed = parseFloat(btn.dataset.speed);
                this.setSpeed(speed);
            });
        });

        this.updatePlayPauseButton();
        this.updateSpeedButtons();
    }

    showMiniPlayer() {
        const miniPlayer = document.getElementById('mini-player');
        if (miniPlayer) {
            miniPlayer.classList.remove('hidden');
        }
    }

    hideMiniPlayer() {
        const miniPlayer = document.getElementById('mini-player');
        if (miniPlayer) {
            miniPlayer.classList.add('hidden');
        }
    }

    formatTime(seconds) {
        if (!isFinite(seconds) || isNaN(seconds)) return '0:00';
        const h = Math.floor(seconds / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        const s = Math.floor(seconds % 60);
        if (h > 0) {
            return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
        }
        return `${m}:${s.toString().padStart(2, '0')}`;
    }

    getCurrentBook() {
        return this.currentBook;
    }

    getCurrentChapter() {
        return this.currentChapter;
    }

    getIsPlaying() {
        return this.isPlaying;
    }
}

export default Player;

