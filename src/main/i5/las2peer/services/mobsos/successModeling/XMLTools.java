package i5.las2peer.services.mobsos.successModeling;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/** This class contains functions to work with XLM documents */
public class XMLTools {

  /**
   * Transforms a document into a string
   * @param doc The document to transform
   * @return String representation of the document
   */
  protected static String toXMLString(Document doc) {
    TransformerFactory tf = TransformerFactory.newInstance();
    Transformer transformer;
    try {
      transformer = tf.newTransformer();
      // below code to remove XML declaration
      // transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      StringWriter writer = new StringWriter();
      transformer.transform(new DOMSource(doc), new StreamResult(writer));
      String output = writer.getBuffer().toString();

      return output;
    } catch (TransformerException e) {
      e.printStackTrace();
    }
    return null;
  }

  /**
   * Function to transform string representation of xml into a document
   * @param xml string representation of a document
   * @return respective document
   * @throws ParserConfigurationException
   * @throws IOException
   * @throws SAXException
   */
  protected static Document loadXMLFromString(String xml)
    throws ParserConfigurationException, IOException, SAXException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

    factory.setNamespaceAware(true);
    DocumentBuilder builder = factory.newDocumentBuilder();

    return builder.parse(new ByteArrayInputStream(xml.getBytes()));
  }

  /**
   * Returns the first occurence of an XML tag-element whose name attribute matches the input
   * @param elementName the input to match the attribute on
   * @param doc The document in which to search
   * @param tagName The tag of the elements which should be taken into consideration
   * @return the first occurence matching tagname and attribute name
   */
  protected static Element extractElementByName(
    String elementName,
    Document doc,
    String tagName
  ) {
    NodeList elements = doc.getElementsByTagName(tagName);

    for (int i = 0; i < elements.getLength(); i++) {
      if (elements.item(i) instanceof Element) {
        if (
          elementName
            .toLowerCase()
            .equals(
              ((Element) elements.item(i)).getAttribute("name").toLowerCase()
            )
        ) {
          return (Element) elements.item(i);
        }
      }
    }
    return null;
  }

  /**
   * Returns the first occurence of an XML tag-element whose name attribute matches the input
   * @param elementName the input to match the attribute on
   * @param doc The element node in which to search
   * @param tagName The tag of the elements which should be taken into consideration
   * @return the first occurence matching tagname and attribute name
   */
  protected static Element extractElementByName(
    String elementName,
    Element doc,
    String tagName
  ) {
    NodeList elements = doc.getElementsByTagName(tagName);

    for (int i = 0; i < elements.getLength(); i++) {
      if (elements.item(i) instanceof Element) {
        if (
          elementName
            .toLowerCase()
            .equals(
              ((Element) elements.item(i)).getAttribute("name").toLowerCase()
            )
        ) {
          return (Element) elements.item(i);
        }
      }
    }
    return null;
  }

  /**
   * Returns the first occurence of a tag-element in a document
   * @param doc document in which to search
   * @param tagName name of the tag we are looking for
   * @return the first occurence matching the tagName
   */
  protected static Element extractElementByTagName(
    Element doc,
    String tagName
  ) {
    NodeList elements = doc.getElementsByTagName(tagName);
    return elements.getLength() > 0 && (elements.item(0) instanceof Element)
      ? (Element) elements.item(0)
      : null;
  }

  /**
   * find all elements with a tag attribute contained in the inputString
   *
   * @param xml        the document to search in
   * @param inpuString the tag by which to search
   * @return
   */
  protected static Set<Node> findMeasuresByAttribute(
    Document xml,
    String inpuString,
    String attribute
  ) {
    Set<Node> list = new HashSet<Node>();
    NodeList measures = xml.getElementsByTagName("measure");
    if (inpuString == null) {
      return null;
    }
    for (int i = 0; i < measures.getLength(); i++) {
      Node measure = measures.item(i);
      if (measure.getNodeType() == Node.ELEMENT_NODE) {
        String[] tags =
          ((Element) measure).getAttribute(attribute).toLowerCase().split(","); // get the name of the
        // measure
        for (int j = 0; j < tags.length; j++) {
          if (
            !tags[j].isEmpty() &&
            inpuString.toLowerCase().contains(tags[j].toLowerCase())
          ) {
            list.add(measure);
            break;
          }
        }
      }
    }
    return list;
  }
}
