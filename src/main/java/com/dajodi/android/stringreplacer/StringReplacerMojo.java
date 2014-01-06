package com.dajodi.android.stringreplacer;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.wagon.util.IoUtils;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import sun.rmi.runtime.Log;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.*;
import java.io.*;
import java.util.*;

/**
 * Replaces string values in an android resource file.
 *
 * @goal replace
 *
 */
public class StringReplacerMojo extends AbstractMojo
{

    public static final String ATTR_NAME = "name";
    public static final String ATTR_REPLACEABLE = "replaceable";

    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * The string resource files that will be used when reading properties.  These files
     * should be relative to the resource directory.  For example, values/strings.xml,
     * values/strings-config.xml, ...
     *
     * @parameter
     * @required
     */
    private String[] files;

    /**
     * The android resource directory.
     *
     * @parameter default-value="${project.build.directory}/filtered-res"
     */
    private File outputDirectory;

    /**
     * The android resource directory.
     *
     * @parameter default-value="res"
     */
    private File resourceDirectory;

    public void execute() throws MojoExecutionException
    {
        getLog().debug("Resource Directory: " + resourceDirectory.getAbsolutePath());
        getLog().debug("Output Directory: " + outputDirectory.getAbsolutePath());

        Properties props = project.getProperties();

        Set<String> toFilter = new HashSet<String>();
        for (String f : files) {
            toFilter.add(FileUtils.normalize(f));
        }

        try {
            List files = FileUtils.getFileNames(resourceDirectory, "**", null, false);

            for (int i = 0; i < files.size(); i++) {
                String file = (String) files.get(i);

                File source = new File(resourceDirectory, file);
                File dest = new File(outputDirectory, file);
                FileUtils.forceMkdir(dest.getParentFile());

                if (toFilter.contains(file)) {
                    getLog().info("Strings will potentially be replace in file: " + file);
                    try {
                        processFile(props, source, dest);
                    } catch (XMLStreamException e) {
                        throw new MojoExecutionException("Error processing file " + source.getAbsolutePath(), e);
                    }
                } else {
                    FileUtils.copyFile(source, dest);
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("error iterating files", e);
        }
    }

    private void processFile(Properties props, File source, File dest) throws FileNotFoundException, XMLStreamException {

        XMLInputFactory xmlInputFactory = XMLInputFactory.newInstance();
        XMLEventReader xmlStreamReader = xmlInputFactory.createXMLEventReader(new FileInputStream(source));
        XMLEventWriter xmlWriter = XMLOutputFactory.newInstance().createXMLEventWriter(new FileOutputStream(dest));
        XMLEventFactory eventFactory = XMLEventFactory.newInstance();

        boolean hijacked = false;
        while (xmlStreamReader.hasNext()) {
            XMLEvent event = xmlStreamReader.nextEvent();
            if (event instanceof StartElement) {
                StartElement se = (StartElement) event;

                getLog().debug("found start element: " + se);

                if (se.getName().getLocalPart().equals("string")) {

                    getLog().debug("found string element");

                    Attribute nameAttr = se.getAttributeByName(new QName(null, ATTR_NAME));
                    Attribute keyAttr = se.getAttributeByName(new QName(null, ATTR_REPLACEABLE));
                    String key = null;

                    if (nameAttr != null) {
                        key = nameAttr.getValue();
                    }

                    boolean replaceable = false;
                    if (keyAttr != null && keyAttr.getValue() != null && keyAttr.getValue().toLowerCase().equals("true")) {
                        replaceable = true;
                    }

                    String value = null;
                    if (!StringUtils.isEmpty(key)) {
                        value = (String) props.get(key);
                    }

                    // allow empty strings
                    if (replaceable && value != null) {
                        getLog().info(String.format("Replacing value <string name=\"%s\"> with <string name=\"%s\">%s</string>", key, key, value));
                        hijacked = true;

                        List<Attribute> attributes = new LinkedList<Attribute>();
                        Iterator iterator = se.getAttributes();
                        while (iterator.hasNext()) {
                            Attribute attribute = (Attribute) iterator.next();
                            if (!attribute.getName().getLocalPart().equals("key")) {
                                attributes.add(attribute);
                            }
                        }

                        StartElement replaced = eventFactory.createStartElement("", null, "string", attributes.iterator(), null);
                        xmlWriter.add(replaced);

                        Characters chars = eventFactory.createCharacters(value);
                        xmlWriter.add(chars);
                    } else {
                        xmlWriter.add(event);
                    }
                } else {
                    xmlWriter.add(event);
                }
            } else {

                // don't have to worry about nested elements, those are not valid Android files
                if (hijacked) {
                    // swallow everything that's not an end element
                    if (event instanceof EndElement) {
                        hijacked = false;
                        xmlWriter.add(event);
                    } else {
                        getLog().debug("Swallowing event: " + event + "::" + event.getClass().getSimpleName());
                    }
                } else {
                    xmlWriter.add(event);
                }
            }
        }

        xmlWriter.flush();
        xmlWriter.close();

    }

}
