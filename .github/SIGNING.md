# Подпись APK и сборка через GitHub Actions

Workflow `.github/workflows/android-build.yml` собирает debug- и release-APK
на каждый push в `main`/`master`/`claude/**`, на pull request и вручную
(`workflow_dispatch`). Готовые APK доступны как артефакты запуска (вкладка
**Actions → выбранный запуск → Artifacts**: `xauplayer-debug`, `xauplayer-release`).

## Куда загрузить ключ подписи

Ключ **не** коммитится в репозиторий. Он загружается в **GitHub Secrets**:

**Settings → Secrets and variables → Actions → New repository secret**
(`https://github.com/lavrentijav/xauplayer/settings/secrets/actions`)

Нужно создать четыре секрета:

| Имя секрета         | Значение                                                        |
|---------------------|-----------------------------------------------------------------|
| `KEYSTORE_BASE64`   | base64-содержимое вашего `.jks`/`.keystore` файла               |
| `KEYSTORE_PASSWORD` | пароль хранилища ключей (storePassword)                          |
| `KEY_ALIAS`         | алиас ключа (key alias)                                          |
| `KEY_PASSWORD`      | пароль ключа (keyPassword)                                       |

### Как создать keystore (если его ещё нет)

```bash
keytool -genkey -v \
  -keystore release.keystore \
  -alias xauplayer \
  -keyalg RSA -keysize 2048 -validity 10000
```

### Как получить `KEYSTORE_BASE64`

```bash
# Linux / macOS
base64 -w 0 release.keystore   # (на macOS: base64 -i release.keystore)

# Windows (PowerShell)
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.keystore"))
```

Скопируйте вывод целиком в значение секрета `KEYSTORE_BASE64`.

## Что происходит без ключа

Если секрет `KEYSTORE_BASE64` не задан, workflow всё равно соберёт APK,
но релизный APK будет **неподписанным** (в лог будет выведено предупреждение).
Такой APK нельзя установить без ручной подписи. Debug-APK подписывается
автоматически debug-ключом и устанавливается для тестирования.

## Локальная сборка с подписью

Те же переменные окружения читает `app/build.gradle.kts`:

```bash
export KEYSTORE_FILE=/absolute/path/to/release.keystore
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=xauplayer
export KEY_PASSWORD=...
./gradlew assembleRelease
```
