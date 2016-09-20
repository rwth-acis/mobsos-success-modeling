package i5.las2peer.services.mobsos.successModeling.visualizations;

import java.util.Map;

import i5.las2peer.services.mobsos.successModeling.database.SQLDatabase;

/**
 *
 * Interface for visualizations of a measure.
 *
 * @author Peter de Lange
 *
 */
public interface Visualization {

	
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
	public String visualize(Map<String, String> queries, SQLDatabase database) throws Exception;
	
	
}
