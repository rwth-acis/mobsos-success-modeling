package i5.las2peer.services.monitoring.provision;

import i5.las2peer.api.Service;
import i5.las2peer.persistency.MalformedXMLException;
import i5.las2peer.services.monitoring.provision.database.SQLDatabase;
import i5.las2peer.services.monitoring.provision.database.SQLDatabaseType;
import i5.las2peer.services.monitoring.provision.successModel.Factor;
import i5.las2peer.services.monitoring.provision.successModel.Measure;
import i5.las2peer.services.monitoring.provision.successModel.SuccessModel;
import i5.las2peer.services.monitoring.provision.successModel.SuccessModel.Dimension;
import i5.las2peer.services.monitoring.provision.successModel.visualizations.Chart;
import i5.las2peer.services.monitoring.provision.successModel.visualizations.Chart.ChartType;
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
import java.util.Collection;
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
	
	public final String NODE_QUERY;
	public final String SERVICE_QUERY;
	
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
	private String successModelsFolderLocation;
	private String DB2Schema;
	
	private SQLDatabase database; //The database instance to write to.
	private Map<String, Measure> knownMeasures = new TreeMap<String, Measure>();
	private Map<String, SuccessModel> knownModels = new TreeMap<String, SuccessModel>();
	
	
	/**
	 *
	 * Constructor of the Service.
	 * Loads the database values from a property file and tries to connect to the database.
	 *
	 */
	public MonitoringDataProvisionService(){
		setFieldValues(); //This sets the values of the configuration file
		
		this.databaseType = SQLDatabaseType.getSQLDatabaseType(databaseTypeInt);
		
		this.database = new SQLDatabase(this.databaseType, this.databaseUser, this.databasePassword,
				this.databaseName, this.databaseHost, this.databasePort);
		
		if(this.databaseType == SQLDatabaseType.MySQL){
			this.NODE_QUERY = "SELECT NODE_ID FROM NODE";
			this.SERVICE_QUERY = "SELECT * FROM SERVICE";
		}
		else {
			this.NODE_QUERY = "SELECT NODE_ID FROM " + DB2Schema + ".NODE";
			this.SERVICE_QUERY = "SELECT * FROM " + DB2Schema + ".SERVICE";
		}
		
		try {
			this.database.connect();
			System.out.println("Monitoring: Database connected!");
		} catch (Exception e) {
			System.out.println("Monitoring: Could not connect to database! " + e.getMessage());
		}
		
		try {
			knownMeasures = updateMeasures();
		} catch (MalformedXMLException e) {
			System.out.println("Measure Catalog seems broken: " + e.getMessage());
		} catch (XMLSyntaxException e) {
			System.out.println("Measure Catalog seems broken: " + e.getMessage());
		} catch (IOException e) {
			System.out.println("Measure Catalog seems broken: " + e.getMessage());
		}
		
		try {
			knownModels = updateModels();
		} catch (IOException e) {
			System.out.println("Problems reading Success Models: " + e.getMessage());
		}
	}
	
	
	/**
	 *
	 * Gets the names of all known measures.
	 * Currently not used by the frontend but can be used
	 * in later implementations to make success model creation
	 * possible directly through the frontend.
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
			} catch (XMLSyntaxException e) {
				System.out.println("Measure Catalog seems broken: " + e.getMessage());
			} catch (IOException e) {
				System.out.println("Measure Catalog seems broken: " + e.getMessage());
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
	 * Returns all stored ( = monitored) nodes.
	 *
	 * @return an array of node id's
	 *
	 */
	public String[] getNodes(){
		List<String> nodeIds = new ArrayList<String>();
		
		ResultSet resultSet;
		try {
			resultSet = database.query(NODE_QUERY);
		} catch (SQLException e) {
			System.out.println("(Get Nodes) The query has lead to an error: " + e);
			return null;
		}
		try {
			while(resultSet.next()){
				nodeIds.add(resultSet.getString(1));
			}
		} catch (SQLException e) {
			System.out.println("Problems reading result set: " + e);
		}
		return nodeIds.toArray(new String[nodeIds.size()]);
	}
	
	
	/**
	 *
	 * Returns all stored ( = monitored) services.
	 *
	 * @return an array of service agent id
	 *
	 */
	public String[] getServices(){
		List<String> monitoredServices = new ArrayList<String>();
		
		ResultSet resultSet;
		try {
			resultSet = database.query(SERVICE_QUERY);
		} catch (SQLException e) {
			System.out.println("(getServiceIds) The query has lead to an error: " + e);
			return null;
		}
		try {
			while(resultSet.next()){
				monitoredServices.add(resultSet.getString(2));
			}
		} catch (SQLException e) {
			System.out.println("Problems reading result set: " + e);
		}
		return monitoredServices.toArray(new String[monitoredServices.size()]);
	}
	
	
	/**
	 * 
	 * Returns the name of all stored success models for the given service.
	 * 
	 * @param serviceName the name of the service
	 * @param update updates the available success models with the content
	 * of the success model folder
	 * 
	 * @return an array of success model names
	 * 
	 */
	public String[] getModels(String serviceName, boolean update){
		if(update)
			try {
				knownModels = updateModels();
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}
		
		Collection<SuccessModel> models = knownModels.values();
		List<String> modelNames = new ArrayList<String>();
		Iterator<SuccessModel> iterator = models.iterator();
		while(iterator.hasNext()){
			SuccessModel model = iterator.next();
			if(model.getServiceName() != null && model.getServiceName().equals(serviceName)){
				modelNames.add(model.getName());
			}
		}
		return modelNames.toArray(new String[0]);
	}
	
	
	/**
	 * 
	 * Visualizes a given service success model.
	 * 
	 * @param modelName the name of the success model
	 * @param updateMeasures if true, all measures are updated from xml file
	 * @param updateModel if true, all models are updated from xml file
	 * 
	 * @return a HTML representation of the success model
	 * 
	 */
	public String visualizeServiceSuccessModel(String modelName, boolean updateMeasures, boolean updateModels){
		if(updateMeasures)
			try {
				knownMeasures = updateMeasures();
			} catch (MalformedXMLException e) {
				System.out.println("Measure Catalog seems broken: " + e.getMessage());
			} catch (XMLSyntaxException e) {
				System.out.println("Measure Catalog seems broken: " + e.getMessage());
			} catch (IOException e) {
				System.out.println("Measure Catalog seems broken: " + e.getMessage());
			}
		if(updateModels)
			try {
				knownModels = updateModels();
			} catch (IOException e) {
				System.out.println("Problems reading Success Models: " + e.getMessage());
			}
		
		return visualizeSuccessModel(modelName, null);
	}
	
	
	/**
	 * 
	 * Visualizes a success model for the given node.
	 * 
	 * @param nodeName the name of node
	 * @param updateMeasures if true, all measures are updated from xml file
	 * @param updateModel if true, all models are updated from xml file
	 * 
	 * @return a HTML representation of the success model
	 * 
	 */
	public String visualizeNodeSuccessModel(String nodeName, boolean updateMeasures, boolean updateModels){
		if(updateMeasures)
			try {
				knownMeasures = updateMeasures();
			} catch (MalformedXMLException e) {
				System.out.println("Measure Catalog seems broken: " + e.getMessage());
			} catch (XMLSyntaxException e) {
				System.out.println("Measure Catalog seems broken: " + e.getMessage());
			} catch (IOException e) {
				System.out.println("Measure Catalog seems broken: " + e.getMessage());
			}
		if(updateModels)
			try {
				knownModels = updateModels();
			} catch (IOException e) {
				System.out.println("Problems reading Success Models: " + e.getMessage());
			}
		return visualizeSuccessModel("Node Success Model", nodeName);
	}
	
	
	/**
	 * 
	 * Visualizes a given success model.
	 * 
	 * @param modelName the name of the success model
	 * @param nodeName the name of a node
	 * necessary if a node success model should be calculated (can be set to null otherwise)
	 * 
	 * @return a HTML representation of the success model
	 * 
	 */
	private String visualizeSuccessModel(String modelName, String nodeName){
		SuccessModel model = knownModels.get(modelName);
		//Reload models once
		if(model == null){
			try {
				knownModels = updateModels();
			} catch (IOException e) {
				System.out.println("Problems reading Success Models: " + e.getMessage());
				return "Problems reading Success Models!";
			}
		}
		model = knownModels.get(modelName);
		if(model == null)
			return "Success Model not known!";
		//Find the Service Agent
		String serviceId = null;
		if(model.getServiceName() != null){
			ResultSet resultSet;
			try {
				resultSet = database.query(SERVICE_QUERY);
			while(resultSet.next()){
				if(resultSet.getString(2).equals(model.getServiceName()))
				serviceId = resultSet.getString(1);
			}
			} catch (SQLException e) {
				System.out.println("(Visualize Success Model) The query has lead to an error: " + e);
				return "Problems getting service agent!";
			}
			if(serviceId == null)
				return "Requested Service: " + model.getServiceName() + " is not monitored!";
		}
		else if(nodeName == null){
			return "No node given!";
		}
		Dimension[] dimensions = Dimension.getDimensions();
		String[] dimensionNames = Dimension.getDimensionNames();
		List<Factor> factorsOfDimension = new ArrayList<Factor>();
		List<Measure> measuresOfFactor = new ArrayList<Measure>();
		
		String returnStatement = "<div id = '" + modelName + "'>\n";
		for(int i = 0; i < dimensions.length; i++){
			returnStatement += "<div id = '" + dimensions[i] + "'>\n";
			returnStatement += "<h3>" + dimensionNames[i] + "</h3>\n";
			factorsOfDimension = model.getFactorsOfDimension(dimensions[i]);
			for(Factor factor : factorsOfDimension){
				returnStatement += "<h4>\nFactor " + factor.getName() + "</h4>\n";
				measuresOfFactor = factor.getMeasures();
				for(Measure measure : measuresOfFactor){
					if(serviceId != null){
						measure = insertService(measure, serviceId);
					}
					else if(nodeName != null){
						measure = insertNode(measure, nodeName);
					}
					returnStatement += measure.getName() + ": ";
					try {
						returnStatement += measure.visualize(database);
						returnStatement += "\n<br>\n";
					} catch (Exception e) {
						System.out.println("Problems visualizing measure: " + measure.getName() + " Exception: " + e);
					}
				}
			}
			returnStatement += "</div>\n";
		}
		returnStatement += "</div>\n";
		return returnStatement;
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
	private Map<String, Measure> updateMeasures() throws MalformedXMLException, XMLSyntaxException, IOException{
		
		Map<String, Measure> measures = new TreeMap<String, Measure>();
		
		File file = new File(catalogFileLocation);
		Element root;
		root = Parser.parse(file, false);
		
		if (!root.getName().equals("Catalog"))
			throw new MalformedXMLException("Catalog expeced!");
			
		for(int measureNumber = 0; measureNumber < root.getChildCount(); measureNumber++){
			Element measureElement = root.getChild(measureNumber);
			
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
					//Replace escape characters with their correct values (seems like the simple XML Parser does not do that)
					query = query.replaceAll( "&amp;&", "&" ).replaceAll( "&lt;", "<" )
							.replaceAll( "&lt;", "<" ).replaceAll( "&gt;", ">" ).replaceAll( "&lt;", "<" );
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
			String type;
			ChartType chartType = null;
			String parameters[] = new String[4];

			type = visualizationElement.getChild(0).getFirstChild().getText();
			parameters[0] = visualizationElement.getChild(1).getFirstChild().getText();
			parameters[1] = visualizationElement.getChild(2).getFirstChild().getText();
			parameters[2] = visualizationElement.getChild(3).getFirstChild().getText();
			parameters[3] = visualizationElement.getChild(4).getFirstChild().getText();
			
			if(type.equals("BarChart"))
				chartType = ChartType.BarChart;
			if(type.equals("LineChart"))
				chartType = ChartType.LineChart;
			if(type.equals("PieChart"))
				chartType = ChartType.PieChart;
			if(type.equals("RadarChart"))
				chartType = ChartType.RadarChart;
			if(type.equals("TimelineChart"))
				chartType = ChartType.TimelineChart;
			
			try {
				return new Chart(chartType, parameters);
			} catch (Exception e) {
				throw new MalformedXMLException("Could not create chart: " + e);
			}
		}
		throw new MalformedXMLException("Unknown visualization type: " + visualizationType);
	}
	
	
	/**
	 *
	 * This method will read the content of the success model folder and generate a
	 * {@link SuccessModel} for each file.
	 *
	 * @return a map with the {@link SuccessModel}s
	 *
	 * @throws MalformedXMLException
	 * @throws XMLSyntaxException
	 * @throws IOException if there exists a problem with the file handling
	 *
	 */
	private Map<String, SuccessModel> updateModels() throws IOException{
		Map<String, SuccessModel> models = new TreeMap<String, SuccessModel>();
		File sucessModelsFolder = new File(successModelsFolderLocation);
		if(!sucessModelsFolder.isDirectory())
			throw new IOException("The given path for the success model folder is not a directory!");
		for (File file : sucessModelsFolder.listFiles()){
			SuccessModel successModel;
			try {
				successModel = readSuccessModelFile(file);
				models.put(successModel.getName(), successModel);
			} catch (XMLSyntaxException e) {
				System.out.println("Error reading Success Model: " + e);
			} catch (MalformedXMLException e) {
				System.out.println("Error reading Success Model: " + e);
			} catch (IOException e) {
				System.out.println("Error reading Success Model: " + e);
			}
		}
		return models;
	}
	
	
	/**
	 * 
	 * Reads a success model file.
	 * 
	 * @param successModelFile
	 * 
	 * @return a {@link SuccessModel}
	 * @throws MalformedXMLException
	 * @throws XMLSyntaxException
	 * @throws IOException
	 * 
	 */
	private SuccessModel readSuccessModelFile(File successModelFile) throws MalformedXMLException, XMLSyntaxException, IOException {
		Element root;
		root = Parser.parse(successModelFile, false);
		boolean nodeSuccessModel = false;
		String modelName = root.getAttribute("name");
		
		if(root.getName().equals("NodeSuccessModel"))
			nodeSuccessModel = true;
		
		//If not a node success model, get the service name
		String serviceName = null;
		if(!nodeSuccessModel){
			if (!root.getName().equals("SuccessModel"))
				throw new MalformedXMLException(successModelFile.toString() + ": Success model expected!");
			if (!root.hasAttribute("service"))
				throw new MalformedXMLException("Service attribute expected!");
			serviceName = root.getAttribute("service");
		}
		if(root.getChildCount() != 6)
			throw new MalformedXMLException(successModelFile.toString() + ": Six dimensions expected!");
		
		List<Factor> factors = new ArrayList<Factor>();
		
		for(int dimensionNumber = 0; dimensionNumber < root.getChildCount(); dimensionNumber++){
			Element dimensionElement = root.getChild(dimensionNumber);
			
			String dimensionName = dimensionElement.getAttribute("name");
			Dimension dimension;
			if(dimensionName.equals("System Quality"))
				dimension = Dimension.SystemQuality;
			else if(dimensionName.equals("Information Quality"))
				dimension = Dimension.InformationQuality;
			else if(dimensionName.equals("Use"))
				dimension = Dimension.Use;
			else if(dimensionName.equals("User Satisfaction"))
				dimension = Dimension.UserSatisfaction;
			else if(dimensionName.equals("Individual Impact"))
				dimension = Dimension.IndividualImpact;
			else if(dimensionName.equals("Community Impact"))
				dimension = Dimension.CommunityImpact;
			else
				throw new MalformedXMLException(successModelFile.toString() + ": Dimension " + dimensionName + " is unknown!");
			
			for(int factorNumber = 0; factorNumber < dimensionElement.getChildCount(); factorNumber++){
				Element factorElement = dimensionElement.getChild(factorNumber);
				String factorName = factorElement.getAttribute("name");
				
				List<Measure> factorMeasures = new ArrayList<Measure>();
				for(int measureNumber = 0; measureNumber < factorElement.getChildCount(); measureNumber++){
					Element measureElement = factorElement.getChild(measureNumber);
					String measureName = measureElement.getAttribute("name");
					if(!knownMeasures.containsKey(measureName))
						knownMeasures = updateMeasures();
					if(!knownMeasures.containsKey(measureName))
						throw new MalformedXMLException(successModelFile.toString() + ": Measure name " + measureName + " is unknown!");
					factorMeasures.add(knownMeasures.get(measureName));
				}
				Factor factor = new Factor(factorName, dimension, factorMeasures);
				factors.add(factor);
			}
		}
		return new SuccessModel(modelName, serviceName, factors);
	}
	
	
	/**
	 *
	 * Inserts the node id into the queries of a measure.
	 *
	 * @param query a query with placeholder
	 * @param queryParameters the corresponding query parameters
	 *
	 * @return the measure with inserted nodeId
	 *
	 */
	private Measure insertNode(Measure measure, String nodeId){
		Pattern pattern = Pattern.compile("\\$NODE\\$");
		Map<String, String> insertedQueries = new HashMap<String, String>();
		
		Iterator<Map.Entry<String, String>> queries = measure.getQueries().entrySet().iterator();
		while (queries.hasNext()) {
			Map.Entry<String, String> entry = queries.next();
			insertedQueries.put(entry.getKey(),(pattern.matcher(entry.getValue()).replaceAll(nodeId)));
		}
		measure.setInsertedQueries(insertedQueries);
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
	private Measure insertService(Measure measure, String serviceId){
		Pattern pattern = Pattern.compile("\\$SERVICE\\$");
		Map<String, String> insertedQueries = new HashMap<String, String>();
		
		Iterator<Map.Entry<String, String>> queries = measure.getQueries().entrySet().iterator();
		while (queries.hasNext()) {
			Map.Entry<String, String> entry = queries.next();
			insertedQueries.put(entry.getKey(),(pattern.matcher(entry.getValue()).replaceAll(serviceId)));
		}
		measure.setInsertedQueries(insertedQueries);
		return measure;
	}
	
	
}
