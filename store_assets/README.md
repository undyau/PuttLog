# Play Store submission assets

Prepared for PuttLog (`com.undy.puttlog`). Everything under this folder plus
`docs/privacy.html` and the release build config in `app/build.gradle.kts` was
put together to get this app ready to submit — the remaining steps below need
a Google Play Developer account and can't be done from here.

## What's ready

- **Signed release bundle**: `app/build/outputs/bundle/release/app-release.aab`
  — build with `./gradlew bundleRelease`. Signed with `app/puttlog-release.jks`
  (gitignored — **back this file and `app/keystore.properties` up somewhere
  safe**, e.g. a password manager or encrypted drive; without them you can't
  ship an update under the same app listing).
- **Store icon**: `app/src/main/ic_launcher-playstore.png` (512x512, meets spec).
- **Screenshots**: `store_assets/screenshots/` — main screen with stats,
  Settings (showing the new Audio input toggle), and main screen with voice
  input enabled.
- **Listing copy**: `store_assets/listing/short_description.txt` (79/80 chars)
  and `full_description.txt` (1038/4000 chars).
- **Privacy policy**: `docs/privacy.html`. Play Console requires a live URL
  for this — easiest path is GitHub Pages:
  1. On GitHub: repo Settings → Pages → Source = "Deploy from a branch",
     branch `main`, folder `/docs`.
  2. It'll publish at `https://undyau.github.io/PuttCount/privacy.html`.
  3. Paste that URL into Play Console's "Privacy policy" field.
- **Data safety / content rating answers**: `store_assets/listing/data_safety_notes.md`.

## What's still needed (manual, in Play Console)

1. A Google Play Developer account ($25 one-time fee) if you don't have one.
2. Create the app listing, upload `app-release.aab`.
3. Fill in Store listing using the drafted copy + screenshots above.
4. Complete Data safety and Content rating questionnaires (answers drafted above).
5. Set pricing/countries, add a feature graphic (1024x500) — not yet created;
   say the word if you want one designed from the app icon.
6. Submit for review.

## Regenerating the release build later

```
JAVA_HOME="<path to a JDK>" ./gradlew bundleRelease
```

Bump `versionCode` (and usually `versionName`) in `app/build.gradle.kts` before
each new release upload — Play Console rejects re-uploading the same versionCode.
