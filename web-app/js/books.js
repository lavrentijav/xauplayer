// Модуль для работы с книгами
import ApiClient from './api.js';
import Player from './player.js';

class BooksManager {
    constructor(apiClient, player) {
        this.api = apiClient;
        this.player = player;
        this.books = [];
        this.currentFilter = 'all';
        this.searchQuery = '';
        this.setupEventListeners();
    }

    setupEventListeners() {
        // Поиск
        const searchInput = document.getElementById('book-search');
        if (searchInput) {
            let searchTimeout;
            searchInput.addEventListener('input', (e) => {
                clearTimeout(searchTimeout);
                searchTimeout = setTimeout(() => {
                    this.searchQuery = e.target.value.trim();
                    this.loadBooks();
                }, 300);
            });
        }

        // Фильтры
        document.querySelectorAll('.filter-tab').forEach(tab => {
            tab.addEventListener('click', () => {
                document.querySelectorAll('.filter-tab').forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                this.currentFilter = tab.dataset.filter;
                this.loadBooks();
            });
        });
    }

    async loadBooks() {
        const booksList = document.getElementById('books-list');
        const loading = document.getElementById('books-loading');
        const empty = document.getElementById('books-empty');

        if (booksList) booksList.innerHTML = '';
        if (loading) loading.style.display = 'block';
        if (empty) empty.style.display = 'none';

        try {
            let books = [];

            if (this.currentFilter === 'all') {
                books = await this.api.getBooks(0, 100, this.searchQuery);
            } else {
                books = await this.api.getBooksByStatus(this.currentFilter, 0, 100);
                // Поиск уже выполняется на сервере через параметр search в getBooksByStatus
            }

            this.books = books || [];

            if (loading) loading.style.display = 'none';

            if (this.books.length === 0) {
                if (empty) empty.style.display = 'block';
                return;
            }

            this.renderBooks();
        } catch (error) {
            console.error('Error loading books:', error);
            if (loading) loading.style.display = 'none';
            if (empty) {
                empty.textContent = 'Ошибка загрузки книг';
                empty.style.display = 'block';
            }
        }
    }

    renderBooks() {
        const booksList = document.getElementById('books-list');
        if (!booksList) return;

        booksList.innerHTML = this.books.map(book => {
            const coverUrl = this.api.getBookCoverUrl(book.id);
            return `
                <div class="book-card" data-book-id="${book.id}">
                    <img src="${coverUrl}" alt="${book.title}" class="book-cover" 
                         onerror="this.style.display='none'">
                    <div class="book-info">
                        <div class="book-title">${book.title}</div>
                        <div class="book-author">${book.author || ''}</div>
                    </div>
                </div>
            `;
        }).join('');

        // Обработчики кликов
        document.querySelectorAll('.book-card').forEach(card => {
            card.addEventListener('click', async () => {
                const bookId = parseInt(card.dataset.bookId);
                const book = this.books.find(b => b.id === bookId);
                if (book) {
                    await this.player.loadBook(book);
                    // Переключаемся на вкладку плеера
                    document.querySelectorAll('.nav-item').forEach(item => {
                        if (item.dataset.tab === 'player') {
                            item.click();
                        }
                    });
                }
            });
        });
    }

    getBooks() {
        return this.books;
    }
}

export default BooksManager;

