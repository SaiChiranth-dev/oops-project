package com.oops_project.back_end_classes;

import java.util.ArrayList;
import java.util.List;

public class Coach {
    public List<Seat> seats = new ArrayList<Seat>();
    public String coachNumber;

    public Coach(String coachNumber) {
        this.coachNumber = coachNumber;
        for (int i = 0; i < 60; i++) {
            Seat s = new Seat(coachNumber + "S" + (i + 1), "Available");
            seats.add(s);
        }
    }

    public void displayCoachDetails() {
        System.out.println("Coach Number: " + coachNumber);
        System.out.println("Number of Seats: " + seats.size());
        for (Seat s : seats) {
            s.displaySeatDetails();
        }
    }
}