import java.util.*;

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

class MainStartup{

    static void reserveSeat(List<Seat> seats){
        for(Seat s: seats){
            if(s.seatState.equals("Available")){
                s.seatState = "Reserved";
                System.out.println("Seat " + s.seatNumber + " has been reserved.");
            }
            else{
                throw new IllegalArgumentException("Seat " + s.seatNumber + " is already reserved.");
            }
        }
    }
    static void cancelSeat(List<Seat> seats){

        // assume proper seat numbers are passed in the list of seats to be cancelled and all seats are reserved before cancellation

        for(Seat s: seats){
            s.seatState = "Available";
            System.out.println("Seat " + s.seatNumber + " has been cancelled.");  
        }
    }


    public static void main(String[] args){
        Train VBE = new Train("Vande Bharat Express", "Bengaluru", "Mysore",10);
        System.out.println("Welcome to the Train Reservation System");
        VBE.displayTrainDetails();
  
    }
}