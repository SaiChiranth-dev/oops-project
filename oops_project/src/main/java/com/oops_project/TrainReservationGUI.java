package com.oops_project;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public class TrainReservationGUI extends JFrame {
    private final Train train;
    private Coach coach;
    private final JPanel seatPanel = new JPanel(new GridLayout(10, 6, 7, 7));
    private final JLabel message = new JLabel("Select available seats");
    private final Set<Seat> selected = new HashSet<>();

    public TrainReservationGUI(Train train) {
        this.train = train;
        coach = train.coaches.get(0);

        setTitle("Train Reservation System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(680, 620);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JLabel heading = new JLabel(train.trainName, SwingConstants.CENTER);
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));
        JLabel route = new JLabel(train.placeOfDeparture + "  →  " + train.placeOfArrival,
                SwingConstants.CENTER);
        JPanel header = new JPanel(new GridLayout(2, 1, 3, 3));
        header.add(heading);
        header.add(route);
        header.setBorder(BorderFactory.createEmptyBorder(12, 10, 5, 10));
        add(header, BorderLayout.NORTH);

        JComboBox<String> coachChoice = new JComboBox<>();
        for (Coach c : train.coaches) coachChoice.addItem(c.coachNumber);
        JPanel coachBar = new JPanel();
        coachBar.add(new JLabel("Coach:"));
        coachBar.add(coachChoice);

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(coachBar, BorderLayout.NORTH);
        seatPanel.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));
        center.add(seatPanel, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        JButton reserve = new JButton("Reserve Selected Seats");
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 10));
        footer.add(new JLabel("Green: Available   Orange: Selected   Red: Reserved"));
        footer.add(reserve);
        footer.add(message);
        add(footer, BorderLayout.SOUTH);

        coachChoice.addActionListener(e -> {
            coach = train.coaches.get(coachChoice.getSelectedIndex());
            selected.clear();
            showSeats();
        });

        reserve.addActionListener(e -> {
            if (selected.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Select at least one available seat.");
                return;
            }
            for (Seat s : selected) s.seatState = "Reserved";
            int booked = selected.size();
            selected.clear();
            showSeats();
            message.setText(booked + " seat(s) reserved");
        });

        showSeats();
        setVisible(true);
    }

    private void showSeats() {
        seatPanel.removeAll();
        for (Seat seat : coach.seats) {
            JButton button = new JButton(seat.seatNumber);
            button.setOpaque(true);
            button.setBorderPainted(false);
            button.setForeground(Color.BLACK);
            updateSeatColor(button, seat);
            button.addActionListener(e -> {
                if ("Reserved".equals(seat.seatState)) {
                    message.setText(seat.seatNumber + " is already reserved");
                    return;
                }
                if (selected.remove(seat)) {
                    message.setText("Seat selection updated");
                } else {
                    selected.add(seat);
                    message.setText(selected.size() + " seat(s) selected");
                }
                updateSeatColor(button, seat);
            });
            seatPanel.add(button);
        }
        seatPanel.revalidate();
        seatPanel.repaint();
    }

    private void updateSeatColor(JButton button, Seat seat) {
        if ("Reserved".equals(seat.seatState)) button.setBackground(Color.RED);
        else if (selected.contains(seat)) button.setBackground(Color.ORANGE);
        else button.setBackground(new Color(105, 210, 125));
    }
}
