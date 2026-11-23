package com.oops_project.GUI;

import com.oops_project.back_end_classes.Coach;
import com.oops_project.back_end_classes.Seat;
import com.oops_project.back_end_classes.Train;
import com.oops_project.database.DatabaseManager;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public class TrainReservationGUI extends JFrame {

    private final Train train;
    private final DatabaseManager dbManager;
    private Coach coach;

    private final JPanel coachBody = new JPanel();
    private final JLabel message = new JLabel("Select your seats");

    private final Set<Seat> selectedSeats = new HashSet<>();

    public TrainReservationGUI(Train train, DatabaseManager dbManager) {
        this.train = train;
        this.dbManager = dbManager;
        this.coach = train.coaches.get(0);

        setTitle("Train Reservation System");
        setSize(950, 750);
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

        JPanel header = new JPanel(new GridLayout(2, 1, 5, 5));
        header.add(heading);
        header.add(route);
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
            displayCoach();
            message.setText("Select your seats");
        });

        displayCoach();
        setVisible(true);
    }

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

    private void reserveSelectedSeats() {
        if (selectedSeats.isEmpty()) {
            message.setText("Please select at least one seat.");
            return;
        }

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