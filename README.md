# KazeCode

Mobile IDE for Android (arm64-v8a, Android 11+). Kotlin + Jetpack Compose.

* Write & run **HTML / JS** (WebView preview + console), **Python** (embedded CPython via Chaquopy), **PHP** (built-in server, needs a PHP binary — see below)
* Open whole project folders, file tree, tabs, create / rename / delete
* Syntax highlighting, line numbers, auto-indent, bracket pairing, code completion + snippets, undo / redo, quick-symbol keys
* **pip**: search PyPI, install / uninstall pure-Python packages (+ dependencies)

## Build on GitHub
1. Create a repo and push this folder (`git init && git add . && git commit -m init && git push`).
2. Open **Actions → Build KazeCode APK** (runs on every push). Download the `KazeCode-arm64-v8a-debug` artifact → install the APK.

## Splash
Replace `app/src/main/res/drawable-nodpi/presplash.png` with your image (a placeholder is included).
Black background, image shown for 3 seconds.

## PHP runtime (optional)
Android has no PHP by default. Provide a **statically linked arm64 `php` CLI binary** and set a repository variable
`PHP_BINARY_URL` (Settings → Secrets and variables → Actions → Variables) pointing to a direct download link.
The workflow bundles it as `libphp.so`; running a `.php` file then starts `php -S 127.0.0.1:8088` and opens it in the preview.

## Notes
* pip installs pure-Python wheels only (no numpy / pandas / anything compiled).
* First launch: tap **Allow** on the home screen to grant "All files access" so folders can be opened.
