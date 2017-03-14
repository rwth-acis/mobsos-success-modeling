package i5.las2peer.services.mobsos.successModeling;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.stream.Collectors;

import i5.las2peer.api.Service;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.logging.NodeObserver.Event;
import i5.las2peer.persistency.MalformedXMLException;
import i5.las2peer.services.mobsos.successModeling.database.SQLDatabase;
import i5.las2peer.services.mobsos.successModeling.database.SQLDatabaseType;
import i5.las2peer.services.mobsos.successModeling.successModel.Factor;
import i5.las2peer.services.mobsos.successModeling.successModel.Measure;
import i5.las2peer.services.mobsos.successModeling.successModel.SuccessModel;
import i5.las2peer.services.mobsos.successModeling.successModel.SuccessModel.Dimension;
import i5.las2peer.services.mobsos.successModeling.visualizations.Chart;
import i5.las2peer.services.mobsos.successModeling.visualizations.Chart.ChartType;
import i5.las2peer.services.mobsos.successModeling.visualizations.KPI;
import i5.las2peer.services.mobsos.successModeling.visualizations.Value;
import i5.las2peer.services.mobsos.successModeling.visualizations.Visualization;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import i5.las2peer.tools.XmlTools;

/**
 *
 * This service will connect to the monitoring database and provide an interface for frontend clients to visualize
 * monitored data.
 *
 * @author Peter de Lange
 *
 */
public class MonitoringDataProvisionService extends Service {

	public final String NODE_QUERY;
	public final String SERVICE_QUERY;

	/**
	 * Configuration parameters, values will be set by the configuration file.
	 */
	private String databaseName;
	private int databaseTypeInt; // See SQLDatabaseType for more information
	private SQLDatabaseType databaseType;
	private String databaseHost;
	private int databasePort;
	private String databaseUser;
	private String databasePassword;
	private Boolean useFileService;
	private String catalogFileLocation;
	private String successModelsFolderLocation;
	private String DB2Schema;

	private SQLDatabase database; // The database instance to write to.
	private TreeMap<String, Map<String, Measure>> measureCatalogs = new TreeMap<String, Map<String, Measure>>();
	private Map<String, SuccessModel> knownModels = new TreeMap<String, SuccessModel>();

	/**
	 *
	 * Constructor of the Service. Loads the database values from a property file and tries to connect to the database.
	 *
	 */
	public MonitoringDataProvisionService() {
		setFieldValues(); // This sets the values of the configuration file

		this.databaseType = SQLDatabaseType.getSQLDatabaseType(databaseTypeInt);

		this.database = new SQLDatabase(this.databaseType, this.databaseUser, this.databasePassword, this.databaseName,
				this.databaseHost, this.databasePort);

		if (this.databaseType == SQLDatabaseType.MySQL) {
			this.NODE_QUERY = "SELECT * FROM NODE";
			this.SERVICE_QUERY = "SELECT * FROM SERVICE";
		} else {
			this.NODE_QUERY = "SELECT * FROM " + DB2Schema + ".NODE";
			this.SERVICE_QUERY = "SELECT * FROM " + DB2Schema + ".SERVICE";
		}

		try {
			this.database.connect();
			System.out.println("Monitoring: Database connected!");
		} catch (Exception e) {
			System.out.println("Monitoring: Could not connect to database! " + e.getMessage());
		}
		/*
		try {
			List<File> filesInFolder = Files.walk(Paths.get(catalogFileLocation)).filter(Files::isRegularFile)
					.map(Path::toFile).collect(Collectors.toList());
			for (File f : filesInFolder) {
				try {
					measureCatalogs.put(f.getName(), updateMeasures(f));
				} catch (MalformedXMLException e) {
					System.out.println("Measure Catalog seems broken: " + e.getMessage());
				} catch (XMLSyntaxException e) {
					System.out.println("Measure Catalog seems broken: " + e.getMessage());
				} catch (IOException e) {
					System.out.println("Measure Catalog seems broken: " + e.getMessage());
				}
			}
		} catch (IOException e) {
			System.out.println("Measure Catalog seems broken: " + e.getMessage());
		}
		
		try {
			knownModels = updateModels(measureCatalogs.firstKey());
		} catch (IOException e) {
			System.out.println("Problems reading Success Models: " + e.getMessage());
		}
		*/
	}

