/*************************************************************************
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
 ************************************************************************/
package org.apache.jackrabbit.vault.fs.impl.io;

import javax.jcr.ImportUUIDBehavior;
import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.util.DocViewProperty;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
* {@code JcrSysViewTransformer} transforms a docview importer hierarchy to a jcr sysview one by translating the
 * vault specific docview nodes and properties into SAX events for the JCR sysview import content handler.
 *
 * @see Session#getImportContentHandler(String, int)
*/
public class JcrSysViewTransformer implements DocViewAdapter {

    /**
     * sysview handler for special content
     */
    private ContentHandler handler;

    JcrSysViewTransformer(Node node) throws RepositoryException, SAXException {
        Session session = node.getSession();
        handler = session.getImportContentHandler(
                node.getPath(),
                ImportUUIDBehavior.IMPORT_UUID_COLLISION_REMOVE_EXISTING);
        // first define the current namespaces
        String[] prefixes = session.getNamespacePrefixes();
        handler.startDocument();
        for (String prefix: prefixes) {
            handler.startPrefixMapping(prefix, session.getNamespaceURI(prefix));
        }
    }

    public void close() throws SAXException {
        handler.endDocument();
    }

    public void startNode(DocViewNode ni) throws SAXException {
        DocViewSAXImporter.log.debug("Transforming element to sysview {}", ni.name);

        AttributesImpl attrs = new AttributesImpl();

        attrs.addAttribute(Name.NS_SV_URI, "name", "sv:name", "CDATA", ni.name);
        handler.startElement(Name.NS_SV_URI, "node", "sv:node", attrs);

        // add the properties
        for (DocViewProperty p: ni.props.values()) {
            if (p != null && p.values != null) {
                attrs = new AttributesImpl();
                attrs.addAttribute(Name.NS_SV_URI, "name", "sv:name", "CDATA", p.name);
                attrs.addAttribute(Name.NS_SV_URI, "type", "sv:type", "CDATA", PropertyType.nameFromValue(p.type));
                handler.startElement(Name.NS_SV_URI, "property", "sv:property", attrs);
                for (String v: p.values) {
                    handler.startElement(Name.NS_SV_URI, "value", "sv:value", DocViewSAXImporter.EMPTY_ATTRIBUTES);
                    handler.characters(v.toCharArray(), 0, v.length());
                    handler.endElement(Name.NS_SV_URI, "value", "sv:value");
                }
                handler.endElement(Name.NS_SV_URI, "property", "sv:property");
            }
        }
    }

    public void endNode() throws SAXException {
        handler.endElement(Name.NS_SV_URI, "node", "sv:node");
    }
}