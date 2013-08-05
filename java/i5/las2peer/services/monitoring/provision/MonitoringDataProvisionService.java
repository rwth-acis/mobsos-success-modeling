package i5.las2peer.services.monitoring.provision;

import i5.las2peer.api.Service;


/**
 * 
 * MonitoringDataProvisionService.java
 * <br>
 * 
 */
public class MonitoringDataProvisionService extends Service{
	
	public MonitoringDataProvisionService(){
		setFieldValues(); //This sets the values of the property file
	}
	
	public String testMethod(){
		return "This will be the great Provision Service!";
	}
}
