package i5.las2peer.services.mobsos.successModeling.successModel;

import org.json.simple.JSONObject;

import java.util.Map;
import java.util.TreeMap;

public class MeasureCatalog {
    private Map<String, Measure> measures;
    private String xml;

    public MeasureCatalog(){
        this.measures = new TreeMap<>();
        this.xml = "";
    }

    public MeasureCatalog(Map<String, Measure> measures, String xml) {
        this.measures = measures;
        this.xml = xml;
    }

    public Map<String, Measure> getMeasures() {
        return measures;
    }

    public void setMeasures(Map<String, Measure> measures) {
        this.measures = measures;
    }

    public String getXml() {
        return xml;
    }

    public void setXml(String xml) {
        this.xml = xml;
    }

    public JSONObject toJSON(){
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("xml", this.xml);
        return jsonObject;
    }
}
