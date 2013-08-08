package i5.las2peer.services.monitoring.provision;

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
	public void testWorkingSetup() {
		
		Client c = new Client(HTTP_ADDRESS, HTTP_PORT, adam.getLoginName(), adamsPass);
		
		try {
			//Login
			c.connect();
			
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
