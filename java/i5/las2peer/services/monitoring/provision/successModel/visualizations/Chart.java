package i5.las2peer.services.monitoring.provision.successModel.visualizations;

import i5.las2peer.services.monitoring.provision.database.SQLDatabase;

import java.util.Map;

/**
 * 
 * Visualizes a query as a chart.
 * 
 * @author Peter de Lange
 *
 */
public class Chart extends Visualization {
	
	/**
	 * 
	 * Constructor, calls the {@link Visualization} constructor with the type.
	 * 
	 */
	public Chart() {
		super(Type.Chart);
	}
	
	
	@Override
	public String visualize(Map<String, String> queries, SQLDatabase database) throws Exception{
		throw new Exception("Sorry, not implemented yet!");
	}
}
