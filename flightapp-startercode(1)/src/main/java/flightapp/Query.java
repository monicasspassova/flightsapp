package flightapp;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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
  
  private static final String CLEAR_TABLES_SQL = 
    "DELETE " + 
    "FROM Users_mspass, Reservations_mspass";
  private PreparedStatement clearTablesStmt;

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
    
  //
  // Instance variables
  //
  String loggedUser = "";


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
      clearTablesStmt.executeQuery();

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

    // TODO: continue adding every time we execute query
    clearTablesStmt = conn.prepareStatement(CLEAR_TABLES_SQL);
    getPassStmt = conn.prepareStatement(GET_PASS_SQL);
    checkUsersStmt = conn.prepareStatement(CHECK_USERS_SQL);
    
  }
  

  /* See QueryAbstract.java for javadoc */
  public String transaction_login(String username, String password) {
    try{
      if (username.equals(loggedUser)){
        return "User already logged in\n";
      }
      

      getPassStmt.setString(1, username);
      ResultSet pass = getPassStmt.executeQuery();
      byte[] saltedHashPass = pass.getBytes(0);

      if (PasswordUtils.plaintextMatchesSaltedHash(password, saltedHashPass)){
        loggedUser = username;
        return "Logged in as " + username + "\n";
      }
      else{
        return "Login failed\n";
      }

    } catch (Exception e) {
      return "Login failed\n";
    }

  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    // TODO: YOUR CODE HERE

    // check that initAmount >= 0 first
    try{
      if (initAmount < 0){
        return "Failed to create user\n";
      }

      // check that user not already in db (need to execute query search)
      checkUsersStmt.setString(1, username);
      ResultSet check = checkUsersStmt.executeQuery();
      
      if (check.getBoolean(0)){
        return "Failed to create user\n";
      }


      // salt and hash pass to store in db

      // create user and insert into table

      return "Failed to create user\n";

    } catch (Exception e) {
      return "Failed to create user\n";
    }
    


  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_search(String originCity, String destinationCity, 
                                   boolean directFlight, int dayOfMonth,
                                   int numberOfItineraries) {
    // WARNING: the below code is insecure (it's susceptible to SQL injection attacks) AND only
    // handles searches for direct flights.  We are providing it *only* as an example of how
    // to use JDBC; you are required to replace it with your own secure implementation.
    //
    // TODO: YOUR CODE HERE

    StringBuffer sb = new StringBuffer();

    try {
      // one hop itineraries
      String unsafeSearchSQL =
        "     SELECT f.day_of_month, f.cid, f.op_carrier_flight_num, f.origin_city, f.dest_city,"
        + "          f.duration_mins, a.num_seats, f.price "
        + "     FROM Flights f, N_Numbers n, Aircraft_Types a"
        + "    WHERE f.tail_num = n.n_number AND n.mfr_mdl_code = a.atid"
        + "      AND origin_city = \'" + originCity + "\'"
        + "      AND dest_city = \'" + destinationCity + "\'"
        + "      AND day_of_month =  " + dayOfMonth
        + " ORDER BY duration_mins ASC"
        + "    LIMIT " + numberOfItineraries;

      Statement searchStatement = conn.createStatement();
      ResultSet oneHopResults = searchStatement.executeQuery(unsafeSearchSQL);

      while (oneHopResults.next()) {
        int result_dayOfMonth = oneHopResults.getInt("day_of_month");
        String result_carrierId = oneHopResults.getString("cid");
        String result_flightNum = oneHopResults.getString("op_carrier_flight_num");
        String result_originCity = oneHopResults.getString("origin_city");
        String result_destCity = oneHopResults.getString("dest_city");
        int result_duration = oneHopResults.getInt("duration_mins");
        int result_capacity = oneHopResults.getInt("num_seats");
        int result_price = oneHopResults.getInt("price");

        sb.append("Day: " + result_dayOfMonth + " Carrier: " + result_carrierId + " Number: "
                  + result_flightNum + " Origin: " + result_originCity + " Destination: "
                  + result_destCity + " Duration: " + result_duration + " Capacity: " + result_capacity
                  + " Price: " + result_price + "\n");
      }
      oneHopResults.close();
    } catch (SQLException e) {
      e.printStackTrace();
    }

    return sb.toString();
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_book(int itineraryId) {
    // TODO: YOUR CODE HERE
    return "Booking failed\n";
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_pay(int reservationId) {
    // TODO: YOUR CODE HERE
    return "Failed to pay for reservation " + reservationId + "\n";
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_reservations() {
    // TODO: YOUR CODE HERE
    return "Failed to retrieve reservations\n";
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
