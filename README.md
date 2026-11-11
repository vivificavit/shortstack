# shortstack

An Android app for parents to track their kids' allowance, spending and savings goals. Everything stays on the phone: no account, no ads, no network access and no permissions.

## What it does

- **Ledger**: one balance per kid, with income and expense entries sorted into categories with icons. Deleting an entry can be undone.
- **Goals**: savings goals, each with a target and an optional buy-by date. Money set aside for goals stays in the total balance but no longer counts as pocket money. Income can send part of an entry to a goal. Spending more than the pocket money takes the rest from a goal you pick. Goals can be marked as bought.
- **Repeating entries**: weekly, every 2 weeks or monthly income and expenses, such as an allowance or a subscription. Due entries are added automatically, including ones missed while the app was closed, and can set aside a fixed amount for a goal each time.
- **Trends**: a chart of the balance and the money saved for goals over time, plus income and expenses by category for a chosen date range.
- **Backup and export**: export everything to a JSON backup file and restore it later, or export entries as a CSV spreadsheet.
- **Currency**: US, Canadian, Australian or New Zealand dollars, British pounds or euros, chosen from the menu. A new install starts with the one for the phone's region. The choice changes the symbol only; amounts aren't converted. To offer another two-decimal currency, add it to `AppCurrency` in `util/Money.kt` with a name in `strings.xml`.

## Building

Requirements: Android Studio (current stable) with the Android SDK for API 37. Gradle downloads the JDK it needs automatically.

```bash
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # unit tests (Robolectric, no emulator needed)
./gradlew lintDebug              # lint
```

CI (`.github/workflows/tests.yml`) runs the tests, lint, a debug build and an unsigned release (R8) build on every push and pull request.

## Releasing to Google Play

1. **Bump the version** in `app/build.gradle.kts`. `versionCode` must go up by 1 for every upload, and `versionName` is what users see.
2. **Set up signing once.** Copy `keystore.properties.example` to `keystore.properties` and fill it in. Keep the `.jks` file outside the repo and back it up. Git ignores both files. You can also use Android Studio's **Build → Generate Signed App Bundle** instead.
3. **Build the bundle:**
   ```bash
   ./gradlew bundleRelease
   ```
   The bundle is written to `app/build/outputs/bundle/release/app-release.aab`.
4. **Upload the bundle** to the track in Play Console. Then upload the R8 mapping file from `app/build/outputs/mapping/release/mapping.txt` under that release's App bundle explorer → Downloads, so crash reports show readable stack traces.
5. **Release notes** for each version go in [`docs/release-notes.md`](docs/release-notes.md). Paste them into the release's "What's new" box, which has a 500-character limit.

Release builds use R8 shrinking (`isMinifyEnabled` and `isShrinkResources`). If you add code that's reached by reflection, add a keep rule to `app/proguard-rules.pro`. Check an R8 build on a device before each release.

## Data and privacy

- Data is stored in a Room database (`allowance_tracker_db`, the app's original name) and a small DataStore preferences file (`settings`) on the device.
- `android:allowBackup="true"` is on, so Android may include the app's data in the user's Google backup and in device-to-device transfers. The privacy policy says so.
- The privacy policy is in [`docs/privacy-policy.md`](docs/privacy-policy.md). Play Console needs a public URL for it.

## Changing the database

The schema is at version 7, and each version's schema is exported to `app/schemas/`. **Never edit a schema version that has been released or installed anywhere.** Every change needs a new version number, a `Migration` in `AppDatabase.kt` added to `ALL_MIGRATIONS`, and a case in `MigrationTest`.

The backup file has its own format number in `BackupCodec.kt`, currently 4. Raise it when the backup format changes. Older app versions refuse files with a newer number, and newer versions must keep restoring older files.

## Project layout

```
app/src/main/java/com/vivificavit/shortstack/
  data/model        Room entities: entries, kids, categories, goals, goal moves, repeating entries
  data/local        AppDatabase, migrations, DAOs, settings store (selected kid, currency)
  data/backup       JSON backup and restore
  data/export       CSV export
  repository        all database writes, including goal rebalancing
  ui/viewmodel      TrackerViewModel and the screen state
  ui/screens        the Ledger, Goals and Trends tabs and the main screen
  ui/components     sheets, dialogs and charts
  util              pure logic (money, goals, dates, repeating schedules, balance history), unit-tested
```
