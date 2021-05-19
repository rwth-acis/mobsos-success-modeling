package i5.las2peer.services.mobsos.successModeling.successModel;

import java.util.List;

public class ServiceSuccessModel extends SuccessModel {
    /**
     * Constructor of a SuccessModel.
     * The service name can be set to null of this model should be used for node monitoring.
     *
     * @param name        the name of this success model
     * @param serviceName the service this model is made for
     * @param factors     a list of {@link Factor}s
     * @param xml
     */
    public ServiceSuccessModel(String name, String serviceName, List<Factor> factors, String xml) {
        super(name, serviceName, factors, xml);
    }
}
