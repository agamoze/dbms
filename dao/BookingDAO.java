package com.evcharging.dao;

import com.evcharging.util.DBConnection;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.UUID;

public class BookingDAO {

    public synchronized Long bookSlotTransactional(Long userId, Long slotId, LocalDateTime startTime) throws SQLException {
        LocalDateTime endTime = startTime.plusHours(1);

        String lockSlotSql = "SELECT cs.slot_id, cs.status AS slot_status, cs.hourly_rate, es.status AS station_status " +
                             "FROM charging_slots cs JOIN ev_stations es ON es.station_id = cs.station_id " +
                             "WHERE cs.slot_id = ? FOR UPDATE";

        String checkOverlapSql = "SELECT booking_id FROM bookings WHERE slot_id = ? AND status = 'CONFIRMED' " +
                                 "AND start_time < ? AND end_time > ? FOR UPDATE";

        String insertBookingSql = "INSERT INTO bookings (user_id, slot_id, start_time, end_time, rate_per_hour, status) " +
                                  "VALUES (?, ?, ?, ?, ?, 'CONFIRMED')";

        String insertPaymentSql = "INSERT INTO payments (booking_id, amount, method, status, transaction_reference) " +
                                  "VALUES (?, ?, 'MOCK', 'PAID', ?)";

        Connection conn = null;
        try {
            conn = DBConnection.getConnection();
            conn.setAutoCommit(false); // Begin Transaction

            BigDecimal hourlyRate;
            // 1. Lock slot and verify status
            try (PreparedStatement stmt = conn.prepareStatement(lockSlotSql)) {
                stmt.setLong(1, slotId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Slot not found.");
                    }
                    if (!"ACTIVE".equals(rs.getString("slot_status")) || !"ACTIVE".equals(rs.getString("station_status"))) {
                        throw new SQLException("Slot or Station is currently inactive/under maintenance.");
                    }
                    hourlyRate = rs.getBigDecimal("hourly_rate");
                }
            }

            // 2. Check overlap interval
            try (PreparedStatement stmt = conn.prepareStatement(checkOverlapSql)) {
                stmt.setLong(1, slotId);
                stmt.setTimestamp(2, Timestamp.valueOf(endTime));
                stmt.setTimestamp(3, Timestamp.valueOf(startTime));
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        throw new SQLException("Booking conflict: Selected slot is already booked for this time interval.");
                    }
                }
            }

            // 3. Insert Booking
            Long bookingId;
            try (PreparedStatement stmt = conn.prepareStatement(insertBookingSql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setLong(1, userId);
                stmt.setLong(2, slotId);
                stmt.setTimestamp(3, Timestamp.valueOf(startTime));
                stmt.setTimestamp(4, Timestamp.valueOf(endTime));
                stmt.setBigDecimal(5, hourlyRate);
                stmt.executeUpdate();

                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        bookingId = rs.getLong(1);
                    } else {
                        throw new SQLException("Failed to retrieve generated booking ID.");
                    }
                }
            }

            // 4. Insert Mock Payment
            try (PreparedStatement stmt = conn.prepareStatement(insertPaymentSql)) {
                stmt.setLong(1, bookingId);
                stmt.setBigDecimal(2, hourlyRate);
                stmt.setString(3, "TXN_MOCK_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                stmt.executeUpdate();
            }

            conn.commit(); // Commit transaction
            return bookingId;

        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback(); // Rollback on failure
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }
}
