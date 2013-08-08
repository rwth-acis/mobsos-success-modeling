package i5.las2peer.services.monitoring.provision;

import i5.las2peer.api.Service;
import i5.las2peer.services.monitoring.provision.database.SQLDatabase;
import i5.las2peer.services.monitoring.provision.database.SQLDatabaseType;


/**
 * 
 * MonitoringDataProvisionService.java
 * <br>
 * This service will connect to the monitoring database and provide an interface
 * for frontend clients to visualize monitored data.
 */
public class MonitoringDataProvisionService extends Service{
	
	/**
	 * Configuration parameters, values will be set by the configuration file.
	 */
	private String databaseName;
	private int databaseTypeInt; //See SQLDatabaseType for more information
	private	SQLDatabaseType databaseType;
	private String databaseHost;
	private int databasePort;
	private String databaseUser;
	private String databasePassword;
	private String catalogFile;
	private String successModelsFolder;
	
	private SQLDatabase database; //The database instance to write to.
	
	
	/**
	 * 
	 * Constructor of the Service. Loads the database values from a property file and tries to connect to the database.
	 * 
	 */
	public MonitoringDataProvisionService(){
		setFieldValues(); //This sets the values of the configuration file
		this.databaseType = SQLDatabaseType.getSQLDatabaseType(databaseTypeInt);
		this.database = new SQLDatabase(this.databaseType, this.databaseUser, this.databasePassword,
				this.databaseName, this.databaseHost, this.databasePort);
		try {
			this.database.connect();
			System.out.println("Monitoring: Database connected!");
		} catch (Exception e) {
			System.out.println("Monitoring: Could not connect to database!");
			e.printStackTrace();
		}
	}
	
}
