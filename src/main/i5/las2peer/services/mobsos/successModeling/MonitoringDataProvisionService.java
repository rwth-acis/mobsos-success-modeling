package i5.las2peer.services.mobsos.successModeling;

import i5.las2peer.api.Context;
import i5.las2peer.api.ManualDeployment;
import i5.las2peer.api.execution.ServiceInvocationException;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.api.security.Agent;
import i5.las2peer.api.security.AgentNotFoundException;
import i5.las2peer.api.security.AgentOperationFailedException;
import i5.las2peer.api.security.UserAgent;
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
import i5.las2peer.services.mobsos.successModeling.queryVisualizationService.QVConnector;
import i5.las2peer.services.mobsos.successModeling.successModel.Factor;
import i5.las2peer.services.mobsos.successModeling.successModel.Measure;
import i5.las2peer.services.mobsos.successModeling.successModel.MeasureCatalog;
import i5.las2peer.services.mobsos.successModeling.successModel.NodeSuccessModel;
import i5.las2peer.services.mobsos.successModeling.successModel.ServiceSuccessModel;
import i5.las2peer.services.mobsos.successModeling.successModel.SuccessModel;
import i5.las2peer.services.mobsos.successModeling.visualizations.Chart;
import i5.las2peer.services.mobsos.successModeling.visualizations.Chart.ChartType;
import i5.las2peer.services.mobsos.successModeling.visualizations.KPI;
import i5.las2peer.services.mobsos.successModeling.visualizations.Value;
import i5.las2peer.services.mobsos.successModeling.visualizations.Visualization;
import i5.las2peer.services.mobsos.successModeling.visualizations.charts.MethodResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This service will connect to the monitoring database and provide an interface for frontend clients to visualize
 * monitored data.
 *
 * @author Peter de Lange
 */
@ServicePath("mobsos-success-modeling")
@ManualDeployment
public class MonitoringDataProvisionService extends RESTService {

  public final String NODE_QUERY;
  public final String SERVICE_QUERY;
  public final String AGENT_QUERY_WITH_MD5ID_PARAM;
  public final String GROUP_QUERY;
  public final String GROUP_QUERY_WITH_ID_PARAM;
  public final String GROUP_AGENT_INSERT;
  public final String GROUP_INFORMATION_INSERT;
  public final String GROUP_INFORMATION_UPDATE;

  /**
   * Interval between rereading all model and measurement files in milliseconds.
   */
  public final long FILE_REFRESH_INTERVAL = 5000;
  private final String fileServicePrefix =
    "i5.las2peer.services.fileService.FileService@";
  private final String fileServiceVersion = "*";
  private final String fileServiceIdentifier =
    fileServicePrefix + fileServiceVersion;
  private final String mobsosQVServiceIdentifier =
    "i5.las2peer.services.mobsos.queryVisualization.QueryVisualizationService@*";
  private final String QV_MOBSOS_DB_KEY = "__mobsos";
  protected SQLDatabase database; // The database instance to write to.
  Boolean useFileService;
  String catalogFileLocation;
  TreeMap<String, MeasureCatalog> measureCatalogs = new TreeMap<>();
  Map<String, SuccessModel> knownModels = new TreeMap<>();
  /**
   * This map contains the same models as knownModels, but it uses groups as first level keys and service names as
   * second level keys. It is only used by the v2 API.
   */
  Map<String, Map<String, SuccessModel>> knownModelsV2 = new TreeMap<>();

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
  private String successModelsFolderLocation;
  private String DB2Schema;

  private FileBackend measureFileBackend;
  private FileBackend modelFileBackend;
  private boolean measureUpdatingStarted = false;
  boolean insertDatabaseCredentialsIntoQVService;
  protected String GRAPHQL_PROTOCOL = "http";
  protected String GRAPHQ_HOST = "127.0.0.1:8090";
  protected String CHART_API_ENDPOINT = "http://localhost:3000";

