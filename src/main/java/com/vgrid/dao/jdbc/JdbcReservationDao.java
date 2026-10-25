package com.vgrid.dao.jdbc;

import com.vgrid.dao.ConflictException;
import com.vgrid.dao.DataAccessException;
import com.vgrid.dao.ReservationDao;
import com.vgrid.model.*;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;

/**
 * The reservations table.
 * Double booking is blocked by the UNIQUE key one_active_booking_per_slot in schema.sql:
 * if two drivers confirm the same slot at the same moment, MySQL accepts only the first INSERT.
 */
public class JdbcReservationDao implements ReservationDao {
    private static final String SLOT_TAKEN = "That time slot was just booked by another driver. Please pick another.";

    private final Database db;

    public JdbcReservationDao(Database db) {
        this.db = db;
    }

    @Override
    public List<Reservation> findReservations(int userId, Function<String, Station> stationLookup) {
        String sql = "SELECT id, station_id, charger_code, booking_date, slot_start, status, note, created_at "
                + "FROM reservations WHERE user_id = ?";
        List<Reservation> result = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Station station = stationLookup.apply(rs.getString("station_id"));
                    Charger charger = station == null ? null : station.findCharger(rs.getString("charger_code"));
                    if (charger == null) {
                        continue;
                    }
                    Reservation reservation = new Reservation("VG-" + rs.getInt("id"), station, charger,
                            rs.getObject("booking_date", LocalDate.class),
                            Reservation.slotLabel(rs.getObject("slot_start", LocalTime.class)),
                            ReservationStatus.valueOf(rs.getString("status")),
                            rs.getObject("created_at", LocalDateTime.class));
                    reservation.setNote(rs.getString("note"));
                    result.add(reservation);
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load reservations", e);
        }
    }

    @Override
    public boolean isSlotTaken(String stationId, String chargerId, LocalDate date, String slot) {
        return findBooking(stationId, chargerId, date, Reservation.slotStart(slot)).isPresent();
    }

    @Override
    public Set<String> findBookedChargers(LocalDate date, LocalTime slotStart) {
        Set<String> keys = new HashSet<>();
        try (PreparedStatement ps = db.connection().prepareStatement("SELECT station_id, charger_code FROM reservations "
                + "WHERE booking_date = ? AND slot_start = ? AND active_flag = 1")) {
            ps.setObject(1, date);
            ps.setObject(2, slotStart);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    keys.add(rs.getString(1) + "/" + rs.getString(2));
                }
            }
            return keys;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load current bookings", e);
        }
    }

    @Override
    public Optional<BookingRef> findBooking(String stationId, String chargerId, LocalDate date, LocalTime slotStart) {
        String sql = "SELECT id, user_id FROM reservations WHERE station_id = ? AND charger_code = ? "
                + "AND booking_date = ? AND slot_start = ? AND active_flag = 1";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, stationId);
            ps.setString(2, chargerId);
            ps.setObject(3, date);
            ps.setObject(4, slotStart);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new BookingRef("VG-" + rs.getInt(1), rs.getInt(2))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not check the time slot", e);
        }
    }

    @Override
    public Reservation createReservation(int userId, Station station, Charger charger, LocalDate date, String slot,
                                         LocalDateTime createdAt) {
        String sql = "INSERT INTO reservations (user_id, station_id, charger_code, booking_date, slot_start, status, created_at) "
                + "VALUES (?, ?, ?, ?, ?, 'CONFIRMED', ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, station.getId());
            ps.setString(3, charger.getId());
            ps.setObject(4, date);
            ps.setObject(5, Reservation.slotStart(slot));
            ps.setObject(6, createdAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new Reservation("VG-" + keys.getInt(1), station, charger, date, slot,
                        ReservationStatus.CONFIRMED, createdAt);
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ConflictException(SLOT_TAKEN);
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the reservation", e);
        }
    }

    @Override
    public void updateStatus(String reservationId, ReservationStatus status, String note) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "UPDATE reservations SET status = ?, note = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, note);
            ps.setInt(3, number(reservationId));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the reservation", e);
        }
    }

    @Override
    public void completeEndedBookings(LocalDateTime now) {
        try (PreparedStatement ps = db.connection().prepareStatement("UPDATE reservations "
                + "SET status = 'COMPLETED', note = COALESCE(note, 'Slot ended.') "
                + "WHERE status = 'CONFIRMED' AND ADDTIME(TIMESTAMP(booking_date, slot_start), '01:00:00') <= ?")) {
            ps.setObject(1, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update finished bookings", e);
        }
    }

    @Override
    public List<BookingRef> reportChargerProblem(String stationId, String chargerId, LocalDateTime now, String note) {
        try {
            return db.inTransaction(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE chargers SET status = 'OUT_OF_SERVICE' WHERE station_id = ? AND code = ?")) {
                    ps.setString(1, stationId);
                    ps.setString(2, chargerId);
                    ps.executeUpdate();
                }
                List<BookingRef> affected = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement("SELECT id, user_id FROM reservations "
                        + "WHERE station_id = ? AND charger_code = ? AND status = 'CONFIRMED' "
                        + "AND ADDTIME(TIMESTAMP(booking_date, slot_start), '01:00:00') > ? FOR UPDATE")) {
                    ps.setString(1, stationId);
                    ps.setString(2, chargerId);
                    ps.setObject(3, now);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            affected.add(new BookingRef("VG-" + rs.getInt(1), rs.getInt(2)));
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE reservations SET status = 'ACTION_REQUIRED', note = ? WHERE id = ?")) {
                    for (BookingRef ref : affected) {
                        ps.setString(1, note);
                        ps.setInt(2, number(ref.reservationId()));
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                return affected;
            });
        } catch (SQLException e) {
            throw new DataAccessException("Could not record the charger problem", e);
        }
    }

    /**
     * Transaction: lock the booking, check the slot is free at the new charger, then move it.
     * SELECT ... FOR UPDATE stops another window from changing these rows until we commit.
     */
    @Override
    public void moveReservation(String reservationId, Station newStation, Charger newCharger, String note) {
        int id = number(reservationId);
        String taken = "That time slot is already booked at " + newStation.getName() + ". Please choose another alternative.";
        try {
            db.inTransaction(c -> {
                LocalDate date;
                LocalTime start;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT booking_date, slot_start FROM reservations WHERE id = ? FOR UPDATE")) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new ConflictException("Reservation " + reservationId + " no longer exists.");
                        }
                        date = rs.getObject("booking_date", LocalDate.class);
                        start = rs.getObject("slot_start", LocalTime.class);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COUNT(*) FROM reservations WHERE station_id = ? AND charger_code = ? "
                                + "AND booking_date = ? AND slot_start = ? AND active_flag = 1 AND id <> ? FOR UPDATE")) {
                    ps.setString(1, newStation.getId());
                    ps.setString(2, newCharger.getId());
                    ps.setObject(3, date);
                    ps.setObject(4, start);
                    ps.setInt(5, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0) {
                            throw new ConflictException(taken);
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE reservations SET station_id = ?, charger_code = ?, status = 'CONFIRMED', note = ? WHERE id = ?")) {
                    ps.setString(1, newStation.getId());
                    ps.setString(2, newCharger.getId());
                    ps.setString(3, note);
                    ps.setInt(4, id);
                    ps.executeUpdate();
                }
                return null;
            });
        } catch (SQLIntegrityConstraintViolationException e) {
            throw new ConflictException(taken);
        } catch (SQLException e) {
            throw new DataAccessException("Could not move the reservation", e);
        }
    }

    @Override
    public int deleteInactive(int userId) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "DELETE FROM reservations WHERE user_id = ? AND active_flag IS NULL")) {
            ps.setInt(1, userId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not clear reservation history", e);
        }
    }

    /** "VG-1043" -> 1043 */
    static int number(String reservationId) {
        return Integer.parseInt(reservationId.substring(reservationId.indexOf('-') + 1));
    }
}
