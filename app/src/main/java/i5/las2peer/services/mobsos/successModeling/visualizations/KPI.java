package i5.las2peer.services.mobsos.successModeling.visualizations;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import i5.las2peer.services.mobsos.successModeling.database.SQLDatabase;
import net.astesana.javaluator.DoubleEvaluator;

/**
 *
 * Returns a Key Performance Indicator as visualization result.
 *
 * @author Peter de Lange
 *
 */
public class KPI implements Visualization {
	
	private String expression = "";
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
	public KPI(String expression) {
		this.expression = expression;
		this.evaluator = new DoubleEvaluator();
	}
	
	
	public String visualize(Map<String, String> queries, SQLDatabase database) throws Exception{
		String expressionWithInsertedValues = "";
		LinkedList<String> values = new LinkedList<String>();
		Matcher m = null;
		try {
			m = Pattern.compile("\b[a-zA-Z]+\b").matcher(expression);
		} catch (PatternSyntaxException e) {
			e.printStackTrace();
			throw new Exception("Could not parse expression. The expression contains an invalid character.");
		}
		while (m.find()) {
			values.add(m.group());
		}
		for (String value : values) {
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
		String returnString = "";
		Double returnValue = evaluator.evaluate(expressionWithInsertedValues);
		if(!Double.isNaN(returnValue)){
			DecimalFormat formatter =   new DecimalFormat  ( ".##" );
			returnString = formatter.format(returnValue).toString();
		}
		//Probably division by zero (can happen with some (correctly formulated) query results); assuming correct result is mostly 0 then
		else{
			returnString = "0";
		}
		return returnString;
	}
	
	
}
