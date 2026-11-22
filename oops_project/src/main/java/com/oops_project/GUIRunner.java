
package com.oops_project;

import javax.swing.SwingUtilities;

public class GUIRunner {

    public static void main(String[] args) {

        Train train = new Train(
            "Vande Bharat Express",
            "Bengaluru",
            "Mysore",
            10
        );

        SwingUtilities.invokeLater(() -> {
            new TrainReservationGUI(train);
        });
    }
}
