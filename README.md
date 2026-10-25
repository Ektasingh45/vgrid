# VGrid — Smart EV Charging Management System

A JavaFX desktop app for EV drivers: pick your city, find compatible charging stations, get an explainable
ranking, book one-hour slots, check in while charging, and move your booking if a charger breaks.

- **Real stations** come from the **Open Charge Map API** (name, address, operator, location, connectors, power).
- **Accounts, bookings, check-ins, alerts and settings** are stored in **MySQL** through **JDBC** and **DAO** classes.
- **Live charger status** comes from what VGrid users do: bookings for the current hour, check-ins
  ("Start charging" / "Done") and problem reports. Open Charge Map has no live availability, and operators'
  own live data (OCPP) is not public.
- If MySQL is not available, the app runs in **offline mode** (same features, kept in memory, nothing saved).

## Requirements

- JDK 17 or 21
- MySQL 8 Community Server (for saved data)
- A free Open Charge Map API key (for real stations)
- Internet the first time each city is loaded

## Setup (once)

1. **Install MySQL 8 Community Server** (dev.mysql.com → MySQL Installer for Windows → "Developer Default",
   which also installs MySQL Workbench). Write down the root password.
2. **Get an Open Charge Map API key:** sign up at <https://openchargemap.org> and register an application in
   your profile. The site shows the key.
3. **Edit `db.properties`** in the project folder:
   ```
   db.password=<your MySQL root password>
   ocm.api.key=<your Open Charge Map key>
   ```
4. **Start the app** (below). The `vgrid` database and all tables (`src/main/resources/db/schema.sql`) are
   created automatically.

Without an API key, Pune uses 10 built-in sample stations and other cities cannot be loaded.
With `data.mode=memory` the app never uses MySQL.

## Run

**Double-click `run-vgrid.bat`** — it compiles and starts the app. JavaFX, the MySQL driver and Gson are in
`lib/`, so it needs no Maven, only JDK 17+.

**Second window** (two users side by side): double-click **`open-another-window.bat`** (it does not recompile).

**IntelliJ:** File → Open… → this folder → Open as Project, wait for Maven, then run
`src/main/java/com/vgrid/Launcher.java` (run `Launcher`, not `MainApp`).

## OCPP server (chargers that report their own status)

Real chargers report their status to their operator's server using **OCPP** (Open Charge Point Protocol).
VGrid has its own small **OCPP 1.6 server** so chargers can do the same with VGrid:

1. Start VGrid once with `run-vgrid.bat` (it compiles everything).
2. Double-click **`run-ocpp-server.bat`**. A console window opens; it connects to the same MySQL database and
   creates the **"VGrid OCPP Test Station"** (next to Baner, Pune) with four chargers:
   ```
   C1  ->  ws://localhost:8887/ocpp/VGRID-PUNE-C1      (C2, C3, C4 the same way)
   ```
3. Point a charger (or an OCPP 1.6 charger simulator) at one of those addresses, with the WebSocket
   sub-protocol `ocpp1.6`. Every message and reply is printed in the console window.
4. In VGrid, open the test station: each charger shows **Live · OCPP** (or **Offline**), its last reported status
   and the energy delivered. Changes appear in every VGrid window within 15 seconds.

| Charger sends | VGrid does |
|---|---|
| `BootNotification` | Accepts the charger (heartbeat every 60 s) |
| `Heartbeat` | Keeps it "online" (offline after 3 minutes of silence) |
| `StatusNotification` Available | Charger **Available** (and back in service after a fault) |
| `StatusNotification` Preparing / Charging / SuspendedEV / SuspendedEVSE / Finishing | Charger **In use** |
| `StatusNotification` Faulted / Unavailable | Charger **Out of service**; every driver booked on it gets an alert + alternatives |
| `StartTransaction` / `MeterValues` / `StopTransaction` | Records the charging session and the kWh delivered |
| `Authorize` | Accepts every idTag (demo server) |
| anything else | `CALLERROR NotImplemented` |

Settings in `db.properties`: `ocpp.port` (default 8887) and `ocpp.test.city` (default pune).

### Charger simulator (pretend chargers for the demo)

