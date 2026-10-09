package com.evcharging.ui;

import com.evcharging.exception.AuthenticationException;
import com.evcharging.model.User;
import com.evcharging.model.UserRole;
import com.evcharging.service.AuthService;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;

public class LoginFrame extends JFrame {

    private final AuthService authService;
    private JTextField emailField;
    private JPasswordField passwordField;

    public LoginFrame() {
        this.authService = new AuthService();
        initComponents();
    }

    private void initComponents() {
        setTitle("EV Charging System - Login");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(420, 320);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Title
        JLabel titleLabel = new JLabel("EV Charging Reservation", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(titleLabel, gbc);

        gbc.gridwidth = 1;

        // Email
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Email:"), gbc);

        emailField = new JTextField(20);
        gbc.gridx = 1; gbc.gridy = 1;
        panel.add(emailField, gbc);

        // Password
        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Password:"), gbc);

        passwordField = new JPasswordField(20);
        gbc.gridx = 1; gbc.gridy = 2;
        panel.add(passwordField, gbc);

        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        JButton loginButton = new JButton("Login");
        JButton registerButton = new JButton("Register");

        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        panel.add(buttonPanel, gbc);

        // Listeners
        loginButton.addActionListener(e -> handleLogin());
        registerButton.addActionListener(e -> openRegisterFrame());

        add(panel);
    }

    private void handleLogin() {
        String email = emailField.getText();
        String password = new String(passwordField.getPassword());

        try {
            User user = authService.login(email, password);
            JOptionPane.showMessageDialog(this, "Welcome back, " + user.getFirstName() + "!", 
                    "Login Successful", JOptionPane.INFORMATION_MESSAGE);

            this.dispose(); // Close login window

            if (user.getRole() == UserRole.ADMIN) {
                new AdminDashboardFrame(user).setVisible(true);
            } else {
                new CustomerDashboardFrame(user).setVisible(true);
            }

        } catch (AuthenticationException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Authentication Error", JOptionPane.ERROR_MESSAGE);
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Database connection error: " + e.getMessage(), 
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openRegisterFrame() {
        new RegisterFrame(this).setVisible(true);
        this.setVisible(false);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}