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
	 * This enumeration stores the type of a visualization.
	 *
	 * @author Peter de Lange
	 *
	 */
	public enum Type {
		
		/**
		 * A simple value to be displayed directly.
		 */
		Value (1),
		
		/**
		 * A key performance indicator. Has to be calculated.
		 */
		KPI (2),
		
		/**
		 * A chart visualization.
		 */
		Chart (3);
		
		private final int code;
		
		
		private Type (int code){
			this.code = code;
		}
		
		
		/**
		 *
		 * Returns the code of the database.
		 *
		 * @return a code
		 *
		 */
		public int getCode(){
			return this.code;
		}
		
	}
	
	private Type type;
	
	
	/**
	 *
	 * Gets the type of this visualization
	 *
	 * @return the type
	 *
	 */
	public Type getType() {
		return type;
	}
	
	
	/**
	 *
	 * Constructor for a new visualization.
	 *
	 * @param type the {@link Type} of the visualization
	 *
	 */
	public Visualization(Type type){
		this.type = type;
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