package i5.las2peer.services.monitoring.provision;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import i5.las2peer.httpConnector.HttpConnector;
import i5.las2peer.httpConnector.client.Client;
import i5.las2peer.p2p.LocalNode;
import i5.las2peer.p2p.ServiceNameVersion;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UserAgent;
import i5.las2peer.services.mobsos.successModeling.MonitoringDataProvisionService;
import i5.las2peer.testing.MockAgentFactory;

/**
 * 
 * Tests for the Monitoring Data Provision Service. Mostly just prints out results of method invocations, since it is
 * not predictable which data is stored at the time these tests are run.
 * 
 * @author Peter de Lange
 *
 */
public class MonitoringDataProvisionServiceTest {

	private static final String HTTP_ADDRESS = "localhost";
	private static final int HTTP_PORT = 8080;

	private LocalNode node;
	private HttpConnector connector;
	private ByteArrayOutputStream logStream;
	private UserAgent adam = null;

	private static final String adamsPass = "adamspass";
	private static final ServiceNameVersion testServiceClass = new ServiceNameVersion(
			MonitoringDataProvisionService.class.getCanonicalName(), "0.1");

	@Before
	public void startServer() throws Exception {
		// start Node
		node = LocalNode.newNode();

		adam = MockAgentFactory.getAdam();
		adam.unlockPrivateKey(adamsPass);
		node.storeAgent(adam);

		node.launch();

		ServiceAgent testService = ServiceAgent.createServiceAgent(testServiceClass, "a pass");
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
	}

	@Test
	public void getMeasuresAndModels() {

		Client c = new Client(HTTP_ADDRESS, HTTP_PORT, adam.getLoginName(), adamsPass);

		try {
			// Login
			c.connect();
			Object result = c.invoke(testServiceClass.getName(), "getNodes");
			String[] resultArray = (String[]) result;
			if (resultArray.length != 0) {
				String knownNode = ((String[]) result)[0];
				System.out.println("Calling Node Success Model with node " + knownNode);

				result = c.invoke(testServiceClass.getName(), "visualizeNodeSuccessModel", knownNode, true, true,
						"measure_catalog-mysql.xml");
				assertTrue(result instanceof String);
				System.out.println("Visualizing Node Success Model Result:\n" + result);
			} else
				System.out.println("No monitored nodes, no node success model visualization possible!");

			result = c.invoke(testServiceClass.getName(), "visualizeServiceSuccessModel", "Chat Service Success Model",
					true, true, "measure_catalog-mysql.xml");
			assertTrue(result instanceof String);
			System.out.println("Visualizing Chat Service Success Model Result:\n" + result);

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
	}

}
