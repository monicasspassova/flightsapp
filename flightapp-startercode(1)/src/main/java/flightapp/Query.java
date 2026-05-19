package flightapp;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Runs queries against a back-end database
 */
public class Query extends QueryAbstract {
  //
  // Canned queries
  //

  // Example constant and instance variable that uses PreparedStatements.  You
  // DO NOT NEED to use this in your implementation; it's merely an example of
  // how to structure your constants, instance variables, initialization code,
  // utility functions, etc.
  private static final String FLIGHT_CAPACITY_SQL =
    "     SELECT a.num_seats"
    + "     FROM Flights f, N_Numbers n, Aircraft_Types a"
    + "    WHERE f.tail_num = n.n_number"
    + "      AND n.mfr_mdl_code = a.atid"
    + "      AND fid = ?";
  private PreparedStatement flightCapacityStmt;
  
  private static final String CLEAR_USERS_SQL = 
    "DELETE " + 
    "FROM Users_mspass";
  private PreparedStatement clearUsersStmt;

  private static final String CLEAR_RES_SQL = 
    "DELETE " +
    "FROM Reservations_mspass";
  private PreparedStatement clearResStmt;

  private static final String GET_PASS_SQL =
    "SELECT password " +
    "FROM Users_mspass " +
    "WHERE username = ?";
  private PreparedStatement getPassStmt;

  private static final String CHECK_USERS_SQL =
    "SELECT count(*) " +
    "FROM Users_mspass " +
    "WHERE username = ?";
  private PreparedStatement checkUsersStmt;

  private static final String INSERT_USER_SQL = 
    "INSERT INTO Users_mspass " +
    "VALUES (?, ?, ?)";
  private PreparedStatement insertUserStmt;

  private static final String ONE_HOP_SEARCH_SQL = 
    "SELECT F.fid, F.day_of_month, F.cid, F.op_carrier_flight_num, F.origin_city, F.dest_city, " +
    "F.duration_mins, A.num_seats as capacity, F.price " +
    "FROM Flights as F, N_Numbers as N, Aircraft_Types as A " +
    "WHERE F.tail_num = N.n_number AND N.mfr_mdl_code = A.atid " +
      "AND F.cancelled = 0 " + 
      "AND F.origin_city = ? " +
      "AND F.dest_city = ? " +
      "AND F.day_of_month = ? " +
    "ORDER BY F.duration_mins ASC " +
    "LIMIT ?";
  private PreparedStatement oneHopSearchStmt;

  private static final String TWO_HOP_SEARCH_SQL = 
    "SELECT F.fid, F2.fid as fid2, F.day_of_month, F.cid, F.op_carrier_flight_num, F.origin_city, F.dest_city, " +
    "F.duration_mins, A.num_seats as capacity, F.price, F2.cid as cid2, F2.op_carrier_flight_num as op_carrier_flight_num2, " + 
    "F2.origin_city as origin_city2, F2.dest_city as dest_city2, F2.duration_mins as duration_mins2, A2.num_seats as capacity2, F2.price as price2 " +
    "FROM Flights as F, Flights as F2, N_Numbers as N, N_Numbers as N2, Aircraft_Types as A, Aircraft_Types as A2 " +
    "WHERE F.tail_num = N.n_number AND N.mfr_mdl_code = A.atid " +
      "AND F2.tail_num = N2.n_number AND N2.mfr_mdl_code = A2.atid " +
      "AND F.cancelled = 0 AND F2.cancelled = 0 " +
      "AND F.origin_city = ? " +
      "AND F2.dest_city = ? " +
      "AND F.dest_city = F2.origin_city " +
      "AND F.day_of_month = ? " +
      "AND F.day_of_month = F2.day_of_month " +
    "ORDER BY F.duration_mins + F2.duration_mins ASC, F.fid ASC, F2.fid ASC " +
    "LIMIT ?";
  private PreparedStatement twoHopSearchStmt;

  private static final String CHECK_BOOK_DAY_SQL = 
    "SELECT count(*) " +
    "FROM Reservations_mspass as R, Flights as F " +
    "WHERE (R.fid1 = F.fid OR R.fid2 = F.fid) AND F.day_of_month = ? AND userid = ?";
  private PreparedStatement checkBookDayStmt;

