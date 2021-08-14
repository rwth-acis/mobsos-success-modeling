package i5.las2peer.services.mobsos.successModeling.successModel;

import i5.las2peer.services.mobsos.successModeling.database.SQLDatabase;
import i5.las2peer.services.mobsos.successModeling.visualizations.Visualization;
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
  private Map<String, String> insertedQueries = new HashMap<String, String>(); //used like a temp value
  private Visualization visualization;
  private String description;

  /**
   *
   * Constructor
   *
   * @param name the name of the measure
   * @param queries a map of queries
   * @param visualization the desired {@link Visualization} for this measure
   *
   */
  public Measure(
    String name,
    Map<String, String> queries,
    Visualization visualization
  ) {
    this.name = name;
    this.queries = queries;
    this.visualization = visualization;
    this.description = "";
  }

  /**
   *
   * Constructor
   *
   * @param name the name of the measure
   * @param queries a map of queries
   * @param visualization the desired {@link Visualization} for this measure
   * @param description an optional description for the measure
   * 	 */
  public Measure(
    String name,
    Map<String, String> queries,
    Visualization visualization,
    String description
  ) {
    this.name = name;
    this.queries = queries;
    this.visualization = visualization;
    if (description == null) {
      this.description = "";
    } else this.description = description;
  }

  /**
   * Visualizes the measure.
   *
   * @param database the database the queries should be executed on
   * @return the result as a String
   *
   * @throws Exception If something went wrong with the visualization (Database errors, wrong query results..)
   */
  public String visualize(SQLDatabase database) throws Exception {
    return this.visualization.visualize(insertedQueries, database);
  }

  /**
   *
   * Gets the queries of this measure.
   *
   * @return a map of queries
   *
   */
  public Map<String, String> getQueries() {
    return this.queries;
  }

  /**
   * Gets the name of this Measure.
   *
   * @return the measure name
   */
  public String getName() {
    return name;
  }

  /**
   * Gets the name of this Measure.
   *
   * @return the measure name
   */
  public String getDescription() {
    return description;
  }

  /**
   * This sets the inserted queries of this measure.
   *
   * @param insertedQueries
   */
  public void setInsertedQueries(Map<String, String> insertedQueries) {
    this.insertedQueries = insertedQueries;
  }

  public Map<String, String> getInsertedQueries() {
    return insertedQueries;
  }
}
