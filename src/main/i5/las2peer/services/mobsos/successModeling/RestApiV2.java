package i5.las2peer.services.mobsos.successModeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import i5.las2peer.api.Context;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.api.security.Agent;
import i5.las2peer.api.security.AgentNotFoundException;
import i5.las2peer.api.security.AgentOperationFailedException;
import i5.las2peer.serialization.MalformedXMLException;
import i5.las2peer.services.mobsos.successModeling.files.FileBackendException;
import i5.las2peer.services.mobsos.successModeling.successModel.Measure;
import i5.las2peer.services.mobsos.successModeling.successModel.MeasureCatalog;
import i5.las2peer.services.mobsos.successModeling.successModel.SuccessModel;
import io.swagger.annotations.*;
import io.swagger.jaxrs.Reader;
import io.swagger.models.Swagger;
import io.swagger.util.Json;
import java.io.ByteArrayInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import net.minidev.json.JSONArray;
import net.minidev.json.parser.JSONParser;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.simple.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Path("/apiv2")
@Api
@Produces(MediaType.APPLICATION_JSON)
@SwaggerDefinition(
  info = @Info(
    title = "MobSOS Success Modeling API v2",
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
public class RestApiV2 {

  private String defaultDatabase = "las2peer";
  private String defaultDatabaseSchema = "las2peermon";

  @javax.ws.rs.core.Context
  UriInfo uri;

  @javax.ws.rs.core.Context
  SecurityContext securityContext;

  private MonitoringDataProvisionService service = (MonitoringDataProvisionService) Context
    .getCurrent()
    .getService();

  @GET
  public Response getSwagger() throws JsonProcessingException {
    Swagger swagger = (new Reader(new Swagger())).read(this.getClass());
    return Response
      .status(Response.Status.OK)
      .entity(Json.mapper().writeValueAsString(swagger))
      .build();
  }

  @GET
  @Path("/services")
  public Response getServices() {
    JSONObject services = new JSONObject();
    ResultSet resultSet;
    try {
      service.reconnect();
      resultSet = service.database.query(service.SERVICE_QUERY);
    } catch (SQLException e) {
      System.out.println("(Get Nodes) The query has lead to an error: " + e);
      return null;
    }
    try {
      while (resultSet.next()) {
        JSONObject serviceInfo = new JSONObject();
        String serviceName = resultSet.getString(2);
        serviceInfo.put("serviceName", serviceName);
        serviceInfo.put("serviceAlias", resultSet.getString(3));
        serviceInfo.put("registrationTime", resultSet.getTimestamp(4));
        services.put(resultSet.getString(1), serviceInfo);
      }
    } catch (SQLException e) {
      System.out.println("Problems reading result set: " + e);
    }
    return Response.status(Response.Status.OK).entity(services).build();
  }

  @GET
  @Path("/groups")
  public Response getGroups() {
    List<GroupDTO> groups = new ArrayList<>();
    ResultSet resultSet;
    try {
      service.reconnect();
      resultSet = service.database.query(service.GROUP_QUERY);
    } catch (SQLException e) {
      System.out.println("(Get Groups) The query has lead to an error: " + e);
      return null;
    }
    try {
      while (resultSet.next()) {
        String groupID = resultSet.getString(1);
        String groupAlias = resultSet.getString(2);
        boolean member = Context.get().hasAccess(groupID);
        GroupDTO groupInformation = new GroupDTO(groupID, groupAlias, member);
        groups.add(groupInformation);
      }
    } catch (SQLException e) {
      System.out.println("Problems reading result set: " + e);
    } catch (AgentOperationFailedException | AgentNotFoundException e) {
      System.out.println("Problems fetching membership state: " + e);
    }
    return Response.status(Response.Status.OK).entity(groups).build();
  }

  @POST
  @Path("/groups")
  public Response addGroup(GroupDTO group) {
    checkGroupMembership(group.groupID);
    try {
      service.reconnect();
      String groupIDHex = DigestUtils.md5Hex(group.groupID);
      ResultSet groupAgentResult = service.database.query(
        service.AGENT_QUERY_WITH_MD5ID_PARAM,
        Collections.singletonList(groupIDHex)
      );
      if (service.database.getRowCount(groupAgentResult) == 0) {
        service.database.queryWithDataManipulation(
          service.GROUP_AGENT_INSERT,
          Collections.singletonList(groupIDHex)
        );
      }
      service.database.queryWithDataManipulation(
        service.GROUP_INFORMATION_INSERT,
        Arrays.asList(groupIDHex, group.groupID, group.name)
      );
    } catch (SQLException e) {
      System.out.println("(Add Group) The query has lead to an error: " + e);
      return null;
    }
    return Response.status(Response.Status.OK).entity(group).build();
  }

  @GET
  @Path("/groups/{group}")
  public Response getGroup(@PathParam("group") String group) {
    GroupDTO groupInformation;
    ResultSet resultSet;
    try {
      service.reconnect();
      resultSet =
        service.database.query(
          service.GROUP_QUERY_WITH_ID_PARAM,
          Collections.singletonList(group)
        );
      if (service.database.getRowCount(resultSet) == 0) {
        throw new NotFoundException("Group " + group + " does not exist");
      }
    } catch (SQLException e) {
      System.out.println("(Get Group) The query has lead to an error: " + e);
      return null;
    }
    try {
      resultSet.next(); //Select the first result
      String groupID = resultSet.getString(1);
      String groupAlias = resultSet.getString(2);
      boolean member = Context.get().hasAccess(groupID);
      groupInformation = new GroupDTO(groupID, groupAlias, member);
    } catch (SQLException e) {
      e.printStackTrace();
      System.out.println("Problems reading result set: " + e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    } catch (AgentOperationFailedException | AgentNotFoundException e) {
      System.out.println("Problems fetching membership state: " + e);
      return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
    }
    return Response.status(Response.Status.OK).entity(groupInformation).build();
  }

  @PUT
  @Path("/groups/{group}")
  public Response updateGroup(GroupDTO group) {
    checkGroupMembership(group.groupID);
    try {
      service.reconnect();
      ResultSet resultSet = service.database.query(
        service.GROUP_QUERY_WITH_ID_PARAM,
        Collections.singletonList(group.groupID)
      );
      if (service.database.getRowCount(resultSet) == 0) {
        throw new NotFoundException("Group " + group + " does not exist");
      }
      service.database.queryWithDataManipulation(
        service.GROUP_INFORMATION_UPDATE,
        Arrays.asList(group.name, group.groupID)
      );
    } catch (SQLException e) {
      System.out.println("(Put Group) The query has lead to an error: " + e);
      return null;
    }
    return Response.status(Response.Status.OK).entity(null).build();
  }

  @GET
  @Path("/measures")
  public Response getMeasureCatalogs() {
    JSONObject catalogs = new JSONObject();
    try {
      for (String measureFile : service.measureCatalogs.keySet()) {
        String group = service.getMeasureCatalogGroup(measureFile);
        if (group != null) catalogs.put(group, getGroupMeasureUri(group));
      }
    } catch (Exception e) {
      // one may want to handle some exceptions differently
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    return Response
      .status(Response.Status.OK)
      .entity(catalogs.toJSONString())
      .build();
  }

  @GET
  @Path("/measures/{group}")
  public Response getMeasureCatalogForGroup(@PathParam("group") String group) {
    try {
      for (String measureFile : service.measureCatalogs.keySet()) {
        String measureGroup = service.getMeasureCatalogGroup(measureFile);
        if (Objects.equals(measureGroup, group)) {
          JSONObject catalog = service.measureCatalogs
            .get(measureFile)
            .toJSON();
          return Response
            .status(Response.Status.OK)
            .entity(catalog.toJSONString())
            .build();
        }
      }
    } catch (Exception e) {
      // one may want to handle some exceptions differently

      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    return Response
      .status(Response.Status.NOT_FOUND)
      .entity(new ErrorDTO("No catalog found for group " + group))
      .build();
  }

  @POST
  @Path("/measures/{group}")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response createMeasureCatalogForGroup(
    @PathParam("group") String group,
    MeasureCatalogDTO measureCatalog
  )
    throws MalformedXMLException, FileBackendException {
    checkGroupMembership(group);
    if (service.getMeasureCatalogByGroup(group) != null) {
      return Response
        .status(Response.Status.BAD_REQUEST)
        .entity(
          new ErrorDTO(
            "Measure catalog for " +
            group +
            " already exists. " +
            "Update the existing catalog instead."
          )
        )
        .build();
    }
    service.writeMeasureCatalog(measureCatalog.xml, group);
    return Response
      .status(Response.Status.CREATED)
      .entity(service.getMeasureCatalogByGroup(group).toJSON())
      .build();
  }

  @PUT
  @Path("/measures/{group}")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response updateMeasureCatalogForGroup(
    @PathParam("group") String group,
    MeasureCatalogDTO measureCatalog
  )
    throws MalformedXMLException, FileBackendException {
    checkGroupMembership(group);
    if (service.getMeasureCatalogByGroup(group) == null) {
      return Response
        .status(Response.Status.BAD_REQUEST)
        .entity(
          new ErrorDTO(
            "Measure catalog for " +
            group +
            " does not exist yet. " +
            "Please create it via POST method."
          )
        )
        .build();
    }
    service.writeMeasureCatalog(measureCatalog.xml, group);
    return Response
      .status(Response.Status.OK)
      .entity(new MeasureCatalogDTO(service.getMeasureCatalogByGroup(group)))
      .build();
  }

  @GET
  @Path("/models")
  public Response handleGetSuccessModels() {
    JSONObject models = getSuccessModels();
    return Response
      .status(Response.Status.OK)
      .entity(models.toJSONString())
      .build();
  }

  public JSONObject getSuccessModels() {
    JSONObject models = new JSONObject();
    try {
      for (String group : service.knownModelsV2.keySet()) {
        if (group != null) models.put(group, getGroupModelsUri(group));
      }
    } catch (Exception e) {
      // one may want to handle some exceptions differently
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    return models;
  }

  @GET
  @Path("/models/{group}")
  public Response handleGetSuccessModelsForGroup(
    @PathParam("group") String group
  ) {
    String models = "";
    try {
      models = getSuccessModelsForGroup(group);
    } catch (Exception e) {
      // one may want to handle some exceptions differently
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    if (models == "{}") {
      return Response
        .status(Response.Status.NOT_FOUND)
        .entity(new ErrorDTO("No catalog found for group " + group))
        .build();
    } else {
      return Response.status(Response.Status.OK).entity(models).build();
    }
  }

  public String getSuccessModelsForGroup(String group) throws Exception {
    JSONObject models = new JSONObject();
    if (service.knownModelsV2.containsKey(group)) {
      Map<String, SuccessModel> groupModels = service.knownModelsV2.get(group);
      for (String serviceName : groupModels.keySet()) {
        SuccessModel model = groupModels.get(serviceName);
        models.put(
          model.getServiceName(),
          getGroupModelsUriForService(group, serviceName)
        );
      }
    }
    return models.toJSONString();
  }

  @GET
  @Path("/models/{group}/{service}")
  public Response getSuccessModelsForGroupAndService(
    @PathParam("group") String group,
    @PathParam("service") String serviceName
  ) {
    try {
      if (successModelExists(group, serviceName)) {
        SuccessModel groupModelForService = service.knownModelsV2
          .get(group)
          .get(serviceName);
        return Response
          .status(Response.Status.OK)
          .entity(new SuccessModelDTO(groupModelForService))
          .build();
      }
    } catch (Exception e) {
      // one may want to handle some exceptions differently
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    return Response
      .status(Response.Status.NOT_FOUND)
      .entity(
        new ErrorDTO(
          "No catalog found for group " + group + " and service " + serviceName
        )
      )
      .build();
  }

  @POST
  @Path("/models/{group}/{service}")
  public Response createSuccessModelsForGroupAndService(
    @PathParam("group") String group,
    @PathParam("service") String serviceName,
    SuccessModelDTO successModel
  )
    throws MalformedXMLException, FileBackendException {
    checkGroupMembership(group);
    if (successModelExists(group, serviceName)) {
      return Response
        .status(Response.Status.BAD_REQUEST)
        .entity(
          new ErrorDTO(
            "Success model for " +
            group +
            " and service " +
            serviceName +
            " already exists. " +
            "Update the existing model instead."
          )
        )
        .build();
    }
    service.writeSuccessModel(successModel.xml, group, serviceName);
    return getSuccessModelsForGroupAndService(group, serviceName);
  }

  @PUT
  @Path("/models/{group}/{service}")
  public Response updateSuccessModelsForGroupAndService(
    @PathParam("group") String group,
    @PathParam("service") String serviceName,
    SuccessModelDTO successModel
  )
    throws MalformedXMLException, FileBackendException {
    checkGroupMembership(group);
    if (!successModelExists(group, serviceName)) {
      return Response
        .status(Response.Status.BAD_REQUEST)
        .entity(
          new ErrorDTO(
            "Success model for " +
            group +
            " and service " +
            serviceName +
            " does not exist yet. " +
            "Create the model instead."
          )
        )
        .build();
    }
    service.writeSuccessModel(successModel.xml, group, serviceName);
    return getSuccessModelsForGroupAndService(group, serviceName);
  }

  @GET
  @Path("/models/{group}/{service}/{measure}")
  public Response handleGetMeasureDataForSuccessModelsAndGroupAndService(
    @PathParam("group") String group,
    @PathParam("service") String serviceName,
    @PathParam("measure") String measureName
  ) {
    try {
      List<String> dbResult = getMeasureDataForSuccessModelsAndGroupAndService(
        group,
        serviceName,
        measureName
      );
      if (dbResult.size() != 0) {
        return Response
          .status(Response.Status.OK)
          .entity(new MeasureDataDTO(dbResult))
          .build();
      }
    } catch (Exception e) {
      // one may want to handle some exceptions differently
      e.printStackTrace();
      Context
        .get()
        .monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
    }
    return Response
      .status(Response.Status.NOT_FOUND)
      .entity(
        new ErrorDTO(
          "No measure " +
          measureName +
          " found for group " +
          group +
          " and service " +
          serviceName
        )
      )
      .build();
  }

  public List<String> getMeasureDataForSuccessModelsAndGroupAndService(
    String group,
    String serviceName,
    String measureName
  )
    throws Exception {
    List<String> dbResult = new ArrayList<>();
    MeasureCatalog catalog = service.getMeasureCatalogByGroup(group);
    if (
      successModelExists(group, serviceName) &&
      catalog != null &&
      catalog.getMeasures().containsKey(measureName)
    ) {
      SuccessModel groupModelForService = service.knownModelsV2
        .get(group)
        .get(serviceName);
      Measure measure = catalog.getMeasures().get(measureName);
      ArrayList<String> serviceList = new ArrayList<String>();
      serviceList.add(serviceName);
      measure = this.service.insertService(measure, serviceList);
      dbResult = this.service.getRawMeasureData(measure, serviceList);
    }
    return dbResult;
  }

  @GET
  @Path("/messageDescriptions/{service}")
  public Response getMessageDescriptions(
    @PathParam("service") String serviceName
  ) {
    Map<String, String> messageDescriptions =
      this.service.getCustomMessageDescriptionsForService(serviceName);
    return Response
      .status(Response.Status.OK)
      .entity(messageDescriptions)
      .build();
  }

  private void checkGroupMembership(String group) {
    if (!service.currentUserIsMemberOfGroup(group)) {
      throw new ForbiddenException("User is not member of group " + group);
    }
  }

  private String getGroupMeasureUri(String group) {
    return this.uri.getBaseUri().toString() + "apiv2/measures/" + group;
  }

  private String getGroupModelsUri(String group) {
    return this.uri.getBaseUri().toString() + "apiv2/models/" + group;
  }

  private String getGroupModelsUriForService(String group, String serviceName) {
    return (
      this.uri.getBaseUri().toString() +
      "apiv2/models/" +
      group +
      "/" +
      serviceName
    );
  }

  private boolean successModelExists(String group, String serviceName) {
    return (
      service.knownModelsV2.containsKey(group) &&
      service.knownModelsV2.get(group).containsKey(serviceName)
    );
  }

  private static class GroupDTO {

    public String groupID;
    public String name;
    public boolean isMember;

    GroupDTO() {}

    public GroupDTO(String groupID, String name, boolean isMember) {
      this.groupID = groupID;
      this.name = name;
      this.isMember = isMember;
    }
  }

  private static class MeasureCatalogDTO {

    public String xml;

    MeasureCatalogDTO() {}

    MeasureCatalogDTO(String xml) {
      this.xml = xml;
    }

    MeasureCatalogDTO(MeasureCatalog catalog) {
      this.xml = catalog.getXml();
    }
  }

  public static class SuccessModelDTO {

    public String xml;

    SuccessModelDTO() {}

    SuccessModelDTO(String xml) {
      this.xml = xml;
    }

    SuccessModelDTO(SuccessModel successModel) {
      this.xml = successModel.getXml();
    }
  }

  public static class MeasureDataDTO {

    public List data;

    MeasureDataDTO() {}

    MeasureDataDTO(List xml) {
      this.data = xml;
    }
  }

  private static class ErrorDTO {

    public String message;

    ErrorDTO(String message) {
      this.message = message;
    }
  }

  // /**
  //  * Bot function to get a visualization
  //  * @param body jsonString containing the query, the Chart type and other optional parameters
  //  * @return image to be displayed in chat
  //  */
  // @Path("/getSuccessModel")
  // @POST
  // public Response getSuccessModel(String body) {
  //   Response res = null;
  //   net.minidev.json.JSONObject chatResponse = new net.minidev.json.JSONObject();
  //   try {
  //     res =
  //       getSuccessModelsForGroupAndService(
  //         "default",
  //         "i5.las2peer.services.mensaService.MensaService"
  //       );

  //     SuccessModelDTO sModel = (SuccessModelDTO) res.getEntity();
  //     System.out.println(res.getEntity());
  //     chatResponse.put("text", sModel.xml);
  //     res = Response.ok(chatResponse.toJSONString()).build();
  //   } catch (Exception e) { // } //   res = Response.ok(chatResponse.toString()).build(); //   chatResponse.put("text", e.getMessage()); //   e.printStackTrace(); // catch (ChatException e) {
  //     chatResponse.put("text", "An error occured 😦");
  //     res = Response.ok(chatResponse.toString()).build();
  //     e.printStackTrace();
  //   }
  //   return res;
  // }

  /**
   * Bot function to get a visualization
   * @param body jsonString containing the query, the Chart type and other optional parameters
   * @return image to be displayed in chat
   */
  @Path("/listMeasures")
  @POST
  @ApiOperation(value = "Processes GraphQL request.")
  @ApiResponses(
    value = {
      @ApiResponse(code = 200, message = "Executed request successfully."),
      @ApiResponse(
        code = 400,
        message = "GraphQL call is not in correct syntax."
      ),
      @ApiResponse(code = 415, message = "Request is missing GraphQL call."),
      @ApiResponse(code = 512, message = "Response is not in correct format."),
      @ApiResponse(code = 513, message = "Internal GraphQL server error."),
      @ApiResponse(code = 514, message = "Schemafile error."),
    }
  )
  public Response listMeasures(String body) {
    JSONParser p = new JSONParser(JSONParser.MODE_PERMISSIVE);

    net.minidev.json.JSONObject chatResponse = new net.minidev.json.JSONObject();
    String chatResponseText = "";
    try {
      net.minidev.json.JSONObject requestObject = (net.minidev.json.JSONObject) p.parse(
        body
      );
      String groupName = requestObject.getAsString("groupName");
      String serviceName = requestObject.getAsString("serviceName");
      String dimension = requestObject.getAsString("dimension");
      String email = requestObject.getAsString("email");
      if (groupName == null) {
        chatResponseText +=
          "No group name was defined so the default group is used\n";
        groupName = "default";
        if (serviceName == null) {
          chatResponseText +=
            "No service name was defined so the mensa service is used\n";
          serviceName = "i5.las2peer.services.mensaService.MensaService";
        }
      } else {
        GroupDTO group = (GroupDTO) this.getGroup(groupName).getEntity();
        if (!group.isMember) {
          throw new ChatException(
            "Sorry I am not part of the group " +
            groupName +
            "😱. Contact your admin to add me to the group"
          );
        }
        String agentId = Context.get().getUserAgentIdentifierByEmail(email);
        if (
          !Context.get().hasAccess(agentId, Context.get().getServiceAgent())
        ) {
          throw new ChatException(
            "Sorry you are not a member of this group 😓. Contact your admin to be added to the group"
          );
        }
      }

      SuccessModelDTO success = (SuccessModelDTO) this.getSuccessModelsForGroupAndService(
          groupName,
          serviceName
        )
        .getEntity();

      chatResponseText += SuccessModelToText(success.xml, dimension);
      chatResponse.put("text", chatResponseText);
    } catch (ChatException e) {
      e.printStackTrace();
      chatResponse.put("text", e.getMessage());
      chatResponse.put("closeContext", false);
    } catch (Exception e) {
      e.printStackTrace();
      chatResponse.put("text", "Sorry an error occured 💁");
      chatResponse.put("closeContext", false);
    }
    return Response.ok(chatResponse).build();
  }

  /**
   * Bot function to get a visualization
   * @param body jsonString containing the query, the Chart type and other optional parameters
   * @return image to be displayed in chat
   */
  @Path("/visualize")
  @POST
  @ApiOperation(value = "Processes GraphQL request.")
  @ApiResponses(
    value = {
      @ApiResponse(code = 200, message = "Executed request successfully."),
      @ApiResponse(
        code = 400,
        message = "GraphQL call is not in correct syntax."
      ),
      @ApiResponse(code = 415, message = "Request is missing GraphQL call."),
      @ApiResponse(code = 512, message = "Response is not in correct format."),
      @ApiResponse(code = 513, message = "Internal GraphQL server error."),
      @ApiResponse(code = 514, message = "Schemafile error."),
    }
  )
  public Response visualizeRequest(String body) {
    JSONParser parser = new JSONParser(JSONParser.MODE_PERMISSIVE);
    Response res = null;
    net.minidev.json.JSONObject chatResponse = new net.minidev.json.JSONObject();

    try {
      net.minidev.json.JSONObject json = (net.minidev.json.JSONObject) parser.parse(
        body
      );

      String measureName = json.getAsString("measureName");
      String tag = json.getAsString("tag");

      Object response = getMeasureCatalogForGroup("default").getEntity();

      json = (net.minidev.json.JSONObject) parser.parse((String) response);
      String xmlString =
        ((net.minidev.json.JSONObject) json).getAsString("xml");
      Document xml = loadXMLFromString(xmlString);

      Element desiredMeasure = findMeasureByName(xml, measureName);

      if (desiredMeasure == null) { //try to find measure using tag search
        Set<Node> list = findMeasuresByTag(xml, tag);
        if (list.isEmpty()) {
          throw new ChatException("No nodes found matching your input💁");
        }
        if (list.size() == 1) { //only one result->use this as the desired measure
          desiredMeasure = (Element) list.iterator().next();
        } else {
          String respString =
            "I found the following measures, matching \"" + tag + "\":\n";
          Iterator<Node> it = list.iterator();

          for (int j = 0; it.hasNext(); j++) {
            respString +=
              j + ". " + ((Element) it.next()).getAttribute("name") + "\n";
          }
          respString += "Please specify your measure";
          throw new ChatException(respString);
        }
      }

      Element visualization = (Element) desiredMeasure
        .getElementsByTagName("visualization")
        .item(
          desiredMeasure.getElementsByTagName("visualization").getLength() - 1
        );

      switch (visualization.getAttribute("type")) {
        case "Chart":
          String imagebase64 = getChartFromMeasure(
            desiredMeasure,
            parser,
            visualization
          );

          chatResponse.put("fileBody", imagebase64);

          chatResponse.put("fileName", "chart.png");
          chatResponse.put("fileType", "image/png");
          res = Response.ok(chatResponse.toString()).build();
          break;
        case "KPI":
          String kpi = getKPIFromMeasure(desiredMeasure, parser, visualization);
          chatResponse.put("text", kpi);
          res = Response.ok(chatResponse.toString()).build();
          break;
        case "Value":
          String value = getValueFromMeasure(
            desiredMeasure,
            parser,
            visualization
          );
          chatResponse.put("text", value);
          res = Response.ok(chatResponse.toString()).build();
          break;
      }
    } catch (ChatException e) {
      e.printStackTrace();
      chatResponse.put("text", e.getMessage());
      res = Response.ok(chatResponse.toString()).build();
    } catch (Exception e) {
      chatResponse.put("text", "An error occured 😦");
      res = Response.ok(chatResponse.toString()).build();
      e.printStackTrace();
    }
    return res;
  }

  public Document loadXMLFromString(String xml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

    factory.setNamespaceAware(true);
    DocumentBuilder builder = factory.newDocumentBuilder();

    return builder.parse(new ByteArrayInputStream(xml.getBytes()));
  }

  /**
   * Makes a request to the GraphQl service
   * @param json contains dbName: name if the db, dbSchema: name of the db schema and query sql query
   * @return the requested data
   * @throws ChatException
   */
  private InputStream graphQLQuery(net.minidev.json.JSONObject json)
    throws ChatException {
    String queryString = prepareGQLQueryString(json);

    try {
      String urlString = service.GRAPHQ_HOST + "/graphql?query=" + queryString;

      URL url = new URL(urlString);
      HttpURLConnection con = (HttpURLConnection) url.openConnection();

      return con.getInputStream();
    } catch (IOException e) {
      e.printStackTrace();
      throw new ChatException("Sorry the graphQL request has failed 😶");
    }
  }

  /**
   * Makes a request to the GraphQl service uses default database and schema
   * @param query sql query
   * @return the requested data
   * @throws ChatException
   */
  private InputStream graphQLQuery(String query) throws ChatException {
    String queryString = prepareGQLQueryString(query);

    try {
      // try {
      //   Object res = Context
      //     .get()
      //     .invoke(
      //       "i5.las2peer.services.mediabaseAPI.MediabaseGraphQLAPI@1.0.0",
      //       "queryExecute",
      //       queryString
      //     );
      //   if (res instanceof String) {
      //     System.out.println("ETZSTSADF");
      //     System.out.println();
      //   } else {
      //     System.out.println("TYEPPEof" + res.getClass());
      //   }
      // } catch (Exception e) {
      //   e.printStackTrace();
      // }

      URL url = new URI(
        "http",
        service.GRAPHQ_HOST,
        "/graphql/graphql",
        "query=" + queryString,
        null
      )
        .toURL();
      System.out.println(url);
      HttpURLConnection con = (HttpURLConnection) url.openConnection();

      return con.getInputStream();
    } catch (IOException | URISyntaxException e) {
      e.printStackTrace();
      throw new ChatException("Sorry the graphQL request has failed 😶");
    }
  }

  /**
   * Prepares the string to the customQuery query of the graphql schema
   * @param json contains dbName: name if the db, dbSchema: name of the db schema and query sql query
   * @return query which can be used as the query parameter in the graphql http request
   * @throws ChatException
   */
  private String prepareGQLQueryString(net.minidev.json.JSONObject json)
    throws ChatException {
    String dbName = json.getAsString("dbName");
    String dbSchema = json.getAsString("dbSchema");
    String queryString = json.getAsString("query");

    if (dbSchema == null) {
      dbSchema = this.defaultDatabaseSchema;
    }
    if (dbName == null) {
      dbName = this.defaultDatabase;
    }
    if (queryString == null) {
      queryString = json.getAsString("msg");
      if (queryString == null) {
        throw new ChatException("Please provide a query");
      }
    }

    return (
      "{customQuery(dbName: \"" +
      dbName +
      "\",dbSchema: \"" +
      dbSchema +
      "\",query: \"" +
      queryString +
      "\")}"
    );
  }

  /**
   * Prepares the string to the customQuery query of the graphql schema
   * @param query  sql query
   * @return query which can be used as the query parameter in the graphql http request
   * @throws ChatException
   */
  private String prepareGQLQueryString(String query) {
    return (
      "{customQuery(dbName: \"" +
      defaultDatabase +
      "\",dbSchema: \"" +
      defaultDatabaseSchema +
      "\",query: \"" +
      query +
      "\")}"
    );
  }

  /**
   * Makes a call to the visulization service to create a chart as png
   * @param data Data which should be visualized
   * @param type type of (Google Charts) chart
   * @param title title of the chart
   * @return chart as base64 encoded string
   * @throws ChatException
   */
  private String getImage(
    net.minidev.json.JSONObject data,
    String type,
    String title
  )
    throws ChatException {
    if (type == null) {
      type = "PieChart";
    }
    data.put("chartType", type);
    if (title != null) {
      JSONObject titleObj = new JSONObject();
      titleObj.put("title", title);
      data.put("options", titleObj.toJSONString());
    }

    try {
      String urlString = service.CHART_API_ENDPOINT + "/customQuery";
      URL url = new URL(urlString);
      HttpURLConnection con = (HttpURLConnection) url.openConnection();
      con.setRequestProperty("Content-Type", "application/json");
      con.setRequestMethod("POST");
      con.setDoOutput(true);
      DataOutputStream wr = new DataOutputStream(con.getOutputStream());
      wr.writeBytes(data.toJSONString());
      wr.flush();
      wr.close();

      InputStream response = con.getInputStream();

      return toBase64(response);
    } catch (IOException e) {
      e.printStackTrace();
      throw new ChatException("Sorry the visualization has failed 😶");
    }
  }

  /**
   * Transforms an Input stream into a base64 encoded string
   * @param is Input stream of a connection
   * @return base64 encoded string
   */
  private String toBase64(InputStream is) {
    try {
      byte[] bytes = org.apache.commons.io.IOUtils.toByteArray(is);

      String chunky = Base64.getEncoder().encodeToString(bytes);

      return chunky;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Returns the first occurence of an element in the document that matches its name attribute with key
   * @param xml the document to search in
   * @param key the key by which to search
   * @return first occurence
   */
  private Element findMeasureByName(Document xml, String key) {
    Element desiredNode = null;
    if (key == null) {
      return null;
    }
    NodeList measures = xml.getElementsByTagName("measure");

    for (int i = 0; i < measures.getLength(); i++) {
      Node measure = measures.item(i);
      if (measure.getNodeType() == Node.ELEMENT_NODE) {
        String name = ((Element) measure).getAttribute("name"); //get the name of the measure

        if (key.toLowerCase().equals(name.toLowerCase())) {
          desiredNode = (Element) measure;
          break;
        }
      }
    }
    return desiredNode;
  }

  /**
   * find all elements that match key on the tag attribute
   * @param xml the document to search in
   * @param tag the tag by which to search
   * @return
   */
  private Set<Node> findMeasuresByTag(Document xml, String tag) {
    Set<Node> list = new HashSet<Node>();
    NodeList measures = xml.getElementsByTagName("measure");
    if (tag == null) {
      return null;
    }
    for (int i = 0; i < measures.getLength(); i++) {
      Node measure = measures.item(i);
      if (measure.getNodeType() == Node.ELEMENT_NODE) {
        String[] tags =
          ((Element) measure).getAttribute("tags").toLowerCase().split(","); //get the name of the measure
        for (int j = 0; j < tags.length; j++) {
          if (tags[j].toLowerCase().equals(tag.toLowerCase())) {
            list.add(measure);
            break;
          }
        }
      }
    }

    return list;
  }

  /**
   * Get the chart from a measure
   * @param measure measure as xml node
   * @param parser json parser to parse response from api calls
   * @param visualization //the visualization xml node
   * @return chart as base64 encoded string
   * @throws Exception
   */
  private String getChartFromMeasure(
    Element measure,
    JSONParser parser,
    Element visualization
  )
    throws Exception {
    String b64 = null;

    NodeList queries = measure.getElementsByTagName("query");
    String sqlQueryString = java.net.URLEncoder.encode(
      ((Element) queries.item(0)).getTextContent().replaceAll("\"", "'"),
      "UTF-8"
    );
    System.out.println(sqlQueryString);
    InputStream graphQLResponse = graphQLQuery(sqlQueryString);
    net.minidev.json.JSONObject json = (net.minidev.json.JSONObject) parser.parse(
      graphQLResponse
    );
    if (json.get("customQuery") == null) {
      throw new ChatException(
        "No data has been collected for this measure yet"
      );
    }
    String chartType = visualization
      .getElementsByTagName("chartType")
      .item(0)
      .getTextContent();
    String chartTitle = visualization
      .getElementsByTagName("title")
      .item(0)
      .getTextContent();

    b64 = getImage(json, chartType, chartTitle);
    return b64;
  }

  /**
   * Visualizes a KPI from a measure
   * @param measure measure as xml node
   * @param parser json parser to parse response from api calls
   * @return
   * @throws Exception
   */
  private String getKPIFromMeasure(
    Element measure,
    JSONParser parser,
    Element visualization
  )
    throws Exception {
    String kpi = "";
    NodeList queries = measure.getElementsByTagName("query");
    String measureName = measure.getAttribute("name");

    kpi += measureName + ": \n";

    HashMap<Integer, String> operationInfo = new HashMap<Integer, String>(); //holds the childs of visualization
    for (int i = 0; i < visualization.getChildNodes().getLength(); i++) {
      Node node = visualization.getChildNodes().item(i);
      if (node instanceof Element) {
        int index = Integer.parseInt(((Element) node).getAttribute("index"));
        String name = ((Element) node).getAttribute("name");
        kpi += name;
        operationInfo.put(index, name); //operands might not be sorted by index}
      }
    }
    kpi += "=";

    HashMap<String, Number> values = new HashMap<String, Number>();
    for (int i = 0; i < queries.getLength(); i++) {
      String queryName = ((Element) queries.item(i)).getAttribute("name");
      String sqlQueryString = java.net.URLEncoder.encode(
        ((Element) queries.item(i)).getTextContent().replaceAll("\"", "'"),
        "UTF-8"
      );

      System.out.println(sqlQueryString);
      InputStream graphQLResponse = graphQLQuery(sqlQueryString);
      net.minidev.json.JSONObject json = (net.minidev.json.JSONObject) parser.parse(
        graphQLResponse
      );
      String value = null;

      value = extractValue(json, parser);
      values.put(queryName, Float.valueOf(value)); //save as floats idk
    }

    float accu = 0; //saves the result
    float curr = 0; //current value which accu will be operated on
    for (int i = 0; i < operationInfo.size(); i++) {
      if (i == 0) {
        accu = (Float) values.get(operationInfo.get(i));
      } else if (i % 2 == 1) {
        curr = (Float) values.get(operationInfo.get(i + 1));
        switch (operationInfo.get(i)) {
          case "/":
            if (
              curr == 0
            ) return "You are trying to divide something by 0 😅"; else accu =
              accu / curr;
            break;
          case "*":
            accu = accu * curr;
            break;
          case "-":
            accu = accu - curr;
            break;
          case "+":
            accu = accu + curr;
            break;
        }
      }
    }
    kpi += String.valueOf(accu);
    return kpi;
  }

  /**
   * Returns the value from a measure
   * @param measure measure as xml node
   * @param parser json parser to parse response from api calls
   * @return
   * @throws Exception
   */
  private String getValueFromMeasure(
    Element measure,
    JSONParser parser,
    Element visualization
  )
    throws Exception {
    String value = null;
    String measureName = measure.getAttribute("name");

    NodeList units = visualization.getElementsByTagName("unit");
    String unit = units.getLength() > 0 ? units.item(0).getTextContent() : null;
    NodeList queries = measure.getElementsByTagName("query");
    String sqlQueryString = java.net.URLEncoder.encode(
      ((Element) queries.item(0)).getTextContent().replaceAll("\"", "'"),
      "UTF-8"
    );
    InputStream graphQLResponse = graphQLQuery(sqlQueryString);
    net.minidev.json.JSONObject json = (net.minidev.json.JSONObject) parser.parse(
      graphQLResponse
    );
    value = extractValue(json, parser);
    return unit != null
      ? measureName + ": " + value + unit
      : measureName + ": " + value;
  }

  /**
   * Extracts a single value from the graphql response
   * @param jsonObject contains the desired data under customQuery
   * @param p used to parse the data
   * @return
   */
  private String extractValue(
    net.minidev.json.JSONObject jsonObject,
    JSONParser p
  )
    throws ChatException {
    JSONArray jsonArray = null;
    if (jsonObject.get("customQuery") instanceof String) {
      String result = (String) jsonObject.get("customQuery");
      try {
        jsonArray =
          (JSONArray) ((net.minidev.json.JSONObject) p.parse(result)).get(
              "result"
            );
      } catch (Exception e) {
        e.printStackTrace();
      }
    } else {
      jsonArray = (JSONArray) jsonObject.get("customQuery");
    }
    Object[] values =
      ((net.minidev.json.JSONObject) jsonArray.get(0)).values().toArray();
    if (values.length == 0) {
      throw new ChatException("No data has been collected for this measure");
    }
    return values[0].toString();
  }

  private String SuccessModelToText(String xml, String dimension)
    throws Exception {
    String res = "";
    Document model = loadXMLFromString(xml);
    NodeList dimensions = model.getElementsByTagName("dimension");
    for (int i = 0; i < dimensions.getLength(); i++) {
      if (
        dimension == null ||
        dimension.equals(((Element) dimensions.item(i)).getAttribute("name"))
      ) {
        res += dimensionToText((Element) dimensions.item(i));
      }
    }
    return res;
  }

  private String dimensionToText(Element dimension) {
    String res = "";
    res += dimension.getAttribute("name") + ":\n";
    NodeList factors = dimension.getElementsByTagName("factor");
    for (int i = 0; i < factors.getLength(); i++) {
      res += "  " + factorToText((Element) factors.item(i));
    }
    return res;
  }

  private String factorToText(Element factor) {
    String res = "";
    res += factor.getAttribute("name") + ":\n";
    NodeList measures = ((Element) factor).getElementsByTagName("measure");
    for (int j = 0; j < measures.getLength(); j++) {
      res += "•" + measureToText((Element) measures.item(j));
    }
    return res;
  }

  private String measureToText(Element measure) {
    return measure.getAttribute("name") + ":\n";
  }

  /** Exceptions ,with messages, that should be returned in Chat */
  protected static class ChatException extends Exception {

    private static final long serialVersionUID = 1L;

    protected ChatException(String message) {
      super(message);
    }
  }
}
