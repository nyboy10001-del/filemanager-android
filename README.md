# File Manager (Kotlin + Jetpack Compose)

A working Android file manager. All 20 screens from the design are implemented and wired to real file operations.
Visual design follows USWDS / GOV.UK patterns: high-contrast blue, 16sp+ text, yellow focus ring,
error summaries, plain-language messages, 48dp touch targets.

## Build on GitHub
1. Push this folder to a GitHub repo (branch `main`).
2. Open **Actions > Android CI**. It builds `app-debug.apk` and uploads it as an artifact.
No Gradle wrapper is needed: the workflow installs Gradle 8.9. To build locally, run `gradle assembleDebug`
(or `gradle wrapper` once, then `./gradlew assembleDebug`).

## Screens
| # | Screen | File |
|---|--------|------|
| 1 | Splash | `StartScreens.kt` |
| 2-4 | Onboarding | `StartScreens.kt` |
| 5 | Home / Files | `HomeScreens.kt` |
| 6 | Storage details | `HomeScreens.kt` |
| 7 | Search | `SearchScreen.kt` |
| 8-10 | Folder view, selection, actions sheet | `BrowserScreen.kt` |
| 11 | Create folder | `OperationScreens.kt` |
| 12 | Copy / Move | `OperationScreens.kt` |
| 13 | Compress (zip) | `OperationScreens.kt` |
| 14 | Share | `OperationScreens.kt` |
| 15 | File details | `DetailsScreen.kt` |
| 16-17 | Recent, Shared | `RecentSharedScreens.kt` |
| 18 | Settings | `SettingsHelpScreens.kt` |
| 20 | Help and feedback | `SettingsHelpScreens.kt` |

## Architecture
- `data/` repository (all file I/O on `Dispatchers.IO`, `Result` for errors), Room (search + share history), DataStore (settings)
- `ui/` Compose screens, one ViewModel per screen, shared components in `ui/components`
- Manual DI via `AppContainer`; single-activity Navigation Compose

## Notes
- Uses **All files access** (`MANAGE_EXTERNAL_STORAGE`). Fine for sideloading; Google Play needs a policy declaration.
- Set your real address in `SUPPORT_EMAIL` (`SettingsHelpScreens.kt`) and replace the placeholder terms text.
- Mockup items with no backend (Account, Cloud storage, Notifications) were left out.
