package i5.las2peer.services.monitoring.provision.successModel;

import i5.las2peer.services.monitoring.provision.database.SQLDatabase;
import i5.las2peer.services.monitoring.provision.successModel.visualizations.Visualization;

import java.util.HashMap;
import java.util.Map;


/**
 * 
 * A measure contains a name, a number of queries and a {@link Visualization}.
 * It can be used to visualize these queries on a database.
 * 
 * @author Peter de Lange
 *
 */
public class Measure {
	
	private String name;
	private Map<String, String> queries = new HashMap<String, String>();
	private Visualization visualization;
	
	
	/**
	 * 
	 * Constructor
	 * 
	 * @param name the name of the measure
	 * @param queries a map of queries
	 * @param visualization the desired {@link Visualization} for this measure
	 * 
	 */
	public Measure(String name, Map<String,String> queries, Visualization visualization){
		this.name = name;
		this.queries = queries;
		this.visualization = visualization;
	}
	
	
	/**
	 * Visualizes the measure.
	 * 
	 * @param database the database the queries should be executed on
	 * @return the result as a String
	 * 
	 * @throws Exception If something went wrong with the visualization (Database errors, wrong query results..)
	 */
	public String visualize(SQLDatabase database) throws Exception{
		return this.visualization.visualize(queries, database);
	}
	
	
	/**
	 * Gets the name of this Measure.
	 * 
	 * @return the measure name
	 */
	public String getName(){
		return name;
	}
	
}
