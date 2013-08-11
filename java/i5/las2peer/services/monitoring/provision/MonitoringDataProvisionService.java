package i5.las2peer.services.monitoring.provision;

import i5.las2peer.api.Service;
import i5.las2peer.persistency.MalformedXMLException;
import i5.las2peer.services.monitoring.provision.database.SQLDatabase;
import i5.las2peer.services.monitoring.provision.database.SQLDatabaseType;
import i5.las2peer.services.monitoring.provision.successModel.Measure;
import i5.las2peer.services.monitoring.provision.successModel.visualizations.KPI;
import i5.las2peer.services.monitoring.provision.successModel.visualizations.Value;
import i5.las2peer.services.monitoring.provision.successModel.visualizations.Visualization;
import i5.simpleXML.Element;
import i5.simpleXML.Parser;
import i5.simpleXML.XMLSyntaxException;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;


/**
 * 
 * This service will connect to the monitoring database and provide an interface
 * for frontend clients to visualize monitored data.
 * 
 * @author Peter de Lange
 * 
 */
public class MonitoringDataProvisionService extends Service{
	
	
	public final String NODE_QUERY = "SELECT NODE_ID FROM NODE";
	public final String SERVICE_QUERY = "SELECT AGENT_ID FROM SERVICE";
	/**
	 * Configuration parameters, values will be set by the configuration file.
	 */
	private String databaseName;
	private int databaseTypeInt; //See SQLDatabaseType for more information
	private	SQLDatabaseType databaseType;
	private String databaseHost;
	private int databasePort;
	private String databaseUser;
	private String databasePassword;
	private String catalogFileLocation;
	private String successModelsFolder;
	
