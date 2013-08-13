package i5.las2peer.services.monitoring.provision.successModel;

import i5.las2peer.services.monitoring.provision.successModel.SuccessModel.Dimension;

import java.util.List;

/**
 * 
 * Simple data class that stores a factors name, the dimension it belongs to
 * and its {@link Measure}s.
 * 
 * @author Peter de Lange
 *
 */
public class Factor {
	
	private String name;
	private Dimension dimension;
	private List<Measure> measures;
	
	
	/**
	 * 
	 * Constructor.
	 * 
	 * @param name the name of the factor
	 * @param dimension the {@link Dimension} the factor belongs to
	 * @param measures a list of {@link Measure}s
	 * 
	 */
	public Factor(String name, Dimension dimension, List<Measure> measures){
		this.name = name;
		this.dimension = dimension;
		this.measures = measures;
	}
	
	
	/**
	 * 
	 * Gets the name of this measure.
	 * 
	 * @return the name
	 * 
	 */
	public String getName(){
		return this.name;
	}
	
	
	/**
	 * 
	 * Gets the {@link Dimension} this factor belongs to.
	 * 
	 * @return a {@link Dimension}
	 * 
	 */
	public Dimension getDimension(){
		return this.dimension;
	}
	
	
	/**
	 * 
	 * Returns all {@link Measure}s of this factor.
	 * 
	 * @return a list of {@link Measure}s
	 * 
	 */
	public List<Measure> getMeasures(){
		return this.measures;
	}
	
	
	/**
	 * 
	 * Adds a {@link Measure} to the factor.
	 * 
	 * @param measure a {@link Measure}
	 * 
	 */
	public void addMeasure(Measure measure){
		measures.add(measure);
	}
	
	
}
