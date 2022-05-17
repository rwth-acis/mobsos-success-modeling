package i5.las2peer.services.mobsos.successModeling;

import i5.las2peer.api.Context;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.serialization.MalformedXMLException;
import i5.las2peer.services.mobsos.successModeling.files.FileBackendException;
import i5.las2peer.services.mobsos.successModeling.successModel.Factor;
import i5.las2peer.services.mobsos.successModeling.successModel.Measure;
import i5.las2peer.services.mobsos.successModeling.successModel.SuccessModel;
import io.swagger.annotations.*;
import java.io.File;
import java.io.FileWriter;
import java.net.HttpURLConnection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

@Path("/")
@Api
@SwaggerDefinition(
  info = @Info(
    title = "MobSOS Success Modeling",
    version = "0.1",
    description = "<p>This service is part of the MobSOS monitoring concept and provides visualization functionality of the monitored data to the web-frontend.</p>",
    termsOfService = "",
    contact = @Contact(
      name = "Alexander Neumann",
      email = "neumann@dbis.rwth-aachen.de"
    ),
    license = @License(
      name = "MIT",
      url = "https://github.com/rwth-acis/mobsos-success-modeling/blob/master/LICENSE"
    )
  )
)
public class RestApiV1 {

  private MonitoringDataProvisionService service = (MonitoringDataProvisionService) Context
    .getCurrent()
    .getService();

  /**
   * Returns all stored ( = monitored) nodes.
   *
   * @return an array of node id's
   */
  @SuppressWarnings("unchecked")
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/nodes")
  public Response getNodes() {
    JSONObject nodeIds = new JSONObject();

    ResultSet resultSet;
    try {
      service.reconnect();
      resultSet = service.database.query(service.NODE_QUERY);
    } catch (SQLException e) {
      System.out.println("(Get Nodes) The query has lead to an error: " + e);
      return null;
    }
    try {
      while (resultSet.next()) {
        nodeIds.put(
          resultSet.getString(1),
          "Location: " + resultSet.getString(2)
        );
      }
    } catch (SQLException e) {
      System.out.println("Problems reading result set: " + e);
    }
    return Response.status(Status.OK).entity(nodeIds.toJSONString()).build();
  }

