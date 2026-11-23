package com.oops_project.back_end_classes;

import java.util.ArrayList;
import java.util.List;

public class Train {
    public List<Coach> coaches = new ArrayList<Coach>();
    public String trainName;
    public String placeOfDeparture;
    public String placeOfArrival;

    public Train(String trainName, String placeOfDeparture, String placeOfArrival, int numberOfCoaches) {
        this.trainName = trainName;
        this.placeOfDeparture = placeOfDeparture;
        this.placeOfArrival = placeOfArrival;

        for (int i = 0; i < numberOfCoaches; i++) {
            Coach c = new Coach("A" + (i + 1));
            coaches.add(c);
        }
    }

    public void displayTrainDetails() {
        System.out.println("Train Name: " + trainName);
        System.out.println("Departure: " + placeOfDeparture);
        System.out.println("Arrival: " + placeOfArrival);
        System.out.println("Number of Coaches: " + coaches.size());
        for (Coach c : coaches) {
            c.displayCoachDetails();
        }
    }
}
