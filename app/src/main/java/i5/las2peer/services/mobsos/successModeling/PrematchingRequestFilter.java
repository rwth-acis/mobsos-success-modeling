package i5.las2peer.services.mobsos.successModeling;

import i5.las2peer.api.Context;
import i5.las2peer.api.security.Agent;
import i5.las2peer.api.security.AnonymousAgent;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.ext.Provider;

/**
 * Start the background tasks responsible for refreshing the models and
 * catalogs.
 * They should be triggered before the first request and keep running during the
 * web servers lifetime.
 */
@Provider
@PreMatching
public class PrematchingRequestFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext ctx) {
        Agent mainAgent = Context.get().getMainAgent();
        if (mainAgent instanceof AnonymousAgent) {
            return;
        }
        MonitoringDataProvisionService service = (MonitoringDataProvisionService) Context.getCurrent()
                .getService();
        try {
            service.startUpdatingMeasures();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (service.insertDatabaseCredentialsIntoQVService) {
            try {
                service.ensureMobSOSDatabaseIsAccessibleInQVService();
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}