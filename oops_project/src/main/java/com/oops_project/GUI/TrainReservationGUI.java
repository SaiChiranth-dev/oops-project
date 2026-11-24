package com.oops_project.GUI;

import com.oops_project.back_end_classes.Coach;
import com.oops_project.back_end_classes.Seat;
import com.oops_project.back_end_classes.Train;
import com.oops_project.database.DatabaseManager;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class TrainReservationGUI extends JFrame {

    private final Train train;
    private final DatabaseManager dbManager;
    private Coach coach;

    private final JPanel coachBody = new JPanel();
    private final JLabel message = new JLabel("Select your seats");
    private final JLabel connectionStatus = new JLabel("Connecting to server...", SwingConstants.CENTER);

    private final Set<Seat> selectedSeats = new HashSet<>();

    // Client-Server socket connection
    private Socket socket;
    private PrintWriter serverOut;
    private BufferedReader serverIn;
    private String passengerName;
    private volatile boolean isConnectedToServer = false;
    private final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();

    public TrainReservationGUI(Train train, DatabaseManager dbManager) {
        this.train = train;
        this.dbManager = dbManager;
        this.coach = train.coaches.get(0);
        this.passengerName = "Passenger_" + (int)(Math.random() * 9000 + 1000);

        setTitle("Train Reservation System - " + passengerName);
        setSize(950, 780);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        setLayout(new BorderLayout(10, 10));

        // Train heading
        JLabel heading = new JLabel(train.trainName, SwingConstants.CENTER);
        heading.setFont(new Font("Arial", Font.BOLD, 22));

        JLabel route = new JLabel(
                train.placeOfDeparture + "  →  " + train.placeOfArrival,
                SwingConstants.CENTER
        );
        route.setFont(new Font("Arial", Font.PLAIN, 16));

        connectionStatus.setFont(new Font("Arial", Font.ITALIC, 12));
        connectionStatus.setForeground(new Color(40, 140, 40));

        JPanel header = new JPanel(new GridLayout(3, 1, 3, 3));
        header.add(heading);
        header.add(route);
        header.add(connectionStatus);
        add(header, BorderLayout.NORTH);

        // Coach selection
        JComboBox<String> coachChoice = new JComboBox<>();
        for (Coach c : train.coaches) {
            coachChoice.addItem(c.coachNumber);
        }

        JPanel coachSelection = new JPanel();
        coachSelection.add(new JLabel("Select Coach:"));
        coachSelection.add(coachChoice);

        // Coach body
        coachBody.setLayout(new BoxLayout(coachBody, BoxLayout.Y_AXIS));
        coachBody.setBackground(new Color(235, 235, 235));
        coachBody.setBorder(
                BorderFactory.createCompoundBorder(
                        new LineBorder(Color.DARK_GRAY, 5),
                        BorderFactory.createEmptyBorder(15, 15, 15, 15)
                )
        );

        JScrollPane scrollPane = new JScrollPane(coachBody);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(coachSelection, BorderLayout.NORTH);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);

        // Bottom panel
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));

        message.setHorizontalAlignment(SwingConstants.CENTER);
        message.setFont(new Font("Arial", Font.BOLD, 14));

        JButton reserveButton = new JButton("Reserve Selected Seats");
        reserveButton.setFont(new Font("Arial", Font.BOLD, 14));
        reserveButton.setBackground(new Color(30, 120, 200));
        reserveButton.setForeground(Color.WHITE);
        reserveButton.addActionListener(e -> reserveSelectedSeats());

        bottomPanel.add(message, BorderLayout.NORTH);
        bottomPanel.add(reserveButton, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // Change coach
        coachChoice.addActionListener(e -> {
            coach = train.coaches.get(coachChoice.getSelectedIndex());
            selectedSeats.clear();
            syncCoachWithServer();
            displayCoach();
            message.setText("Select your seats in Coach " + coach.coachNumber);
        });

        // Window closing cleanup
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnectFromServer();
            }
        });

        // 1. Establish connection to Server (port 5050)
        connectToServer();

        // 2. Initial render
        syncCoachWithServer();
        displayCoach();
        setVisible(true);
    }

    // =========================================================================
    // CLIENT-SERVER CONNECTION & REAL-TIME BROADCAST LISTENER
    // =========================================================================

    private void connectToServer() {
        try {
            socket = new Socket("localhost", 5050);
            serverOut = new PrintWriter(socket.getOutputStream(), true);
            serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            isConnectedToServer = true;

            // Start dedicated background listener for incoming broadcasts & responses
            Thread listenerThread = new Thread(this::listenForServerMessages, "ServerListenerThread");
            listenerThread.setDaemon(true);
            listenerThread.start();

            // Login to server
            String welcome = responseQueue.poll(3, TimeUnit.SECONDS);
            String loginResp = sendCommand("LOGIN " + passengerName);

            connectionStatus.setText("● Connected to Server (Port 5050) as " + passengerName + " - Real-Time Sync Active");
            connectionStatus.setForeground(new Color(0, 140, 0));
            setTitle("Train Reservation System - " + passengerName + " [LIVE SERVER SYNC]");

        } catch (Exception e) {
            isConnectedToServer = false;
            connectionStatus.setText("○ Server Offline (Running in Local Standalone Mode)");
            connectionStatus.setForeground(new Color(180, 50, 50));
            setTitle("Train Reservation System - " + passengerName + " [STANDALONE MODE]");
            System.out.println("[GUI] Could not connect to Server on port 5050. Using local SQLite mode.");
        }
    }

    private void disconnectFromServer() {
        if (isConnectedToServer && serverOut != null) {
            try {
                serverOut.println("QUIT");
                isConnectedToServer = false;
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {}
        }
    }

    /**
     * Dedicated background thread that continuously reads from the socket.
     * Dispatches SEAT_UPDATE events to the GUI, and command responses to the response queue.
     */
    private void listenForServerMessages() {
        try {
            String line;
            while (isConnectedToServer && (line = serverIn.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("SEAT_UPDATE ")) {
                    // Broadcast event from server: SEAT_UPDATE <trainName...> <coachNumber> <seatNumber> <Reserved/Available>
                    handleSeatUpdateBroadcast(line);
                } else {
                    // Synchronous response to a user command (200, 409, 400, etc.)
                    responseQueue.offer(line);
                }
            }
        } catch (IOException e) {
            if (isConnectedToServer) {
                SwingUtilities.invokeLater(() -> {
                    connectionStatus.setText("○ Disconnected from Server (Switched to Standalone)");
                    connectionStatus.setForeground(Color.RED);
                });
            }
        } finally {
            isConnectedToServer = false;
        }
    }

    private void handleSeatUpdateBroadcast(String line) {
        String[] parts = line.split("\\s+");
        if (parts.length >= 5) {
            String coachNumber = parts[parts.length - 3];
            String seatNumber = parts[parts.length - 2];
            String newState = parts[parts.length - 1];

            // Update in-memory data model
            for (Coach c : train.coaches) {
                if (c.coachNumber.equalsIgnoreCase(coachNumber)) {
                    for (Seat s : c.seats) {
                        if (s.seatNumber.equalsIgnoreCase(seatNumber)) {
                            s.seatState = newState;

                            // If this user was currently selecting this seat, notify and unselect
                            if ("Reserved".equalsIgnoreCase(newState) && selectedSeats.contains(s)) {
                                selectedSeats.remove(s);
                                SwingUtilities.invokeLater(() -> {
                                    message.setText("Alert: Seat " + seatNumber + " was just booked by another user!");
                                });
                            }
                            break;
                        }
                    }
                }
            }

            // Immediately repaint coach if on the same coach
            if (coach.coachNumber.equalsIgnoreCase(coachNumber)) {
                SwingUtilities.invokeLater(this::displayCoach);
            }
        }
    }

    private synchronized String sendCommand(String cmd) {
        if (!isConnectedToServer || serverOut == null) {
            return null;
        }
        responseQueue.clear();
        serverOut.println(cmd);
        try {
            return responseQueue.poll(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private void syncCoachWithServer() {
        if (!isConnectedToServer) return;

        String cmd = "GET_SEATS " + train.trainName.replace(' ', '_') + " " + coach.coachNumber;
        String response = sendCommand(cmd);

        if (response != null && response.startsWith("200 SEAT_LIST")) {
            String[] parts = response.split("\\s+");
            if (parts.length >= 4) {
                String seatData = parts[3];
                String[] seatPairs = seatData.split(",");
                for (String pair : seatPairs) {
                    String[] kv = pair.split(":");
                    if (kv.length == 2) {
                        String seatNum = kv[0];
                        String state = kv[1];
                        for (Seat s : coach.seats) {
                            if (s.seatNumber.equalsIgnoreCase(seatNum)) {
                                s.seatState = state;
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // GUI DISPLAY & SEAT SELECTION
    // =========================================================================

    private void displayCoach() {
        coachBody.removeAll();

        JLabel entrance = new JLabel("FRONT ENTRANCE", SwingConstants.CENTER);
        entrance.setFont(new Font("Arial", Font.BOLD, 14));
        entrance.setAlignmentX(Component.CENTER_ALIGNMENT);
        coachBody.add(entrance);

        for (int i = 0; i < coach.seats.size(); i += 6) {
            JPanel row = new JPanel(new GridLayout(1, 9, 5, 5));
            row.setBackground(new Color(235, 235, 235));

            row.add(createWindow());

            for (int j = 0; j < 6; j++) {
                Seat seat = coach.seats.get(i + j);
                JButton seatButton = new JButton(seat.seatNumber);
                seatButton.setPreferredSize(new Dimension(75, 42));
                seatButton.setFont(new Font("Arial", Font.PLAIN, 11));
                seatButton.setOpaque(true);

                if ("Reserved".equals(seat.seatState)) {
                    seatButton.setBackground(Color.RED);
                    seatButton.setForeground(Color.WHITE);
                } else if (selectedSeats.contains(seat)) {
                    seatButton.setBackground(Color.ORANGE);
                } else {
                    seatButton.setBackground(Color.GREEN);
                }

                seatButton.addActionListener(e -> {
                    if ("Reserved".equals(seat.seatState)) {
                        message.setText("Seat " + seat.seatNumber + " is already reserved.");
                    } else {
                        if (selectedSeats.contains(seat)) {
                            selectedSeats.remove(seat);
                        } else {
                            selectedSeats.add(seat);
                        }
                        displayCoach();
                        message.setText(selectedSeats.size() + " seat(s) selected");
                    }
                });

                row.add(seatButton);

                if (j == 2) {
                    JLabel aisle = new JLabel("AISLE", SwingConstants.CENTER);
                    aisle.setForeground(Color.GRAY);
                    aisle.setFont(new Font("Arial", Font.BOLD, 10));
                    row.add(aisle);
                }
            }

            row.add(createWindow());
            coachBody.add(row);
            coachBody.add(Box.createVerticalStrut(8));
        }

        JLabel exit = new JLabel("REAR EXIT", SwingConstants.CENTER);
        exit.setFont(new Font("Arial", Font.BOLD, 14));
        exit.setAlignmentX(Component.CENTER_ALIGNMENT);
        coachBody.add(exit);

        JPanel legend = new JPanel();
        legend.add(new JLabel("Green = Available"));
        legend.add(new JLabel("Orange = Selected"));
        legend.add(new JLabel("Red = Reserved"));
        legend.add(new JLabel("Blue = Window"));
        coachBody.add(legend);

        coachBody.revalidate();
        coachBody.repaint();
    }

    private JLabel createWindow() {
        JLabel window = new JLabel("W", SwingConstants.CENTER);
        window.setPreferredSize(new Dimension(35, 42));
        window.setBackground(Color.CYAN);
        window.setOpaque(true);
        window.setForeground(Color.BLUE);
        window.setBorder(new LineBorder(Color.BLUE, 2));
        return window;
    }

    // =========================================================================
    // RESERVATION ACTION: VIA SERVER OR LOCAL DB
    // =========================================================================

    private void reserveSelectedSeats() {
        if (selectedSeats.isEmpty()) {
            message.setText("Please select at least one seat.");
            return;
        }

        if (isConnectedToServer) {
            // Client-Server Mode: Send atomic booking request over TCP socket
            List<String> seatNums = new ArrayList<>();
            for (Seat seat : selectedSeats) {
                seatNums.add(seat.seatNumber);
            }

            String cmd = "BOOK " + train.trainName.replace(' ', '_') + " " + coach.coachNumber + " " + String.join(",", seatNums);
            String response = sendCommand(cmd);

            if (response != null && response.startsWith("200 BOOKING_SUCCESS")) {
                for (Seat seat : selectedSeats) {
                    seat.seatState = "Reserved";
                }
                String bookedList = String.join(" ", seatNums);
                selectedSeats.clear();
                displayCoach();

                // Extract PNR from response
                String pnrInfo = response.substring(20);
                message.setText("Reserved via Server: " + bookedList + " [" + pnrInfo + "]");

            } else if (response != null && response.startsWith("409 BOOKING_CONFLICT")) {
                String conflictMsg = response.substring(21);
                // Immediately refresh coach to show which seat was taken
                syncCoachWithServer();
                selectedSeats.clear();
                displayCoach();

                JOptionPane.showMessageDialog(
                        this,
                        conflictMsg + "\n\nYour seat selection has been refreshed.",
                        "Booking Conflict - Seat Already Reserved",
                        JOptionPane.WARNING_MESSAGE
                );
                message.setText(conflictMsg);

            } else {
                message.setText("Server response: " + response);
            }

        } else {
            // Standalone Fallback: Direct SQLite reservation
            boolean saved = dbManager.reserveSeats(
                    train.trainName,
                    coach.coachNumber,
                    selectedSeats
            );

            if (saved) {
                StringBuilder reservedList = new StringBuilder();
                for (Seat seat : selectedSeats) {
                    reservedList.append(seat.seatNumber).append(" ");
                }
                selectedSeats.clear();
                displayCoach();
                message.setText("Reserved & saved to DB: " + reservedList.toString().trim());
            } else {
                message.setText("Failed to save reservation to database.");
            }
        }
    }
}