  private static final String BOOK_RESERVATION_SQL =
    "INSERT INTO Reservations_mspass " + 
    "VALUES (?, ?, 0, ?, ?)";
  private PreparedStatement bookReservationStmt;

  private static final String FIND_RESERVATION_SQL = 
    "SELECT * " +
    "FROM Reservations_mspass " +
    "WHERE rid = ? AND userid = ? AND paid = 0";
  private PreparedStatement findReservationStmt;

  private static final String FIND_PRICE_SQL =
    "SELECT price " +
    "FROM Flights " +
    "WHERE fid = ?";
  private PreparedStatement findPriceStmt;

  private static final String GET_USER_BALANCE_SQL =
    "SELECT balance " +
    "FROM Users_mspass " +
    "WHERE username = ?";
  private PreparedStatement getUserBalanceStmt;

  private static final String UPDATE_PAID_RES_SQL =
    "UPDATE Reservations_mspass " +
    "SET paid = 1 " +
    "WHERE rid = ?";
  private PreparedStatement updatePaidResStmt;

  private static final String UPDATE_USER_BALANCE_SQL =
    "UPDATE Users_mspass " +
    "SET balance = ? " +
    "WHERE username = ?";
  private PreparedStatement updateUserBalanceStmt;

  private static final String GET_RESERVATIONS_SQL = 
    "SELECT * " +
    "FROM Reservations_mspass " +
    "WHERE userid = ?";
  private PreparedStatement getReservationsStmt;

  private static final String GET_FLIGHT_INFO_SQL = 
    "SELECT fid, day_of_month, cid, op_carrier_flight_num, origin_city, dest_city, duration_mins, price " +
    "FROM Flights " +
    "WHERE fid = ?";
  private PreparedStatement getFlightInfoStmt;

  private static final String CHECK_CAPACITY_SQL =
    "SELECT count(*) " +
    "FROM Reservations_mspass " +
    "WHERE fid1 = ? OR fid2 = ?";
  private PreparedStatement checkCapacityStmt;


  //
  // Instance variables
  //
  String loggedUser = null;
  ArrayList<Object[]> searchResults = new ArrayList<Object[]>();
  private static int rid = 1;

  protected Query() throws SQLException, IOException {
    prepareStatements();
  }

  /**
   * Clear the data in any custom tables created.
   * 
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
      clearResStmt.executeUpdate();
      clearUsersStmt.executeUpdate();
      rid = 1;

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * prepare all the SQL statements in this method.
   */
  private void prepareStatements() throws SQLException {
    // Example initialization of a PreparedStatement. You DO NOT NEED to use
    // this in your implementation; it's merely an example of how to structure
    // your constants, instance variables, initialization code, utility
    // functions, etc.
    flightCapacityStmt = conn.prepareStatement(FLIGHT_CAPACITY_SQL);

    clearUsersStmt = conn.prepareStatement(CLEAR_USERS_SQL);
    clearResStmt = conn.prepareStatement(CLEAR_RES_SQL);
    getPassStmt = conn.prepareStatement(GET_PASS_SQL);
    checkUsersStmt = conn.prepareStatement(CHECK_USERS_SQL);
    insertUserStmt = conn.prepareStatement(INSERT_USER_SQL);
    oneHopSearchStmt = conn.prepareStatement(ONE_HOP_SEARCH_SQL);
    twoHopSearchStmt = conn.prepareStatement(TWO_HOP_SEARCH_SQL);
    checkBookDayStmt = conn.prepareStatement(CHECK_BOOK_DAY_SQL);
    bookReservationStmt = conn.prepareStatement(BOOK_RESERVATION_SQL);
    findReservationStmt = conn.prepareStatement(FIND_RESERVATION_SQL);
    findPriceStmt = conn.prepareStatement(FIND_PRICE_SQL);
    getUserBalanceStmt = conn.prepareStatement(GET_USER_BALANCE_SQL);
    updatePaidResStmt = conn.prepareStatement(UPDATE_PAID_RES_SQL);
    updateUserBalanceStmt = conn.prepareStatement(UPDATE_USER_BALANCE_SQL);
    getReservationsStmt = conn.prepareStatement(GET_RESERVATIONS_SQL);
    getFlightInfoStmt = conn.prepareStatement(GET_FLIGHT_INFO_SQL);
    checkCapacityStmt = conn.prepareStatement(CHECK_CAPACITY_SQL);

  }
  


