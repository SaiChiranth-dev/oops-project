package com.oops_project.GUI;

import com.oops_project.back_end_classes.Train;
import com.oops_project.database.DatabaseManager;

import javax.swing.SwingUtilities;

public class GUIRunner {

    public static void main(String[] args) {
        Train train = new Train(
                "Vande Bharat Express",
                "Bengaluru",
                "Mysore",
                10
        );

        DatabaseManager dbManager = new DatabaseManager();
        dbManager.syncTrainSeats(train);

        SwingUtilities.invokeLater(() -> {
            new TrainReservationGUI(train, dbManager);
        });
    }
}
