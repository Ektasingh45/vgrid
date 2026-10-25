-- =====================================================================
--  VGrid database schema (MySQL 8), version 2
--  The app runs this file automatically every time it connects.
--  "IF NOT EXISTS" means existing tables and data are never touched.
-- =====================================================================

CREATE TABLE IF NOT EXISTS app_meta (
    name  VARCHAR(50)  PRIMARY KEY,
    value VARCHAR(255) NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    id            INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(200) NOT NULL,             -- PBKDF2 hash, never the plain password
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Which cities have been loaded, from where, and when.
CREATE TABLE IF NOT EXISTS city_imports (
    city          VARCHAR(20) PRIMARY KEY,           -- 'pune', 'mumbai', ...
    source        VARCHAR(60) NOT NULL,              -- 'Open Charge Map' or 'Sample data'
    station_count INT         NOT NULL,
    imported_at   DATETIME    NOT NULL
);

CREATE TABLE IF NOT EXISTS stations (
    id            VARCHAR(20)  PRIMARY KEY,          -- 'OCM123456' (Open Charge Map) or 'ST01' (sample)
    city          VARCHAR(20)  NOT NULL,
    name          VARCHAR(100) NOT NULL,
    operator      VARCHAR(100) NOT NULL,
    address       VARCHAR(200) NOT NULL,
    area          VARCHAR(60)  NOT NULL,
    latitude      DOUBLE       NOT NULL,
    longitude     DOUBLE       NOT NULL,
    price_per_kwh DECIMAL(6,2) NOT NULL,
    rating        DECIMAL(2,1) NOT NULL DEFAULT 0,   -- 0 = no rating
    open_hours    VARCHAR(50)  NOT NULL,
    source        VARCHAR(60)  NOT NULL,             -- 'Open Charge Map #123456' or 'Sample data'
    INDEX idx_stations_city (city)
);

CREATE TABLE IF NOT EXISTS station_amenities (
    station_id VARCHAR(20) NOT NULL,
    position   INT         NOT NULL,                 -- keeps the display order
    amenity    VARCHAR(80) NOT NULL,
    PRIMARY KEY (station_id, position),
    FOREIGN KEY (station_id) REFERENCES stations(id) ON DELETE CASCADE
);

-- Only AVAILABLE / OUT_OF_SERVICE are stored. "Reserved now" and "In use" are worked out
-- live from the reservations and charging_sessions tables.
CREATE TABLE IF NOT EXISTS chargers (
    station_id VARCHAR(20) NOT NULL,
    code       VARCHAR(5)  NOT NULL,                 -- 'C1', unique only inside its station
    connector  ENUM('CCS2','TYPE_2','CHADEMO','BHARAT_DC001') NOT NULL,
    power_kw   DECIMAL(5,1) NOT NULL,
    status     ENUM('AVAILABLE','OUT_OF_SERVICE') NOT NULL,
    PRIMARY KEY (station_id, code),
    FOREIGN KEY (station_id) REFERENCES stations(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS reservations (
    id           INT AUTO_INCREMENT PRIMARY KEY,     -- shown in the app as 'VG-<id>'
    user_id      INT         NOT NULL,
    station_id   VARCHAR(20) NOT NULL,
    charger_code VARCHAR(5)  NOT NULL,
    booking_date DATE        NOT NULL,
    slot_start   TIME        NOT NULL,               -- 10:00 means the 10:00 - 11:00 slot
    status       ENUM('CONFIRMED','ACTION_REQUIRED','COMPLETED','CANCELLED') NOT NULL,
    note         VARCHAR(255) NULL,
    created_at   DATETIME    NOT NULL,
    -- 1 while the booking is active, NULL once completed or cancelled (MySQL fills it in itself).
    active_flag  TINYINT GENERATED ALWAYS AS
                 (IF(status IN ('CONFIRMED','ACTION_REQUIRED'), 1, NULL)) STORED,
    -- No double booking: only ONE active booking per charger, date and slot.
    -- Unique keys ignore NULLs, so a cancelled slot can be booked again.
    UNIQUE KEY one_active_booking_per_slot (station_id, charger_code, booking_date, slot_start, active_flag),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (station_id, charger_code) REFERENCES chargers(station_id, code)
) AUTO_INCREMENT = 1001;

-- "Start charging" / "Done" check-ins.
CREATE TABLE IF NOT EXISTS charging_sessions (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    user_id        INT         NOT NULL,
    station_id     VARCHAR(20) NOT NULL,
    charger_code   VARCHAR(5)  NOT NULL,
    reservation_id VARCHAR(12) NULL,                 -- the user's own booking for this hour, if any
    started_at     DATETIME    NOT NULL,
    ended_at       DATETIME    NULL,                 -- NULL while charging
    active_flag    TINYINT GENERATED ALWAYS AS (IF(ended_at IS NULL, 1, NULL)) STORED,
    UNIQUE KEY one_session_per_charger (station_id, charger_code, active_flag),
    UNIQUE KEY one_session_per_user (user_id, active_flag),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (station_id, charger_code) REFERENCES chargers(station_id, code)
);

CREATE TABLE IF NOT EXISTS notifications (
    id             INT AUTO_INCREMENT PRIMARY KEY,
    user_id        INT          NOT NULL,
    type           ENUM('RESERVATION_CONFIRMED','RESERVATION_CANCELLED','CHARGER_UNAVAILABLE',
                        'ALTERNATIVE_SUGGESTION','INFO') NOT NULL,
    title          VARCHAR(150) NOT NULL,
    message        VARCHAR(500) NOT NULL,
    created_at     DATETIME     NOT NULL,
    reservation_id VARCHAR(12)  NULL,                -- 'VG-1001'
    is_read        BOOLEAN      NOT NULL DEFAULT FALSE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS activities (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    user_id    INT          NOT NULL,
    title      VARCHAR(150) NOT NULL,
    detail     VARCHAR(255) NOT NULL,
    created_at DATETIME     NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS user_settings (
    user_id             INT PRIMARY KEY,
    city                VARCHAR(20)  NOT NULL,
    home_location       VARCHAR(60)  NOT NULL,
    preferred_connector ENUM('CCS2','TYPE_2','CHADEMO','BHARAT_DC001') NOT NULL,
    preferred_power_kw  DECIMAL(5,1) NOT NULL,
    max_distance_km     INT          NOT NULL,
    max_wait_min        INT          NOT NULL,
    notify_reservation  BOOLEAN      NOT NULL,
    notify_charger      BOOLEAN      NOT NULL,
    notify_alternatives BOOLEAN      NOT NULL,
    email_updates       BOOLEAN      NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- ---------------------------------------------------------------------
--  OCPP: chargers that report their own status to the VGrid OCPP server
-- ---------------------------------------------------------------------

-- One row per OCPP charge point; each is linked to one VGrid charger.
CREATE TABLE IF NOT EXISTS ocpp_chargers (
    ocpp_id      VARCHAR(40) PRIMARY KEY,             -- the id in ws://host:8887/ocpp/<ocpp_id>
    station_id   VARCHAR(20) NOT NULL,
    charger_code VARCHAR(5)  NOT NULL,
    connected    BOOLEAN     NOT NULL DEFAULT FALSE,  -- WebSocket open right now
    status       VARCHAR(30) NULL,                    -- last OCPP status: Available, Charging, Faulted, ...
    error_code   VARCHAR(40) NULL,
    vendor       VARCHAR(100) NULL,                   -- from BootNotification
    model        VARCHAR(100) NULL,
    last_seen    DATETIME    NULL,                    -- last message (heartbeats keep this fresh)
    UNIQUE KEY one_ocpp_per_charger (station_id, charger_code),
    FOREIGN KEY (station_id, charger_code) REFERENCES chargers(station_id, code) ON DELETE CASCADE
);

-- Charging sessions reported by OCPP chargers (StartTransaction / StopTransaction).
CREATE TABLE IF NOT EXISTS ocpp_transactions (
    id           INT AUTO_INCREMENT PRIMARY KEY,      -- the transactionId sent back to the charger
    ocpp_id      VARCHAR(40) NOT NULL,
    id_tag       VARCHAR(40) NULL,                    -- the RFID card / app id that started it
    meter_start  INT         NOT NULL,                -- Wh
    meter_last   INT         NOT NULL,                -- Wh, updated by MeterValues
    started_at   DATETIME    NOT NULL,
    stopped_at   DATETIME    NULL,                    -- NULL while charging
    FOREIGN KEY (ocpp_id) REFERENCES ocpp_chargers(ocpp_id) ON DELETE CASCADE
);

INSERT IGNORE INTO app_meta (name, value) VALUES ('schema_version', '2');
