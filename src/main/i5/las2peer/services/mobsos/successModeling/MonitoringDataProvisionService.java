package i5.las2peer.services.mobsos.successModeling;

import i5.las2peer.api.Context;
import i5.las2peer.api.ManualDeployment;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;
import i5.las2peer.serialization.MalformedXMLException;
import i5.las2peer.serialization.XmlTools;
import i5.las2peer.services.mobsos.successModeling.database.SQLDatabase;
import i5.las2peer.services.mobsos.successModeling.database.SQLDatabaseType;
import i5.las2peer.services.mobsos.successModeling.files.FileBackend;
import i5.las2peer.services.mobsos.successModeling.files.FileBackendException;
import i5.las2peer.services.mobsos.successModeling.files.FileServiceFileBackend;
import i5.las2peer.services.mobsos.successModeling.files.LocalFileBackend;
import i5.las2peer.services.mobsos.successModeling.successModel.Factor;
import i5.las2peer.services.mobsos.successModeling.successModel.Measure;
import i5.las2peer.services.mobsos.successModeling.successModel.SuccessModel;
import i5.las2peer.services.mobsos.successModeling.visualizations.Chart;
import i5.las2peer.services.mobsos.successModeling.visualizations.Chart.ChartType;
import i5.las2peer.services.mobsos.successModeling.visualizations.KPI;
import i5.las2peer.services.mobsos.successModeling.visualizations.Value;
import i5.las2peer.services.mobsos.successModeling.visualizations.Visualization;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 *
 * This service will connect to the monitoring database and provide an interface for frontend clients to visualize
 * monitored data.
 *
 * @author Peter de Lange
 *
 */
@ServicePath("mobsos-success-modeling")
@ManualDeployment
public class MonitoringDataProvisionService extends RESTService {

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
	private final String fileServicePrefix = "i5.las2peer.services.fileService.FileService@";
	private final String fileServiceVersion = "1.1";
	private String successModelsFolderLocation;
	private String DB2Schema;
	private final String fileServiceIdentifier = fileServicePrefix + fileServiceVersion;
	protected SQLDatabase database; // The database instance to write to.
	Boolean useFileService;
	String catalogFileLocation;
	TreeMap<String, Map<String, Measure>> measureCatalogs = new TreeMap<>();
	Map<String, SuccessModel> knownModels = new TreeMap<>();
	private FileBackend measureFileBackend;
	private FileBackend modelFileBackend;
	private boolean measureUpdatingStarted = false;

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