  protected String defaultGroupId =
    "17fa54869efcd27a04b8077a6274385415cc5e8ba8a0e3c14a9cbe0a030327ad6f4003d4a8eb629c23dfd812f61e908cd4908fbd061ff3268aa9b81bc43f6ebb";
  protected String defaultServiceName =
    "i5.las2peer.services.mensaService.MensaService";

  /**
   * Constructor of the Service. Loads the database values from a property file and tries to connect to the database.
   */
  public MonitoringDataProvisionService() {
    setFieldValues(); // This sets the values of the configuration file

    this.databaseType = SQLDatabaseType.getSQLDatabaseType(databaseTypeInt);

    this.database =
      new SQLDatabase(
        this.databaseType,
        this.databaseUser,
        this.databasePassword,
        this.databaseName,
        this.databaseHost,
        this.databasePort
      );

    if (this.databaseType == SQLDatabaseType.MySQL) {
      this.NODE_QUERY = "SELECT * FROM NODE";
      this.SERVICE_QUERY =
        "SELECT SERVICE.AGENT_ID,SERVICE_CLASS_NAME,SERVICE_PATH,REGISTRATION_DATE FROM SERVICE LEFT JOIN REGISTERED_AT ON SERVICE.AGENT_ID = REGISTERED_AT.AGENT_ID ORDER BY REGISTRATION_DATE";
      this.AGENT_QUERY_WITH_MD5ID_PARAM =
        "SELECT * FROM AGENT WHERE AGENT_ID = ?";
      this.GROUP_QUERY =
        "SELECT GROUP_AGENT_ID,GROUP_NAME " +
        "FROM GROUP_INFORMATION " +
        "WHERE PUBLIC=1";
      this.GROUP_QUERY_WITH_ID_PARAM =
        this.GROUP_QUERY + " AND GROUP_AGENT_ID=?";
      this.GROUP_AGENT_INSERT = "INSERT INTO AGENT VALUES (?, \"GROUP\")";
      this.GROUP_INFORMATION_INSERT =
        "INSERT INTO GROUP_INFORMATION VALUES (?, ?, ?, 1)";
      this.GROUP_INFORMATION_UPDATE =
        "UPDATE GROUP_INFORMATION SET GROUP_NAME = ? WHERE GROUP_AGENT_ID = ?";
    } else {
      this.NODE_QUERY = "SELECT * FROM " + DB2Schema + ".NODE";
      this.SERVICE_QUERY =
        "SELECT SERVICE.AGENT_ID,SERVICE_CLASS_NAME,SERVICE_PATH FROM " +
        DB2Schema +
        ".SERVICE LEFT OUTER JOIN REGISTERED_AT ON SERVICE.AGENT_ID = REGISTERED_AT.AGENT_ID ORDER BY REGISTRATION_DATE";
      this.AGENT_QUERY_WITH_MD5ID_PARAM =
        "SELECT * FROM " + DB2Schema + ".AGENT WHERE AGENT_ID = ?";
      this.GROUP_QUERY =
        "SELECT GROUP_AGENT_ID,GROUP_NAME " +
        "FROM " +
        DB2Schema +
        ".GROUP_INFORMATION " +
        "WHERE PUBLIC=1";
      this.GROUP_QUERY_WITH_ID_PARAM =
        this.GROUP_QUERY + " AND GROUP_AGENT_ID=?";
      this.GROUP_AGENT_INSERT =
        "INSERT INTO " + DB2Schema + ".AGENT VALUES (?, \"GROUP\")";
      this.GROUP_INFORMATION_INSERT =
        "INSERT INTO " + DB2Schema + ".GROUP_INFORMATION VALUES (?, ?, ?, 1)";
      this.GROUP_INFORMATION_UPDATE =
        "UPDATE " +
        DB2Schema +
        ".GROUP_INFORMATION SET GROUP_NAME = ? WHERE GROUP_AGENT_ID = ?";
    }

    try {
      this.database.connect();
      System.out.println("Monitoring: Database connected!");
    } catch (Exception e) {
      System.out.println(
        "Monitoring: Could not connect to database! " + e.getMessage()
      );
    }

    if (useFileService) {
      measureFileBackend =
        new FileServiceFileBackend(catalogFileLocation, fileServiceIdentifier);
      modelFileBackend =
        new FileServiceFileBackend(
          successModelsFolderLocation,
          fileServiceIdentifier
        );
    } else {
      measureFileBackend = new LocalFileBackend(catalogFileLocation);
      modelFileBackend = new LocalFileBackend(successModelsFolderLocation);
    }
  }

