package com.evcharging.ui;

import com.evcharging.dao.BookingDAO;
import com.evcharging.dao.SlotDAO;
import com.evcharging.dao.StationDAO;
import com.evcharging.exception.BookingConflictException;
import com.evcharging.exception.ValidationException;
import com.evcharging.model.ChargingSlot;
import com.evcharging.model.EVStation;
import com.evcharging.model.User;
import com.evcharging.service.BookingService;
import com.evcharging.util.DBConnection;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class CustomerDashboardFrame extends JFrame {

    private final User currentUser;
    private final StationDAO stationDAO;
    private final SlotDAO slotDAO;
    private final BookingService bookingService;

    private JTable stationTable;
    private DefaultTableModel stationTableModel;
    private JTable slotTable;
    private DefaultTableModel slotTableModel;
    private JTable historyTable;
    private DefaultTableModel historyTableModel;

    private JTextField searchField;
    private JTextField cityField;
    private JTextField bookingTimeField; // Format: YYYY-MM-DD HH:00

    public CustomerDashboardFrame(User user) {
        this.currentUser = user;
        this.stationDAO = new StationDAO();
        this.slotDAO = new SlotDAO();
        this.bookingService = new BookingService();
        initComponents();
        loadStations();
        loadBookingHistory();
    }

    private void initComponents() {
        setTitle("EV Charging - Customer Dashboard (" + currentUser.getFirstName() + " " + currentUser.getLastName() + ")");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 650);
        setLocationRelativeTo(null);

        JTabbedPane tabbedPane = new JTabbedPane();

        // TAB 1: Search & Book
        JPanel bookingPanel = new JPanel(new BorderLayout(10, 10));
        bookingPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Search Bar
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        searchPanel.add(new JLabel("Station Name:"));
        searchField = new JTextField(12);
        searchPanel.add(searchField);

        searchPanel.add(new JLabel("City:"));
        cityField = new JTextField(10);
        searchPanel.add(cityField);

        JButton searchButton = new JButton("Search");
        searchPanel.add(searchButton);
        bookingPanel.add(searchPanel, BorderLayout.NORTH);

        // Center Tables Split Pane
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        // Station Table
        stationTableModel = new DefaultTableModel(new String[]{"ID", "Station Name", "Address", "City", "Hours"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        stationTable = new JTable(stationTableModel);
        splitPane.setTopComponent(new JScrollPane(stationTable));

        // Slot Table
        slotTableModel = new DefaultTableModel(new String[]{"Slot ID", "Slot #", "Connector", "Power (kW)", "Rate/Hr (₹)", "Status"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        slotTable = new JTable(slotTableModel);
        splitPane.setBottomComponent(new JScrollPane(slotTable));

        bookingPanel.add(splitPane, BorderLayout.CENTER);

        // Booking Action Panel
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        actionPanel.add(new JLabel("Start Time (YYYY-MM-DD HH:00):"));
        bookingTimeField = new JTextField(15);
        bookingTimeField.setText(LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00")));
        actionPanel.add(bookingTimeField);

        JButton bookButton = new JButton("Confirm Booking");
        actionPanel.add(bookButton);
        bookingPanel.add(actionPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("Find & Book Charger", bookingPanel);

        // TAB 2: Booking History
        JPanel historyPanel = new JPanel(new BorderLayout(10, 10));
        historyPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        historyTableModel = new DefaultTableModel(new String[]{"Booking ID", "Station", "Slot #", "Start Time", "End Time", "Rate (₹)", "Status"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        historyTable = new JTable(historyTableModel);
        historyPanel.add(new JScrollPane(historyTable), BorderLayout.CENTER);

        JPanel historyActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelBookingButton = new JButton("Cancel Selected Booking");
        JButton refreshHistoryButton = new JButton("Refresh");
        historyActionPanel.add(cancelBookingButton);
        historyActionPanel.add(refreshHistoryButton);
        historyPanel.add(historyActionPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("My Bookings History", historyPanel);

        // Event Listeners
        searchButton.addActionListener(e -> loadStations());

        stationTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && stationTable.getSelectedRow() != -1) {
                Long stationId = (Long) stationTableModel.getValueAt(stationTable.getSelectedRow(), 0);
                loadSlotsForStation(stationId);
            }
        });

        bookButton.addActionListener(e -> handleBooking());
        cancelBookingButton.addActionListener(e -> handleCancelBooking());
        refreshHistoryButton.addActionListener(e -> loadBookingHistory());

        add(tabbedPane);
    }

    private void loadStations() {
        try {
            stationTableModel.setRowCount(0);
            List<EVStation> stations = stationDAO.findActiveStations(searchField.getText(), cityField.getText());
            for (EVStation s : stations) {
                stationTableModel.addRow(new Object[]{
                        s.getStationId(), s.getStationName(), s.getAddress(), s.getCity(),
                        s.getOpeningTime() + " - " + s.getClosingTime()
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error fetching stations: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadSlotsForStation(Long stationId) {
        try {
            slotTableModel.setRowCount(0);
            List<ChargingSlot> slots = slotDAO.findByStation(stationId);
            for (ChargingSlot slot : slots) {
                slotTableModel.addRow(new Object[]{
                        slot.getSlotId(), slot.getSlotNumber(), slot.getConnectorType(),
                        slot.getMaxPowerKw(), slot.getHourlyRate(), slot.getStatus()
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error fetching slots: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleBooking() {
        int selectedSlotRow = slotTable.getSelectedRow();
        if (selectedSlotRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a charging slot from the table first.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Long slotId = (Long) slotTableModel.getValueAt(selectedSlotRow, 0);

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            String timeText = bookingTimeField.getText().trim();
            if (!timeText.contains(":")) timeText += ":00";
            
            LocalDateTime startTime = LocalDateTime.parse(timeText, formatter);

            Long bookingId = bookingService.createBooking(currentUser.getUserId(), slotId, startTime);
            JOptionPane.showMessageDialog(this, "Booking confirmed successfully! Booking ID: " + bookingId, "Success", JOptionPane.INFORMATION_MESSAGE);
            
            loadBookingHistory();

        } catch (ValidationException | BookingConflictException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Booking Failed", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid date/time format. Please use 'YYYY-MM-DD HH:00'.", "Input Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadBookingHistory() {
        try {
            historyTableModel.setRowCount(0);
            String sql = "SELECT b.booking_id, es.station_name, cs.slot_number, b.start_time, b.end_time, b.rate_per_hour, b.status " +
                         "FROM bookings b " +
                         "JOIN charging_slots cs ON cs.slot_id = b.slot_id " +
                         "JOIN ev_stations es ON es.station_id = cs.station_id " +
                         "WHERE b.user_id = ? ORDER BY b.start_time DESC";

            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, currentUser.getUserId());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        historyTableModel.addRow(new Object[]{
                                rs.getLong("booking_id"),
                                rs.getString("station_name"),
                                rs.getInt("slot_number"),
                                rs.getTimestamp("start_time").toLocalDateTime(),
                                rs.getTimestamp("end_time").toLocalDateTime(),
                                rs.getBigDecimal("rate_per_hour"),
                                rs.getString("status")
                        });
                    }
                }
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error loading history: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleCancelBooking() {
        int selectedRow = historyTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Select a booking to cancel.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Long bookingId = (Long) historyTableModel.getValueAt(selectedRow, 0);
        String currentStatus = (String) historyTableModel.getValueAt(selectedRow, 6);

        if (!"CONFIRMED".equalsIgnoreCase(currentStatus)) {
            JOptionPane.showMessageDialog(this, "Only CONFIRMED bookings can be cancelled.", "Action Invalid", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to cancel booking #" + bookingId + "?", "Confirm Cancel", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("UPDATE bookings SET status = 'CANCELLED' WHERE booking_id = ?")) {
                stmt.setLong(1, bookingId);
                stmt.executeUpdate();
                JOptionPane.showMessageDialog(this, "Booking cancelled successfully. Payment will be marked as REFUNDED.", "Cancelled", JOptionPane.INFORMATION_MESSAGE);
                loadBookingHistory();
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(this, "Error cancelling booking: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}