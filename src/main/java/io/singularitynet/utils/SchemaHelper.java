// --------------------------------------------------------------------------------------------------
//  Copyright (c) 2016 Microsoft Corporation
//  
//  Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
//  associated documentation files (the "Software"), to deal in the Software without restriction,
//  including without limitation the rights to use, copy, modify, merge, publish, distribute,
//  sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
//  furnished to do so, subject to the following conditions:
//  
//  The above copyright notice and this permission notice shall be included in all copies or
//  substantial portions of the Software.
//  
//  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
//  NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
//  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
//  DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
//  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
// --------------------------------------------------------------------------------------------------

package io.singularitynet.utils;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;


/** Helper functions for serialising/deserialising our schema-defined objects.
 */
public class SchemaHelper
{
    private static HashMap<String, JAXBContext> jaxbContentCache = new HashMap<String, JAXBContext>();

    /** Serialise the object to an XML string
     * @param objclass the class of the object to be serialised
     * @return an XML string representing the object, or null if the object couldn't be serialised
     * @throws JAXBException 
     */
    
    static private JAXBContext getJAXBContext(Class<?> objclass) throws JAXBException
    {
        JAXBContext jaxbContext;
        if (jaxbContentCache.containsKey(objclass.getName()))
        {
            jaxbContext = jaxbContentCache.get(objclass.getName());
        }
        else
        {
            jaxbContext = JAXBContext.newInstance(objclass);
            jaxbContentCache.put(objclass.getName(), jaxbContext);
        }
        return jaxbContext;
    }

    static public String serialiseObject(Object obj, Class<?> objclass) throws JAXBException
    {
        JAXBContext jaxbContext = getJAXBContext(objclass);
        Marshaller m = jaxbContext.createMarshaller();
        StringWriter w = new StringWriter();
        m.marshal(obj, w);
        String xmlString = w.toString();
        return xmlString;
    }

    /** Attempt to construct the specified object from this XML string
     * @param xml the XML string to parse
     * @param objclass the class of the object requested
     * @return if successful, an instance of class objclass that captures the data in the XML string
     */
    static public Object deserialiseObject(String xml, Class<?> objclass) throws JAXBException, SAXException, XMLStreamException
    {
        Object result = null;
        JAXBContext jaxbContext = getJAXBContext(objclass);
        InputStream targetStream = new ByteArrayInputStream(xml.getBytes());
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        result = unmarshaller.unmarshal(targetStream);
        return result;
    }

    /** Retrieve the name of the root node in an XML string.
     * @param xml The XML string to parse.
     * @return The name of the root node, or null if parsing failed.
     */
    static public String getRootNodeName(String xml)
    {
        String rootNodeName = null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setExpandEntityReferences(false);
            DocumentBuilder dBuilder = dbf.newDocumentBuilder();
            InputSource inputSource = new InputSource(new StringReader(xml));
            Document doc = dBuilder.parse(inputSource);
            doc.getDocumentElement().normalize();
            rootNodeName = doc.getDocumentElement().getNodeName();
        } catch (SAXException e) {
            System.out.println("SAX exception: " + e);
        } catch (IOException e) {
            System.out.println("IO exception: " + e);
        } catch (ParserConfigurationException e) {
            System.out.println("ParserConfiguration exception: " + e);
        }
        return rootNodeName;
    }

    static public boolean testSchemaVersionNumbers(String modVersion)
    {
        // modVersion will be in three parts - eg 0.19.1
        // We only care about the major and minor release numbers.
        String[] parts = modVersion.split("\\.");
        if (parts.length != 3)
        {
            System.out.println("Malformed mod version number: " + modVersion + " - should be of form x.y.z. Has CMake been run?");
            return false;
        }
        String requiredVersion = parts[0] + "." + parts[1];
        System.out.println("Testing schemas against internal version number: " + requiredVersion);
        InputStream stream = SchemaHelper.class.getClassLoader().getResourceAsStream("schemas.index");
        if (stream == null)
        {
            System.out.println("Cannot find index of schema files in resources - try rebuilding.");
            return false;   // Failed to find index in resources - check that gradle build has happened!
        }
        Scanner scanner = new Scanner(stream);
        while (scanner.hasNextLine())
        {
            String xsdFile = scanner.nextLine();
            String version = getVersionNumber(xsdFile);
            if (version == null || !version.equals(requiredVersion))
            {
                scanner.close();
                System.out.println("Version error: schema file " + xsdFile + " has the wrong version number - should be " + requiredVersion + ", actually " + version);
                return false;
            }
        }
        scanner.close();
        return true;
    }
    
    static private String getVersionNumber(String url)
    {
        // Load the XSD file as a string:
        InputStream stream = SchemaHelper.class.getClassLoader().getResourceAsStream(url);
        Scanner scanner = new Scanner(stream, "UTF-8");
        scanner.useDelimiter("\\A");
        String xml = scanner.next();
        scanner.close();

        // Now try to parse the XSD Document, and find the schema version number:
        try
        {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setExpandEntityReferences(false);
            DocumentBuilder dBuilder = dbf.newDocumentBuilder();
            InputSource inputSource = new InputSource(new StringReader(xml));
            Document doc = dBuilder.parse(inputSource);
            doc.getDocumentElement().normalize();
            NamedNodeMap atts = doc.getDocumentElement().getAttributes();
            if (atts != null)
            {
                Node node = atts.getNamedItem("version");
                if (node != null)
                    return node.getNodeValue();
            }
        } catch (SAXException e) {
            System.out.println("SAX exception: " + e);
        } catch (IOException e) {
            System.out.println("IO exception: " + e);
        } catch (ParserConfigurationException e) {
            System.out.println("ParserConfiguration exception: " + e);
        }
        return null;
    }

    /** Return the text value of the first child of the named node, or the specified default if the node can't be found.<br>
     * For example, calling getNodeValue(el, "Mode", "whatever") on a list of elements which contains the following XML:
     *  <Mode>survival</Mode>
     * should return "survival".
     * @param elements the list of XML elements to search
     * @param nodeName the name of the parent node to extract the text value from
     * @param defaultValue the default to return if the node is empty / doesn't exist
     * @return the text content of the desired element
     */
    static public String getNodeValue(List<Element> elements, String nodeName, String defaultValue)
    {
        if (elements != null)
        {
            for (Element el : elements)
            {
                if (el.getNodeName().equals(nodeName))
                {
                    if (el.getFirstChild() != null)
                    {
                        return el.getFirstChild().getTextContent();
                    }
                }
            }
        }
        return defaultValue;
    }
}
