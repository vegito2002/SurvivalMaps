package com.oose2016.group4.server;

import com.google.gson.Gson;
//import com.todoapp.Todo;
//import com.todoapp.TestTodoServer.Response;


import org.sql2o.Connection;
import org.sql2o.Query;
import org.sql2o.Sql2o;
import org.sql2o.Sql2oException;
import org.sqlite.SQLiteDataSource;

import spark.Spark;
import spark.utils.IOUtils;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

//import org.testng.annotations.Test;

public class ServerTest {
	
	private final Logger logger = LoggerFactory.getLogger(ServerTest.class);
	
	SQLiteDataSource dSource;
	// ------------------------------------------------------------------------//
	// Setup - based on To-Do server unit tests.
	// ------------------------------------------------------------------------//

	
	@Before
	public void setup() throws Exception {
		// Clear the database and then start the server
		dSource = clearDB();

		// Start the main server
		Bootstrap.main(null);
		Spark.awaitInitialization();
	}
	
	
	@After
	public void tearDown() {
		// Stop the server
		clearDB();
		Spark.stop();
	}

	// ------------------------------------------------------------------------//
	// Tests
	// ------------------------------------------------------------------------//

	@Test
	public void testGetLatLong() throws Exception {
		Coordinate c = new Coordinate(0.5, 0.7);
		assertEquals(0.5, c.getLatitude(), 0);
		assertEquals(0.7, c.getLongitude(), 0);
	}
	
	@Test
	public void testSortAndExpand() throws Exception {
		//c1 < c2
		Coordinate c1 = new Coordinate(0.5, 0.7);
		Coordinate c2 = new Coordinate(0.9, 1.1);
		Coordinate.sortAndExpand(c1, c2);
		assertEquals(0.49, c1.getLatitude(), 0);
		assertEquals(0.6886, c1.getLongitude(), 0.01);
		assertEquals(0.91, c2.getLatitude(), 0);
		assertEquals(1.11608, c2.getLongitude(), 0.01);

		//c3 > c4
		Coordinate c3 = new Coordinate(0.9, 1.1);
		Coordinate c4 = new Coordinate(0.5, 0.7);
		Coordinate.sortAndExpand(c3, c4);
		assertEquals(0.49, c3.getLatitude(), 0);
		assertEquals(0.6886, c3.getLongitude(), 0.01);
		assertEquals(0.91, c4.getLatitude(), 0);
		assertEquals(1.11608, c4.getLongitude(), 0.01);
		
		//c5.lat < c6.lat; c5.long > c6.long
		Coordinate c5 = new Coordinate(0.5, 1.1);
		Coordinate c6 = new Coordinate(0.9, 0.7);
		Coordinate.sortAndExpand(c5, c6);
		assertEquals(0.49, c5.getLatitude(), 0);
		assertEquals(0.6886, c5.getLongitude(), 0.01);
		assertEquals(0.91, c6.getLatitude(), 0);
		assertEquals(1.11608, c6.getLongitude(), 0.01);
		
		//c7.lat > c8.lat; c5.long < c6.long
		Coordinate c7 = new Coordinate(0.9, 0.7);
		Coordinate c8 = new Coordinate(0.5, 1.1);
		Coordinate.sortAndExpand(c7, c8);
		assertEquals(0.49, c7.getLatitude(), 0);
		assertEquals(0.6886, c7.getLongitude(), 0.01);
		assertEquals(0.91, c8.getLatitude(), 0);
		assertEquals(1.11608, c8.getLongitude(), 0.01);
	}

	@Test
	public void testGetAvoidLinkIds() throws Exception {
		SurvivalService s = new SurvivalService(dSource);
		
		Coordinate from = new Coordinate(30, -100);
		Coordinate to = new Coordinate(40, -70);
		int[] red = s.getAvoidLinkIds(from, to).getRed();
		int[] yellow = s.getAvoidLinkIds(from, to).getYellow();
		
		int[] redTarget = {48299070, 36327827, 28819535, 37416235, 
				35909734, 1179627, 29907635, 29635202, 48302329, 
				27356703, 27594660, 49012296, 40948040, 48298941, 
				47004373, 44009832, 43317606, 27232763, 35893926, 40947872};
		int[] yellowTarget = {1184010, 1184490, 1187635, 1194826, 
				1196216, 1197046, 1198468, 1202785, 1213592, 28840608, 
				36299695, 37180155, 40912890, 44009705, 46469921, 
				47825278, 52427587, 53198833, 56220229, 56761221}; 

		assertTrue(Arrays.equals(red, redTarget));
		assertTrue(Arrays.equals(yellow, yellowTarget));
		
		Coordinate from1 = null;
		Coordinate to1 = null;
		assertEquals(s.getAvoidLinkIds(from1, to1), null);
	}
	
