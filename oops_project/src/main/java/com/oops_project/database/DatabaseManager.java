package com.oops_project.database;

import com.oops_project.back_end_classes.Coach;
import com.oops_project.back_end_classes.Seat;
import com.oops_project.back_end_classes.Train;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:sqlite:reservation.db";

    public DatabaseManager() {
        initializeDatabase();
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    public final void initializeDatabase() {
        String createTableSQL = """
                CREATE TABLE IF NOT EXISTS seats (
                    train_name TEXT NOT NULL,
                    coach_number TEXT NOT NULL,
                    seat_number TEXT NOT NULL,
                    seat_state TEXT NOT NULL,
                    PRIMARY KEY (train_name, coach_number, seat_number)
                );
                """;

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
        } catch (SQLException e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
        }
    }

    public void syncTrainSeats(Train train) {
        String insertIfMissingSQL = """
                INSERT OR IGNORE INTO seats (train_name, coach_number, seat_number, seat_state)
                VALUES (?, ?, ?, ?);
                """;

        String selectSeatStateSQL = """
                SELECT seat_state FROM seats
                WHERE train_name = ? AND coach_number = ? AND seat_number = ?;
                """;

        try (Connection conn = connect();
             PreparedStatement insertStmt = conn.prepareStatement(insertIfMissingSQL);
             PreparedStatement selectStmt = conn.prepareStatement(selectSeatStateSQL)) {

            conn.setAutoCommit(false);

            for (Coach coach : train.coaches) {
                for (Seat seat : coach.seats) {
                    insertStmt.setString(1, train.trainName);
                    insertStmt.setString(2, coach.coachNumber);
                    insertStmt.setString(3, seat.seatNumber);
                    insertStmt.setString(4, seat.seatState);
                    insertStmt.addBatch();
                }
            }
            insertStmt.executeBatch();
            conn.commit();

            for (Coach coach : train.coaches) {
                for (Seat seat : coach.seats) {
                    selectStmt.setString(1, train.trainName);
                    selectStmt.setString(2, coach.coachNumber);
                    selectStmt.setString(3, seat.seatNumber);

                    try (ResultSet rs = selectStmt.executeQuery()) {
                        if (rs.next()) {
                            seat.seatState = rs.getString("seat_state");
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error syncing seats with database: " + e.getMessage());
        }
    }

    public boolean reserveSeats(String trainName, String coachNumber, Collection<Seat> seatsToReserve) {
        String updateSQL = """
                UPDATE seats
                SET seat_state = 'Reserved'
                WHERE train_name = ? AND coach_number = ? AND seat_number = ?;
                """;

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {

            conn.setAutoCommit(false);

            for (Seat seat : seatsToReserve) {
                pstmt.setString(1, trainName);
                pstmt.setString(2, coachNumber);
                pstmt.setString(3, seat.seatNumber);
                pstmt.addBatch();
            }

            pstmt.executeBatch();
            conn.commit();

            for (Seat seat : seatsToReserve) {
                seat.seatState = "Reserved";
            }
            return true;
        } catch (SQLException e) {
            System.err.println("Error reserving seats: " + e.getMessage());
            return false;
        }
    }
}
