package i5.las2peer.services.monitoring.provision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import i5.las2peer.httpConnector.HttpConnector;
import i5.las2peer.httpConnector.client.Client;
import i5.las2peer.p2p.LocalNode;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UserAgent;
import i5.las2peer.testing.MockAgentFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * Tests for the Monitoring Data Provision Service.
 * Please note, that this tests will only work with a valid database entry
 * that contains some data. There are no insert statements formulated here,
 * so all queried results in these tests will have to be generated before running them.
 * 
 * @author Peter de Lange
 *
 */
public class MonitoringDataProvisionServiceTest {
	
	private static final String HTTP_ADDRESS = "localhost";
	private static final int HTTP_PORT = HttpConnector.DEFAULT_HTTP_CONNECTOR_PORT;
	
	private LocalNode node;
	private HttpConnector connector;
	private ByteArrayOutputStream logStream;
	private UserAgent adam = null;
	
	private static final String adamsPass = "adamspass";
	private static final String testServiceClass = "i5.las2peer.services.monitoring.provision.MonitoringDataProvisionService";
	
	
	@Before
	public void startServer() throws Exception {
		// start Node
		node = LocalNode.newNode();
		
		adam = MockAgentFactory.getAdam();
		
		node.storeAgent(adam);
		
		node.launch();

		ServiceAgent testService = ServiceAgent.generateNewAgent(
				testServiceClass, "a pass");
		testService.unlockPrivateKey("a pass");

		node.registerReceiver(testService);

		// start connector
		logStream = new ByteArrayOutputStream();
		connector = new HttpConnector();
		connector.setSocketTimeout(10000);
		connector.setLogStream(new PrintStream(logStream));
		connector.start(node);
	}
	
	
	@After
	public void shutDownServer() throws Exception {
		connector.stop();
		node.shutDown();
		
		connector = null;
		node = null;
		
		LocalNode.reset();
		
		System.out.println("Connector-Log:");
		System.out.println("--------------");

		System.out.println(logStream.toString());
	}
	
	
	@Test
	public void getMeasureNames() {
		
		Client c = new Client(HTTP_ADDRESS, HTTP_PORT, adam.getLoginName(), adamsPass);
		
		try {
			//Login
			c.connect();
			
			
			Object result = c.invoke(testServiceClass, "getMeasureNames", true);
			String[] resultArray = (String[]) result;
			assertEquals(2, resultArray.length);
			assertTrue(resultArray[0].equals("MyFirstMeasure"));
			assertTrue(resultArray[1].equals("MySecondMeasure"));
			
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
		
		try {
		
		//and logout
		c.disconnect();
		
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}
	
	
	@Test
	public void getMeasuresAndNodes() {
		
		Client c = new Client(HTTP_ADDRESS, HTTP_PORT, adam.getLoginName(), adamsPass);
		
		try {
			//Login
			c.connect();
			
			Object result = c.invoke(testServiceClass, "getMeasure", "MyFirstMeasure");
			try{
				Double resultDouble = Double.parseDouble((String) result);
				System.out.println("MyFirstResult: " + resultDouble);
			} catch (NumberFormatException e){
				e.printStackTrace();
				fail("Exception: " + e);
			}
			
			result = c.invoke(testServiceClass, "getMeasure", "MySecondMeasure");
			assertTrue(result instanceof String);
			System.out.println("MySecondMeasureResult: " + result);
			
			result = c.invoke(testServiceClass, "getNodes");
			assertTrue(result instanceof String[]);
			String[] resultArray = (String[]) result;
			for(String node : resultArray)
				System.out.println(node);
			
			
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
		
		try {
		
		//and logout
		c.disconnect();
		
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}
	
}
