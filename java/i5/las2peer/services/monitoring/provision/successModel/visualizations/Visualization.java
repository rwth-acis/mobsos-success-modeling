package i5.las2peer.services.monitoring.provision.successModel.visualizations;

import i5.las2peer.services.monitoring.provision.database.SQLDatabase;

import java.util.Map;

/**
 *
 * Basic abstract class for visualizations of a measure.
 *
 * @author Peter de Lange
 *
 */
public abstract class Visualization {
	
	
	/**
	 *
	 * Constructor for a new visualization.
	 *
	 */
	public Visualization(){
	}
	
	
	/**
	 *
	 * Executes the given database queries and visualizes the results according to the visualization.
	 *
	 * @param queries
	 * @param database
	 *
	 * @return the visualization as a String
	 *
	 * @throws Exception
	 *
	 */
	public abstract String visualize(Map<String, String> queries, SQLDatabase database) throws Exception;
	
}