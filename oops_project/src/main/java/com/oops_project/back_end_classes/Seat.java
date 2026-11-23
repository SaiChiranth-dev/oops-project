// A3S1

package com.oops_project.back_end_classes;

public class Seat {
    public String seatNumber;
    public String seatState;

    public Seat(String seatNumber, String seatState) {
        this.seatNumber = seatNumber;
        this.seatState = seatState;
    }

    public void displaySeatDetails() {
        System.out.println("Seat Number: " + seatNumber);
        System.out.println("Seat State: " + seatState);
    }
}