  /**
   * Visualizes a success model for the given node.
   *
   * @param content JSON String containing:
   *                <ul>
   *                <li>nodeName the name of the node</li>
   *                <li>updateMeasures if true, all measures are updated from xml file</li>
   *                <li>updateModels if true, all models are updated from xml file</li>
   *                </ul>
   * @return a HTML representation of the success model
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_HTML)
  @Path("/visualize/nodeSuccessModel")
  public Response visualizeNodeSuccessModel(String content) {
    try {
      JSONParser parser = new JSONParser();
      JSONObject params = (JSONObject) parser.parse(content);

      String nodeName = (String) params.get("nodeName");
      boolean updateMeasures = Boolean.parseBoolean(
        (String) params.get("updateMeasures")
      );
      boolean updateModels = Boolean.parseBoolean(
        (String) params.get("updateModels")
      );
      String catalog = (String) params.get("catalog");
      if (updateMeasures) {
        if (service.useFileService) {
          List<String> measureFiles = service.getMeasureCatalogList();
          for (String s : measureFiles) {
            try {
              service.updateMeasures(s);
            } catch (MalformedXMLException e) {
              System.out.println(
                "Measure Catalog seems broken: " + e.getMessage()
              );
            }
          }
        } else {
          try {
            List<File> filesInFolder = Files
              .walk(Paths.get(service.catalogFileLocation))
              .filter(Files::isRegularFile)
              .map(java.nio.file.Path::toFile)
              .collect(Collectors.toList());
            for (File f : filesInFolder) {
              try {
                System.out.println(f.getName());
                if (f.getName().endsWith(".xml")) {
                  service.updateMeasures(f.toString());
                }
              } catch (MalformedXMLException e) {
                System.out.println(
                  "Measure Catalog seems broken: " + e.getMessage()
                );
              }
            }
          } catch (IOException e) {
            System.out.println(
              "Measure Catalog seems broken: " + e.getMessage()
            );
          }
        }
      }
      if (updateModels) {
        service.knownModels = service.updateModels(catalog);
      }
      return Response
        .status(Status.OK)
        .entity(
          service.visualizeSuccessModel("Node Success Model", nodeName, catalog)
        )
        .build();
    } catch (ParseException | FileBackendException e1) {
      // TODO Auto-generated catch block
      System.out.println(e1.toString());
      e1.printStackTrace();
    }
    return Response.status(Status.BAD_REQUEST).entity("Error").build();
  }

  /**
   * Visualizes a given service success model.
   *
   * @param content JSON String containing:
   *                <ul>
   *                <li>modelName the name of the success model</li>
   *                <li>updateMeasures if true, all measures are updated from xml file</li>
   *                <li>updateModels if true, all models are updated from xml file</li>
   *                </ul>
   * @return a HTML representation of the success model
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.TEXT_HTML)
  @Path("/visualize/serviceSuccessModel")
  public Response visualizeServiceSuccessModel(String content) {
    try {
      JSONParser parser = new JSONParser();
      JSONObject params = (JSONObject) parser.parse(content);

      String modelName = (String) params.get("modelName");
      boolean updateMeasures = Boolean.parseBoolean(
        (String) params.get("updateMeasures")
      );
      boolean updateModels = Boolean.parseBoolean(
        (String) params.get("updateModels")
      );
      String catalog = (String) params.get("catalog");
      if (updateMeasures) {
        try {
          if (service.useFileService) {
            List<String> measureFiles = service.getMeasureCatalogList();
            for (String s : measureFiles) {
              try {
                service.updateMeasures(s);
              } catch (MalformedXMLException e) {
                System.out.println(
                  "Measure Catalog seems broken: " + e.getMessage()
                );
              }
            }
          } else {
            List<File> filesInFolder = Files
              .walk(Paths.get(service.catalogFileLocation))
              .filter(Files::isRegularFile)
              .map(java.nio.file.Path::toFile)
              .collect(Collectors.toList());
            for (File f : filesInFolder) {
              try {
                if (f.getName().endsWith(".xml")) {
                  service.updateMeasures(
                    f.toString().substring(service.catalogFileLocation.length())
                  );
                }
              } catch (MalformedXMLException e) {
                System.out.println(
                  "Measure Catalog seems broken: " + e.getMessage()
                );
              }
            }
          }
        } catch (IOException | FileBackendException e) {
          System.out.println("Measure Catalog seems broken: " + e.getMessage());
        }
      }
      if (updateModels) {
        service.knownModels = service.updateModels(catalog);
      }
      return Response
        .status(Status.OK)
        .entity(service.visualizeSuccessModel(modelName, null, catalog))
        .build();
    } catch (ParseException e1) {
      // TODO Auto-generated catch block
      System.out.println(e1.toString());
      e1.printStackTrace();
    }
    return Response.status(Status.BAD_REQUEST).entity("Error").build();
  }

  /**
   * Gets the names of all known measures. Currently not used by the frontend but can be used in later
   * implementations to make success model creation possible directly through the frontend.
   *
   * @param update if true, the list is read again
   * @return an array of names
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/measures")
  public Response getMeasureNames(
    @QueryParam("catalog") String catalog,
    @QueryParam("update") boolean update
  ) {
    if (update) {
      try {
        List<File> filesInFolder = Files
          .walk(Paths.get(service.catalogFileLocation))
          .filter(Files::isRegularFile)
          .map(java.nio.file.Path::toFile)
          .collect(Collectors.toList());
        for (File f : filesInFolder) {
          try {
            if (f.getName().endsWith(".xml")) {
              service.measureCatalogs.put(
                catalog,
                service.updateMeasures(
                  f.toString().substring(service.catalogFileLocation.length())
                )
              );
            }
          } catch (MalformedXMLException e) {
            System.out.println(
              "Measure Catalog seems broken: " + e.getMessage()
            );
          }
        }
      } catch (IOException e) {
        System.out.println("Measure Catalog seems broken: " + e.getMessage());
      }
    }
    String[] returnArray = new String[service.measureCatalogs
      .get(catalog)
      .getMeasures()
      .size()];
    int counter = 0;
    for (String key : service.measureCatalogs
      .get(catalog)
      .getMeasures()
      .keySet()) {
      returnArray[counter] = key;
      counter++;
    }
    return Response.status(Status.OK).entity(returnArray).build();
  }

  /**
   * Returns all stored ( = monitored) services.
   *
   * @return an array of service agent id
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/services")
  public Response getServices() {
    JSONObject monitoredServices = new JSONObject();
    ResultSet resultSet;
    try {
      service.reconnect();
      resultSet = service.database.query(service.SERVICE_QUERY);
    } catch (SQLException e) {
      System.out.println(
        "(getServiceIds) The query has lead to an error: " + e
      );
      return null;
    }
    try {
      while (resultSet.next()) {
        monitoredServices.put(resultSet.getString(2), resultSet.getString(3));
      }
    } catch (SQLException e) {
      System.out.println("Problems reading result set: " + e);
    }
    return Response
      .status(Status.OK)
      .entity(monitoredServices.toJSONString())
      .build();
  }

  /**
   * Returns the name of all stored success models for the given service.
   *
   * @param serviceName the name of the service
   * @param update      updates the available success models with the content of the success model folder
   * @return an array of success model names
   */
  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/models")
  public Response getModels(
    @QueryParam("service") String serviceName,
    @QueryParam("update") boolean update,
    @QueryParam("catalog") String catalog
  ) {
    if (serviceName.contains("@")) {
      serviceName = serviceName.split("@")[0];
    }

    if (update) {
      service.knownModels = service.updateModels(catalog);
    }

    Collection<SuccessModel> models = service.knownModels.values();
    List<String> modelNames = new ArrayList<>();
    for (SuccessModel model : models) {
      if (
        model.getServiceName() != null &&
        model.getServiceName().equals(serviceName)
      ) {
        modelNames.add(model.getName());
      }
    }
    return Response
      .status(Status.OK)
      .entity(modelNames.toArray(new String[0]))
      .build();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/measureCatalogs")
  public Response getMeasureCatalogs() {
    JSONObject catalogs = new JSONObject();
    try {
      List<String> resultList = service.getMeasureCatalogLocations();
      catalogs.put("catalogs", resultList);
      return Response.status(Status.OK).entity(catalogs.toJSONString()).build();
    } catch (Exception e) {
      // one may want to handle some exceptions differently
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    return Response.status(Status.OK).entity(catalogs.toJSONString()).build();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/trainingSet/{unitId}")
  public Response getTrainingSet(
    @QueryParam("service") String serviceName,
    @PathParam("unitId") String unit,
    @QueryParam("messageType") String logMessageType
  ) {
    net.minidev.json.JSONArray resultList = service.getTrainingDataSet(
      serviceName,
      unit,
      logMessageType
    );
    return Response.status(Status.OK).entity(resultList.toJSONString()).build();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("/trainingUnits")
  public Response getTrainingSetUnits(
    @QueryParam("service") String serviceName,
    @QueryParam("messageType") String logMessageType
  ) {
    net.minidev.json.JSONArray resultList = service.getTrainingDataUnits(
      serviceName,
      logMessageType
    );
    return Response.status(Status.OK).entity(resultList.toJSONString()).build();
  }


  @GET
  @Produces(MediaType.TEXT_HTML)
  @Path("/visualizeDebug")
  public Response visualizeFourFactorsDev(
      @QueryParam("service") String serviceName,
      @QueryParam("messageType") String logMessageType) {

    this.visualizeFourFactors(
        "{\"dmodelname\":\"someModel\",\"factorOne\":\"f1\",\"factorTwo\":\"f2\",\"factorThree\":\"f3\",\"factorFour\":\"f4\"}");
    return Response.status(Status.OK).entity("").build();
  }

  @POST
  @Path("/visualizeFourFactors")
  @Consumes(MediaType.TEXT_HTML)
  @Produces(MediaType.TEXT_HTML)
  @ApiOperation(value = "", notes = "")
  @ApiResponses(value = { @ApiResponse(code = HttpURLConnection.HTTP_OK, message = "") })
  public File visualizeFourFactors(String input){
    System.out.println("called");
    List<Factor> factorsOfDimension;
    List<Measure> measuresOfFactor;
    File factorsFile = new File("factors.txt");
    try {
      FileWriter writer = new FileWriter(factorsFile);
      JSONParser parser = new JSONParser();

      JSONObject bodyInput = (JSONObject) parser.parse(input);

      String modelname = (String) bodyInput.get("dmodelname");
      String factorOne = (String) bodyInput.get("factorOne");
      String factorTwo = (String) bodyInput.get("factorTwo");
      String factorThree = (String) bodyInput.get("factorThree");
      String factorFour= (String) bodyInput.get("factorFour");

      SuccessModel mensaSuccessModel = service.knownModels.get(modelname);

      if (mensaSuccessModel == null) {
        service.knownModels = service.updateModels(service.catalogFileLocation);
      }
      mensaSuccessModel = service.knownModels.get(modelname);
      if (modelname == null) {
        writer.write("model is unknown");
        writer.flush();
        writer.close();
        return factorsFile;
      }
      SuccessModel.Dimension[] dimensions = SuccessModel.Dimension.getDimensions();

      for (int i = 0; i < dimensions.length; i++) {
        factorsOfDimension = mensaSuccessModel.getFactorsOfDimension(dimensions[i]);
        for (Factor factor : factorsOfDimension) {
          measuresOfFactor = factor.getMeasures();
          for (Measure measure : measuresOfFactor) {
            if(measure.getName() == factorOne || measure.getName() == factorTwo || measure.getName() == factorThree || measure.getName() == factorFour)
              writer.write(measure.visualize(service.database));
            writer.flush();
            writer.close();
          }
        }
      }
    }
    catch (Exception e) {
      System.out.println(service.knownModels);
    }
    return factorsFile;
  }


}
