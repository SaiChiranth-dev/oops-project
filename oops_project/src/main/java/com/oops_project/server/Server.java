package com.oops_project.server;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ============================================================================
 * RAILWAY RESERVATION MULTI-USER CONCURRENT SERVER
 * ============================================================================
 * 
 * Features:
 * 1. Multi-threaded Architecture: Handles multiple simultaneous user connections
 *    via an elastic cached thread pool.
 * 2. Strict Concurrency Control (Fair ReentrantLock): Guarantees that two or
 *    more users CANNOT book the same seat simultaneously (race-condition free).
 * 3. Atomic Database Transactions: Direct synchronization with SQLite database
 *    (reservation.db) with rollback on conflict.
 * 4. Real-time Broadcast: Broadcasts seat updates to all active clients whenever
 *    a seat is reserved or cancelled.
 * 5. Built-in Concurrency Stress Test: Launch with "--test" to simulate 5 users
 *    racing to book the exact same seat.
 */
public class Server {

    public static final int DEFAULT_PORT = 5050;
    private static final String DEFAULT_TRAIN = "Vande Bharat Express";
    private static final int DEFAULT_COACH_COUNT = 10;
    private static final int SEATS_PER_COACH = 60;

    private final int port;
    private final String dbUrl;
    private final ExecutorService threadPool;
    private final List<ClientHandler> activeClients;
    private final ReentrantLock bookingLock;
    private final Map<String, String> inMemorySeatCache; // Key: train:coach:seat -> State

    private volatile boolean isRunning = false;
    private ServerSocket serverSocket;

    public Server(int port) {
        this.port = port;
        this.dbUrl = resolveDatabaseUrl();
        this.threadPool = Executors.newCachedThreadPool();
        this.activeClients = new CopyOnWriteArrayList<>();
        this.bookingLock = new ReentrantLock(true); // Fair lock ensures FIFO ordering of booking requests
        this.inMemorySeatCache = new ConcurrentHashMap<>();

        initializeDatabaseAndCache();
    }