	@Test
	public void testGetCrimes() {
		SurvivalService s = new SurvivalService(dSource);
		Sql2o db = s.getDb();
		
		try (Connection conn = db.open()){
			String sql1 = "CREATE TABLE IF NOT EXISTS TestCrimes "
					+ "(date INTEGER NOT NULL, linkId INTEGER NOT NULL, address TEXT NOT NULL, "
					+ "latitude REAL NOT NULL, longitude REAL NOT NULL, "
					+ "type TEXT, PRIMARY KEY (date, linkId, type));";
			conn.createQuery(sql1).executeUpdate();
			
			// Crime(int date, String address, String type, double latitude, double longitude, int linkid)
			List<Crime> crimeList = new LinkedList<>();
			List<Crime> valid = new LinkedList<>();
			List<Crime> invalid = new LinkedList<>();
			
			// the only ones that should return
			crimeList.add(new Crime(20, "a2", "type2", 200, 200, 1));
			crimeList.add(new Crime(30, "a3", "type3", 300, 300, 2));
			crimeList.add(new Crime(40, "a4", "type4", 400, 400, 3));
			
			// latitude or longitude should be too small
			crimeList.add(new Crime(20, "a1", "type1", 100, 200, 4));
			crimeList.add(new Crime(20, "a12", "type12", 200, 100, 5));			
			
			// latitude or longitude should be too big
			crimeList.add(new Crime(40, "a5", "type5", 500, 400, 6));
			crimeList.add(new Crime(40, "a54", "type54", 400, 500, 7));
			
			// date is too small or too big
			crimeList.add(new Crime(10, "a2", "type2", 200, 200, 8));
			crimeList.add(new Crime(50, "a2", "type2", 200, 200, 9));
			
			for (Crime c : crimeList) {
				String sql = "insert into TestCrimes(date, linkId, address, latitude, longitude, type) "
						+ "SELECT * FROM (SELECT :dateParam, :linkIdParam, :addressParam, :latitudeParam, :longitudeParam, :typeParam) "
						+ "where not exists (select * from crimes where date = :dateParam and linkId = :linkIdParam "
						+ "and type = :typeParam);";
				
				Query query = conn.createQuery(sql);
				query.addParameter("dateParam", c.getDate()).addParameter("linkIdParam", c.getLinkId())
					.addParameter("addressParam", c.getAddress()).addParameter("latitudeParam", c.getLat())
					.addParameter("longitudeParam", c.getLng()).addParameter("typeParam", c.getType())
					.executeUpdate();
			}
			
			double fromLng = 200;
			double toLng = 400;
			double fromLat = 200;
			double toLat = 400;
			int fromDate = 20;
			int toDate = 40;
			int timeOfDay = 1000;
			
			CrimePoint from = new CrimePoint(fromDate, fromLat, fromLng);
			CrimePoint to = new CrimePoint(toDate, toLat, toLng);
			List<Crime> crimes = s.getCrimes(from, to, timeOfDay, "TestCrimes");
			
			crimes.forEach(crime -> assertTrue(crime.getLat() >= fromLat && crime.getLat() <= toLat
					&& crime.getLng() >= fromLng && crime.getLng() <= toLng
					&& crime.getDate() >= fromDate && crime.getDate() <= toDate));
		} catch (Sql2oException e) {
			logger.error("Failed to get crimes in ServerTest", e);
		}
			
	}
	
	@Test
	public void testUpdateDB() {
		SurvivalService s = new SurvivalService(dSource);
		//s.updateDB();
		//exceeded the number of monthly MapQuest transactions 11/27/16
	}
	
	/**
	 * Code based on To-Do server test.
	 */
	/*@Test
	public void testSetupEndpoints() {
		SurvivalService s = new SurvivalService(dSource);
		SurvivalController controller = new SurvivalController(s);
		
		Response r = request("GET", "/avoidLinkIds", "fromLat=38.987194&toLat=39.004611&fromLng=-76.945999&toLng=-76.875671");
        assertEquals("Failed to get todo", 200, r.httpStatus);
	} */
	
	
	// ------------------------------------------------------------------------//
	// Generic Helper Methods and classes
	// ------------------------------------------------------------------------//
	
	private Response request(String method, String path, String json) {
		try {
			URL url = new URL("http", "localhost", Bootstrap.getPort(), path);
			System.out.println(url);
			HttpURLConnection http = (HttpURLConnection) url.openConnection();
			http.setRequestMethod(method);
			http.setDoInput(true);
			if (json != null) {
				http.setDoOutput(true);
				http.setRequestProperty("Content-Type", "application/json");
				OutputStreamWriter output = new OutputStreamWriter(http.getOutputStream());
				output.write(json);
				output.flush();
				output.close();
			}

			int responseCode = http.getResponseCode();
			String responseBody;
			try {
				responseBody = IOUtils.toString(http.getInputStream());
			} catch (Exception e) {
				responseBody = IOUtils.toString(http.getErrorStream());
			}
			return new Response(responseCode, responseBody);
		} catch (IOException e) {
			e.printStackTrace();
			fail("Sending request failed: " + e.getMessage());
			return null;
		}
	}

	private static class Response {
		public String content;
		public int httpStatus;

		public Response(int httpStatus, String content) {
			this.content = content;
			this.httpStatus = httpStatus;
		}

		public <T> T getContentAsObject(Type type) {
			return new Gson().fromJson(content, type);
		}
	}

	// ------------------------------------------------------------------------//
	// Survival Maps Specific Helper Methods and classes
	// ------------------------------------------------------------------------//
	private SQLiteDataSource clearDB() {
		SQLiteDataSource dataSource = new SQLiteDataSource();
		dataSource.setUrl("jdbc:sqlite:server.db"); 

		Sql2o db = new Sql2o(dataSource);

		try (Connection conn = db.open()) {
			String sql = "DROP TABLE IF EXISTS TestCrimes";
			conn.createQuery(sql).executeUpdate();
		}
		
		return dataSource;
	}
}
