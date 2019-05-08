package i5.las2peer.services.mobsos.successModeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import i5.las2peer.api.Context;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.serialization.MalformedXMLException;
import i5.las2peer.services.mobsos.successModeling.files.FileBackendException;
import i5.las2peer.services.mobsos.successModeling.successModel.Measure;
import i5.las2peer.services.mobsos.successModeling.successModel.MeasureCatalog;
import i5.las2peer.services.mobsos.successModeling.successModel.SuccessModel;
import io.swagger.annotations.*;
import io.swagger.jaxrs.Reader;
import io.swagger.models.Swagger;
import io.swagger.util.Json;
import org.json.simple.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
