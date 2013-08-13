package i5.las2peer.services.monitoring.provision.successModel.visualizations;

import i5.las2peer.services.monitoring.provision.database.SQLDatabase;
import i5.las2peer.services.monitoring.provision.successModel.visualizations.charts.BarChart;
import i5.las2peer.services.monitoring.provision.successModel.visualizations.charts.LineChart;
import i5.las2peer.services.monitoring.provision.successModel.visualizations.charts.MethodResult;
import i5.las2peer.services.monitoring.provision.successModel.visualizations.charts.PieChart;
import i5.las2peer.services.monitoring.provision.successModel.visualizations.charts.RadarChart;
import i5.las2peer.services.monitoring.provision.successModel.visualizations.charts.TimelineChart;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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
	 * This enumeration stores the type of a chart.
	 *
	 * @author Peter de Lange
	 *
	 */
	public enum ChartType {
		BarChart,
		LineChart,
		PieChart,
		TimelineChart,
		RadarChart;
	}
	
	private ChartType chartType;
	private String[] parameters;
	
	
	/**
	 *
	 * Constructor, calls the {@link Visualization} constructor with "Chart".
	 *
	 * @param type the desired {@link ChartType}.
	 * @param parameters a String of parameters: [div-Id, title, height, width]
	 * 
	 * @throws Exception if not exactly four parameters are given
	 *
	 */
	public Chart(ChartType type, String[] parameters) throws Exception {
		this.chartType = type;
		if(parameters.length != 4)
			throw new Exception("Exactly four parameters needed!");
		this.parameters = parameters;
	}
	
	
	@Override
	public String visualize(Map<String, String> queries, SQLDatabase database) throws Exception{
		
		//Please note that no parameter check is done here.
		//Responsibility for valid parameters lies with the calling entity!
		if(queries.size() == 1){
			List<String> list = new ArrayList<String>(queries.values());
			ResultSet resultSet;
			try {
				resultSet = database.query(list.get(0));
			} catch (SQLException e) {
				throw new Exception("The query has lead to an error: " + e);
			}
			MethodResult methodResult = new MethodResult(resultSet);
			switch(chartType){
			case BarChart:
				BarChart barChart = new BarChart(methodResult, parameters);
				return barChart.getResultHTML();
			case LineChart:
				LineChart lineChart = new LineChart(methodResult, parameters);
				return lineChart.getResultHTML();
			case PieChart:
				PieChart pieChart = new PieChart(methodResult, parameters);
				return pieChart.getResultHTML();
			case TimelineChart:
				TimelineChart timelineChart = new TimelineChart(methodResult, parameters);
				return timelineChart.getResultHTML();
			case RadarChart:
				RadarChart radarChart = new RadarChart(methodResult, parameters);
				return radarChart.getResultHTML();
			}
		}
		throw new Exception("More than one query defined for chart visualization!");
	}
	
	
}