We have no real charger, so **`run-charger-simulator.bat`** opens a window that pretends to be the four chargers
C1–C4. Each one opens its own WebSocket to the OCPP server and sends exactly the messages a real charger sends,
so the server cannot tell the difference:

```
Simulator (pretend chargers) --OCPP--> OCPP server --> MySQL --> VGrid app (every 15 s)
```

| In the simulator | Messages sent | VGrid shows |
|---|---|---|
| **Connect all** | `BootNotification`, `StatusNotification Available`, then a `Heartbeat` every 60 s | **Available · Live · OCPP** |
| **Plug in & charge** (battery kWh, now %, target %) | `Preparing`, `StartTransaction`, `Charging`, `MeterValues` every few seconds | **In use**, kWh delivered |
| *(nothing, the target is reached)* | `StopTransaction`, `Finishing`, `Available` | **Available** again by itself |
| **Unplug now** | `StopTransaction`, `Available` | **Available** |
| **Fault** / **Fixed** | `StatusNotification Faulted` (GroundFailure) / `Available` | **Out of service** + alerts / back in service |
| **Disconnect** | closes the WebSocket | **Offline** |

Charging time = (target % − battery %) × battery kWh ÷ charger kW. With **Demo speed** on, 1 second is
1 simulated minute (a 22-minute charge takes 22 seconds). Every message appears in the simulator's log and in the
server console.

**Demo:** start `run-ocpp-server.bat`, then `run-vgrid.bat` (sign in, open the "VGrid OCPP Test Station"),
then `run-charger-simulator.bat` → **Connect all** → **Plug in & charge** on C1 and watch it charge and free
itself; press **Fault** on a charger someone booked to show the alert. In VS Code these are the tasks
"Run OCPP server", "Run VGrid" and "Run charger simulator" (Terminal → Run Task…).

## First use

1. The login page's status pill should say **Connected · MySQL**.
2. **Create an account.** There are no built-in accounts; your password is stored as a salted PBKDF2 hash.
3. Your city starts as **Pune**; its real stations are downloaded on first sign-in.
4. **Settings → City** to switch (Pune, Mumbai, Delhi NCR, Bengaluru, Hyderabad, Chennai). A new city is
   downloaded the first time, then read from MySQL. **Refresh Stations** downloads the latest data again.

## Demo script (for the viva)

1. Show the login page (status pill), create an account, sign in.
2. **Dashboard / Find Stations:** real stations of your city; open **View Details** — the data source line
   and the Open Charge Map credit show where each value comes from.
3. **Reserve Charger:** pick a charger, date and free slot → `VG-1001`. Close the app, reopen, sign in: still there.
4. **Smart Recommendations:** explainable ranking; change the arrival time to 18:00 (peak) and compare.
5. **Two users:** open a second window with `open-another-window.bat` and create a second account.
   - Both pick the **same free slot** and confirm: only one booking succeeds (database UNIQUE key).
   - In window 2 open a station and press **Start charging**: within 15 seconds window 1 shows that charger
     as **In use**, and the station's congestion goes up. Press **Done** to free it.
   - In window 2 press **Report problem** on the charger window 1 booked: window 1 gets an alert and its
     booking needs action → **Find an Alternative** → **Confirm Change** (slot checked in a transaction).
6. **Settings → City → Mumbai → Save:** real Mumbai stations are downloaded and shown.

## How live status and congestion work

| Charger shows | Because |
|---|---|
| Out of service | Open Charge Map lists it as not operational, or a driver pressed **Report problem** (anyone can press **It works now**) |
| In use | A driver pressed **Start charging** (ends with **Done**, or automatically after 1 hour) |
| Reserved now | Someone has a booking on it for the current hour |
| Available | None of the above |

**Congestion** = chargers reserved or in use ÷ working chargers: under 50% low, 50–80% moderate, above 80% high.
For a later arrival time the time-of-day rule adjusts it (peak hours 8–11 and 17–21 one level up, nights one
level down). Open windows re-read the database every 15 seconds.

## Project structure