		if (useFileService) {
			measureFileBackend = new FileServiceFileBackend(catalogFileLocation, fileServiceIdentifier);
			modelFileBackend = new FileServiceFileBackend(successModelsFolderLocation, fileServiceIdentifier);
		} else {
			measureFileBackend = new LocalFileBackend(catalogFileLocation);
			modelFileBackend = new LocalFileBackend(successModelsFolderLocation);
		}
	}

	/**
	 * 
	 * Reconnect to the database (can be called in case of an error).
	 * 
	 */
	void reconnect() {
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

	List<String> getMeasureCatalogLocations() {
		try {
			return measureFileBackend.listFiles();
		} catch (FileBackendException e) {
			e.printStackTrace();
			Context.get().monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
			return new ArrayList<>();
		}
	}

	/**
	 *
	 * This method will read the content of the success model folder and generate a {@link SuccessModel} for each file.
	 *
	 * @return a map with the {@link SuccessModel}s
	 *
	 * @throws IOException if there exists a problem with the file handling
	 *
	 */
	Map<String, SuccessModel> updateModels(String measureCatalog) {
		Map<String, SuccessModel> models = new TreeMap<>();
		try {
			List<String> successModelFiles = getSuccessModels();
			for (String successModelFile : successModelFiles) {
				String successModelFileContent = getSuccessModelFile(successModelFile);
				System.out.println(successModelFileContent);
				SuccessModel successModel;
				successModel = readSuccessModelFile(successModelFile, successModelFileContent, measureCatalog);
				models.put(successModel.getName(), successModel);
			}
		} catch (Exception e) {
			e.printStackTrace();
			Context.get().monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
		}
		return models;
	}

	private List<String> getSuccessModels() throws FileBackendException {
		return modelFileBackend.listFiles().stream().filter(s -> s.endsWith(".xml")).collect(Collectors.toList());
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
	String visualizeSuccessModel(String modelName, String nodeName, String catalog) {
		SuccessModel model = knownModels.get(modelName);
		// Reload models once
		if (model == null) {
			knownModels = updateModels(catalog);
		}
		model = knownModels.get(modelName);
		if (model == null) {
			return "Success Model not known!";
		}
		// Find the Service Agent
		String serviceId = null;
		if (model.getServiceName() != null) {
			ResultSet resultSet;
			try {
				reconnect();
				resultSet = database.query(SERVICE_QUERY);
				while (resultSet.next()) {
					if (resultSet.getString(2).equals(model.getServiceName())) {
						serviceId = resultSet.getString(1);
					}
				}
			} catch (SQLException e) {
				System.out.println("(Visualize Success Model) The query has lead to an error: " + e);
				return "Problems getting service agent!";
			}
			if (serviceId == null) {
				return "Requested Service: " + model.getServiceName() + " is not monitored!";
			}
		} else if (nodeName == null) {
			return "No node given!";
		}
		SuccessModel.Dimension[] dimensions = SuccessModel.Dimension.getDimensions();
		String[] dimensionNames = SuccessModel.Dimension.getDimensionNames();
		List<Factor> factorsOfDimension;
		List<Measure> measuresOfFactor;

		StringBuilder returnStatement = new StringBuilder("<div id = '" + modelName + "'>\n");
		for (int i = 0; i < dimensions.length; i++) {
			returnStatement.append("<div id = '").append(dimensions[i]).append("'>\n");
			returnStatement.append("<h3>").append(dimensionNames[i]).append("</h3>\n");
			factorsOfDimension = model.getFactorsOfDimension(dimensions[i]);
			for (Factor factor : factorsOfDimension) {
				returnStatement.append("<h4>").append(factor.getName()).append("</h4>\n");
				measuresOfFactor = factor.getMeasures();
				for (Measure measure : measuresOfFactor) {
					if (serviceId != null) {
						measure = insertService(measure, serviceId);
					} else if (nodeName != null) {
						measure = insertNode(measure, nodeName);
					}
					returnStatement.append(measure.getName()).append(": ");
					try {
						returnStatement.append(measure.visualize(database));
						returnStatement.append("\n<br>\n");
					} catch (Exception e) {
						System.out.println("Problems visualizing measure: " + measure.getName() + " Exception: " + e);
					}
				}
			}
			returnStatement.append("</div>\n");
		}
		returnStatement.append("</div>\n");
		return returnStatement.toString();
	}

	private void refreshMeasures() {
		try {
			List<String> measureFiles = getMeasureCatalogList();
			for (String measureFile : measureFiles) {
				measureCatalogs.put(measureFile, updateMeasures(measureFile));
			}
		} catch (MalformedXMLException | FileBackendException e) {
			e.printStackTrace();
		}
	}

	/**
	 *
	 * This method will read the contents of the catalog file and update the available measures.
	 *
	 * @return a map with the measures
	 *
	 * @throws MalformedXMLException
	 * @throws IOException if the catalog file does not exist
	 *
	 */
	protected Map<String, Measure> updateMeasures(String measureFile)
			throws MalformedXMLException {

		Map<String, Measure> measures = new TreeMap<>();
		Element root;

		String measureXML = getMeasurelFile(measureFile);
		root = XmlTools.getRootElement(measureXML, "Catalog");
		NodeList children = root.getChildNodes();
		for (int measureNumber = 0; measureNumber < children.getLength(); measureNumber++) {
			if (children.item(measureNumber).getNodeType() == Node.ELEMENT_NODE) {
				Element measureElement = (Element) children.item(measureNumber);

				Map<String, String> queries = new HashMap<>();
				Visualization visualization = null;

				if (!measureElement.hasAttribute("name")) {
					throw new MalformedXMLException("Catalog contains a measure without a name!");
				}
				String measureName = measureElement.getAttribute("name");
				if (measures.containsKey("measureName")) {
					throw new MalformedXMLException("Catalog already contains a measure " + measureName + "!");
				}
				NodeList mChildren = measureElement.getChildNodes();
				for (int measureChildCount = 0; measureChildCount < mChildren.getLength(); measureChildCount++) {
					if (mChildren.item(measureChildCount).getNodeType() == Node.ELEMENT_NODE) {
						Element measureChild = (Element) mChildren.item(measureChildCount);
						String childType = measureChild.getNodeName();

						if (childType.equals("query")) {
							String queryName = measureChild.getAttribute("name");
							String query = measureChild.getFirstChild().getTextContent();
							// Replace escape characters with their correct values (seems like the simple XML Parser
							// does not do
							// that)
							query = query.replaceAll("&amp;&", "&").replaceAll("&lt;", "<").replaceAll("&lt;", "<")
									.replaceAll("&gt;", ">").replaceAll("&lt;", "<");
							queries.put(queryName, query);
						}

						else if (childType.equals("visualization")) {
							if (visualization != null) {
								throw new MalformedXMLException(
										"Measure " + measureName + " is broken, duplicate 'Visualization' entry!");
							}
							visualization = readVisualization(measureChild);
						}

						else {
							throw new MalformedXMLException(
									"Measure " + measureName + " is broken, illegal node " + childType + "!");
						}
					}
				}

				if (visualization == null) {
					throw new MalformedXMLException("Measure " + measureName + " is broken, no visualization element!");
				}
				if (queries.isEmpty()) {
					throw new MalformedXMLException("Measure " + measureName + " is broken, no query element!");
				}

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
	 */
	private Visualization readVisualization(Element visualizationElement) throws MalformedXMLException {
		String visualizationType = visualizationElement.getAttribute("type");
		if (visualizationType.equals("Value")) {
			return new Value();
		} else if (visualizationType.equals("KPI")) {
			Map<Integer, String> expression = new TreeMap<>();
			NodeList children = visualizationElement.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
					int index = Integer.valueOf(((Element) children.item(i)).getAttribute("index"));
					String name = ((Element) children.item(i)).getAttribute("name");
					expression.put(index, name);
				}
			}
			return new KPI(expression);
		} else if (visualizationType.equals("Chart")) {
			String type;
			ChartType chartType = null;
			String[] parameters = new String[4];
			NodeList children = visualizationElement.getChildNodes();
			Element[] elements = new Element[5];
			int j = 0;
			for (int i = 0; i < children.getLength(); ++i) {
				if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
					elements[j] = (Element) children.item(i);
					j++;
				}
				if (j >= 5) {
					break;
				}
			}
			type = elements[0].getFirstChild().getTextContent();
			for (int i = 0; i < 4; ++i) {
				parameters[i] = elements[i + 1].getFirstChild().getTextContent();
			}

			if (type.equals("BarChart")) {
				chartType = ChartType.BarChart;
			}
			if (type.equals("LineChart")) {
				chartType = ChartType.LineChart;
			}
			if (type.equals("PieChart")) {
				chartType = ChartType.PieChart;
			}
			if (type.equals("RadarChart")) {
				chartType = ChartType.RadarChart;
			}
			if (type.equals("TimelineChart")) {
				chartType = ChartType.TimelineChart;
			}

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
	 * Reads a success model file.
	 *
	 * @param successModelFileName
	 * 
	 * @return a {@link SuccessModel}
	 * @throws MalformedXMLException
	 * @throws IOException
	 */
	private SuccessModel readSuccessModelFile(String successModelFileName, String successModelFileContent, String measureFile)
			throws MalformedXMLException, IOException {
		Element root;
			try {
				root = XmlTools.getRootElement(successModelFileContent, "SuccessModel");
			} catch (MalformedXMLException e) {
				root = XmlTools.getRootElement(successModelFileContent, "NodeSuccessModel");
			}
		boolean nodeSuccessModel = false;
		String modelName = root.getAttribute("name");
		if (root.getNodeName().equals("NodeSuccessModel")) {
			nodeSuccessModel = true;
		}

		// If not a node success model, get the service name
		String serviceName = null;
		if (!nodeSuccessModel) {
			if (!root.getNodeName().equals("SuccessModel")) {
				throw new MalformedXMLException(successModelFileName.toString() + ": Success model expected!");
			}
			if (!root.hasAttribute("service")) {
				throw new MalformedXMLException("Service attribute expected!");
			}
			serviceName = root.getAttribute("service");
		}
		NodeList children = root.getChildNodes();
		ArrayList<Element> elements = new ArrayList<>();
		for (int dimensionNumber = 0; dimensionNumber < children.getLength(); dimensionNumber++) {
			if (children.item(dimensionNumber).getNodeType() == Node.ELEMENT_NODE) {
				elements.add((Element) children.item(dimensionNumber));
			}
		}
		if (elements.size() != 6) {
			throw new MalformedXMLException(successModelFileName + ": Six dimensions expected!");
		}
		List<Factor> factors = new ArrayList<>();

		for (Element dimensionElement : elements) {

			String dimensionName = dimensionElement.getAttribute("name");
			SuccessModel.Dimension dimension;
			switch (dimensionName) {
				case "System Quality":
					dimension = SuccessModel.Dimension.SystemQuality;
					break;
				case "Information Quality":
					dimension = SuccessModel.Dimension.InformationQuality;
					break;
				case "Use":
					dimension = SuccessModel.Dimension.Use;
					break;
				case "User Satisfaction":
					dimension = SuccessModel.Dimension.UserSatisfaction;
					break;
				case "Individual Impact":
					dimension = SuccessModel.Dimension.IndividualImpact;
					break;
				case "Community Impact":
					dimension = SuccessModel.Dimension.CommunityImpact;
					break;
				default:
					throw new MalformedXMLException(
							successModelFileName + ": Dimension " + dimensionName + " is unknown!");
			}
			NodeList dChildren = dimensionElement.getChildNodes();
			for (int factorNumber = 0; factorNumber < dChildren.getLength(); factorNumber++) {
				if (dChildren.item(factorNumber).getNodeType() == Node.ELEMENT_NODE) {
					Element factorElement = (Element) dChildren.item(factorNumber);
					String factorName = factorElement.getAttribute("name");

					List<Measure> factorMeasures = new ArrayList<>();
					NodeList fChildren = factorElement.getChildNodes();
					for (int measureNumber = 0; measureNumber < fChildren.getLength(); measureNumber++) {
						if (fChildren.item(measureNumber).getNodeType() == Node.ELEMENT_NODE) {
							Element measureElement = (Element) fChildren.item(measureNumber);
							String measureName = measureElement.getAttribute("name");
							File catalog;
							if (useFileService) {
								catalog = new File("");
							} else {
								catalog = new File(measureFile);
							}
							if (measureCatalogs.get(measureFile) == null) {
								measureCatalogs.put(measureFile, new HashMap<>());
							}
							if (!measureCatalogs.get(measureFile).containsKey(measureName)) {
								measureCatalogs.put(measureFile, updateMeasures(measureFile));
							}
							if (!measureCatalogs.get(measureFile).containsKey(measureName)) {
								throw new MalformedXMLException(
										successModelFileName + ": Measure name " + measureName + " is unknown!");
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
	 * @param measure
	 * @param nodeId
	 * @return the measure with inserted nodeId
	 */
	private Measure insertNode(Measure measure, String nodeId) {
		Pattern pattern = Pattern.compile("\\$NODE\\$");
		return insertQueryVariable(measure, nodeId, pattern);
	}

	/**
	 *
	 * Inserts the service id into the queries of a measure.
	 *
	 * @param measure
	 * @param serviceId
	 * @return the measure with inserted serviceId
	 */
	private Measure insertService(Measure measure, String serviceId) {
		Pattern pattern = Pattern.compile("\\$SERVICE\\$");
		return insertQueryVariable(measure, serviceId, pattern);
	}

	private Measure insertQueryVariable(Measure measure, String serviceId, Pattern pattern) {
		Map<String, String> insertedQueries = new HashMap<>();

		for (Map.Entry<String, String> entry : measure.getQueries().entrySet()) {
			insertedQueries.put(entry.getKey(), (pattern.matcher(entry.getValue()).replaceAll(serviceId)));
		}
		measure.setInsertedQueries(insertedQueries);
		return measure;
	}

	List<String> getMeasureCatalogList() throws FileBackendException {
		return measureFileBackend.listFiles().stream().filter(s -> s.endsWith(".xml")).collect(Collectors.toList());
	}

	private ArrayList<String> getFileIndexFromFileService(String catalogFileLocation) {
		try {
			// RMI call

			Object result = Context.get().invoke("i5.las2peer.services.fileService.FileService@" + fileServiceVersion,
					"getFileIndex");
			if (result != null) {
				@SuppressWarnings("unchecked")
				ArrayList<Map<String, Object>> response = (ArrayList<Map<String, Object>>) result;
				// Filter results
				ArrayList<String> resultList = new ArrayList<>();
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
			Context.get().monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
		}
		return new ArrayList<>();
	}

	private String getMeasurelFile(String file) {
		try {
			return measureFileBackend.getFile(file);
		} catch (Exception e) {
			// one may want to handle some exceptions differently
			e.printStackTrace();
			Context.get().monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
		}
		return "";
	}

	private String getSuccessModelFile(String file) {
		try {
			return modelFileBackend.getFile(file);
		} catch (Exception e) {
			// one may want to handle some exceptions differently
			e.printStackTrace();
			Context.get().monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
		}
		return "";
	}

	@Override
	protected void initResources() {
		getResourceConfig().register(RestApi.class);
	}

	void startUpdatingMeasures() {
		if (!measureUpdatingStarted) {
			refreshMeasures();

			// TODO: update models and measures regularly
			new Thread(() -> {
				refreshMeasures();
			}).start();
			measureUpdatingStarted = true;
		}

	}
}
