# Android-Biblical-Month

An Android (non-root) app that lets you manage and display a **moon-sighting-based** “Biblical month” date system, with:

- **Today screen**: shows “Month X, Day Y, Year Z” based on recorded month starts.
- **Projected calendar view**: shows the current month and projects future months until you confirm actual starts.
- **Persistent notification**: optionally keeps today’s lunar date visible.
- **Home-screen widget**: shows today’s lunar date + Gregorian date.
- **Prompts on day 29/30**: asks if the **new moon was seen**; if confirmed, the app starts the next month.
- **Aviv barley prompt (12/29)**: asks if barley is aviv to decide **new year vs 13th month**.
- **Device calendar export**: exports month starts + feast days into a user-selected device calendar (with permissions).

## Project

- **Language/UI**: Kotlin + Jetpack Compose
- **Storage**: Room (month starts + year decisions) + DataStore (settings)
- **Scheduling**: WorkManager (periodic “tick” for prompts + notification/widget updates)

## Build / run (Cursor/terminal friendly)

The repo includes the **Gradle wrapper**, so you can build without Android Studio:

```bash
./gradlew assembleDebug
```

To install to a connected device:

```bash
./gradlew installDebug
```

## How it works (MVP)

1. Set an **anchor** (e.g., “Month 1 Day 1 = today”) from the Today tab.
2. On **day 29/30**, the app prompts you to confirm if the new moon was seen.
3. If confirmed, the app records the next month start (tomorrow), and the calendar updates.
4. Feast days are projected using simple rules, configurable in Settings (Firstfruits rule).
