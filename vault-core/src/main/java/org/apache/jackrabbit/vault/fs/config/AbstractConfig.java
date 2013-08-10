/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jackrabbit.vault.fs.config;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.util.RejectingEntityResolver;
import org.apache.jackrabbit.vault.util.xml.serialize.OutputFormat;
import org.apache.jackrabbit.vault.util.xml.serialize.XMLSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * <code>VaultUserConfig</code>...
 *
 */
abstract public class AbstractConfig {

    protected static Logger log = LoggerFactory.getLogger(AbstractConfig.class);

    public static final String DIR_NAME = ".vault";

    public static final String ATTR_VERSION = "version";

    protected double version = getSupportedVersion();

    abstract protected String getRootElemName();

    abstract protected double getSupportedVersion();

    protected void load(Element doc) throws ConfigurationException {
        if (!doc.getNodeName().equals(getRootElemName())) {
            throw new ConfigurationException("unexpected element: " + doc.getNodeName());
        }
        String v = doc.getAttribute(ATTR_VERSION);
        if (v == null || v.equals("")) {
            v = "1.0";
        }
        version = Double.parseDouble(v);
        if (version > getSupportedVersion()) {
            throw new ConfigurationException("version " + version + " not supported.");
        }

        NodeList nl = doc.getChildNodes();
        for (int i=0; i<nl.getLength(); i++) {
            Node child = nl.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                doLoad((Element) child);
            }
        }
    }

    abstract protected void doLoad(Element child) throws ConfigurationException;

    public boolean load(File configFile) throws IOException, ConfigurationException {
        return configFile.canRead() && load(FileUtils.openInputStream(configFile));
    }

    public boolean load(InputStream in) throws IOException, ConfigurationException {
        try {
            DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            // disable DTD loading (bug #36897)
            builder.setEntityResolver(new RejectingEntityResolver());
            Document document = builder.parse(in);
            Element doc = document.getDocumentElement();
            load(doc);
            return true;
        } catch (ParserConfigurationException e) {
            throw new ConfigurationException(e);
        } catch (SAXException e) {
            throw new ConfigurationException(e);
        } finally {
            IOUtils.closeQuietly(in);
        }
    }

    public void save(File configFile) throws IOException {
        save(FileUtils.openOutputStream(configFile));
    }
    
    public void save(OutputStream out) throws IOException {
        OutputFormat fmt = new OutputFormat("xml", "UTF-8", true);
        fmt.setLineWidth(0);
        fmt.setIndent(2);
        XMLSerializer ser = new XMLSerializer(out, fmt);
        try {
            write(ser);
        } catch (SAXException e) {
            throw new IOException(e.toString());
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

    public File getConfigDir() throws IOException {
        File userHome = new File(System.getProperty("user.home"));
        File configDir = new File(userHome, DIR_NAME);
        if (!configDir.exists()) {
            configDir.mkdirs();
            if (!configDir.exists()) {
                throw new IOException("Error: Unable to create " + configDir.getAbsolutePath());
            }
        }
        return configDir;
    }

    protected void write(ContentHandler handler) throws SAXException {
        handler.startDocument();
        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute("", ATTR_VERSION, "", "CDATA", String.valueOf(version));
        handler.startElement("", getRootElemName(), "", attrs);
        doWrite(handler);
        handler.endElement("", getRootElemName(), "");
        handler.endDocument();
    }

    abstract protected void doWrite(ContentHandler handler) throws SAXException;
}