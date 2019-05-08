package i5.las2peer.services.mobsos.successModeling;

import i5.las2peer.api.Context;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.ext.Provider;

/**
 * Start the background tasks responsible for refreshing the models and catalogs.
 * They should be triggered before the first request and keep running during the web servers lifetime.
 */
@Provider
@PreMatching
public class PrematchingRequestFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext ctx) {
        MonitoringDataProvisionService service = (MonitoringDataProvisionService) Context.getCurrent()
                .getService();
        service.startUpdatingMeasures();
    }
}