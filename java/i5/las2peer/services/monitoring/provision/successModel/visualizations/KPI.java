package i5.las2peer.services.monitoring.provision.successModel.visualizations;

import i5.las2peer.services.monitoring.provision.database.SQLDatabase;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;

import net.astesana.javaluator.DoubleEvaluator;

/**
 *
 * Returns a Key Performance Indicator as visualization result.
 *
 * @author Peter de Lange
 *
 */
public class KPI implements Visualization {
	
	private Map<Integer, String> expression = new TreeMap<Integer, String>();
	//Using the "javaluator" for evaluating expressions
	//http://javaluator.sourceforge.net/en/home/
	private DoubleEvaluator evaluator;
	
	
	/**
	 *
	 * Constructor.
	 *
	 * @param expression a (sorted) map of Strings, containing the expression to calculate the KPI
	 *
	 */
	public KPI(Map<Integer, String> expression){
		this.expression = expression;
		this.evaluator = new DoubleEvaluator();
	}
	
	
	public String visualize(Map<String, String> queries, SQLDatabase database) throws Exception{
		String expressionWithInsertedValues = "";
		
		for (String value : expression.values()) {
			if(queries.containsKey(value)){
				//Query!
				ResultSet resultSet;
				ResultSetMetaData resultSetMetaData;
				try{
					resultSet = database.query(queries.get(value));
					resultSetMetaData = resultSet.getMetaData();
				} catch (SQLException e) {
					e.printStackTrace();
					return("(KPI Visualization) The query has lead to an error: " + e);
				}
				
				if(resultSetMetaData.getColumnCount() != 1){
					throw new Exception("KPI queries have to return a single value! " + queries.get(value));
				}
				if(!resultSet.next()){
					throw new Exception("KPI result is empty! " +  queries.get(value));
				}
				double queryResult = resultSet.getDouble(1);
				if(resultSet.next()){
					throw new Exception("KPI queries have to return a single value! " + queries.get(value));
				}
				expressionWithInsertedValues += queryResult;
			}
			else{
				expressionWithInsertedValues += value;
			}
		}
		
		Double returnValue = evaluator.evaluate(expressionWithInsertedValues);
		String returnString = returnValue.toString();
		//Probably division by zero (can happen with some (correctly formulated) query results); assuming correct result is mostly 0 then
		if(returnString.equals("NaN"))
			returnString = "0"; 
		return returnString;
	}
	
	
}
