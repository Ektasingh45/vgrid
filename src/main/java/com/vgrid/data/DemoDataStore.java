package com.vgrid.data;

import com.vgrid.dao.*;
import com.vgrid.model.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;

/**
 * Offline mode: keeps all data in memory using ArrayLists. It implements the same DAO interfaces as
 * the MySQL classes and behaves like a database (it stores rows and hands out copies), so the services
 * cannot tell the difference. Used when db.properties says data.mode=memory or MySQL cannot be reached.
 * Everything is lost when the app closes.
 */
public final class DemoDataStore implements DataLayer, UserDao, StationDao, ReservationDao, ChargingDao,
        NotificationDao, SettingsDao {

    /** One reservation "row", like a line in the reservations table. */
    private static final class Row {
        final int number;
        final int userId;
        String stationId;
        String chargerId;
        final LocalDate date;
        final String slot;
        ReservationStatus status;
        String note;
        final LocalDateTime createdAt;

        Row(int number, int userId, String stationId, String chargerId, LocalDate date, String slot,
            LocalDateTime createdAt) {
            this.number = number;
            this.userId = userId;
            this.stationId = stationId;
            this.chargerId = chargerId;
            this.date = date;
            this.slot = slot;
            this.status = ReservationStatus.CONFIRMED;
            this.createdAt = createdAt;
        }

        boolean active() {
            return status == ReservationStatus.CONFIRMED || status == ReservationStatus.ACTION_REQUIRED;
        }

        LocalDateTime slotEnd() {
            return date.atTime(Reservation.slotStart(slot)).plusHours(1);
        }

        String id() {
            return "VG-" + number;
        }
    }

    private final List<Account> accounts = new ArrayList<>();
    private final Map<String, List<Station>> stationsByCity = new HashMap<>();
    private final Map<String, CityImport> imports = new HashMap<>();
    private final List<Row> reservations = new ArrayList<>();
    private final List<ChargingSession> sessions = new ArrayList<>();
    private final Map<Integer, List<AppNotification>> notifications = new HashMap<>();
    private final Map<Integer, List<ActivityItem>> activities = new HashMap<>();
    private final Map<Integer, UserSettings> settings = new HashMap<>();
    private int nextUserId = 1;
    private int nextReservationNumber = SampleData.FIRST_RESERVATION;
    private int nextNotificationId = 1;

    // ------------------------------------------------------------------ DataLayer

    @Override public UserDao users() { return this; }
    @Override public StationDao stations() { return this; }
    @Override public ReservationDao reservations() { return this; }
    @Override public ChargingDao charging() { return this; }
    @Override public NotificationDao notifications() { return this; }
    @Override public SettingsDao settings() { return this; }
    /** The OCPP server needs MySQL, so offline mode never has OCPP chargers. */
    @Override public OcppDao ocpp() { return Map::of; }
    @Override public boolean isDatabase() { return false; }
    @Override public String description() { return "Offline mode · data kept in memory"; }

    // ------------------------------------------------------------------ users

    @Override
    public Optional<Account> findAccount(String email) {
        return accounts.stream().filter(a -> a.user().email().equals(email)).findFirst();
    }

    @Override
    public User createUser(String name, String email, String passwordHash) {
        if (findAccount(email).isPresent()) {
            throw new ConflictException("An account with this email already exists.");
        }
        User user = new User(nextUserId++, name, email);
        accounts.add(new Account(user, passwordHash));
        return user;
    }

    // ------------------------------------------------------------------ stations

    @Override
    public List<Station> findStations(String cityId) {
        return stationsByCity.getOrDefault(cityId, List.of()).stream()
                .sorted(Comparator.comparing(Station::getName))
                .map(DemoDataStore::copy).toList();
    }

    @Override
    public Optional<Station> findStation(String stationId) {
        return stored(stationId).map(DemoDataStore::copy);
    }

    @Override
    public void saveStations(String cityId, List<Station> stations, String source) {
        Set<String> newIds = new HashSet<>();
        List<Station> list = new ArrayList<>();
        for (Station s : stations) {
            newIds.add(s.getId());
            list.add(copy(s));
        }
        // Keep old stations that a booking or check-in still points to.
        for (Station old : stationsByCity.getOrDefault(cityId, List.of())) {
            boolean used = reservations.stream().anyMatch(r -> r.stationId.equals(old.getId()))
                    || sessions.stream().anyMatch(s -> s.stationId().equals(old.getId()));
            if (!newIds.contains(old.getId()) && used) {
                list.add(old);
            }
        }
        stationsByCity.put(cityId, list);
        imports.put(cityId, new CityImport(cityId, source, stations.size(), LocalDateTime.now()));
    }

    @Override
    public Optional<CityImport> findImport(String cityId) {
        return Optional.ofNullable(imports.get(cityId));
    }

    @Override
    public void updateChargerStatus(String stationId, String chargerId, ChargerStatus status) {
        stored(stationId).map(s -> s.findCharger(chargerId)).ifPresent(c ->
                c.setStatus(status == ChargerStatus.OUT_OF_SERVICE ? ChargerStatus.OUT_OF_SERVICE : ChargerStatus.AVAILABLE));
    }

    private Optional<Station> stored(String stationId) {
        return stationsByCity.values().stream().flatMap(List::stream)
                .filter(s -> s.getId().equals(stationId)).findFirst();
    }

    /** A separate copy, like a fresh row from a database, so live statuses never change the stored data. */
    private static Station copy(Station s) {
        List<Charger> chargers = new ArrayList<>();
        s.getChargers().forEach(c -> chargers.add(new Charger(c.getId(), c.getConnectorType(), c.getPowerKw(), c.getStatus())));
        return new Station(s.getId(), s.getCityId(), s.getName(), s.getOperator(), s.getAddress(), s.getArea(),
                s.getLatitude(), s.getLongitude(), s.getPricePerKwh(), s.getRating(), s.getOpenHours(),
                List.copyOf(s.getAmenities()), chargers, s.getSource());
    }

    // ------------------------------------------------------------------ reservations

    @Override
    public List<Reservation> findReservations(int userId, Function<String, Station> stationLookup) {
        List<Reservation> result = new ArrayList<>();
        for (Row row : reservations) {
            if (row.userId != userId) continue;
            Station station = stationLookup.apply(row.stationId);
            Charger charger = station == null ? null : station.findCharger(row.chargerId);
            if (charger == null) continue;
            Reservation r = new Reservation(row.id(), station, charger, row.date, row.slot, row.status, row.createdAt);
            r.setNote(row.note);
            result.add(r);
        }
        return result;
    }

    @Override
    public boolean isSlotTaken(String stationId, String chargerId, LocalDate date, String slot) {
        return findBooking(stationId, chargerId, date, Reservation.slotStart(slot)).isPresent();
    }

    @Override
    public Set<String> findBookedChargers(LocalDate date, LocalTime slotStart) {
        Set<String> keys = new HashSet<>();
        for (Row r : reservations) {
            if (r.active() && r.date.equals(date) && Reservation.slotStart(r.slot).equals(slotStart)) {
                keys.add(r.stationId + "/" + r.chargerId);
            }
        }
        return keys;
    }

    @Override
    public Optional<BookingRef> findBooking(String stationId, String chargerId, LocalDate date, LocalTime slotStart) {
        return reservations.stream()
                .filter(r -> r.active() && r.stationId.equals(stationId) && r.chargerId.equals(chargerId)
                        && r.date.equals(date) && Reservation.slotStart(r.slot).equals(slotStart))
                .findFirst().map(r -> new BookingRef(r.id(), r.userId));
    }

    @Override
    public Reservation createReservation(int userId, Station station, Charger charger, LocalDate date, String slot,
                                         LocalDateTime createdAt) {
        if (isSlotTaken(station.getId(), charger.getId(), date, slot)) {
            throw new ConflictException("That time slot was just booked by another driver. Please pick another.");
        }
        Row row = new Row(nextReservationNumber++, userId, station.getId(), charger.getId(), date, slot, createdAt);
        reservations.add(row);
        return new Reservation(row.id(), station, charger, date, slot, ReservationStatus.CONFIRMED, createdAt);
    }

    @Override
    public void updateStatus(String reservationId, ReservationStatus status, String note) {
        row(reservationId).ifPresent(r -> {
            r.status = status;
            r.note = note;
        });
    }

    @Override
    public void completeEndedBookings(LocalDateTime now) {
        for (Row r : reservations) {
            if (r.status == ReservationStatus.CONFIRMED && !r.slotEnd().isAfter(now)) {
                r.status = ReservationStatus.COMPLETED;
                if (r.note == null) r.note = "Slot ended.";
            }
        }
    }

    @Override
    public List<BookingRef> reportChargerProblem(String stationId, String chargerId, LocalDateTime now, String note) {
        updateChargerStatus(stationId, chargerId, ChargerStatus.OUT_OF_SERVICE);
        List<BookingRef> affected = new ArrayList<>();
        for (Row r : reservations) {
            if (r.status == ReservationStatus.CONFIRMED && r.stationId.equals(stationId)
                    && r.chargerId.equals(chargerId) && r.slotEnd().isAfter(now)) {
                r.status = ReservationStatus.ACTION_REQUIRED;
                r.note = note;
                affected.add(new BookingRef(r.id(), r.userId));
            }
        }
        return affected;
    }

    @Override
    public void moveReservation(String reservationId, Station newStation, Charger newCharger, String note) {
        Row row = row(reservationId)
                .orElseThrow(() -> new ConflictException("Reservation " + reservationId + " no longer exists."));
        boolean taken = reservations.stream().anyMatch(r -> r != row && r.active()
                && r.stationId.equals(newStation.getId()) && r.chargerId.equals(newCharger.getId())
                && r.date.equals(row.date) && r.slot.equals(row.slot));
        if (taken) {
            throw new ConflictException("That time slot is already booked at " + newStation.getName()
                    + ". Please choose another alternative.");
        }
        row.stationId = newStation.getId();
        row.chargerId = newCharger.getId();
        row.status = ReservationStatus.CONFIRMED;
        row.note = note;
    }

    @Override
    public int deleteInactive(int userId) {
        int before = reservations.size();
        reservations.removeIf(r -> r.userId == userId && !r.active());
        return before - reservations.size();
    }

    private Optional<Row> row(String reservationId) {
        return reservations.stream().filter(r -> r.id().equals(reservationId)).findFirst();
    }

    // ------------------------------------------------------------------ charging check-ins

    @Override
    public List<ChargingSession> findActiveSessions(LocalDateTime now) {
        sessions.removeIf(s -> !s.endsAt().isAfter(now));
        return List.copyOf(sessions);
    }

    @Override
    public Optional<ChargingSession> findActiveSession(int userId, LocalDateTime now) {
        return findActiveSessions(now).stream().filter(s -> s.userId() == userId).findFirst();
    }

    @Override
    public void startSession(ChargingSession session) {
        boolean busy = findActiveSessions(session.startedAt()).stream().anyMatch(s -> s.userId() == session.userId()
                || (s.stationId().equals(session.stationId()) && s.chargerId().equals(session.chargerId())));
        if (busy) {
            throw new ConflictException("Someone just started charging there, or you are already charging elsewhere.");
        }
        sessions.add(session);
    }

    @Override
    public Optional<ChargingSession> endSession(int userId, LocalDateTime now) {
        Optional<ChargingSession> active = findActiveSession(userId, now);
        active.ifPresent(sessions::remove);
        return active;
    }

    // ------------------------------------------------------------------ notifications & activity

    @Override
    public List<AppNotification> findNotifications(int userId) {
        return new ArrayList<>(listOf(notifications, userId));
    }

    @Override
    public void addNotification(int userId, NotificationType type, String title, String message,
                                LocalDateTime time, String reservationId) {
        listOf(notifications, userId).add(new AppNotification(nextNotificationId++, type, title, message,
                time, reservationId, false));
    }

    @Override
    public void markRead(int notificationId) {
        notifications.values().stream().flatMap(List::stream)
                .filter(n -> n.getId() == notificationId).forEach(n -> n.setRead(true));
    }

    @Override
    public void markAllRead(int userId) {
        listOf(notifications, userId).forEach(n -> n.setRead(true));
    }

    @Override
    public void deleteNotification(int notificationId) {
        notifications.values().forEach(list -> list.removeIf(n -> n.getId() == notificationId));
    }

    @Override
    public int deleteRead(int userId) {
        List<AppNotification> list = listOf(notifications, userId);
        int before = list.size();
        list.removeIf(AppNotification::isRead);
        return before - list.size();
    }

    @Override
    public void addActivity(int userId, ActivityItem item) {
        listOf(activities, userId).add(item);
    }

    @Override
    public List<ActivityItem> findActivity(int userId, int limit) {
        return listOf(activities, userId).stream()
                .sorted(Comparator.comparing(ActivityItem::time).reversed())
                .limit(limit)
                .toList();
    }

    // ------------------------------------------------------------------ settings

    @Override
    public UserSettings loadSettings(int userId) {
        return settings.getOrDefault(userId, UserSettings.defaults(City.PUNE));
    }

    @Override
    public void saveSettings(int userId, UserSettings userSettings) {
        settings.put(userId, userSettings);
    }

    private static <T> List<T> listOf(Map<Integer, List<T>> map, int userId) {
        return map.computeIfAbsent(userId, id -> new ArrayList<>());
    }
}
