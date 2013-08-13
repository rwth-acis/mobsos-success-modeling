package i5.las2peer.services.monitoring.provision.successModel;

import java.util.HashMap;
import java.util.Map;

/**
 * 
 * A SuccessModel bundles {@link Measure}s into factors and dimensions.
 * This implementation uses the model of Delone & McLean.
 * 
 * @author Peter de Lange
 *
 */
public class SuccessModel {
	private String name;
	private String serviceName;
	//<FactorName, Dimension>
	private Map<String, String> factors = new HashMap<String, String>();
	//<Measure, FactorName>
	private Map<String, String> measures = new HashMap<String, String>();
	
	public SuccessModel(String name, String serviceName){
		this.name = name;
		this.serviceName = serviceName;
	}
	
	
	
	public String getName(){
		return this.name;
	}
}