	/**
	 * 
	 * Reconnect to the database (can be called in case of an error).
	 * 
	 */
	public void reconnect() {
		try {
			if (!database.isConnected()) {
				this.database.connect();
				System.out.println("Monitoring: Database reconnected!");
			}
		} catch (Exception e) {
			System.out.println("Monitoring: Could not connect to database!");
			e.printStackTrace();
		}
	}

	/**
	 *
	 * Gets the names of all known measures. Currently not used by the frontend but can be used in later implementations
	 * to make success model creation possible directly through the frontend.
	 *
	 * @param update if true, the list is read again
	 *
	 * @return an array of names
	 *
	 */
	public String[] getMeasureNames(String catalog, boolean update) {
		if (update)
			try {
				List<File> filesInFolder = Files.walk(Paths.get(catalogFileLocation)).filter(Files::isRegularFile)
						.map(Path::toFile).collect(Collectors.toList());
				for (File f : filesInFolder) {
					try {
						System.out.println(f);
						measureCatalogs.put(catalog,updateMeasures(f, catalog));
					} catch (MalformedXMLException e) {
						System.out.println("Measure Catalog seems broken: " + e.getMessage());
					} catch (IOException e) {
						System.out.println("Measure Catalog seems broken: " + e.getMessage());
					}
				}
			} catch (IOException e) {
				System.out.println("Measure Catalog seems broken: " + e.getMessage());
			}
		String[] returnArray = new String[measureCatalogs.get(catalog).size()];
		int counter = 0;
		for (String key : measureCatalogs.get(catalog).keySet()) {
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
	public String[] getNodes() {
		List<String> nodeIds = new ArrayList<String>();

		ResultSet resultSet;
		try {
			reconnect();
			resultSet = database.query(NODE_QUERY);
		} catch (SQLException e) {
			System.out.println("(Get Nodes) The query has lead to an error: " + e);
			return null;
		}
		try {
			while (resultSet.next()) {
				nodeIds.add(resultSet.getString(1) + " Location: " + resultSet.getString(2));
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
	public String[] getServices() {
		List<String> monitoredServices = new ArrayList<String>();

		ResultSet resultSet;
		try {
			reconnect();
			resultSet = database.query(SERVICE_QUERY);
		} catch (SQLException e) {
			System.out.println("(getServiceIds) The query has lead to an error: " + e);
			return null;
		}
		try {
			while (resultSet.next()) {
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
	 * @param update updates the available success models with the content of the success model folder
	 * 
	 * @return an array of success model names
	 * 
	 */
	public String[] getModels(String serviceName, boolean update, String catalog) {
		if (update)
			try {
				knownModels = updateModels(catalog);
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}

		Collection<SuccessModel> models = knownModels.values();
		List<String> modelNames = new ArrayList<String>();
		Iterator<SuccessModel> iterator = models.iterator();
		while (iterator.hasNext()) {
			SuccessModel model = iterator.next();
			if (model.getServiceName() != null && model.getServiceName().equals(serviceName)) {
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
	 * @param updateModels if true, all models are updated from xml file
	 * 
	 * @return a HTML representation of the success model
	 * 
	 */
	public String visualizeServiceSuccessModel(String modelName, boolean updateMeasures, boolean updateModels,
			String catalog) {
		if (updateMeasures)
			try {
				if (useFileService) {
					ArrayList<String> measureFiles = getMeasureCatalogList();
					for (String s : measureFiles) {
						try {
							updateMeasures(new File(""), s);
						} catch (MalformedXMLException e) {
							System.out.println("Measure Catalog seems broken: " + e.getMessage());
						} catch (IOException e) {
							System.out.println("Measure Catalog seems broken: " + e.getMessage());
						}
					}
				} else {
					List<File> filesInFolder = Files.walk(Paths.get(catalogFileLocation)).filter(Files::isRegularFile)
							.map(Path::toFile).collect(Collectors.toList());
					for (File f : filesInFolder) {
						try {
							updateMeasures(f, catalog);
						} catch (MalformedXMLException e) {
							System.out.println("Measure Catalog seems broken: " + e.getMessage());
							System.out.println("Measure Catalog seems broken: " + e.getMessage());
						}
					}
				}
			} catch (IOException e) {
				System.out.println("Measure Catalog seems broken: " + e.getMessage());
			}
		if (updateModels)
			try {
				knownModels = updateModels(catalog);
			} catch (IOException e) {
				System.out.println("Problems reading Success Models: " + e.getMessage());
			}

		return visualizeSuccessModel(modelName, null, catalog);
	}

	/**
	 * 
	 * Visualizes a success model for the given node.
	 * 
	 * @param nodeName the name of node
	 * @param updateMeasures if true, all measures are updated from xml file
	 * @param updateModels if true, all models are updated from xml file
	 * 
	 * @return a HTML representation of the success model
	 * 
	 */
	public String visualizeNodeSuccessModel(String nodeName, boolean updateMeasures, boolean updateModels,
			String catalog) {
		if (updateMeasures)
			if (useFileService) {
				ArrayList<String> measureFiles = getMeasureCatalogList();
				for (String s : measureFiles) {
					try {
						updateMeasures(new File(""), s);
					} catch (MalformedXMLException e) {
						System.out.println("Measure Catalog seems broken: " + e.getMessage());
					} catch (IOException e) {
						System.out.println("Measure Catalog seems broken: " + e.getMessage());
					}
				}
			} else {
				try {
					List<File> filesInFolder = Files.walk(Paths.get(catalogFileLocation)).filter(Files::isRegularFile)
							.map(Path::toFile).collect(Collectors.toList());
					for (File f : filesInFolder) {
						try {
							updateMeasures(f, catalog);
						} catch (MalformedXMLException e) {
							System.out.println("Measure Catalog seems broken: " + e.getMessage());
						} catch (IOException e) {
							System.out.println("Measure Catalog seems broken: " + e.getMessage());
						}
					}
				} catch (IOException e) {
					System.out.println("Measure Catalog seems broken: " + e.getMessage());
				}
			}
		if (updateModels)
			try {
				knownModels = updateModels(catalog);
			} catch (IOException e) {
				System.out.println("Problems reading Success Models: " + e.getMessage());
			}
		return visualizeSuccessModel("Node Success Model", nodeName, catalog);
	}

	/**
	 * 
	 * Visualizes a given success model.
	 * 
	 * @param modelName the name of the success model
	 * @param nodeName the name of a node necessary if a node success model should be calculated (can be set to null
	 *            otherwise)
	 * 
	 * @return a HTML representation of the success model
	 * 
	 */
	private String visualizeSuccessModel(String modelName, String nodeName, String catalog) {
		SuccessModel model = knownModels.get(modelName);
		// Reload models once
		if (model == null) {
			try {
				knownModels = updateModels(catalog);
			} catch (IOException e) {
				System.out.println("Problems reading Success Models: " + e.getMessage());
				return "Problems reading Success Models!";
			}
		}
		model = knownModels.get(modelName);
		if (model == null)
			return "Success Model not known!";
		// Find the Service Agent
		String serviceId = null;
		if (model.getServiceName() != null) {
			ResultSet resultSet;
			try {
				reconnect();
				resultSet = database.query(SERVICE_QUERY);
				while (resultSet.next()) {
					if (resultSet.getString(2).equals(model.getServiceName()))
						serviceId = resultSet.getString(1);
				}
			} catch (SQLException e) {
				System.out.println("(Visualize Success Model) The query has lead to an error: " + e);
				return "Problems getting service agent!";
			}
			if (serviceId == null)
				return "Requested Service: " + model.getServiceName() + " is not monitored!";
		} else if (nodeName == null) {
			return "No node given!";
		}
		Dimension[] dimensions = Dimension.getDimensions();
		String[] dimensionNames = Dimension.getDimensionNames();
		List<Factor> factorsOfDimension = new ArrayList<Factor>();
		List<Measure> measuresOfFactor = new ArrayList<Measure>();

		String returnStatement = "<div id = '" + modelName + "'>\n";
		for (int i = 0; i < dimensions.length; i++) {
			returnStatement += "<div id = '" + dimensions[i] + "'>\n";
			returnStatement += "<h3>" + dimensionNames[i] + "</h3>\n";
			factorsOfDimension = model.getFactorsOfDimension(dimensions[i]);
			for (Factor factor : factorsOfDimension) {
				returnStatement += "<h4>" + factor.getName() + "</h4>\n";
				measuresOfFactor = factor.getMeasures();
				for (Measure measure : measuresOfFactor) {
					if (serviceId != null) {
						measure = insertService(measure, serviceId);
					} else if (nodeName != null) {
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
	private Map<String, Measure> updateMeasures(File catalog, String measureFile)
			throws MalformedXMLException, IOException {

		Map<String, Measure> measures = new TreeMap<String, Measure>();
		Element root;
		if (useFileService) {
			String measureXML = getFile(measureFile);
			root = XmlTools.getRootElement(measureXML, "Catalog");
		} else {
			try{
			root = XmlTools.getRootElement(catalog, "Catalog");
			}catch(MalformedXMLException e){
				e.printStackTrace();
				return measures;
			}
		}
		NodeList children = root.getChildNodes();
		for (int measureNumber = 0; measureNumber < children.getLength(); measureNumber++) {
			if(children.item(measureNumber).getNodeType() == Node.ELEMENT_NODE){
				Element measureElement = (Element)children.item(measureNumber);
	
				Map<String, String> queries = new HashMap<String, String>();
				Visualization visualization = null;
	
				if (!measureElement.hasAttribute("name"))
					throw new MalformedXMLException("Catalog contains a measure without a name!");
				String measureName = measureElement.getAttribute("name");
				if (measures.containsKey("measureName"))
					throw new MalformedXMLException("Catalog already contains a measure " + measureName + "!");
				NodeList mChildren = measureElement.getChildNodes();
				for (int measureChildCount = 0; measureChildCount < mChildren.getLength(); measureChildCount++) {
					if(mChildren.item(measureChildCount).getNodeType() == Node.ELEMENT_NODE){
						Element measureChild = (Element)mChildren.item(measureChildCount);
						String childType = measureChild.getNodeName();
		
						if (childType.equals("query")) {
							String queryName = measureChild.getAttribute("name");
							String query = measureChild.getFirstChild().getTextContent();
							// Replace escape characters with their correct values (seems like the simple XML Parser does not do
							// that)
							query = query.replaceAll("&amp;&", "&").replaceAll("&lt;", "<").replaceAll("&lt;", "<")
									.replaceAll("&gt;", ">").replaceAll("&lt;", "<");
							queries.put(queryName, query);
						}
		
						else if (childType.equals("visualization")) {
							if (visualization != null)
								throw new MalformedXMLException(
										"Measure " + measureName + " is broken, duplicate 'Visualization' entry!");
							visualization = readVisualization(measureChild);
						}
		
						else {
							throw new MalformedXMLException(
									"Measure " + measureName + " is broken, illegal node " + childType + "!");
						}
					}
				}
	
				if (visualization == null)
					throw new MalformedXMLException("Measure " + measureName + " is broken, no visualization element!");
				if (queries.isEmpty())
					throw new MalformedXMLException("Measure " + measureName + " is broken, no query element!");
	
				measures.put(measureName, new Measure(measureName, queries, visualization));
			}
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
	private Visualization readVisualization(Element visualizationElement)
			throws MalformedXMLException {
		String visualizationType = visualizationElement.getAttribute("type");
		if (visualizationType.equals("Value")) {
			return new Value();
		} else if (visualizationType.equals("KPI")) {
			Map<Integer, String> expression = new TreeMap<Integer, String>();
			NodeList children = visualizationElement.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				if(children.item(i).getNodeType() == Node.ELEMENT_NODE){
					int index = Integer.valueOf(((Element)children.item(i)).getAttribute("index"));
					String name = ((Element)children.item(i)).getAttribute("name");
					expression.put(index, name);
				}
			}
			return new KPI(expression);
		} else if (visualizationType.equals("Chart")) {
			String type;
			ChartType chartType = null;
			String parameters[] = new String[4];
			NodeList children = visualizationElement.getChildNodes();
			Element elements[] = new Element[5];
			int j = 0;
			for(int i=0;i<children.getLength();++i){
				if(children.item(i).getNodeType() == Node.ELEMENT_NODE){
					elements[j] =(Element)children.item(i);
					j++;
				}
				if(j>=5)break;
			}
			type = elements[0].getFirstChild().getTextContent();
			for(int i=0;i<4;++i){
				parameters[i] = elements[i+1].getFirstChild().getTextContent();
			}

			if (type.equals("BarChart"))
				chartType = ChartType.BarChart;
			if (type.equals("LineChart"))
				chartType = ChartType.LineChart;
			if (type.equals("PieChart"))
				chartType = ChartType.PieChart;
			if (type.equals("RadarChart"))
				chartType = ChartType.RadarChart;
			if (type.equals("TimelineChart"))
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
	 * This method will read the content of the success model folder and generate a {@link SuccessModel} for each file.
	 *
	 * @return a map with the {@link SuccessModel}s
	 *
	 * @throws MalformedXMLException
	 * @throws XMLSyntaxException
	 * @throws IOException if there exists a problem with the file handling
	 *
	 */
	private Map<String, SuccessModel> updateModels(String measureCatalog) throws IOException {
		Map<String, SuccessModel> models = new TreeMap<String, SuccessModel>();
		if (useFileService) {
			ArrayList<String> sModels = getSuccessModels();
			for (String sm : sModels) {
				System.out.println("Model: " + sm);
				String smc = getFile(sm);
				try {
					System.out.println(smc);
					SuccessModel successModel;
					try {
						successModel = readSuccessModelFile(new File(""), smc, measureCatalog);
						models.put(successModel.getName(), successModel);
					} catch (MalformedXMLException e) {
						System.out.println("Error reading Success Model: " + e);
					} catch (IOException e) {
						System.out.println("Error reading Success Model: " + e);
					}
				} catch (Exception x) {
					x.printStackTrace();
				}
			}
		} else {
			File sucessModelsFolder = new File(successModelsFolderLocation);
			if (!sucessModelsFolder.isDirectory())
				throw new IOException("The given path for the success model folder is not a directory!");
			for (File file : sucessModelsFolder.listFiles()) {
				SuccessModel successModel;
				try {
					successModel = readSuccessModelFile(file, "", catalogFileLocation+measureCatalog);
					models.put(successModel.getName(), successModel);
				} catch (MalformedXMLException e) {
					System.out.println("Error reading Success Model: " + e);
				} catch (IOException e) {
					System.out.println("Error reading Success Model: " + e);
				}
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
	private SuccessModel readSuccessModelFile(File successModelFile, String successModelFileContent, String measureFile)
			throws MalformedXMLException, IOException {
		Element root;
		if (useFileService){
			try{
				root = XmlTools.getRootElement(successModelFileContent, "SuccessModel");
			} catch(MalformedXMLException e){
				root = XmlTools.getRootElement(successModelFileContent, "NodeSuccessModel");	
			}
		}
		else{
			try{
				root = XmlTools.getRootElement(successModelFile, "SuccessModel");
			}catch(MalformedXMLException e){
				root = XmlTools.getRootElement(successModelFile, "NodeSuccessModel");	
			}
		}
		boolean nodeSuccessModel = false;
		String modelName = root.getAttribute("name");
		if (root.getNodeName().equals("NodeSuccessModel"))
			nodeSuccessModel = true;

		// If not a node success model, get the service name
		String serviceName = null;
		if (!nodeSuccessModel) {
			if (!root.getNodeName().equals("SuccessModel"))
				throw new MalformedXMLException(successModelFile.toString() + ": Success model expected!");
			if (!root.hasAttribute("service"))
				throw new MalformedXMLException("Service attribute expected!");
			serviceName = root.getAttribute("service");
		}
		NodeList children = root.getChildNodes();
		ArrayList<Element> elements = new ArrayList<>();
		for (int dimensionNumber = 0; dimensionNumber < children.getLength(); dimensionNumber++) {
			if(children.item(dimensionNumber).getNodeType() == Node.ELEMENT_NODE){
				elements.add((Element)children.item(dimensionNumber));
			}
		}
		if (elements.size() != 6)
			throw new MalformedXMLException(successModelFile.toString() + ": Six dimensions expected!");
		List<Factor> factors = new ArrayList<Factor>();

		for (Element dimensionElement:elements) {

			String dimensionName = dimensionElement.getAttribute("name");
			Dimension dimension;
			if (dimensionName.equals("System Quality"))
				dimension = Dimension.SystemQuality;
			else if (dimensionName.equals("Information Quality"))
				dimension = Dimension.InformationQuality;
			else if (dimensionName.equals("Use"))
				dimension = Dimension.Use;
			else if (dimensionName.equals("User Satisfaction"))
				dimension = Dimension.UserSatisfaction;
			else if (dimensionName.equals("Individual Impact"))
				dimension = Dimension.IndividualImpact;
			else if (dimensionName.equals("Community Impact"))
				dimension = Dimension.CommunityImpact;
			else
				throw new MalformedXMLException(
						successModelFile.toString() + ": Dimension " + dimensionName + " is unknown!");
			NodeList dChildren = dimensionElement.getChildNodes();
			for (int factorNumber = 0; factorNumber < dChildren.getLength(); factorNumber++) {
				if(dChildren.item(factorNumber).getNodeType() == Node.ELEMENT_NODE){
					Element factorElement = (Element)dChildren.item(factorNumber);
					String factorName = factorElement.getAttribute("name");
	
					List<Measure> factorMeasures = new ArrayList<Measure>();
					NodeList fChildren = factorElement.getChildNodes();
					for (int measureNumber = 0; measureNumber < fChildren.getLength(); measureNumber++) {
						if(fChildren.item(measureNumber).getNodeType() == Node.ELEMENT_NODE){
							Element measureElement = (Element)fChildren.item(measureNumber);
							String measureName = measureElement.getAttribute("name");
							File catalog;
							if (useFileService) {
								catalog = new File("");
							} else {
								catalog = new File(measureFile);
							}
							if (measureCatalogs.get(measureFile) == null){
								measureCatalogs.put(measureFile, new HashMap<String, Measure>());
							}
							if (!measureCatalogs.get(measureFile).containsKey(measureName)){
								measureCatalogs.put(measureFile, updateMeasures(catalog, measureFile));
							}
							if (!measureCatalogs.get(measureFile).containsKey(measureName)){
								throw new MalformedXMLException(
										successModelFile.toString() + ": Measure name " + measureName + " is unknown!");
							}
							factorMeasures.add(measureCatalogs.get(measureFile).get(measureName));
						}
					}
					Factor factor = new Factor(factorName, dimension, factorMeasures);
					factors.add(factor);
				}
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
	private Measure insertNode(Measure measure, String nodeId) {
		Pattern pattern = Pattern.compile("\\$NODE\\$");
		Map<String, String> insertedQueries = new HashMap<String, String>();

		Iterator<Map.Entry<String, String>> queries = measure.getQueries().entrySet().iterator();
		while (queries.hasNext()) {
			Map.Entry<String, String> entry = queries.next();
			insertedQueries.put(entry.getKey(), (pattern.matcher(entry.getValue()).replaceAll(nodeId)));
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
	private Measure insertService(Measure measure, String serviceId) {
		Pattern pattern = Pattern.compile("\\$SERVICE\\$");
		Map<String, String> insertedQueries = new HashMap<String, String>();

		Iterator<Map.Entry<String, String>> queries = measure.getQueries().entrySet().iterator();
		while (queries.hasNext()) {
			Map.Entry<String, String> entry = queries.next();
			insertedQueries.put(entry.getKey(), (pattern.matcher(entry.getValue()).replaceAll(serviceId)));
		}
		measure.setInsertedQueries(insertedQueries);
		return measure;
	}

	public String getMeasureCatalogs() {
		try {
			// RMI call

			Object result = this.invokeServiceMethod("i5.las2peer.services.fileService.FileService@1.0", "getFileIndex",
					new Serializable[] {});
			if (result != null) {
				@SuppressWarnings("unchecked")
				ArrayList<Map<String, Object>> response = (ArrayList<Map<String, Object>>) result;
				// Filter results
				ArrayList<String> resultList = new ArrayList<String>();
				for (Map<String, Object> k : response) {
					if (((String) k.get("identifier")).contains(catalogFileLocation)) {
						resultList.add((String) k.get("identifier"));
					}
				}
				return resultList.toString();

			} else {
				System.out.println("Fehler");
			}
		} catch (Exception e) {
			// one may want to handle some exceptions differently
			e.printStackTrace();
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return "[]";
	}

	public ArrayList<String> getMeasureCatalogList() {
		try {
			// RMI call

			Object result = this.invokeServiceMethod("i5.las2peer.services.fileService.FileService@1.0", "getFileIndex",
					new Serializable[] {});
			if (result != null) {
				@SuppressWarnings("unchecked")
				ArrayList<Map<String, Object>> response = (ArrayList<Map<String, Object>>) result;
				// Filter results
				ArrayList<String> resultList = new ArrayList<String>();
				for (Map<String, Object> k : response) {
					if (((String) k.get("identifier")).contains(catalogFileLocation)) {
						resultList.add((String) k.get("identifier"));
					}
				}
				return resultList;

			} else {
				System.out.println("Fehler");
			}
		} catch (Exception e) {
			// one may want to handle some exceptions differently
			e.printStackTrace();
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return new ArrayList<String>();
	}

	public ArrayList<String> getSuccessModels() {
		try {
			// RMI call

			Object result = this.invokeServiceMethod("i5.las2peer.services.fileService.FileService@1.0", "getFileIndex",
					new Serializable[] {});
			if (result != null) {
				@SuppressWarnings("unchecked")
				ArrayList<Map<String, Object>> response = (ArrayList<Map<String, Object>>) result;
				// Filter results
				ArrayList<String> resultList = new ArrayList<String>();
				for (Map<String, Object> k : response) {
					if (((String) k.get("identifier")).contains(successModelsFolderLocation)) {
						resultList.add((String) k.get("identifier"));
					}
				}
				return resultList;

			} else {
				System.out.println("Fehler");
			}
		} catch (Exception e) {
			// one may want to handle some exceptions differently
			e.printStackTrace();
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return new ArrayList<String>();
	}

	public String getFile(String file) {
		try {
			// RMI call

			Object result = this.invokeServiceMethod("i5.las2peer.services.fileService.FileService@1.0", "fetchFile",
					new Serializable[] { file });
			if (result != null) {
				@SuppressWarnings("unchecked")
				Map<String, Object> response = (Map<String, Object>) result;
				return new String((byte[]) response.get("content"));

			} else {
				System.out.println("Fehler");
			}
		} catch (Exception e) {
			// one may want to handle some exceptions differently
			e.printStackTrace();
			L2pLogger.logEvent(this, Event.SERVICE_ERROR, e.toString());
		}
		return "";
	}

}