```
src/main/java/com/vgrid/
├── Launcher.java              ← run this from IntelliJ
├── MainApp.java               login page → connect to MySQL (background) → load the user's city → main app
├── model/                     Station, Charger, Reservation, User, UserSettings, City, ChargingSession, enums …
├── dao/                       DAO interfaces (User, Station, Reservation, Charging, Notification, Settings) + DataLayer
│   └── jdbc/                  MySQL: Database (connection + transactions), Jdbc*Dao (all SQL), MySqlDataLayer
├── data/
│   ├── AppConfig              reads db.properties
│   ├── DataLayerFactory       picks MySQL or offline mode
│   ├── OpenChargeMapClient    HTTPS request + Gson JSON parsing → Station objects
│   ├── DemoDataStore          offline mode: the same DAO interfaces kept in memory
│   └── SampleData             10 Pune stations used only when Open Charge Map is not available
├── service/                   logic, no UI and no SQL:
│   ├── AuthService            sign-in / sign-up, password hashing
│   ├── StationImportService   loads each city once (Open Charge Map, or sample fallback for Pune)
│   ├── StationService         per-city stations, live status, Haversine distances, search, forecast
│   ├── RecommendationService  rule-based ranking + "why this station" explanation
│   ├── ReservationService     book / cancel / report problem / switch (with slot checks)
│   ├── ChargingService        Start charging / Done
│   ├── NotificationService    alerts (also to other users) and activity log
│   ├── SettingsService        preferences incl. city
│   └── UserSession            the signed-in user and their settings
├── ocpp/                      OCPP 1.6 server (run-ocpp-server.bat):
│   ├── OcppServerMain         starts it: reads db.properties, connects to MySQL, creates the test station
│   ├── OcppServer             WebSocket server, ws://host:8887/ocpp/<chargePointId>, sub-protocol ocpp1.6
│   └── OcppHandler            reads each OCPP message, updates MySQL, builds the reply
├── simulator/                 charger simulator (run-charger-simulator.bat):
│   ├── SimulatedCharger       one pretend charger: WebSocket client, OCPP messages, charging maths
│   ├── ChargerSimulatorApp    the window with four charger cards and the message log
│   └── SimulatorLauncher      run this from IntelliJ
├── controller/AppController   navigation, dialogs, background loading, 15-second live refresh, sign-out
├── view/                      LoginView + one class per screen, Sidebar, TopBar, components/, dialogs/
└── util/                      Format, PasswordHasher (PBKDF2), Geo (Haversine)
src/main/resources/db/schema.sql   all MySQL tables
db.properties                      MySQL connection + Open Charge Map key
```

```
Screens → AppController → Services → DAO interfaces → Jdbc*Dao → MySQL
                              │                    └→ DemoDataStore (offline)
                              └→ StationImportService → OpenChargeMapClient → Open Charge Map API (HTTPS/JSON)
```

### Database rules that keep data safe

- **No double booking:** UNIQUE key on (station, charger, date, slot, `active_flag`); `active_flag` is a
  generated column (1 while active, NULL when completed/cancelled, so a released slot can be booked again).
- **One check-in per charger and per user:** two UNIQUE keys on `charging_sessions`.
- **Transactions:** switching a booking (lock rows with `SELECT … FOR UPDATE`, check the slot, update) and
  reporting a problem (mark the charger out of service + flag every affected booking) commit or roll back as one.
- **Foreign keys** link bookings, sessions, alerts and settings to users, and chargers to stations.
- All SQL uses `PreparedStatement` (protects against SQL injection).

## Ranking (no machine learning)

Score out of 100: `85 − 2.5×distance − 0.8×wait − congestion penalty (0/12/25)
+ 8 if a compatible charger is free (else −10) + up to 10 for higher power`. Distances use the Haversine
formula on real coordinates × 1.25 for roads.

## Known limitations

- No live data from operators' chargers (they send OCPP only to their own servers); live status reflects VGrid
  users' bookings, check-ins and reports, plus chargers connected to VGrid's own OCPP server.
- The OCPP server implements the core of OCPP 1.6 only (no reservations sent to chargers, remote start/stop,
  firmware updates or smart charging), accepts every idTag, and uses plain `ws://` without charger passwords.
- Open Charge Map has no prices for most stations, opening hours or ratings: prices are estimates.
- Each app window connects to MySQL directly with the password from `db.properties` — fine on one laptop or a
  lab network; a real product would put a server (e.g. Spring Boot REST API) in between.
- Email updates are not sent.

Station data © Open Charge Map contributors, licensed CC BY 4.0.
