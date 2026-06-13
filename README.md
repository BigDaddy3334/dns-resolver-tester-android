# DNS Resolver Tester Android

Прототип Android-приложения для проверки DNS tunnel resolver'ов из реальной
мобильной или Wi-Fi сети телефона.

Приложение не использует код WhiteDNS. Оно упаковывает бинарники DNS tunnel
клиента как native libraries, запускает отдельный процесс клиента для каждого
resolver'а, открывает локальный SOCKS5-порт и проверяет реальный HTTPS-трафик
через этот туннель.

## Что Проверяется

- Resolver успешно запускает tunnel client.
- Локальный SOCKS5-порт становится доступен.
- HTTPS probe проходит через туннель.
- Download test достигает заданной минимальной скорости в KB/s.

Успешные resolver'ы сохраняются в:

```text
/data/data/su.alq.stormdnstester/files/good_resolvers.txt
/data/data/su.alq.stormdnstester/files/resolver_results.csv
```

В интерфейсе также есть кнопка `Copy good` для копирования списка рабочих
resolver'ов.

Поле `Subscription URI` принимает ссылки `stormdns://` и `masterdns://`.
Для `stormdns://` поддерживаются WhiteDNS-style base64url JSON profile links.
Обе схемы также поддерживают простой query-формат:

```text
stormdns://v.example.com?key=secret&method=1&resolvers=1.1.1.1,8.8.8.8
masterdns://v.example.com?encryption_key=secret&encryption_method=1
```

Поле resolver'ов принимает любой из этих форматов:

```text
1.1.1.1
8.8.8.8
9.9.9.9
```

```text
1.1.1.1, 8.8.8.8, 9.9.9.9
```

```text
1.1.1.1; 8.8.8.8 9.9.9.9
```

Профили speed test:

- Cloudflare 200 KB
- Speedtest.net page
- Yandex Internetometer
- Custom URL

`Parallel workers` задаёт количество resolver'ов, которые проверяются
одновременно. Каждый worker запускает изолированный процесс клиента на своём
локальном SOCKS-порту. На телефонах лучше не завышать это значение: `2-4`
обычно безопаснее, чем очень высокие значения.

## Сборка

Откройте директорию проекта в Android Studio и соберите модуль `app`.

Debug-сборка из CLI:

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew assembleDebug
```

Выходные debug APK:

```text
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
app/build/outputs/apk/debug/app-universal-debug.apk
```

Сейчас проект включает:

- `arm64-v8a/libstormdns_client.so`
- `armeabi-v7a/libstormdns_client.so`

Чтобы обновить клиентские бинарники:

```bash
python3 scripts/fetch_stormdns_binaries.py
```

Не коммитьте реальные production keys в исходный код.

## Примечания

Это тестовый инструмент, а не полноценный VPN UI. Он специально проверяет
resolver'ы по одному, потому что resolver, который проходит статусные проверки
туннеля, всё равно может не пропускать реальный HTTP-трафик из-за rate limiting,
MTU-проблем или DNS-перехвата в мобильной сети.
