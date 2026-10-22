# Horizon – your own Android TV home screen

A big blurred background of the selected app, one row of favourites, all other apps below it, a clock in the top right. No ads, no recommendations, no tracking, and no internet permission.

**On the remote**
- Arrows move between tiles, **OK** opens an app.
- **Hold OK** (or press the Menu key) on a tile to add or remove a favourite, move it left or right, or open App info.
- **Home** or **Back** jumps back to the first favourite.

## 1. Build the APK in the cloud (GitHub, free, about 5 min)
1. Open your empty repository `horizon-launcher` on github.com and click **uploading an existing file**.
2. Drag in **everything inside** this `launcher` folder, including `.github`, `app`, `gradle`, `gradlew` and the other files. Then click **Commit changes**.
   - If Windows hides `.github`: in File Explorer, go to View → Show → Hidden items.
3. Go to the **Actions** tab. "Build APK" starts on its own; wait for the green tick (2–4 min).
4. Click the run, then download **horizon-apk** under *Artifacts*. Unzip it to get `app-release.apk`.

(Alternative: open this folder in Android Studio → Build → Build APK(s).)

## 2. Install it on the TV
Put `app-release.apk` into the `Neuer Ordner` folder, then run:
```
./tv.sh install-apk app-release.apk
./tv.sh set-home
```
`set-home` makes Horizon the preferred home screen and presses Home so you see it. The TV's own launcher stays installed and enabled.
If the TV keeps showing its own home screen, stop and ask Claude. The next step changes more and needs your OK.

## Undo
`./tv.sh undo` restores the old home screen and uninstalls Horizon (and reverts the other changes).

## Updating
Change the code, upload it to GitHub again, download the new APK and run `./tv.sh install-apk app-release.apk`. Your favourites are kept because every build is signed with the same key (`app/horizon.keystore`).
