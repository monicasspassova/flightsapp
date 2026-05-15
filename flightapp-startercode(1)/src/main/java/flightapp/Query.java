package flightapp;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
    "SELECT F.day_of_month, F.cid, F.op_carrier_flight_num, F.origin_city, F.dest_city, " +
    "F.durations_mins, A.num_seats, F.price " +
    "FROM Flights as F, N_Numbers as N, Aircraft_Types as A " +
    "WHERE F.tail_num = N.n_number AND N.mfr_mdl_code = A.atid " +
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
    "ORDER BY F.duration_mins ASC, F2.duration_mins ASC, F.fid ASC, F2.fid ASC " +
    "LIMIT ?";
  private PreparedStatement twoHopSearchStmt;
  //
  // Instance variables
  //
  String loggedUser = null;


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
      clearUsersStmt.executeQuery();
      clearResStmt.executeQuery();

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
    clearUsersStmt = conn.prepareStatement(CLEAR_USERS_SQL);
    clearResStmt = conn.prepareStatement(CLEAR_RES_SQL);
    getPassStmt = conn.prepareStatement(GET_PASS_SQL);
    checkUsersStmt = conn.prepareStatement(CHECK_USERS_SQL);
    insertUserStmt = conn.prepareStatement(INSERT_USER_SQL);
    oneHopSearchStmt = conn.prepareStatement(ONE_HOP_SEARCH_SQL);
    twoHopSearchStmt = conn.prepareStatement(TWO_HOP_SEARCH_SQL);

  }
  


  /* See QueryAbstract.java for javadoc */
  public String transaction_login(String username, String password) {
    try{
      if (loggedUser != null){
        return "User already logged in\n";
      }
      

      getPassStmt.setString(1, username.toUpperCase());
      ResultSet pass = getPassStmt.executeQuery();
      pass.next();
      byte[] saltedHashPass = pass.getBytes(1);

      if (PasswordUtils.plaintextMatchesSaltedHash(password, saltedHashPass)){
        loggedUser = username.toUpperCase();
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
      checkUsersStmt.setString(1, username.toUpperCase());
      ResultSet check = checkUsersStmt.executeQuery();

      if (check.next() && check.getInt(1) > 0 ){
        return "Failed to create user\n";
      }      

      // salt and hash pass to store in db
      byte[] dbPass = PasswordUtils.saltAndHashPassword(password);

      // create user and insert into table
      insertUserStmt.setString(1, username.toUpperCase());
      insertUserStmt.setBytes(2, dbPass);
      insertUserStmt.setInt(3, initAmount);

      insertUserStmt.executeUpdate();

      return "Created user " + username +"\n";

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

    StringBuffer sb = new StringBuffer();
    ArrayList<Object[]> itineraries = new ArrayList<>();

    try {

      // get direct results first

      // then if needed, get indirect

      // add together, sort in ascending order, then cut off rest 

      
      // one hop itineraries
      oneHopSearchStmt.setString(1, originCity);
      oneHopSearchStmt.setString(2, destinationCity);
      oneHopSearchStmt.setInt(3, dayOfMonth);
      oneHopSearchStmt.setInt(4, numberOfItineraries);
      ResultSet oneHopResults = oneHopSearchStmt.executeQuery();

      while(oneHopResults.next()){
        Object[] it = {
          1,
          oneHopResults.getInt("duration_mins"), // total duration


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
        twoHopSearchStmt.setString(1, originCity);
        twoHopSearchStmt.setString(2, destinationCity);
        twoHopSearchStmt.setInt(3, dayOfMonth);
        twoHopSearchStmt.setInt(4, numberOfItineraries - itineraries.size());
        ResultSet twoHopResults = twoHopSearchStmt.executeQuery();

        while(twoHopResults.next()){
          Object[] it = {
            2,
            twoHopResults.getInt("duration_mins") + twoHopResults.getInt("duration_mins2"),

            twoHopResults.getInt("fid"),
            twoHopResults.getInt("day_of_month"),
            twoHopResults.getString("cid"),
            twoHopResults.getInt("op_carrier_flight_num"),
            twoHopResults.getString("origin_city"),
            twoHopResults.getString("dest_city"),
            twoHopResults.getInt("duration_mins"),
            twoHopResults.getInt("capacity"),
            twoHopResults.getInt("price"),

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

    
      if (itineraries.isEmpty()){
        return "No flights match your selection\n";
      }

      itineraries.sort(Comparator.comparingInt(a -> (int) a[1]));
      
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

        sb.append("Itinerary " + index + ": " + result_numFlights + " flights(s), " + 
                  result_totalDuration + " minutes\n");
        
        sb.append("ID: " + result_fid + " Day: " + result_dayOfMonth + " Carrier: " + result_carrierId + " Number: "
                  + result_carrierNum + " Origin: " + result_originCity + " Destination: "
                  + result_destCity + " Duration: " + result_duration + " Capacity: " + result_capacity
                  + " Price: " + result_price + "\n");

        index++;

        if (result_numFlights == 1){
          continue;
        }

        int result_fid2 = (int) it[11];
        int result_dayOfMonth2 = (int) it[12];
        String result_carrierId2 = (String) it[13];
        int result_carrierNum2 = (int) it[14];
        String result_originCity2 = (String) it[15];
        String result_destCity2 = (String) it[16];
        int result_duration2 = (int) it[17];
        int result_capacity2 = (int) it[18];
        int result_price2 = (int) it[19];
        
        sb.append("ID: " + result_fid2 + " Day: " + result_dayOfMonth2 + " Carrier: " + result_carrierId2 + " Number: "
                  + result_carrierNum2 + " Origin: " + result_originCity2 + " Destination: "
                  + result_destCity2 + " Duration: " + result_duration2 + " Capacity: " + result_capacity2
                  + " Price: " + result_price2 + "\n");

      }

    
    } catch (SQLException e) {
      e.printStackTrace();
      return "Failed to search\n";
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
