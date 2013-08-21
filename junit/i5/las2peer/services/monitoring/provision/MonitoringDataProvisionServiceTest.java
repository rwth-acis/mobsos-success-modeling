package i5.las2peer.services.monitoring.provision;

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
 * that contains some data. At least one node has to be stored in the database.
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
	public void getNames() {
		
		Client c = new Client(HTTP_ADDRESS, HTTP_PORT, adam.getLoginName(), adamsPass);
		
		try {
			//Login
			c.connect();
			
			
			Object result = c.invoke(testServiceClass, "getMeasureNames", true);
			assertTrue(result instanceof String[]);
			String[] resultArray = (String[]) result;
			for(String measureName : resultArray)
				System.out.println("Result of asking for all measures: " + measureName);
			
			result = c.invoke(testServiceClass, "getNodes");
			assertTrue(result instanceof String[]);
			resultArray = (String[]) result;
			for(String node : resultArray)
				System.out.println("Result of asking for all nodes: " + node);
			
			result = c.invoke(testServiceClass, "getServiceIds");
			assertTrue(result instanceof String[]);
			resultArray = (String[]) result;
			for(String service : resultArray)
				System.out.println("Result of asking for all service ids: " + service);
			
			result = c.invoke(testServiceClass, "getModels", true);
			assertTrue(result instanceof String[]);
			resultArray = (String[]) result;
			for(String service : resultArray)
				System.out.println("Result of asking for all models: " + service);
			
			
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
		
		try {
		
		//Logout
		c.disconnect();
		
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}
	
	
	@Test
	public void getMeasuresAndModels() {
		
		Client c = new Client(HTTP_ADDRESS, HTTP_PORT, adam.getLoginName(), adamsPass);
		
		try {
			//Login
			c.connect();
			Object result = c.invoke(testServiceClass, "getNodes");
			String knownNode = ((String[]) result)[0];
			

			System.out.println("Calling Measure Visualizations with node " + knownNode);
			result = c.invoke(testServiceClass, "visualizeNodeMeasure", "Successful Agent Uploads Ratio", knownNode);
			
			Double resultDouble = Double.parseDouble((String) result);
			System.out.println("Successful Agent Uploads (KPI) Result: " + resultDouble);
			
			result = c.invoke(testServiceClass, "visualizeMeasure", "Type of Agent A");
			assertTrue(result instanceof String);
			System.out.println("Type of Agent A (Value) Result: " + result);
			
			result = c.invoke(testServiceClass, "visualizeNodeMeasure", "Send Messages over Time (Node)", knownNode);
			assertTrue(result instanceof String);
			System.out.println("Send Messages over Time (Node) (Chart) Result:\n" + result);
			
			result = c.invoke(testServiceClass, "visualizeSuccessModel", "Sample Node Success Model", knownNode);
			assertTrue(result instanceof String);
			System.out.println("Visualizing Sample Node Success Model Result:\n" + result);
			
			result = c.invoke(testServiceClass, "visualizeServiceSuccessModel", "Chat Service Success Model");
			assertTrue(result instanceof String);
			System.out.println("Visualizing Chat Service Success Model Result:\n" + result);
			
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
		
		try {
		
		//Logout
		c.disconnect();
		
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}
	
}
