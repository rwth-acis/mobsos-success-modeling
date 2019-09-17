package i5.las2peer.services.mobsos.successModeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import i5.las2peer.api.Context;
import i5.las2peer.api.logging.MonitoringEvent;
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
import org.apache.commons.codec.digest.DigestUtils;
import org.json.simple.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

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
                        email = "neumann@dbis.rwth-aachen.de"),
                license = @License(
                        name = "MIT",
                        url = "https://github.com/rwth-acis/mobsos-success-modeling/blob/master/LICENSE")))
public class RestApiV2 {
    @javax.ws.rs.core.Context
    UriInfo uri;
    @javax.ws.rs.core.Context
    SecurityContext securityContext;
    private MonitoringDataProvisionService service = (MonitoringDataProvisionService) Context.getCurrent()
            .getService();

    @GET
    public Response getSwagger() throws JsonProcessingException {
        Swagger swagger = (new Reader(new Swagger())).read(this.getClass());
        return Response.status(Response.Status.OK)
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
        return Response.status(Response.Status.OK)
                .entity(services)
                .build();
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
        return Response.status(Response.Status.OK)
                .entity(groups)
                .build();
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
                service.database.queryWithDataManipulation(service.GROUP_AGENT_INSERT, Collections.singletonList(groupIDHex));
            }
            service.database.queryWithDataManipulation(
                    service.GROUP_INFORMATION_INSERT,
                    Arrays.asList(groupIDHex, group.groupID, group.name)
            );
        } catch (SQLException e) {
            System.out.println("(Add Group) The query has lead to an error: " + e);
            return null;
        }
        return Response.status(Response.Status.OK)
                .entity(group)
                .build();
    }

    @GET
    @Path("/groups/{group}")
    public Response getGroup(@PathParam("group") String group) {
        GroupDTO groupInformation;
        ResultSet resultSet;
        try {
            service.reconnect();
            resultSet = service.database.query(service.GROUP_QUERY_WITH_ID_PARAM, Collections.singletonList(group));
            if (service.database.getRowCount(resultSet) == 0) {
                throw new NotFoundException("Group " + group + " does not exist");
            }
        } catch (SQLException e) {
            System.out.println("(Get Group) The query has lead to an error: " + e);
            return null;
        }
        try {
            String groupID = resultSet.getString(1);
            String groupAlias = resultSet.getString(2);
            boolean member = Context.get().hasAccess(groupID);
            groupInformation = new GroupDTO(groupID, groupAlias, member);
        } catch (SQLException e) {
            System.out.println("Problems reading result set: " + e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        } catch (AgentOperationFailedException | AgentNotFoundException e) {
            System.out.println("Problems fetching membership state: " + e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
        return Response.status(Response.Status.OK)
                .entity(groupInformation)
                .build();
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
        return Response.status(Response.Status.OK)
                .entity(null)
                .build();
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
            Context.get().monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
        }
        return Response.status(Response.Status.OK)
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
                    JSONObject catalog = service.measureCatalogs.get(measureFile).toJSON();
                    return Response.status(Response.Status.OK).entity(catalog.toJSONString()).build();
                }
            }
        } catch (Exception e) {
            // one may want to handle some exceptions differently
            e.printStackTrace();
            Context.get().monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
        }
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorDTO("No catalog found for group " + group))
                .build();
    }

    @POST
    @Path("/measures/{group}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createMeasureCatalogForGroup(@PathParam("group") String group, MeasureCatalogDTO measureCatalog)
            throws MalformedXMLException, FileBackendException {
        checkGroupMembership(group);
        if (service.getMeasureCatalogByGroup(group) != null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorDTO("Measure catalog for " + group + " already exists. " +
                            "Update the existing catalog instead."))
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
    public Response updateMeasureCatalogForGroup(@PathParam("group") String group, MeasureCatalogDTO measureCatalog)
            throws MalformedXMLException, FileBackendException {
        checkGroupMembership(group);
        if (service.getMeasureCatalogByGroup(group) == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorDTO("Measure catalog for " + group + " does not exist yet. " +
                            "Please create it via POST method."))
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
    public Response getSuccessModels() {
        JSONObject models = new JSONObject();
        try {
            for (String group : service.knownModelsV2.keySet()) {
                if (group != null) models.put(group, getGroupModelsUri(group));
            }
        } catch (Exception e) {
            // one may want to handle some exceptions differently
            e.printStackTrace();
            Context.get().monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
        }
        return Response.status(Response.Status.OK).entity(models.toJSONString()).build();
    }


    @GET
    @Path("/models/{group}")
    public Response getSuccessModelsForGroup(@PathParam("group") String group) {
        try {
            if (service.knownModelsV2.containsKey(group)) {
                Map<String, SuccessModel> groupModels = service.knownModelsV2.get(group);
                JSONObject models = new JSONObject();
                for (String serviceName : groupModels.keySet()) {
                    SuccessModel model = groupModels.get(serviceName);
                    models.put(model.getServiceName(), getGroupModelsUriForService(group, serviceName));
                }
                return Response.status(Response.Status.OK).entity(models.toJSONString()).build();
            }
        } catch (Exception e) {
            // one may want to handle some exceptions differently
            e.printStackTrace();
            Context.get().monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
        }
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorDTO("No catalog found for group " + group))
                .build();
    }


    @GET
    @Path("/models/{group}/{service}")
    public Response getSuccessModelsForGroupAndService(@PathParam("group") String group,
                                                       @PathParam("service") String serviceName) {
        try {
            if (successModelExists(group, serviceName)) {
                SuccessModel groupModelForService = service.knownModelsV2.get(group).get(serviceName);
                return Response.status(Response.Status.OK)
                        .entity(new SuccessModelDTO(groupModelForService))
                        .build();
            }
        } catch (Exception e) {
            // one may want to handle some exceptions differently
            e.printStackTrace();
            Context.get().monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
        }
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorDTO("No catalog found for group " + group + " and service " + serviceName))
                .build();
    }


    @POST
    @Path("/models/{group}/{service}")
    public Response createSuccessModelsForGroupAndService(@PathParam("group") String group,
                                                          @PathParam("service") String serviceName,
                                                          SuccessModelDTO successModel) throws MalformedXMLException,
            FileBackendException {
        checkGroupMembership(group);
        if (successModelExists(group, serviceName)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorDTO("Success model for " + group + " and service " + serviceName
                            + " already exists. " + "Update the existing model instead."))
                    .build();
        }
        service.writeSuccessModel(successModel.xml, group, serviceName);
        return getSuccessModelsForGroupAndService(group, serviceName);
    }

    @PUT
    @Path("/models/{group}/{service}")
    public Response updateSuccessModelsForGroupAndService(@PathParam("group") String group,
                                                          @PathParam("service") String serviceName,
                                                          SuccessModelDTO successModel)
            throws MalformedXMLException, FileBackendException {
        checkGroupMembership(group);
        if (!successModelExists(group, serviceName)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorDTO("Success model for " + group + " and service " + serviceName
                            + " does not exist yet. " + "Create the model instead."))
                    .build();
        }
        service.writeSuccessModel(successModel.xml, group, serviceName);
        return getSuccessModelsForGroupAndService(group, serviceName);
    }

    @GET
    @Path("/models/{group}/{service}/{measure}")
    public Response getMeasureDataForSuccessModelsAndGroupAndService(@PathParam("group") String group,
                                                                     @PathParam("service") String serviceName,
                                                                     @PathParam("measure") String measureName) {
        try {
            MeasureCatalog catalog = service.getMeasureCatalogByGroup(group);
            if (successModelExists(group, serviceName) && catalog != null && catalog.getMeasures().containsKey(measureName)) {
                SuccessModel groupModelForService = service.knownModelsV2.get(group).get(serviceName);
                Measure measure = catalog.getMeasures().get(measureName);
                measure = this.service.insertService(measure, serviceName);
                List<String> dbResult = this.service.getRawMeasureData(measure, serviceName);
                return Response.status(Response.Status.OK)
                        .entity(new MeasureDataDTO())
                        .build();
            }
        } catch (Exception e) {
            // one may want to handle some exceptions differently
            e.printStackTrace();
            Context.get().monitorEvent(this, MonitoringEvent.SERVICE_ERROR, e.toString());
        }
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ErrorDTO("No measure " + measureName + " found for group " + group +
                        " and service " + serviceName))
                .build();
    }

    @GET
    @Path("/messageDescriptions/{service}")
    public Response getMessageDescriptions(@PathParam("service") String serviceName) {
        Map<String, String> messageDescriptions = this.service.getCustomMessageDescriptionsForService(serviceName);
        return Response.status(Response.Status.OK)
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
        return this.uri.getBaseUri().toString() + "apiv2/models/" + group + "/" + serviceName;
    }

    private boolean successModelExists(String group, String serviceName) {
        return service.knownModelsV2.containsKey(group) && service.knownModelsV2.get(group).containsKey(serviceName);
    }

    private static class GroupDTO {
        public String groupID;
        public String name;
        public boolean isMember;

        GroupDTO() {
        }

        public GroupDTO(String groupID, String name, boolean isMember) {
            this.groupID = groupID;
            this.name = name;
            this.isMember = isMember;
        }
    }

    private static class MeasureCatalogDTO {
        public String xml;

        MeasureCatalogDTO() {
        }

        MeasureCatalogDTO(String xml) {
            this.xml = xml;
        }

        MeasureCatalogDTO(MeasureCatalog catalog) {
            this.xml = catalog.getXml();
        }
    }

    public static class SuccessModelDTO {
        public String xml;

        SuccessModelDTO() {
        }

        SuccessModelDTO(String xml) {
            this.xml = xml;
        }

        SuccessModelDTO(SuccessModel successModel) {
            this.xml = successModel.getXml();
        }
    }

    public static class MeasureDataDTO {
        public List data;

        MeasureDataDTO() {
        }

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
}
