package com.evcharging.ui;

import com.evcharging.dao.StationDAO;
import com.evcharging.model.EVStation;
import com.evcharging.model.StationStatus;
import com.evcharging.model.User;
import com.evcharging.service.AdminService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class AdminDashboardFrame extends JFrame {

    private final User adminUser;
    private final StationDAO stationDAO;
    private final AdminService adminService;

    private JTable adminStationTable;
    private DefaultTableModel adminStationTableModel;
    private JTextArea reportTextArea;

    public AdminDashboardFrame(User user) {
        this.adminUser = user;
        this.stationDAO = new StationDAO();
        this.adminService = new AdminService();
        initComponents();
        loadAllStations();
    }

    private void initComponents() {
        setTitle("EV Charging System - Admin Control Center (" + adminUser.getFirstName() + ")");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(850, 550);
        setLocationRelativeTo(null);

        JTabbedPane tabbedPane = new JTabbedPane();

        // TAB 1: Station Management
        JPanel stationPanel = new JPanel(new BorderLayout(10, 10));
        stationPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        adminStationTableModel = new DefaultTableModel(new String[]{"ID", "Station Name", "City", "Hours", "Status"}, 0) {
            @Override public boolean isCellEditable(int row, int col) { return false; }
        };
        adminStationTable = new JTable(adminStationTableModel);
        stationPanel.add(new JScrollPane(adminStationTable), BorderLayout.CENTER);

        JPanel stationControlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton activateBtn = new JButton("Set Active");
        JButton maintenanceBtn = new JButton("Set Maintenance");
        JButton refreshBtn = new JButton("Refresh");

        stationControlPanel.add(activateBtn);
        stationControlPanel.add(maintenanceBtn);
        stationControlPanel.add(refreshBtn);
        stationPanel.add(stationControlPanel, BorderLayout.SOUTH);

        tabbedPane.addTab("Station Management", stationPanel);

        // TAB 2: Revenue Reports
        JPanel reportPanel = new JPanel(new BorderLayout(10, 10));
        reportPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel reportFilterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        reportFilterPanel.add(new JLabel("Select Station:"));

        JButton generateReportBtn = new JButton("Generate Revenue Report");
        reportFilterPanel.add(generateReportBtn);
        reportPanel.add(reportFilterPanel, BorderLayout.NORTH);

        reportTextArea = new JTextArea();
        reportTextArea.setEditable(false);
        reportTextArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        reportPanel.add(new JScrollPane(reportTextArea), BorderLayout.CENTER);

        tabbedPane.addTab("Revenue Analytics", reportPanel);

        // Listeners
        refreshBtn.addActionListener(e -> loadAllStations());
        activateBtn.addActionListener(e -> updateStatus(StationStatus.ACTIVE));
        maintenanceBtn.addActionListener(e -> updateStatus(StationStatus.MAINTENANCE));
        generateReportBtn.addActionListener(e -> handleGenerateReport());

        add(tabbedPane);
    }

    private void loadAllStations() {
        try {
            adminStationTableModel.setRowCount(0);
            List<EVStation> stations = stationDAO.listAllStations();
            for (EVStation s : stations) {
                adminStationTableModel.addRow(new Object[]{
                        s.getStationId(), s.getStationName(), s.getCity(),
                        s.getOpeningTime() + " - " + s.getClosingTime(), s.getStatus()
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error fetching stations: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateStatus(StationStatus newStatus) {
        int selectedRow = adminStationTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Select a station from the table.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Long stationId = (Long) adminStationTableModel.getValueAt(selectedRow, 0);
        try {
            adminService.updateStationStatus(stationId, newStatus);
            JOptionPane.showMessageDialog(this, "Station status updated to " + newStatus, "Success", JOptionPane.INFORMATION_MESSAGE);
            loadAllStations();
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error updating status: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleGenerateReport() {
        int selectedRow = adminStationTable.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a station from the 'Station Management' tab first.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Long stationId = (Long) adminStationTableModel.getValueAt(selectedRow, 0);
        LocalDateTime fromDate = LocalDateTime.now().minusMonths(6);
        LocalDateTime toDate = LocalDateTime.now().plusDays(1);

        try {
            Map<String, Object> report = adminService.getStationRevenueReport(stationId, fromDate, toDate);

            if (report.isEmpty()) {
                reportTextArea.setText("No data returned for selected station.");
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("====================================================\n");
            sb.append("           STATION REVENUE REPORT                   \n");
            sb.append("====================================================\n");
            sb.append(String.format(" Station ID:            %s\n", report.get("station_id")));
            sb.append(String.format(" Station Name:          %s\n", report.get("station_name")));
            sb.append(String.format(" Paid Bookings Count:   %s\n", report.get("paid_bookings_count")));
            sb.append("----------------------------------------------------\n");
            sb.append(String.format(" Total Gross Revenue:  ₹ %s\n", report.get("total_paid_revenue")));
            sb.append(String.format(" Total Refunds Issued: ₹ %s\n", report.get("total_refunded_amount")));
            sb.append("----------------------------------------------------\n");
            sb.append(String.format(" NET REVENUE:          ₹ %s\n", report.get("net_revenue")));
            sb.append("====================================================\n");

            reportTextArea.setText(sb.toString());

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error executing procedure: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