  /**
   * Reconnect to the database (can be called in case of an error).
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
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
      return new ArrayList<>();
    }
  }

  /**
   * This method will read the content of the success model folder and generate a {@link SuccessModel} for each file.
   *
   * @return a map with the {@link SuccessModel}s
   * @throws IOException if there exists a problem with the file handling
   */
  Map<String, SuccessModel> updateModels(String measureCatalog) {
    Map<String, SuccessModel> models = new TreeMap<>();
    try {
      List<String> successModelFiles = getSuccessModels();
      for (String successModelFile : successModelFiles) {
        String successModelFileContent = getSuccessModelFile(successModelFile);
        System.out.println(successModelFileContent);
        SuccessModel successModel;
        successModel =
          readSuccessModelFile(successModelFileContent, measureCatalog);
        models.put(successModel.getName(), successModel);
      }
    } catch (Exception e) {
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    return models;
  }

  private List<String> getSuccessModels() throws FileBackendException {
    return modelFileBackend
      .listFiles()
      .stream()
      .filter(s -> s.endsWith(".xml"))
      .collect(Collectors.toList());
  }

  public ArrayList<String> getServiceIds(String service) {
    ArrayList<String> serviceId = null;

    ResultSet resultSet;
    try {
      reconnect();
      resultSet = database.query(SERVICE_QUERY);
      serviceId = new ArrayList<String>();
      while (resultSet.next()) {
        if (resultSet.getString(2).equals(service)) {
          serviceId.add(resultSet.getString(1));
        }
      }
    } catch (SQLException e) {
      System.out.println(
        "(Visualize Success Model) The query has lead to an error: " + e
      );
      return new ArrayList<String>();
    }
    return serviceId;
  }

  /**
   * Visualizes a given success model.
   *
   * @param modelName the name of the success model
   * @param nodeName the name of a node necessary if a node success model should be calculated (can be set to null
   *            otherwise)
   * @return a HTML representation of the success model
   */
  String visualizeSuccessModel(
    String modelName,
    String nodeName,
    String catalog
  ) {
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
    ArrayList<String> serviceId = null;
    if (model.getServiceName() != null && model.getServiceName() != "node") {
      ResultSet resultSet;
      try {
        reconnect();
        resultSet = database.query(SERVICE_QUERY);
        serviceId = new ArrayList<String>();
        while (resultSet.next()) {
          if (resultSet.getString(2).equals(model.getServiceName())) {
            serviceId.add(resultSet.getString(1));
          }
        }
      } catch (SQLException e) {
        System.out.println(
          "(Visualize Success Model) The query has lead to an error: " + e
        );
        return "Problems getting service agent!";
      }
      if (serviceId == null) {
        return (
          "Requested Service: " + model.getServiceName() + " is not monitored!"
        );
      }
    } else if (nodeName == null) {
      return "No node given!";
    }
    SuccessModel.Dimension[] dimensions = SuccessModel.Dimension.getDimensions();
    String[] dimensionNames = SuccessModel.Dimension.getDimensionNames();
    List<Factor> factorsOfDimension;
    List<Measure> measuresOfFactor;
    StringBuilder returnStatement = new StringBuilder(
      "<div id = '" + modelName + "'>\n"
    );
    for (int i = 0; i < dimensions.length; i++) {
      returnStatement
        .append("<div id = '")
        .append(dimensions[i])
        .append("'>\n");
      returnStatement
        .append("<h3>")
        .append(dimensionNames[i])
        .append("</h3>\n");
      factorsOfDimension = model.getFactorsOfDimension(dimensions[i]);
      for (Factor factor : factorsOfDimension) {
        returnStatement
          .append("<h4>")
          .append(factor.getName())
          .append("</h4>\n");
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
            System.out.println(
              "Problems visualizing measure: " +
              measure.getName() +
              " Exception: " +
              e
            );
          }
        }
      }
      returnStatement.append("</div>\n");
    }
    returnStatement.append("</div>\n");
    return returnStatement.toString();
  }

  List<String> getRawMeasureData(Measure measure, ArrayList<String> serviceId) {
    List<String> result = new ArrayList<>();
    measure = insertService(measure, serviceId);
    try {
      reconnect();
      for (String query : measure.getInsertedQueries().values()) {
        System.out.println("[RAW SQL] running raw measurement query: " + query);
        ResultSet resultSet = database.query(query);
        MethodResult methodResult = new MethodResult(resultSet);
        System.out.println("[RAW SQL] got result: " + methodResult.toString());
        result.add(methodResult.toString());
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    return result;
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

  public String getDirectory(String filePath) {
    File parentDirFile = new File(filePath).getParentFile();
    if (parentDirFile == null) {
      return null;
    }
    return parentDirFile.getName();
  }

  private boolean sameDirectory(String path1, String path2) {
    String file1Dir = getDirectory(path1);
    String file2Dir = getDirectory(path2);
    return Objects.equals(file1Dir, file2Dir);
  }

  public String getMeasureCatalogGroup(String catalogPath) {
    return getDirectory(catalogPath);
  }

  public String getSuccessModelGroup(String modelPath) {
    return getDirectory(modelPath);
  }

  private String getMeasureCatalogForModel(String modelPath) {
    for (String measureFilePath : measureCatalogs.keySet()) {
      if (sameDirectory(modelPath, measureFilePath)) {
        return measureFilePath;
      }
    }
    return null;
  }

  private void refreshModels() {
    Map<String, SuccessModel> models = new TreeMap<>();
    Map<String, Map<String, SuccessModel>> modelsV2 = new TreeMap<>();
    try {
      List<String> successModelFiles = getSuccessModels();
      for (String successModelFile : successModelFiles) {
        String successModelFileContent = getSuccessModelFile(successModelFile);
        String measureCatalog = getMeasureCatalogForModel(successModelFile);
        if (measureCatalog != null) {
          SuccessModel successModel;
          successModel =
            readSuccessModelFile(successModelFileContent, measureCatalog);
          models.put(successModel.getName(), successModel);
          // also insert success model in a second map that is structured differently for the v2 API
          String group = getSuccessModelGroup(successModelFile);
          if (group != null) {
            if (!modelsV2.containsKey(group)) {
              modelsV2.put(group, new TreeMap<>());
            }
            modelsV2
              .get(group)
              .put(successModel.getServiceName(), successModel);
          }
        } else {
          System.out.println(
            "No measure catalog found for model " + successModelFile
          );
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    knownModels = models;
    knownModelsV2 = modelsV2;
  }

  private void refreshMeasuresAndModels() {
    refreshMeasures();
    refreshModels();
  }

  void ensureMobSOSDatabaseIsAccessibleInQVService() {
    QVConnector connector = new QVConnector(this.mobsosQVServiceIdentifier);
    List<String> databaseKeys;
    try {
      databaseKeys = connector.getDatabaseKeys();
      if (databaseKeys.contains(this.QV_MOBSOS_DB_KEY)) {
        return;
      }
    } catch (ServiceInvocationException e) {
      System.out.println("ServiceInvocationException:" + e.getMessage());
      e.printStackTrace();
    }
    QVConnector.SQLDatabaseType dbType;
    if (this.databaseType == SQLDatabaseType.DB2) {
      dbType = QVConnector.SQLDatabaseType.DB2;
    } else {
      dbType = QVConnector.SQLDatabaseType.MYSQL;
    }
    try {
      System.out.println("Adding DB...");
      connector.grantUserAccessToDatabase(
        this.QV_MOBSOS_DB_KEY,
        dbType,
        this.databaseUser,
        this.databasePassword,
        this.databaseName,
        this.databaseHost,
        this.databasePort
      );
    } catch (Exception e) {
      System.out.println("Could not access the MobSOS QV Service: " + e);
    }
  }

  /**
   * This method will read the contents of the catalog file and update the available measures.
   *
   * @return a map with the measures
   * @throws MalformedXMLException
   */
  protected MeasureCatalog updateMeasures(String measureFile)
    throws MalformedXMLException {
    String measureXML = getMeasureFile(measureFile);
    return readMeasureCatalog(measureXML);
  }

  private MeasureCatalog readMeasureCatalog(String measureXML)
    throws MalformedXMLException {
    Map<String, Measure> measures = new TreeMap<>();
    Element root;
    root = XmlTools.getRootElement(measureXML, "Catalog");
    NodeList children = root.getChildNodes();
    for (
      int measureNumber = 0;
      measureNumber < children.getLength();
      measureNumber++
    ) {
      if (children.item(measureNumber).getNodeType() == Node.ELEMENT_NODE) {
        Element measureElement = (Element) children.item(measureNumber);

        Map<String, String> queries = new HashMap<>();
        Visualization visualization = null;

        if (!measureElement.hasAttribute("name")) {
          throw new MalformedXMLException(
            "Catalog contains a measure without a name!"
          );
        }
        String measureName = measureElement.getAttribute("name");
        if (measures.containsKey("measureName")) {
          throw new MalformedXMLException(
            "Catalog already contains a measure " + measureName + "!"
          );
        }
        NodeList mChildren = measureElement.getChildNodes();
        for (
          int measureChildCount = 0;
          measureChildCount < mChildren.getLength();
          measureChildCount++
        ) {
          if (
            mChildren.item(measureChildCount).getNodeType() == Node.ELEMENT_NODE
          ) {
            Element measureChild = (Element) mChildren.item(measureChildCount);
            String childType = measureChild.getNodeName();

            if (childType.equals("query")) {
              String queryName = measureChild.getAttribute("name");
              String query = measureChild.getFirstChild().getTextContent();
              // Replace escape characters with their correct values (seems like the simple XML Parser
              // does not do
              // that)
              query =
                query
                  .replaceAll("&amp;&", "&")
                  .replaceAll("&lt;", "<")
                  .replaceAll("&lt;", "<")
                  .replaceAll("&gt;", ">")
                  .replaceAll("&lt;", "<");
              queries.put(queryName, query);
            } else if (childType.equals("visualization")) {
              if (visualization != null) {
                throw new MalformedXMLException(
                  "Measure " +
                  measureName +
                  " is broken, duplicate 'Visualization' entry!"
                );
              }
              visualization = readVisualization(measureChild);
            } else {
              throw new MalformedXMLException(
                "Measure " +
                measureName +
                " is broken, illegal node " +
                childType +
                "!"
              );
            }
          }
        }

        if (visualization == null) {
          throw new MalformedXMLException(
            "Measure " + measureName + " is broken, no visualization element!"
          );
        }
        if (queries.isEmpty()) {
          throw new MalformedXMLException(
            "Measure " + measureName + " is broken, no query element!"
          );
        }

        measures.put(
          measureName,
          new Measure(measureName, queries, visualization)
        );
      }
    }

    return new MeasureCatalog(measures, measureXML);
  }

  /**
   * Helper method that reads a visualization object of the catalog file.
   *
   * @return a visualization object
   * @throws MalformedXMLException
   */
  private Visualization readVisualization(Element visualizationElement)
    throws MalformedXMLException {
    String visualizationType = visualizationElement.getAttribute("type");
    if (visualizationType.equals("Value")) {
      return new Value();
    } else if (visualizationType.equals("KPI")) {
      Map<Integer, String> expression = new TreeMap<>();
      NodeList children = visualizationElement.getChildNodes();
      for (int i = 0; i < children.getLength(); i++) {
        if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
          int index = Integer.valueOf(
            ((Element) children.item(i)).getAttribute("index")
          );
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
    throw new MalformedXMLException(
      "Unknown visualization type: " + visualizationType
    );
  }

  /**
   * Reads a success model file.
   *
   * @return a {@link SuccessModel}
   * @throws MalformedXMLException
   */
  private SuccessModel readSuccessModelFile(
    String successModelXml,
    String measureFilePath
  )
    throws MalformedXMLException {
    Element root;
    try {
      root = XmlTools.getRootElement(successModelXml, "SuccessModel");
    } catch (MalformedXMLException e) {
      root = XmlTools.getRootElement(successModelXml, "NodeSuccessModel");
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
        throw new MalformedXMLException("Success model expected!");
      }
      if (!root.hasAttribute("service")) {
        throw new MalformedXMLException("Service attribute expected!");
      }
      serviceName = root.getAttribute("service");
    }
    NodeList children = root.getChildNodes();
    ArrayList<Element> elements = new ArrayList<>();
    for (
      int dimensionNumber = 0;
      dimensionNumber < children.getLength();
      dimensionNumber++
    ) {
      if (
        children.item(dimensionNumber).getNodeType() == Node.ELEMENT_NODE &&
        children.item(dimensionNumber).getNodeName().equals("dimension")
      ) {
        elements.add((Element) children.item(dimensionNumber));
      }
    }
    if (elements.size() != 6) {
      throw new MalformedXMLException("Six dimensions expected!");
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
            "Dimension " + dimensionName + " is unknown!"
          );
      }
      NodeList dChildren = dimensionElement.getChildNodes();
      for (
        int factorNumber = 0;
        factorNumber < dChildren.getLength();
        factorNumber++
      ) {
        if (dChildren.item(factorNumber).getNodeType() == Node.ELEMENT_NODE) {
          Element factorElement = (Element) dChildren.item(factorNumber);
          String factorName = factorElement.getAttribute("name");
          List<Measure> factorMeasures = new ArrayList<>();
          NodeList fChildren = factorElement.getChildNodes();
          for (
            int measureNumber = 0;
            measureNumber < fChildren.getLength();
            measureNumber++
          ) {
            if (
              fChildren.item(measureNumber).getNodeType() == Node.ELEMENT_NODE
            ) {
              Element measureElement = (Element) fChildren.item(measureNumber);
              String measureName = measureElement.getAttribute("name");
              if (measureCatalogs.get(measureFilePath) == null) {
                measureCatalogs.put(measureFilePath, new MeasureCatalog());
              }
              if (
                !measureCatalogs
                  .get(measureFilePath)
                  .getMeasures()
                  .containsKey(measureName)
              ) {
                measureCatalogs.put(
                  measureFilePath,
                  updateMeasures(measureFilePath)
                );
              }
              if (
                !measureCatalogs
                  .get(measureFilePath)
                  .getMeasures()
                  .containsKey(measureName)
              ) {
                throw new MalformedXMLException(
                  "Measure name " + measureName + " is unknown!"
                );
              }
              factorMeasures.add(
                measureCatalogs
                  .get(measureFilePath)
                  .getMeasures()
                  .get(measureName)
              );
            }
          }
          Factor factor = new Factor(factorName, dimension, factorMeasures);
          factors.add(factor);
        }
      }
    }
    if (nodeSuccessModel) {
      return new NodeSuccessModel(modelName, factors, successModelXml);
    }
    return new ServiceSuccessModel(
      modelName,
      serviceName,
      factors,
      successModelXml
    );
  }

  /**
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
   * Inserts the service id into the queries of a measure.
   *
   * @param measure
   * @param serviceId
   * @return the measure with inserted serviceId
   */
  /*
	protected Measure insertService(Measure measure, String serviceId) {
	    Pattern pattern = Pattern.compile("\\$SERVICE\\$");
	    return insertQueryVariable(measure, serviceId, pattern);
	}
	*/
  protected Measure insertService(
    Measure measure,
    ArrayList<String> serviceId
  ) {
    String[] ps = new String[2];
    ps[0] = "SOURCE";
    ps[1] = "DESTINATION";
    Pattern[] pattern = new Pattern[ps.length];
    for (int i = 0; i < ps.length; i++) {
      pattern[i] = Pattern.compile("\\$" + ps[i] + "_AGENT\\$");
    }

    pattern[1] = Pattern.compile("\\$DESTINATION_AGENT\\$");
    Map<String, String> insertedQueries = new HashMap<>();

    Iterator<Map.Entry<String, String>> queries = measure
      .getQueries()
      .entrySet()
      .iterator();
    while (queries.hasNext()) {
      Map.Entry<String, String> entry = queries.next();
      String[] r = new String[ps.length];
      for (int i = 0; i < ps.length; i++) {
        r[i] = "(";
      }
      for (String s : serviceId) {
        for (int i = 0; i < ps.length; i++) {
          r[i] += ps[i] + "_AGENT = '" + s + "' OR ";
        }
      }
      for (int i = 0; i < ps.length; i++) {
        r[i] = r[i].substring(0, r[i].length() - 3) + ")";
      }
      String toReplace = entry.getValue();
      for (int i = 0; i < ps.length; i++) {
        toReplace = pattern[i].matcher(toReplace).replaceAll(r[i]);
      }
      insertedQueries.put(entry.getKey(), toReplace);
    }
    measure.setInsertedQueries(insertedQueries);
    return measure;
  }

  private Measure insertQueryVariable(
    Measure measure,
    String serviceId,
    Pattern pattern
  ) {
    Map<String, String> insertedQueries = new HashMap<>();

    for (Map.Entry<String, String> entry : measure.getQueries().entrySet()) {
      insertedQueries.put(
        entry.getKey(),
        (pattern.matcher(entry.getValue()).replaceAll(serviceId))
      );
    }
    measure.setInsertedQueries(insertedQueries);
    return measure;
  }

  List<String> getMeasureCatalogList() throws FileBackendException {
    return measureFileBackend
      .listFiles()
      .stream()
      .filter(s -> s.endsWith(".xml"))
      .collect(Collectors.toList());
  }

  private String getMeasureFile(String file) {
    try {
      return measureFileBackend.getFile(file);
    } catch (Exception e) {
      // one may want to handle some exceptions differently
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    return "";
  }

  private String getMeasureCatalogFilePathByGroup(String group) {
    for (String measureFile : measureCatalogs.keySet()) {
      if (Objects.equals(getMeasureCatalogGroup(measureFile), group)) {
        return measureFile;
      }
    }
    return null;
  }

  MeasureCatalog getMeasureCatalogByGroup(String group) {
    String measureFile = getMeasureCatalogFilePathByGroup(group);
    if (measureFile == null) {
      return null;
    }
    return measureCatalogs.get(measureFile);
  }

  void writeMeasureCatalog(String xml, String group)
    throws MalformedXMLException, FileBackendException {
    readMeasureCatalog(xml);
    measureFileBackend.writeFile(
      Paths.get(group, "measure-catalog.xml").toString(),
      xml,
      group
    );
    refreshMeasuresAndModels();
  }

  void writeSuccessModel(String xml, String group, String expectedServiceName)
    throws MalformedXMLException, FileBackendException {
    String measureFilePath = getMeasureCatalogFilePathByGroup(group);
    SuccessModel successModel = readSuccessModelFile(xml, measureFilePath);
    if (!successModel.getServiceName().equals(expectedServiceName)) {
      throw new MalformedXMLException(
        "Service name is " +
        successModel.getServiceName() +
        " and not " +
        expectedServiceName
      );
    }
    modelFileBackend.writeFile(
      Paths.get(group, successModel.getServiceName() + ".xml").toString(),
      xml,
      group
    );
    refreshMeasuresAndModels();
  }

  Map<String, String> getCustomMessageDescriptionsForService(String serviceID) {
    try {
      return (Map<String, String>) Context
        .get()
        .invoke(serviceID, "getCustomMessageDescriptions");
    } catch (ServiceInvocationException e) {
      System.out.println(serviceID + ": " + e.getMessage());
      return new HashMap<>();
    }
  }

  public String getSuccessModelFile(String file) {
    try {
      return modelFileBackend.getFile(file);
    } catch (Exception e) {
      // one may want to handle some exceptions differently
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    return "";
  }

  public boolean currentUserIsMemberOfGroup(String group) {
    Agent agent = Context.get().getMainAgent();
    if (agent instanceof UserAgent) {
      try {
        return Context.get().hasAccess(group);
      } catch (AgentNotFoundException | AgentOperationFailedException e) {
        return false;
      }
    }
    return false;
  }

  public net.minidev.json.JSONArray getTrainingDataUnits(
    String serviceName,
    String logMessageType
  ) {
    net.minidev.json.JSONArray resultList = new net.minidev.json.JSONArray();
    try {
      // GET SERVICE AGENT
      ArrayList<String> sa = getServiceIds(serviceName);
      // GET MESSAGE FOR SERVICE AGENT
      String q =
        "SELECT REMARKS->>\"$.unit\" u FROM MESSAGE WHERE (SOURCE_AGENT='" +
        sa.get(0) +
        "'";
      if (sa.size() > 1) {
        for (int i = 1; i < sa.size(); ++i) {
          q += " OR SOURCE_AGENT='" + sa.get(i) + "'";
        }
      }
      q += ") AND EVENT='" + logMessageType + "' GROUP BY REMARKS->>\"$.unit\"";
      reconnect();
      ResultSet resultSet = database.query(q);
      while (resultSet.next()) {
        String u = resultSet.getString(1);
        resultList.add(u);
      }
    } catch (Exception e) {
      // one may want to handle some exceptions differently
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    return resultList;
  }

  public net.minidev.json.JSONArray getTrainingDataSet(
    String serviceName,
    String unit,
    String logMessageType
  ) {
    net.minidev.json.JSONArray resultList = new net.minidev.json.JSONArray();
    try {
      // GET SERVICE AGENT
      ArrayList<String> sa = getServiceIds(serviceName);
      // GET MESSAGE FOR SERVICE AGENT
      String q =
        "SELECT JSON_EXTRACT(REMARKS,'$.from') f, JSON_EXTRACT(REMARKS,'$.to') t FROM MESSAGE WHERE (SOURCE_AGENT='" +
        sa.get(0) +
        "'";
      if (sa.size() > 1) {
        for (int i = 1; i < sa.size(); ++i) {
          q += " OR SOURCE_AGENT='" + sa.get(i) + "'";
        }
      }
      q += ") AND EVENT='" + logMessageType + "'";
      if (unit != null && unit.length() > 0) {
        q += " AND JSON_EXTRACT(REMARKS,'$.unit')='" + unit + "'";
      }
      reconnect();
      ResultSet resultSet = database.query(q);
      while (resultSet.next()) {
        String from = resultSet.getString(1);
        String to = resultSet.getString(2);
        net.minidev.json.JSONObject j = new net.minidev.json.JSONObject();
        j.put("from", from);
        j.put("to", to);
        resultList.add(j);
      }
    } catch (Exception e) {
      // one may want to handle some exceptions differently
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    return resultList;
  }

  @Override
  protected void initResources() {
    getResourceConfig().register(PrematchingRequestFilter.class);
    getResourceConfig().register(RestApiV1.class);
    getResourceConfig().register(RestApiV2.class);
  }

  void startUpdatingMeasures() {
    if (!measureUpdatingStarted) {
      refreshMeasuresAndModels();
      measureUpdatingStarted = true;
      Context
        .get()
        .getExecutor()
        .execute(
          () -> {
            while (measureUpdatingStarted) {
              try {
                Thread.sleep(FILE_REFRESH_INTERVAL);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
              refreshMeasuresAndModels();
            }
          }
        );
    }
  }
}
