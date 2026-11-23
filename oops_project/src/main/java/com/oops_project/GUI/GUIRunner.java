
package com.oops_project.GUI;

import javax.swing.SwingUtilities;

import com.oops_project.back_end_classes.Train;

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