    /**
     * Resolves the SQLite database file path robustly across different working directories.
     */
    private String resolveDatabaseUrl() {
        String[] candidatePaths = {
                "reservation.db",
                "../reservation.db",
                "../../reservation.db",
                "../../../reservation.db",
                "c:/Users/saisa/OneDrive/Documents/Railway Reservation System/reservation.db"
        };

        for (String path : candidatePaths) {
            File f = new File(path);
            if (f.exists()) {
                System.out.println("[SERVER] Found existing database at: " + f.getAbsolutePath());
                return "jdbc:sqlite:" + f.getAbsolutePath().replace('\\', '/');
            }
        }

        // Default to local reservation.db if not found
        return "jdbc:sqlite:reservation.db";
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    /**
     * Ensures table schema exists and synchronizes in-memory seat cache from SQLite.
     */
    private void initializeDatabaseAndCache() {
        String createTableSQL = """
                CREATE TABLE IF NOT EXISTS seats (
                    train_name TEXT NOT NULL,
                    coach_number TEXT NOT NULL,
                    seat_number TEXT NOT NULL,
                    seat_state TEXT NOT NULL,
                    PRIMARY KEY (train_name, coach_number, seat_number)
                );
                """;

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);

            // Populate default train seats if empty
            String checkCountSQL = "SELECT count(*) FROM seats WHERE train_name = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkCountSQL)) {
                checkStmt.setString(1, DEFAULT_TRAIN);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        System.out.println("[SERVER] Initializing default seats for " + DEFAULT_TRAIN + "...");
                        seedTrainSeats(conn, DEFAULT_TRAIN, DEFAULT_COACH_COUNT);
                    }
                }
            }

            // Load all seats into in-memory cache for ultra-fast verification
            String loadSeatsSQL = "SELECT train_name, coach_number, seat_number, seat_state FROM seats";
            try (ResultSet rs = stmt.executeQuery(loadSeatsSQL)) {
                int loaded = 0;
                while (rs.next()) {
                    String key = makeSeatKey(
                            rs.getString("train_name"),
                            rs.getString("coach_number"),
                            rs.getString("seat_number")
                    );
                    inMemorySeatCache.put(key, rs.getString("seat_state"));
                    loaded++;
                }
                System.out.println("[SERVER] Cached " + loaded + " seat states in memory.");
            }

        } catch (SQLException e) {
            System.err.println("[SERVER] Warning initializing database: " + e.getMessage());
        }
    }

    private void seedTrainSeats(Connection conn, String trainName, int coachCount) throws SQLException {
        String insertSQL = "INSERT OR IGNORE INTO seats (train_name, coach_number, seat_number, seat_state) VALUES (?, ?, ?, 'Available')";
        conn.setAutoCommit(false);
        try (PreparedStatement pstmt = conn.prepareStatement(insertSQL)) {
            for (int c = 1; c <= coachCount; c++) {
                String coachNumber = "A" + c;
                for (int s = 1; s <= SEATS_PER_COACH; s++) {
                    String seatNumber = coachNumber + "S" + s;
                    pstmt.setString(1, trainName);
                    pstmt.setString(2, coachNumber);
                    pstmt.setString(3, seatNumber);
                    pstmt.addBatch();
                }
            }
            pstmt.executeBatch();
            conn.commit();
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private static String makeSeatKey(String train, String coach, String seat) {
        return train.trim().toLowerCase() + ":" + coach.trim().toUpperCase() + ":" + seat.trim().toUpperCase();
    }

    /**
     * Start the socket server and listen for incoming client connections.
     */
    public void start() {
        isRunning = true;
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("=================================================================");
            System.out.println(" RAILWAY RESERVATION SERVER STARTED SUCCESSFULLY");
            System.out.println(" Port: " + port);
            System.out.println(" Database: " + dbUrl);
            System.out.println(" Concurrency Control: Fair ReentrantLock + Atomic SQLite Tx");
            System.out.println(" Ready to accept multiple client connections...");
            System.out.println("=================================================================");

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(clientSocket, this);
                    activeClients.add(handler);
                    threadPool.execute(handler);
                    System.out.println("[SERVER] New user connected: " + clientSocket.getRemoteSocketAddress()
                            + " | Active users: " + activeClients.size());
                } catch (SocketException e) {
                    if (!isRunning) {
                        break; // Server was intentionally stopped
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Server exception: " + e.getMessage());
        } finally {
            stop();
        }
    }

    /**
     * Stop the server and clean up resources.
     */
    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        for (ClientHandler client : activeClients) {
            client.close();
        }
        activeClients.clear();
        threadPool.shutdownNow();
        System.out.println("[SERVER] Server stopped.");
    }

    public void removeClient(ClientHandler handler) {
        activeClients.remove(handler);
        System.out.println("[SERVER] User disconnected: " + handler.getUsername()
                + " | Active users: " + activeClients.size());
    }

    /**
     * Broadcast a message to all active connected clients.
     * If sender is provided, sender is excluded from the broadcast.
     */
    public void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : activeClients) {
            if (sender == null || client != sender) {
                client.sendMessage(message);
            }
        }
    }

    // =========================================================================
    // CONCURRENCY-SAFE RESERVATION LOGIC
    // =========================================================================

    /**
     * Atomically reserve a set of seats for a user.
     * Guarantees that if two users attempt to book the same seat at the same instant,
     * only ONE user will succeed, and the other will receive a conflict error.
     */
    public BookingResult reserveSeats(String username, String trainName, String coachNumber, List<String> seatNumbers) {
        return reserveSeats(username, trainName, coachNumber, seatNumbers, null);
    }

    public BookingResult reserveSeats(String username, String trainName, String coachNumber, List<String> seatNumbers, ClientHandler sender) {
        if (seatNumbers == null || seatNumbers.isEmpty()) {
            return new BookingResult(false, "No seats specified for reservation.", null);
        }

        // CRITICAL SECTION: Lock ensures only one booking transaction executes at a time
        bookingLock.lock();
        long startTime = System.currentTimeMillis();
        try {
            System.out.println("[LOCK ACQUIRED] User '" + username + "' is attempting to book: " + seatNumbers);

            // Phase 1: Check in-memory state and verify ALL requested seats are available
            List<String> unavailableSeats = new ArrayList<>();
            for (String seatNumber : seatNumbers) {
                String key = makeSeatKey(trainName, coachNumber, seatNumber);
                String currentState = inMemorySeatCache.get(key);

                if (currentState == null) {
                    // Seat not in cache, check database
                    currentState = querySeatStateFromDB(trainName, coachNumber, seatNumber);
                    if (currentState != null) {
                        inMemorySeatCache.put(key, currentState);
                    }
                }

                if (currentState == null) {
                    unavailableSeats.add(seatNumber + " (Not Found)");
                } else if (!"Available".equalsIgnoreCase(currentState)) {
                    unavailableSeats.add(seatNumber + " (Already " + currentState + ")");
                }
            }

            // If ANY requested seat is already reserved, reject the ENTIRE booking request
            if (!unavailableSeats.isEmpty()) {
                String errorReason = "Conflict: Seat(s) " + String.join(", ", unavailableSeats) + " are already booked by another user.";
                System.out.println("[CONFLICT REJECTED] User '" + username + "' failed: " + errorReason);
                return new BookingResult(false, errorReason, null);
            }

            // Phase 2: Atomic SQLite Update Transaction with condition (seat_state = 'Available')
            String updateSQL = """
                    UPDATE seats
                    SET seat_state = 'Reserved'
                    WHERE train_name = ? AND coach_number = ? AND seat_number = ? AND seat_state = 'Available';
                    """;

            boolean dbSuccess = false;
            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {
                    for (String seatNumber : seatNumbers) {
                        pstmt.setString(1, trainName);
                        pstmt.setString(2, coachNumber);
                        pstmt.setString(3, seatNumber);
                        int rowsUpdated = pstmt.executeUpdate();
                        if (rowsUpdated != 1) {
                            // Row was not updated, meaning another process changed it or it's not available
                            conn.rollback();
                            return new BookingResult(false, "Database conflict: Seat " + seatNumber + " could not be reserved.", null);
                        }
                    }
                    conn.commit();
                    dbSuccess = true;
                } catch (SQLException e) {
                    conn.rollback();
                    return new BookingResult(false, "Database transaction error: " + e.getMessage(), null);
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                return new BookingResult(false, "Database connection error: " + e.getMessage(), null);
            }

            if (dbSuccess) {
                // Phase 3: Update in-memory cache
                for (String seatNumber : seatNumbers) {
                    String key = makeSeatKey(trainName, coachNumber, seatNumber);
                    inMemorySeatCache.put(key, "Reserved");
                }

                String pnr = "PNR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                long elapsed = System.currentTimeMillis() - startTime;
                System.out.println("[BOOKING SUCCESS] User '" + username + "' booked seats: " + seatNumbers + " | PNR: " + pnr + " (" + elapsed + "ms)");

                // Phase 4: Broadcast real-time update to all other connected clients
                for (String seatNumber : seatNumbers) {
                    broadcast("SEAT_UPDATE " + trainName + " " + coachNumber + " " + seatNumber + " Reserved", sender);
                }

                return new BookingResult(true, "Reservation confirmed! PNR: " + pnr, pnr);
            }

            return new BookingResult(false, "Reservation failed unexpectedly.", null);

        } finally {
            bookingLock.unlock();
            System.out.println("[LOCK RELEASED] User '" + username + "' transaction completed.");
        }
    }

    /**
     * Cancels an existing reservation atomically.
     */
    public boolean cancelReservation(String username, String trainName, String coachNumber, List<String> seatNumbers) {
        bookingLock.lock();
        try {
            String updateSQL = """
                    UPDATE seats
                    SET seat_state = 'Available'
                    WHERE train_name = ? AND coach_number = ? AND seat_number = ? AND seat_state = 'Reserved';
                    """;

            try (Connection conn = getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {
                    for (String seatNumber : seatNumbers) {
                        pstmt.setString(1, trainName);
                        pstmt.setString(2, coachNumber);
                        pstmt.setString(3, seatNumber);
                        pstmt.executeUpdate();
                    }
                    conn.commit();

                    for (String seatNumber : seatNumbers) {
                        String key = makeSeatKey(trainName, coachNumber, seatNumber);
                        inMemorySeatCache.put(key, "Available");
                        broadcast("SEAT_UPDATE " + trainName + " " + coachNumber + " " + seatNumber + " Available", null);
                    }
                    return true;
                } catch (SQLException e) {
                    conn.rollback();
                    return false;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                return false;
            }
        } finally {
            bookingLock.unlock();
        }
    }

    /**
     * Get all seats and their current states for a coach.
     */
    public Map<String, String> getCoachSeats(String trainName, String coachNumber) {
        Map<String, String> result = new ConcurrentHashMap<>();
        String prefix = makeSeatKey(trainName, coachNumber, "");

        for (Map.Entry<String, String> entry : inMemorySeatCache.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String seatNumber = entry.getKey().substring(prefix.length());
                result.put(seatNumber, entry.getValue());
            }
        }

        if (result.isEmpty()) {
            // Fallback to DB
            String selectSQL = "SELECT seat_number, seat_state FROM seats WHERE train_name = ? AND coach_number = ?";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(selectSQL)) {
                pstmt.setString(1, trainName);
                pstmt.setString(2, coachNumber);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        String seat = rs.getString("seat_number");
                        String state = rs.getString("seat_state");
                        result.put(seat, state);
                        inMemorySeatCache.put(makeSeatKey(trainName, coachNumber, seat), state);
                    }
                }
            } catch (SQLException e) {
                System.err.println("[SERVER] Error querying coach seats: " + e.getMessage());
            }
        }

        return result;
    }

    private String querySeatStateFromDB(String trainName, String coachNumber, String seatNumber) {
        String query = "SELECT seat_state FROM seats WHERE train_name = ? AND coach_number = ? AND seat_number = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, trainName);
            pstmt.setString(2, coachNumber);
            pstmt.setString(3, seatNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("seat_state");
                }
            }
        } catch (SQLException ignored) {}
        return null;
    }

    public int getActiveClientCount() {
        return activeClients.size();
    }

    // =========================================================================
    // INNER CLASSES: BookingResult, ClientHandler
    // =========================================================================

    public static class BookingResult {
        public final boolean success;
        public final String message;
        public final String pnr;

        public BookingResult(boolean success, String message, String pnr) {
            this.success = success;
            this.message = message;
            this.pnr = pnr;
        }
    }

    /**
     * Handles connection, session, and commands for a single connected client.
     */
    public static class ClientHandler implements Runnable {
        private final Socket socket;
        private final Server server;
        private PrintWriter out;
        private BufferedReader in;
        private String username = "Anonymous";
        private volatile boolean running = true;

        public ClientHandler(Socket socket, Server server) {
            this.socket = socket;
            this.server = server;
        }

        public String getUsername() {
            return username;
        }

        public void sendMessage(String msg) {
            if (out != null) {
                out.println(msg);
            }
        }

        public void close() {
            running = false;
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {}
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                out.println("200 WELCOME Railway Reservation Server v1.0 | Type HELP for commands.");

                String line;
                while (running && (line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    handleCommand(line);
                }
            } catch (IOException e) {
                // Connection dropped
            } finally {
                server.removeClient(this);
                close();
            }
        }

        private void handleCommand(String line) {
            String[] parts = line.split("\\s+");
            String command = parts[0].toUpperCase();

            switch (command) {
                case "LOGIN" -> {
                    if (parts.length > 1) {
                        this.username = parts[1];
                        out.println("200 LOGIN_OK Welcome " + username);
                    } else {
                        out.println("400 ERROR Missing username. Usage: LOGIN <username>");
                    }
                }
                case "GET_SEATS" -> {
                    // Usage: GET_SEATS <trainName> <coachNumber>
                    if (parts.length >= 3) {
                        String trainName = parts[1].replace('_', ' ');
                        String coachNumber = parts[2];
                        Map<String, String> seats = server.getCoachSeats(trainName, coachNumber);

                        StringBuilder sb = new StringBuilder("200 SEAT_LIST ");
                        sb.append(trainName.replace(' ', '_')).append(" ").append(coachNumber).append(" ");
                        List<String> sortedSeats = new ArrayList<>(seats.keySet());
                        Collections.sort(sortedSeats);

                        for (int i = 0; i < sortedSeats.size(); i++) {
                            String s = sortedSeats.get(i);
                            sb.append(s).append(":").append(seats.get(s));
                            if (i < sortedSeats.size() - 1) sb.append(",");
                        }
                        out.println(sb.toString());
                    } else {
                        out.println("400 ERROR Usage: GET_SEATS <train_name> <coach_number>");
                    }
                }
                case "BOOK" -> {
                    // Usage: BOOK <trainName> <coachNumber> <seat1,seat2,...>
                    if (parts.length >= 4) {
                        String trainName = parts[1].replace('_', ' ');
                        String coachNumber = parts[2];
                        List<String> requestedSeats = Arrays.asList(parts[3].split(","));

                        BookingResult result = server.reserveSeats(username, trainName, coachNumber, requestedSeats, this);
                        if (result.success) {
                            out.println("200 BOOKING_SUCCESS " + result.pnr + " " + result.message);
                        } else {
                            out.println("409 BOOKING_CONFLICT " + result.message);
                        }
                    } else {
                        out.println("400 ERROR Usage: BOOK <train_name> <coach_number> <seat1,seat2,...>");
                    }
                }
                case "CANCEL" -> {
                    // Usage: CANCEL <trainName> <coachNumber> <seat1,seat2,...>
                    if (parts.length >= 4) {
                        String trainName = parts[1].replace('_', ' ');
                        String coachNumber = parts[2];
                        List<String> seatsToCancel = Arrays.asList(parts[3].split(","));
                        boolean success = server.cancelReservation(username, trainName, coachNumber, seatsToCancel);
                        if (success) {
                            out.println("200 CANCEL_SUCCESS Seats marked available: " + String.join(",", seatsToCancel));
                        } else {
                            out.println("400 CANCEL_FAILED Unable to cancel reservation.");
                        }
                    } else {
                        out.println("400 ERROR Usage: CANCEL <train_name> <coach_number> <seat1,seat2,...>");
                    }
                }
                case "STATUS" -> {
                    out.println("200 STATUS ActiveUsers=" + server.getActiveClientCount()
                            + " Time=" + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }
                case "HELP" -> {
                    out.println("200 COMMANDS: LOGIN <user> | GET_SEATS <train> <coach> | BOOK <train> <coach> <seats> | CANCEL <train> <coach> <seats> | STATUS | QUIT");
                }
                case "QUIT", "EXIT" -> {
                    out.println("200 BYE Connection closing.");
                    close();
                }
                default -> out.println("404 UNKNOWN_COMMAND Type HELP for available commands.");
            }
        }
    }

    // =========================================================================
    // CONCURRENCY TEST SUITE (Simulates Multiple Users Booking Same Seat)
    // =========================================================================

    public static void runConcurrencySimulation(int serverPort) {
        System.out.println("\n>>> STARTING CONCURRENCY RACE-CONDITION SIMULATION <<<");
        System.out.println("Scenario: 5 different users try to book the EXACT SAME SEAT (A1S1) simultaneously.\n");

        final String targetSeat = "A1S1";
        final String coach = "A1";
        final String train = "Vande_Bharat_Express";
        final int userCount = 5;

        // Reset seat to Available first via test connection
        try (Socket s = new Socket("localhost", serverPort);
             PrintWriter w = new PrintWriter(s.getOutputStream(), true);
             BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
            r.readLine(); // Welcome
            w.println("CANCEL " + train + " " + coach + " " + targetSeat);
            r.readLine(); // Result
        } catch (Exception ignored) {}

        ExecutorService testPool = Executors.newFixedThreadPool(userCount);
        List<String> outcomes = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finishLine = new CountDownLatch(userCount);

        for (int i = 1; i <= userCount; i++) {
            final String user = "Passenger_" + i;
            testPool.execute(() -> {
                try {
                    try (Socket socket = new Socket("localhost", serverPort);
                         PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                         BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                        in.readLine(); // Welcome banner
                        out.println("LOGIN " + user);
                        in.readLine(); // Login ack

                        // Wait for all threads to be ready
                        startGun.await();

                        // FIRE SIMULTANEOUS REQUESTS
                        out.println("BOOK " + train + " " + coach + " " + targetSeat);
                        String response;
                        while ((response = in.readLine()) != null) {
                            if (response.startsWith("200") || response.startsWith("409") || response.startsWith("400")) {
                                break;
                            }
                        }
                        outcomes.add(user + " -> " + response);
                    }
                } catch (Exception e) {
                    outcomes.add(user + " -> ERROR: " + e.getMessage());
                } finally {
                    finishLine.countDown();
                }
            });
        }

        try {
            // Trigger all threads at the exact same moment
            Thread.sleep(300);
            startGun.countDown();
            finishLine.await();
            testPool.shutdown();

            System.out.println(">>> SIMULATION RESULTS <<<");
            int successCount = 0;
            int conflictCount = 0;

            for (String outcome : outcomes) {
                System.out.println("  " + outcome);
                if (outcome.contains("200 BOOKING_SUCCESS")) {
                    successCount++;
                } else if (outcome.contains("409 BOOKING_CONFLICT")) {
                    conflictCount++;
                }
            }

            System.out.println("\n-------------------------------------------------------------");
            System.out.println("Summary: Successful Bookings = " + successCount + " | Conflict Rejections = " + conflictCount);
            if (successCount == 1 && conflictCount == (userCount - 1)) {
                System.out.println(">>> TEST PASSED: Only ONE user got the seat! No double-booking! <<<");
            } else {
                System.out.println(">>> TEST FAILED: Concurrency violation detected! <<<");
            }
            System.out.println("-------------------------------------------------------------\n");

            // Clean up: Reset seat to Available after test
            try (Socket s = new Socket("localhost", serverPort);
                 PrintWriter w = new PrintWriter(s.getOutputStream(), true);
                 BufferedReader r = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                r.readLine();
                w.println("CANCEL " + train + " " + coach + " " + targetSeat);
                r.readLine();
            } catch (Exception ignored) {}

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // =========================================================================
    // MAIN ENTRY POINT
    // =========================================================================

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        boolean runTest = false;

        for (String arg : args) {
            if (arg.equalsIgnoreCase("--test") || arg.equalsIgnoreCase("-test")) {
                runTest = true;
            } else if (arg.startsWith("--port=")) {
                try {
                    port = Integer.parseInt(arg.substring(7));
                } catch (NumberFormatException ignored) {}
            }
        }

        final Server server = new Server(port);

        // Clean shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        if (runTest) {
            // Launch server in background thread, run simulation, and exit
            Thread serverThread = new Thread(server::start);
            serverThread.setDaemon(true);
            serverThread.start();

            try {
                Thread.sleep(800); // Wait for server to bind
                runConcurrencySimulation(port);
            } catch (InterruptedException ignored) {}

            server.stop();
            System.exit(0);
        } else {
            // Normal server execution
            server.start();
        }
    }
}
