# VGrid — Smart EV Charging Management System (Frontend)

A JavaFX desktop frontend for the VGrid PBL project. It runs **completely offline**:
there is no database, backend, API or internet connection. All stations, congestion values,
reservations and alerts are **simulated sample data kept in memory** (Java `ArrayList`s).

## Requirements

- JDK 17 or 21
- IntelliJ IDEA (Community or Ultimate). IntelliJ's bundled Maven downloads JavaFX automatically.

## Quickest way: double-click

Double-click **`run-vgrid.bat`** in the project folder. It compiles the code and opens the app.
It uses the JavaFX jars in `lib/`, so it needs no Maven and no internet, only JDK 17+.

## Run in IntelliJ IDEA

1. **File → Open…** and select this folder (the one containing `pom.xml`). Choose **Open as Project**.
2. Wait for Maven to finish importing. The first time, it downloads JavaFX 21 (internet needed once).
3. Check **File → Project Structure → Project → SDK** is set to JDK 17 or 21.
4. Choose **one** of these ways to run:
   - **Easiest:** open `src/main/java/com/vgrid/Launcher.java` and click the green ▶ next to `main`.
   - **Maven:** open the *Maven* tool window → `vgrid-frontend` → *Plugins* → *javafx* → double-click **javafx:run**.

> Run `Launcher`, not `MainApp`. Running `MainApp` directly from the classpath gives
> "JavaFX runtime components are missing". `Launcher` exists only to avoid that error.

From a terminal with Maven installed you can also run: `mvn clean javafx:run`

## Demo script (for the viva)

1. **Dashboard:** summary cards (click them), recommended stations, next reservation and recent activity.
2. **Find Stations:** type `Baner` in search, change the filters, use **Reset Filters**, open **View Details**.
3. **Reserve Charger:** pick a charger, a date and a free time slot, then **Confirm Reservation**. A demo ID
   such as `VG-1043` appears, and the booking shows up in **My Reservations**.
4. **Smart Recommendations:** select **Find Best Station**. The top result explains *why* it was chosen.
   Change the arrival time to 18:00 (peak hour) and compare the results.
5. **My Reservations:** `VG-1038` needs attention. Select **Find an Alternative**, pick a station and
   **Confirm Change**. On a confirmed booking, *Find an Alternative* first simulates a charger failure.
6. **Alerts:** each alert opens the related screen. Try *Mark All as Read*, *Clear Read* and dismiss.
7. **Settings:** change home location, connector or distance, then **Save Changes**. The dashboard and
   distances update. **Reset Demo Data** restores everything.

## Project structure

```
src/main/java/com/vgrid/
├── Launcher.java              ← run this from IntelliJ
├── MainApp.java               JavaFX Application: creates the window and loads style.css
├── model/                     Plain data classes: Station, Charger, Reservation, AppNotification,
│                              UserSettings, Location, enums, RecommendationRequest/Result
├── data/DemoDataStore.java    ALL sample data in ArrayLists (the only class that owns data)
├── service/                   Logic, no UI code:
│   ├── StationService         distance, search & filters, simulated wait/congestion by time of day
│   ├── RecommendationService  rule-based ranking + "why this station" explanation
│   ├── ReservationService     create / cancel / simulate failure / switch to alternative
│   └── NotificationService    alerts and recent-activity log
├── controller/
│   ├── AppController          navigation, dialogs, confirmations, toast messages
│   ├── Screen                 list of screens (titles + icons)
│   └── Refreshable            screens that redraw after data changes
├── view/                      One class per screen + Sidebar, TopBar, BaseView
│   ├── components/            Reusable UI: StationCard, SummaryCard, StatusBadge, EmptyState,
│   │                          ResponsiveGrid, Icons (SVG), Ui (factory helpers)
│   └── dialogs/               StationDetails, Reservation form, Success, ReservationDetails, Confirm
└── util/Format.java           Text formatting (km, kW, dates, "5 min ago")
src/main/resources/css/style.css   The complete design system (colours, cards, buttons, inputs…)
```

The UI is built in Java code (no FXML), so every screen is a single readable class.

## How the recommendation ranking works (no machine learning)

1. **Exclude** stations without a working charger of the chosen connector and minimum power,
   stations beyond the distance limit, and stations above the waiting-time limit.
2. **Simulated forecast:** 08–11 and 17–21 are peak hours (wait × 1.6, congestion one level higher);
   22–06 is quiet (wait × 0.4, one level lower).
3. **Score out of 100:** `85 − 2.5×distance − 0.8×wait − congestion penalty (0/12/25)`
   `+ 8 if a compatible charger is free now (else −10) + up to 10 for higher power`.
4. Sort by score. The top result gets a plain-English reason, for example:
   *"This station matches your CCS2 connector requirement and has the shortest predicted wait."*

## Adding MySQL / JDBC later

Only `DemoDataStore` holds data, and screens never touch it directly. They call the services.
To integrate a database:

1. Create DAO classes (for example `StationDao` and `ReservationDao`) that use JDBC.
2. Make the services call those DAOs instead of `DemoDataStore`.
3. The screens, dialogs, components and CSS stay unchanged.
