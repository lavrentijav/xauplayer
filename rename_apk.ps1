# Путь к файлам
$GradleFile = "app/build.gradle.kts"
$ApkFile    = "app/release/app-release.apk"
$OutputDir  = "C:\Users\lavre\Downloads"

Write-Host "Поиск versionName в $GradleFile..."

# Проверка существования файлов
if (-not (Test-Path $GradleFile)) {
    Write-Host "Ошибка: файл $GradleFile не найден!" -ForegroundColor Red
    Pause
    exit 1
}

if (-not (Test-Path $ApkFile)) {
    Write-Host "Ошибка: файл $ApkFile не найден!" -ForegroundColor Red
    Pause
    exit 1
}

# Чтение файла и поиск versionName
$content = Get-Content $GradleFile -Raw

if ($content -match 'versionName\s*=\s*"([^"]+)"') {
    $VersionName = $Matches[1]
} else {
    Write-Host "Ошибка: versionName не найден в $GradleFile" -ForegroundColor Red
    Write-Host 'Убедитесь, что строка выглядит как: versionName = "1.0.0"'
    Pause
    exit 1
}

Write-Host "Найдена версия: $VersionName"

# Новое имя файла
$NewName = "XAuPlayer_v$VersionName.apk"
$NewPath = Join-Path $OutputDir $NewName

Write-Host "Переименование: $ApkFile → $NewName"

# Если файл уже существует — удаляем
if (Test-Path $NewPath) {
    Remove-Item $NewPath -Force
}

# Переименование файла
Rename-Item -Path $ApkFile -NewName $NewName

if (Test-Path $NewPath) {
    Write-Host ""
    Write-Host "========================================"
    Write-Host "Успешно! Файл переименован в: $NewName"
    Write-Host "========================================"
} else {
    Write-Host ""
    Write-Host "========================================"
    Write-Host "Ошибка: Не удалось переименовать файл!"
    Write-Host "========================================"
}

Pause
