# DNS Resolver Tester Android

Prototype Android app for filtering DNS tunnel resolvers from the real phone network.

The app does not reuse WhiteDNS code. It packages DNS tunnel client binaries
as native libraries, starts one client process per resolver, opens the
local SOCKS5 port, then checks real HTTPS traffic through that tunnel.

## What It Filters

- Resolver starts the tunnel client successfully.
- Local SOCKS5 port becomes reachable.
- HTTPS probe succeeds through the tunnel.
- Download test reaches the configured minimum KB/s.

Successful resolvers are written to:

```text
/data/data/su.alq.stormdnstester/files/good_resolvers.txt
/data/data/su.alq.stormdnstester/files/resolver_results.csv
```

The UI also has `Copy good` for copying the accepted resolver list.

The `Subscription URI` field accepts `stormdns://` and `masterdns://` links.
For `stormdns://`, WhiteDNS-style base64url JSON profile links are supported.
Both schemes also support simple query links like:

```text
stormdns://v.example.com?key=secret&method=1&resolvers=1.1.1.1,8.8.8.8
masterdns://v.example.com?encryption_key=secret&encryption_method=1
```

Resolver input accepts any of these formats:

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

Speed test profiles:

- Cloudflare 200 KB
- Speedtest.net page
- Yandex Internetometer
- Custom URL

`Parallel workers` controls how many resolvers are tested at the same time.
Each worker starts an isolated client process on its own local SOCKS port.
Keep it modest on phones: `2-4` is usually safer than very high values.

## Build

Open this directory in Android Studio and build the `app` module.

CLI debug build:

```bash
export ANDROID_HOME=/opt/android-sdk
./gradlew assembleDebug
```

Debug outputs:

```text
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
app/build/outputs/apk/debug/app-universal-debug.apk
```

The project currently includes:

- `arm64-v8a/libstormdns_client.so`
- `armeabi-v7a/libstormdns_client.so`

To refresh client binaries:

```bash
python3 scripts/fetch_stormdns_binaries.py
```

## Current Server Defaults

Use these values in the app:

```text
Domain: v.alq.su
Encryption method: 1
Encryption key: paste the server key
```

Do not commit real production keys into the source tree.

## Notes

This is a test tool, not a VPN UI. It intentionally tests resolvers one-by-one
because a resolver that passes tunnel status checks can still fail real HTTP
traffic after rate limiting, MTU issues, or mobile-network DNS interception.