  /* See QueryAbstract.java for javadoc */
  public String transaction_login(String username, String password) {
    try{
      // check if user is already logged in first
      // then verify password
      // then login user

      // check if user is already logged in
      if (loggedUser != null){
        return "User already logged in\n";
      }

      // start transaction
      conn.setAutoCommit(false);

      // get password to compare from users database
      getPassStmt.clearParameters();
      getPassStmt.setString(1, username.toLowerCase());
      ResultSet pass = getPassStmt.executeQuery();

      // read password data
      pass.next();
      byte[] saltedHashPass = pass.getBytes(1);

      // end transaction
      conn.commit();
      conn.setAutoCommit(true);

      // compare password to password in database
      if (PasswordUtils.plaintextMatchesSaltedHash(password, saltedHashPass)){
        loggedUser = username.toLowerCase();
        searchResults = new ArrayList<>();
        return "Logged in as " + username + "\n";
      }
      else{
        return "Login failed\n";
      }

    } catch (SQLException e){
      try {
        conn.rollback();
        conn.setAutoCommit(true);

        // retry transaction if possible
        if (isRetryable(e)){
          return transaction_login(username, password); 
        }
      } catch (SQLException exc){
        exc.printStackTrace();
      }

      return "Login failed\n";

    } catch (Exception e) {
      return "Login failed\n";
    }
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    try{
      // check valid initial amount
      // then check user doesn't already exist
      // then create and store customer info
      
      // check that initial amount >= 0 first
      if (initAmount < 0){
        return "Failed to create user\n";
      }

      // start transaction
      conn.setAutoCommit(false);

      // check that user not already in db
      checkUsersStmt.clearParameters();
      checkUsersStmt.setString(1, username.toLowerCase());
      ResultSet check = checkUsersStmt.executeQuery();

      // if user already exists
      if (check.next() && check.getInt(1) > 0 ){
        conn.rollback();
        conn.setAutoCommit(true);
        return "Failed to create user\n";
      }      

      // salt and hash pass to store in db
      byte[] dbPass = PasswordUtils.saltAndHashPassword(password);

      // create user and insert into table
      insertUserStmt.clearParameters();
      insertUserStmt.setString(1, username.toLowerCase());
      insertUserStmt.setBytes(2, dbPass);
      insertUserStmt.setInt(3, initAmount);
      insertUserStmt.executeUpdate();

      // end transaction
      conn.commit();
      conn.setAutoCommit(true);

      // successful user creation
      return "Created user " + username +"\n";

    } catch (SQLException e){
      try {
        conn.rollback();
        conn.setAutoCommit(true);

        // retry transaction if possible
        if (isRetryable(e)){
          return transaction_createCustomer(username, password, initAmount);
        }
      } catch (SQLException exc){
        exc.printStackTrace();
      }

      return "Failed to create user\n";

    } catch (Exception e) {
      return "Failed to create user\n";
    }
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_search(String originCity, String destinationCity, 
                                   boolean directFlight, int dayOfMonth,
                                   int numberOfItineraries) {

    // initialize return string and itineraries to display
    StringBuffer sb = new StringBuffer();
    ArrayList<Object[]> itineraries = new ArrayList<>();

    try {
      // get direct results first
      // then if needed, get indirect
      // add together and sort in ascending order
      // no manual transaction needed since only reading data from immutable table

      // one hop itineraries
      oneHopSearchStmt.clearParameters();
      oneHopSearchStmt.setString(1, originCity);
      oneHopSearchStmt.setString(2, destinationCity);
      oneHopSearchStmt.setInt(3, dayOfMonth);
      oneHopSearchStmt.setInt(4, numberOfItineraries);
      ResultSet oneHopResults = oneHopSearchStmt.executeQuery();

      while(oneHopResults.next()){
        Object[] it = {
          1, // num of flights (direct)
          oneHopResults.getInt("duration_mins"), // total duration for direct flights
          oneHopResults.getInt("fid"),
          oneHopResults.getInt("day_of_month"),
          oneHopResults.getString("cid"),
          oneHopResults.getInt("op_carrier_flight_num"),
          oneHopResults.getString("origin_city"),
          oneHopResults.getString("dest_city"),
          oneHopResults.getInt("duration_mins"),
          oneHopResults.getInt("capacity"),
          oneHopResults.getInt("price")
        };

        itineraries.add(it);
      }
      oneHopResults.close();

      if (!directFlight){

        twoHopSearchStmt.clearParameters();
        twoHopSearchStmt.setString(1, originCity);
        twoHopSearchStmt.setString(2, destinationCity);
        twoHopSearchStmt.setInt(3, dayOfMonth);
        twoHopSearchStmt.setInt(4, numberOfItineraries - itineraries.size());
        ResultSet twoHopResults = twoHopSearchStmt.executeQuery();

        while(twoHopResults.next()){
          Object[] it = {
            2, // num of flights (indirect)
            // total duration for indirect flights
            twoHopResults.getInt("duration_mins") + twoHopResults.getInt("duration_mins2"), 

            // flight 1 info
            twoHopResults.getInt("fid"),
            twoHopResults.getInt("day_of_month"),
            twoHopResults.getString("cid"),
            twoHopResults.getInt("op_carrier_flight_num"),
            twoHopResults.getString("origin_city"),
            twoHopResults.getString("dest_city"),
            twoHopResults.getInt("duration_mins"),
            twoHopResults.getInt("capacity"),
            twoHopResults.getInt("price"),

            // flight 2 info
            twoHopResults.getInt("fid2"),
            twoHopResults.getInt("day_of_month"),
            twoHopResults.getString("cid2"),
            twoHopResults.getInt("op_carrier_flight_num2"),
            twoHopResults.getString("origin_city2"),
            twoHopResults.getString("dest_city2"),
            twoHopResults.getInt("duration_mins2"),
            twoHopResults.getInt("capacity2"),
            twoHopResults.getInt("price2"),

          };

          itineraries.add(it);
        }

        twoHopResults.close();
      }


      searchResults = itineraries;
      // check if no search results
      if (itineraries.isEmpty()){
        return "No flights match your selection\n";
      }

      // sort results in ascending order
      itineraries.sort(Comparator.comparingInt((Object[] a) -> (int) a[1])
                      .thenComparingInt(a -> (int) a[2]) // break tie with fid
                      .thenComparingInt(a -> (int) a[0] == 2 ? (int) a[11] : -1)); // break tie with fid2 if indirect
      
      

      // read itineraries and create return string
      int index = 0;
      for(Object[] it : itineraries) {
        int result_numFlights = (int) it[0];
        int result_totalDuration = (int) it[1];
        int result_fid = (int) it[2];
        int result_dayOfMonth = (int) it[3];
        String result_carrierId = (String) it[4];
        int result_carrierNum = (int) it[5];
        String result_originCity = (String) it[6];
        String result_destCity = (String) it[7];
        int result_duration = (int) it[8];
        int result_capacity = (int) it[9];
        int result_price = (int) it[10];

        sb.append("Itinerary " + index + ": " + result_numFlights + " flight(s), " + 
                  result_totalDuration + " minutes\n");
        
        sb.append("    ID:" + result_fid + " Day:" + result_dayOfMonth + " Carrier:" + result_carrierId + " CarrierNum:"
                  + result_carrierNum + " Origin:'" + result_originCity + "' Dest:'"
                  + result_destCity + "' Duration:" + result_duration + " Capacity:" + result_capacity
                  + " Price:" + result_price + "\n");

        index++;

        // if direct flight, skip adding the second flight info
        if (result_numFlights == 1){
          continue;
        }

        // add second flight info
        int result_fid2 = (int) it[11];
        int result_dayOfMonth2 = (int) it[12];
        String result_carrierId2 = (String) it[13];
        int result_carrierNum2 = (int) it[14];
        String result_originCity2 = (String) it[15];
        String result_destCity2 = (String) it[16];
        int result_duration2 = (int) it[17];
        int result_capacity2 = (int) it[18];
        int result_price2 = (int) it[19];
        
        sb.append("    ID:" + result_fid2 + " Day:" + result_dayOfMonth2 + " Carrier:" + result_carrierId2 + " CarrierNum:"
                  + result_carrierNum2 + " Origin:'" + result_originCity2 + "' Dest:'"
                  + result_destCity2 + "' Duration:" + result_duration2 + " Capacity:" + result_capacity2
                  + " Price:" + result_price2 + "\n");

      }

    
    } catch (SQLException e) {
      e.printStackTrace();
      return "Failed to search\n";
    }

    // save most recent search results for reading later
    searchResults = itineraries;

    return sb.toString();
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_book(int itineraryId) {
    try{
      // check if user logged in first
      // check itineraryId validity
      // check if day already booked by user
      // check flight capacity to see if user can book it
      // then finally book reservation, update reservations table

      // check if logged in yet or not
      if (loggedUser == null){
        return "Cannot book reservations, not logged in\n";
      }

      // check itId validity
      if (searchResults.isEmpty() || searchResults.get(itineraryId) == null || itineraryId >= searchResults.size()){
        return "No such itinerary " + itineraryId + "\n";
      }

      // get itinerary info from search results
      Object[] itinerary = searchResults.get(itineraryId);

      // start transaction
      conn.setAutoCommit(false);

      // check if day already booked
      checkBookDayStmt.clearParameters();
      checkBookDayStmt.setInt(1, (int) itinerary[3]);
      checkBookDayStmt.setString(2, loggedUser);
      ResultSet result = checkBookDayStmt.executeQuery();

      result.next();

      if(result.getInt(1) > 0){
        return "You cannot book two flights in the same day\n";
      }

      // check flight capacity
      checkCapacityStmt.clearParameters();
      checkCapacityStmt.setInt(1, (int)itinerary[2]);
      checkCapacityStmt.setInt(2, (int)itinerary[2]);
      ResultSet capacity = checkCapacityStmt.executeQuery();

      capacity.next();
      if(capacity.getInt(1) >= getFlightCapacity((int)itinerary[2])){
        conn.rollback();
        conn.setAutoCommit(true);
        return "Booking failed\n";
      }

      // check second flight capacity if necessary
      if ((int) itinerary[0] == 2){
        checkCapacityStmt.clearParameters();
        checkCapacityStmt.setInt(1, (int)itinerary[11]);
        checkCapacityStmt.setInt(2, (int)itinerary[11]);
        capacity = checkCapacityStmt.executeQuery();

        capacity.next();
        if (capacity.getInt(1) >= getFlightCapacity((int)itinerary[11])){
          conn.rollback();
          conn.setAutoCommit(true);
          return "Booking failed\n";
        }
      }
      

      // can now book reservation (insert into table)
      bookReservationStmt.clearParameters();
      bookReservationStmt.setInt(1, rid);
      bookReservationStmt.setString(2, loggedUser);
      bookReservationStmt.setInt(3, (int)itinerary[2]);

      if ((int)itinerary[0] == 2){
        bookReservationStmt.setInt(4, (int)itinerary[11]);
      }
      else {
        bookReservationStmt.setNull(4, Types.INTEGER);
      }

      bookReservationStmt.executeUpdate();

      // end transaction
      conn.commit();
      conn.setAutoCommit(true);

      // increment global rid after successful booking
      rid += 1;

      // successful booking
      return "Booked flight(s), reservation ID: " + (rid-1) + "\n";

    } catch (SQLException e){
      try {
        conn.rollback();
        conn.setAutoCommit(true);
        if (isRetryable(e)){
          return transaction_book(itineraryId);
        }
      } catch (SQLException exc){
        exc.printStackTrace();
      }

      return "Booking failed\n";

    } catch (Exception e){
      return "Booking failed\n";
    }
    
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_pay(int reservationId) {
    try{
      // check if a user is logged in
      // get user's unpaid reservations
      // get price for reservation
      // check that user can pay for reservation
      // finally pay reservation, update users and reservations tables

      // check if logged in yet or not
      if (loggedUser == null){
        return "Cannot pay, not logged in\n";
      }

      // start transaction
      conn.setAutoCommit(false);

      // find reservation
      findReservationStmt.clearParameters();
      findReservationStmt.setInt(1, reservationId);
      findReservationStmt.setString(2, loggedUser);
      ResultSet reservation = findReservationStmt.executeQuery();

      // check that there are unpaid reservations
      if (!reservation.next()){
        return "Cannot find unpaid reservation " + reservationId + " under user: " + loggedUser + "\n";
      }

      // save fids for later
      int fid1 = reservation.getInt("fid1");
      int fid2 = reservation.getInt("fid2");

      // determine if reservation is direct or not
      if (reservation.wasNull()){
        fid2 = -1;
      }

      // find price of reservation
      int totalPrice = 0;

      findPriceStmt.clearParameters();
      findPriceStmt.setInt(1, fid1);
      ResultSet res = findPriceStmt.executeQuery();
      res.next();
      totalPrice += res.getInt("price");
      
      // add second flight price if necessary
      if (fid2 != -1){
        findPriceStmt.setInt(1,fid2);
        res = findPriceStmt.executeQuery();
        res.next();
        totalPrice += res.getInt("price");
      }

      // determine if we have enough to pay (get user balance)
      getUserBalanceStmt.clearParameters();
      getUserBalanceStmt.setString(1, loggedUser);
      ResultSet bal = getUserBalanceStmt.executeQuery();

      bal.next();
      int balance = bal.getInt("balance");

      // check if user doesn't have adequate balance
      if (balance < totalPrice){
        return "User has only " + balance + " in account but itinerary costs " + totalPrice + "\n";
      }

      // we have enough to pay, need to update reservations (paid col) and users (subtract balance)
      balance = balance - totalPrice;

      updatePaidResStmt.clearParameters();
      updatePaidResStmt.setInt(1, reservationId);
      updatePaidResStmt.executeUpdate();
      updateUserBalanceStmt.setInt(1, balance);
      updateUserBalanceStmt.setString(2, loggedUser);
      updateUserBalanceStmt.executeUpdate();

      // end transaction
      conn.commit();
      conn.setAutoCommit(true);

      // successful pay
      return "Paid reservation: " + reservationId + " remaining balance: " + balance + "\n";

    } catch (SQLException e){
      try {
        conn.rollback();
        conn.setAutoCommit(true);

        // retry transaction if possible
        if (isRetryable(e)){ 
          return transaction_pay(reservationId);
        }
      } catch (SQLException exc){
        exc.printStackTrace();
      }

      return "Failed to pay for reservation " + reservationId + "\n";

    } catch (Exception e){
      return "Failed to pay for reservation " + reservationId + "\n";
    }
    
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_reservations() {
    // check if logged in
    // then get reservations for user
    // get flight info for each reservation

    // initialize return string and user reservations list 
    StringBuffer sb = new StringBuffer();
    ArrayList<Object[]> reservations = new ArrayList<>();

    try{
      // check if logged in yet or not
      if (loggedUser == null){
        return "Cannot view reservations, not logged in\n";
      }

      // start transaction
      conn.setAutoCommit(false);

      // get user reservations
      getReservationsStmt.clearParameters();
      getReservationsStmt.setString(1, loggedUser);
      ResultSet resResults = getReservationsStmt.executeQuery();

      // check if user has any reservations
      if (!resResults.next()){
        return "No reservations found\n";
      }

      // get reservation info
      do {
        int fid2 = resResults.getInt("fid2");
        boolean dir = resResults.wasNull();
        Object[] rsv = {
          resResults.getInt("rid"),
          resResults.getInt("paid"),
          resResults.getInt("fid1"),
          dir ? -1 : fid2
        };

        reservations.add(rsv);
      } while (resResults.next());

      // read reservation info, find flight info
      for (Object[] rs : reservations){
        getFlightInfoStmt.clearParameters();
        getFlightInfoStmt.setInt(1, (int) rs[2]);
        ResultSet flightInfo = getFlightInfoStmt.executeQuery();

        // read flight info
        flightInfo.next();
        int fid = flightInfo.getInt("fid");
        int dayOfMonth = flightInfo.getInt("day_of_month");
        String cid = flightInfo.getString("cid");
        int carrierNum = flightInfo.getInt("op_carrier_flight_num");
        String origin = flightInfo.getString("origin_city");
        String dest = flightInfo.getString("dest_city");
        int duration = flightInfo.getInt("duration_mins");
        int capacity = getFlightCapacity((int)rs[2]);
        int price = flightInfo.getInt("price");

        // create/add reservation string
        int rid = (int) rs[0];
        String paid = (int) rs[1] == 1 ? "paid" : "unpaid";

        sb.append("Reservation " + rid + " (" + paid + "):\n");
        sb.append("    ID:" + fid + " Day:" + dayOfMonth + " Carrier:" + cid
          + " CarrierNum:" + carrierNum + " Origin:'" + origin + "' Dest:'" + dest
          + "' Duration:" + duration + " Capacity:" + capacity + " Price:" + price + "\n");

        // check if reservation is direct
        if ((int) rs[3] == -1){
          continue;
        }

        // continue getting flight info/adding to string for second flight if necessary
        getFlightInfoStmt.clearParameters();
        getFlightInfoStmt.setInt(1, (int) rs[3]);
        ResultSet flightInfo2 = getFlightInfoStmt.executeQuery();

        flightInfo2.next();
        int fid2 = flightInfo2.getInt("fid");
        int dayOfMonth2 = flightInfo2.getInt("day_of_month");
        String cid2 = flightInfo2.getString("cid");
        int carrierNum2 = flightInfo2.getInt("op_carrier_flight_num");
        String origin2 = flightInfo2.getString("origin_city");
        String dest2 = flightInfo2.getString("dest_city");
        int duration2 = flightInfo2.getInt("duration_mins");
        int capacity2 = getFlightCapacity((int)rs[3]);
        int price2 = flightInfo2.getInt("price");

        sb.append("    ID:" + fid2 + " Day:" + dayOfMonth2 + " Carrier:" + cid2
          + " CarrierNum:" + carrierNum2 + " Origin:'" + origin2 + "' Dest:'" + dest2
          + "' Duration:" + duration2 + " Capacity:" + capacity2 + " Price:" + price2 + "\n");

      }

      // end transaction
      conn.commit();
      conn.setAutoCommit(true);

      return sb.toString();

    } catch (SQLException e){
      try {
        conn.rollback();
        conn.setAutoCommit(true);

        // retry transaction if possible
        if (isRetryable(e)){
          return transaction_reservations();
        }
      } catch (SQLException exc){
        exc.printStackTrace();
      }
      return "Failed to retrieve reservations\n";
    } catch (Exception e){
      return "Failed to retrieve reservations\n";
    }

  }

  /**
   * Example utility function that uses PreparedStatements.  You DO NOT NEED
   * to use this in your implementation; it's merely an example of how to
   * structure your constants, instance variables, initialization code,
   * utility functions, etc.
   */
  private int getFlightCapacity(int fid) throws SQLException {
    flightCapacityStmt.clearParameters();
    flightCapacityStmt.setInt(1, fid);

    ResultSet results = flightCapacityStmt.executeQuery();
    results.next();
    int capacity = results.getInt("num_seats");
    results.close();

    return capacity;
  }

  /**
   * Utility function to determine whether an error was caused by a retryable
   * error, such as a deadlock.
   */
  private static boolean isRetryable(SQLException e) {
    return "40001".equals(e.getSQLState()) || "40P01".equals(e.getSQLState());
  }

  /**
   * A class to store information about a single flight
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String carrierNum;
    public String originCity;
    public String destCity;
    public int duration;
    public int capacity;
    public int price;

    Flight(int id, int day, String carrier, String cnum, String origin, String dest, int dur,
           int cap, int pri) {
      fid = id;
      dayOfMonth = day;
      carrierId = carrier;
      carrierNum = cnum;
      originCity = origin;
      destCity = dest;
      duration = dur;
      capacity = cap;
      price = pri;
    }
    
    @Override
    public String toString() {
      return "    ID:" + fid + " Day:" + dayOfMonth + " Carrier:" + carrierId
          + " CarrierNum:" + carrierNum + " Origin:'" + originCity + "' Dest:'" + destCity
          + "' Duration:" + duration + " Capacity:" + capacity + " Price:" + price;
    }
  }
}
