
// 1 Train  = 10 Coaches (can change)
// 1 Coach = 60 Seats   (can change)
// SeatState  = ["Reserved","Available"]
// updateCycle = 2s
// color codes 
// RED reserved,booked 
// GREEN available
// Orange ( Selecting/ Also to show which ticked they booked), can only be seen in by the user who booked/booking
// contn.  it for others, it is red
// add a server

package com.oops_project;

import com.oops_project.back_end_classes.Train;
import com.oops_project.database.DatabaseManager;
import com.oops_project.GUI.TrainReservationGUI;

import javax.swing.SwingUtilities;

public class MainStartup {

    public static void main(String[] args) {
        Train vbe = new Train("Vande Bharat Express", "Bengaluru", "Mysore", 10);

        DatabaseManager dbManager = new DatabaseManager();
        dbManager.syncTrainSeats(vbe);

        SwingUtilities.invokeLater(() -> {
            new TrainReservationGUI(vbe, dbManager);
        });
    }
}