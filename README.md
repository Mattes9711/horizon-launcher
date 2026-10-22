# Smart-TV – a custom Android TV home screen

Own launcher for my Vestel Android TV 11 (formerly "Horizon"). Kotlin + Jetpack Compose for TV, no ads, no tracking, no internet permission.
Design language inspired by big-screen home screens (hero background, glass dock, parallax tiles) – original assets only.

## Remote
| Key | Action |
|---|---|
| Arrows | move between apps |
| OK | open app |
| Hold OK or MENU | context menu: move left/right, add/remove from top row, app info |
| Up on the top row | full-screen background (Down/OK/Back returns) |
| Home / Back | back to the first app |

## Build
GitHub Actions builds `app-release.apk` on every push (Actions tab → latest run → Artifacts → `horizon-apk`).

## Install on the TV (from the PC folder with `tv.sh`)
```
./tv.sh install-apk app-release.apk
```
Every build is signed with `app/horizon.keystore`, so updates keep the layout.

## Revert to the stock launcher
```
./tv.sh undo
```
or manually: `adb uninstall app.horizon.launcher` (and `adb shell pm enable com.google.android.tvlauncher` if it was disabled).

## Roadmap
1. Home screen core (Compose, grid, glass dock, parallax focus, DataStore) – **current**
2. Top shelf / hero content
3. Edit mode and folders
4. Control Center
5. App switcher
6. Screensaver (DreamService)
7. Settings, search, sounds
8. AirPlay mirroring (optional)