	private SQLDatabase database; //The database instance to write to.
	private Map<String, Measure> knownMeasures = new TreeMap<String, Measure>();
	
	
	/**
	 * 
	 * Constructor of the Service. Loads the database values from a property file and tries to connect to the database.
	 * 
	 */
	public MonitoringDataProvisionService(){
		setFieldValues(); //This sets the values of the configuration file
		this.databaseType = SQLDatabaseType.getSQLDatabaseType(databaseTypeInt);
		this.database = new SQLDatabase(this.databaseType, this.databaseUser, this.databasePassword,
				this.databaseName, this.databaseHost, this.databasePort);
		try {
			this.database.connect();
			System.out.println("Monitoring: Database connected!");
		} catch (Exception e) {
			System.out.println("Monitoring: Could not connect to database!");
			e.printStackTrace();
		}
		try {
			knownMeasures = updateMeasures();
		} catch (MalformedXMLException e) {
			System.out.println("Measure Catalog seems broken: " + e.getMessage());
			e.printStackTrace();
		} catch (XMLSyntaxException e) {
			System.out.println("Measure Catalog seems broken: " + e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("Measure Catalog seems broken: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	
	/**
	 * 
	 * Gets the names of all known measures.
	 * 
	 * @param update if true, the list is read again
	 * 
	 * @return an array of names
	 * 
	 */
	public String[] getMeasureNames(boolean update){
		if(update)
			try {
				knownMeasures = updateMeasures();
			} catch (MalformedXMLException e) {
				System.out.println("Measure Catalog seems broken: " + e.getMessage());
				e.printStackTrace();
			} catch (XMLSyntaxException e) {
				System.out.println("Measure Catalog seems broken: " + e.getMessage());
				e.printStackTrace();
			} catch (IOException e) {
				System.out.println("Measure Catalog seems broken: " + e.getMessage());
				e.printStackTrace();
			}
		
		String[] returnArray = new String[knownMeasures.size()];
		int counter = 0;
		for (String key : knownMeasures.keySet()) {
		    returnArray[counter] = key;
		    counter++;
		}
		
		return returnArray;
	}
	
	
	/**
	 * 
	 * Returns all stored nodes.
	 * 
	 * @return an array of node id's
	 * 
	 * @throws Exception
	 * 
	 */
	public String[] getNodes() throws Exception{
		List<String> nodeIds = new ArrayList<String>();
		
		ResultSet resultSet;
		try {
			resultSet = database.query(NODE_QUERY);
		} catch (SQLException e) {
			throw new Exception("The query has lead to an error: " + e);
		}
		while(resultSet.next()){
			nodeIds.add(resultSet.getString(1));
		}
		return nodeIds.toArray(new String[nodeIds.size()]);
	}
	
	
	/**
	 * 
	 * Returns all stored ( = monitored) services.
	 * 
	 * @return an array of service agent id
	 * 
	 * @throws Exception
	 * 
	 */
	public String[] getServices() throws Exception{
		List<String> serviceAgentIds = new ArrayList<String>();
		
		ResultSet resultSet;
		try {
			resultSet = database.query(SERVICE_QUERY);
		} catch (SQLException e) {
			throw new Exception("The query has lead to an error: " + e);
		}
		while(resultSet.next()){
			serviceAgentIds.add(resultSet.getString(1));
		}
		return serviceAgentIds.toArray(new String[serviceAgentIds.size()]);
	}
	
	
	/**
	 * 
	 * Executes a node measure query and returns the result.
	 * 
	 * @param measureName the name of the measure
	 * @param node the name of the node this measure is for
	 * 
	 * @return the result as a string
	 * 
	 */
	public String visualizeNodeMeasure(String measureName, String nodeId){
		return visualizeMeasure(measureName, nodeId, null);
	}
	
	
	/**
	 * 
	 * Executes a service measure query and returns the result.
	 * 
	 * @param measureName the name of the measure
	 * @param the serviceId this measure should be for (can be null)
	 * 
	 * @return the result as a string
	 * 
	 */
	public String visualizeServiceMeasure(String measureName, String serviceId){
		return visualizeMeasure(measureName, null, serviceId);
	}
	
	
	/**
	 * 
	 * Executes a measure query and returns the result.
	 * 
	 * @param measureName the name of the measure
	 * 
	 * @return the result as a string
	 * 
	 */
	public String visualizeMeasure(String measureName){
		return visualizeMeasure(measureName, null, null);
	}
	
	
	/**
	 * 
	 * Executes a measure query and returns the result.
	 * 
	 * @param measureName the name of the measure
	 * @param node the name of the node this measure is for (can be null)
	 * @param the serviceId this measure should be for (can be null)
	 * 
	 * @return the result as a string
	 * 
	 */
	public String visualizeMeasure(String measureName, String nodeId, String serviceId){
		Measure measure = knownMeasures.get(measureName);

		if(measure == null){
			//Reload the list
			try {
				knownMeasures = updateMeasures();
			} catch (MalformedXMLException e) {
				System.out.println("Measure Catalog seems broken: " + e.getMessage());
				e.printStackTrace();
			} catch (XMLSyntaxException e) {
				System.out.println("Measure Catalog seems broken: " + e.getMessage());
				e.printStackTrace();
			} catch (IOException e) {
				System.out.println("Measure Catalog seems broken: " + e.getMessage());
				e.printStackTrace();
			}
			if(measure == null) //still not found
				return "Measure does not exist!";
		}
		
		//Insert parameters, if fields are set
		if(nodeId != null)
			measure = insertNode(measure, nodeId);
		if(serviceId != null)
			measure = insertServiceId(measure, serviceId);
		
		try {
			return measure.visualize(this.database);
		} catch (Exception e) {
			e.printStackTrace();
			return "Exception occured while trying to get the measure: " + e;
		}
	}
	
	
	/**
	 * 
	 * This method will read the contents of the catalog file and update the available measures.
	 * 
	 * @return a map with the measures
	 * 
	 * @throws MalformedXMLException
	 * @throws XMLSyntaxException
	 * @throws IOException if the catalog file does not exist
	 * 
	 */
	public  Map<String, Measure> updateMeasures() throws MalformedXMLException, XMLSyntaxException, IOException{
		
		Map<String, Measure> measures = new TreeMap<String, Measure>();
		
		File file = new File(catalogFileLocation);
		Element root;
		root = Parser.parse(file, false);
		
		if (!root.getName().equals("catalog"))
			throw new MalformedXMLException("Catalog expeced");
			
		for(int rootChildCount = 0; rootChildCount < root.getChildCount(); rootChildCount++){
			Element measureElement = root.getChild(rootChildCount);

			Map<String,String> queries = new HashMap<String, String>();
			Visualization visualization = null;
			
			if(!measureElement.hasAttribute("name"))
				throw new MalformedXMLException("Catalog contains a measure without a name!");
			String measureName = measureElement.getAttribute("name");
			if(measures.containsKey("measureName"))
				throw new MalformedXMLException("Catalog already contains a measure " + measureName + "!");
			
			for(int measureChildCount = 0; measureChildCount < measureElement.getChildCount(); measureChildCount++){
				
				Element measureChild = measureElement.getChild(measureChildCount);
				String childType = measureChild.getName();
				
				if(childType.equals("query")){
					String queryName = measureChild.getAttribute("name");
					String query = measureChild.getFirstChild().getText();
					queries.put(queryName, query);
				}
				
				else if(childType.equals("visualization")){
					if(visualization != null)
						throw new MalformedXMLException("Measure " + measureName + " is broken, duplicate 'Visualization' entry!");
					visualization = readVisualization(measureChild);
				}
				
				else{
					throw new MalformedXMLException("Measure " + measureName + " is broken, illegal node " + childType + "!");
				}
				
			}
			
			if(visualization == null)
				throw new MalformedXMLException("Measure " + measureName + " is broken, no visualization element!");
			if(queries.isEmpty())
				throw new MalformedXMLException("Measure " + measureName + " is broken, no query element!");
			
			measures.put(measureName, new Measure(measureName, queries, visualization));
		}
		
		return measures;
	}
	
	
	/**
	 * 
	 * Helper method that reads a visualization object of the catalog file.
	 * 
	 * @return a visualization object
	 * 
	 * @throws MalformedXMLException
	 * @throws XMLSyntaxException
	 * 
	 */
	private Visualization readVisualization(Element visualizationElement) throws XMLSyntaxException, MalformedXMLException{
		String visualizationType = visualizationElement.getAttribute("type");
		if(visualizationType.equals("Value")){
			return new Value();
		}
		else if(visualizationType.equals("KPI")){
			Map<Integer, String> expression = new TreeMap<Integer, String>();
			for(int i  = 0; i < visualizationElement.getChildCount(); i++){
				int index = Integer.valueOf(visualizationElement.getChild(i).getAttribute("index"));
				String name = visualizationElement.getChild(i).getAttribute("name");
				expression.put(index, name);
			}
			return new KPI(expression);
		}
		else if(visualizationType.equals("Chart")){
			//TODO
			throw new MalformedXMLException("Sorry, not implemented yet!");
		}
		return null;
	}
	
	
	/**
	 * 
	 * Inserts the node id into the queries of a measure.
	 * 
	 * @param query a query with placeholder
	 * @param queryParameters the corresponding query parameters
	 * 
	 * @return the measure with inserted node id
	 *  
	 */
	private Measure insertNode(Measure measure, String nodeId){
		Pattern pattern = Pattern.compile("\\$NODE\\$");
		
		Iterator<Map.Entry<String, String>> queries = measure.getQueries().entrySet().iterator();
		while (queries.hasNext()) {
		    Map.Entry<String, String> entry = queries.next();
			entry.setValue(pattern.matcher(entry.getValue()).replaceAll(nodeId));
		}
		return measure;
	}
	
	
	/**
	 * 
	 * Inserts the service id into the queries of a measure.
	 * 
	 * @param query a query with placeholder
	 * @param queryParameters the corresponding query parameters
	 * 
	 * @return the measure with inserted serviceId
	 * 
	 */
	private Measure insertServiceId(Measure measure, String serviceId){
		Pattern pattern = Pattern.compile("\\$SERVICEID\\$");
		
		Iterator<Map.Entry<String, String>> queries = measure.getQueries().entrySet().iterator();
		while (queries.hasNext()) {
		    Map.Entry<String, String> entry = queries.next();
			entry.setValue(pattern.matcher(entry.getValue()).replaceAll(serviceId));
		}
		return measure;
	}
	
	
}
