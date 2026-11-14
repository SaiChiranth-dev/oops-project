package com.oops_project;

public class Seat{
    public String seatNumber;
    public String seatState;

    Seat(String seatNumber, String seatState){
        this.seatNumber = seatNumber;
        this.seatState = seatState;
    }

    void displaySeatDetails(){
        System.out.println("Seat Number: " + seatNumber);
        System.out.println("Seat State: " + seatState);
    }
}