package i5.las2peer.services.monitoring.provision;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import i5.las2peer.p2p.LocalNode;
import i5.las2peer.p2p.ServiceNameVersion;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UserAgent;
import i5.las2peer.services.mobsos.successModeling.MonitoringDataProvisionService;
import i5.las2peer.testing.MockAgentFactory;
import i5.las2peer.webConnector.WebConnector;
import i5.las2peer.webConnector.client.ClientResponse;
import i5.las2peer.webConnector.client.MiniClient;

/**
 * 
 * Tests for the Monitoring Data Provision Service. Mostly just prints out results of method invocations, since it is
 * not predictable which data is stored at the time these tests are run.
 * 
 * @author Peter de Lange
 *
 */
public class MonitoringDataProvisionServiceTest {

	private static final String HTTP_ADDRESS = "http://127.0.0.1";
	private static final int HTTP_PORT = WebConnector.DEFAULT_HTTP_PORT;

	private LocalNode node;
	private static WebConnector connector;
	private static MiniClient c1;
	private static UserAgent user1;
	private ByteArrayOutputStream logStream;

	private static final String adamsPass = "adamspass";
	private static final ServiceNameVersion testServiceClass = new ServiceNameVersion(
			MonitoringDataProvisionService.class.getCanonicalName(), "0.1");

	@Before
	public void startServer() throws Exception {
		// start Node
		node = LocalNode.newNode();

		user1 = MockAgentFactory.getAdam();
		user1.unlockPrivateKey(adamsPass);
		node.storeAgent(user1);

		node.launch();

		ServiceAgent testService = ServiceAgent.createServiceAgent(testServiceClass, "a pass");
		testService.unlockPrivateKey("a pass");

		node.registerReceiver(testService);

		// start connector
		logStream = new ByteArrayOutputStream();
		connector = new WebConnector(true, HTTP_PORT, false, 1000);
		connector.setLogStream(new PrintStream(logStream));
		connector.start(node);
		Thread.sleep(1000); // wait a second for the connector to become ready

		c1 = new MiniClient();
		c1.setAddressPort(HTTP_ADDRESS, HTTP_PORT);
		c1.setLogin(Long.toString(user1.getId()), "adamspass");
	}

	@After
	public void shutDownServer() throws Exception {
		if (connector != null) {
			connector.stop();
		}
		if (node != null) {
			node.shutDown();
		}

		connector = null;
		node = null;

		LocalNode.reset();

		System.out.println("Connector-Log:");
		System.out.println("--------------");

		if (logStream != null) {
			System.out.println(logStream.toString());
		}
	}

	@Test
	public void getNames() {
		/*
		Client c = new Client(HTTP_ADDRESS, HTTP_PORT, adam.getLoginName(), adamsPass);
		
		try {
			// Login
			c.connect();
		
			Object result = c.invoke(testServiceClass.getName(), "getMeasureNames", "measure_catalog/measure_catalog-mysql.xml", true);
			assertTrue(result instanceof String[]);
			String[] resultArray = (String[]) result;
			for (String measureName : resultArray)
				System.out.println("Result of asking for all measures: " + measureName);
		
			result = c.invoke(testServiceClass.getName(), "getNodes");
			assertTrue(result instanceof String[]);
			resultArray = (String[]) result;
			for (String node : resultArray)
				System.out.println("Result of asking for all nodes: " + node);
		
			result = c.invoke(testServiceClass.getName(), "getServices");
			assertTrue(result instanceof String[]);
			resultArray = (String[]) result;
			for (String service : resultArray)
				System.out.println("Result of asking for all monitored service names: " + service);
			if (resultArray.length != 0) {
				String serviceName = resultArray[0];
				System.out.println("Calling getModels with service: " + serviceName);
				result = c.invoke(testServiceClass.getName(), "getModels", serviceName, true,
						"measure_catalog-mysql.xml");
				assertTrue(result instanceof String[]);
				resultArray = (String[]) result;
				for (String service : resultArray)
					System.out.println("Result of asking for all models: " + service);
			} else
				System.out.println("Result of asking for all monitored service names: none!");
		
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
		
		try {
		
			// Logout
			c.disconnect();
		
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
		*/
	}

	@Test
	public void getMeasuresAndModels() {
		try {
			ClientResponse result = c1.sendRequest("GET", "mobsos-success-modeling/nodes", "", "*/*",
					"application/json", new HashMap<String, String>());
			assertTrue(result.getHttpCode() == 200);
			JSONParser parser = new JSONParser();
			JSONObject resultObject = (JSONObject) parser.parse(result.getResponse());
			if (resultObject.size() != 0) {
				String knownNode = (String) resultObject.keySet().toArray()[0];
				// String location = (String) resultObject.get(knownNode);

				System.out.println("Calling Node Success Model with node " + knownNode);
				String params = "{\"nodeName\":\"" + knownNode + "\"," + "\"updateMeasures\":\"true\","
						+ "\"updateModels\":\"true\"," + "\"catalog\":\"measure_catalogs/measure_catalog-mysql.xml\"}";
				ClientResponse result2 = c1.sendRequest("POST", "mobsos-success-modeling/visualize/nodeSuccessModel",
						params, "application/json", "application/json", new HashMap<String, String>());
				assertTrue(result2.getHttpCode() == 200);
				System.out.println("Visualizing Node Success Model Result:\n" + result2.getResponse());
			} else
				System.out.println("No monitored nodes, no node success model visualization possible!");

			String params = "{\"modelName\":\"Chat Service Success Model\"," + "\"updateMeasures\":\"true\","
					+ "\"updateModels\":\"true\"," + "\"catalog\":\"measure_catalogs/measure_catalog-mysql.xml\"}";
			ClientResponse result3 = c1.sendRequest("POST", "mobsos-success-modeling/visualize/serviceSuccessModel",
					params, "application/json", "application/json", new HashMap<String, String>());
			assertTrue(result3.getHttpCode() == 200);
			System.out.println("Visualizing Chat Service Success Model Result:\n" + result3.getResponse());

		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}

}
