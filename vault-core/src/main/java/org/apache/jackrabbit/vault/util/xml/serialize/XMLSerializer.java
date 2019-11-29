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
package org.apache.jackrabbit.vault.util.xml.serialize;

import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * <b>ASF Note</b>: This class and dependencies were copied from the sun jdk1.5
 * source base. The original serializer is extended by a new OutputFormat
 * setting that allows to break the line for each attribute. additionally,
 * all attribute names are sorted alphabetically.
 * Some Features were removed to limit the number of dependent classes:
 * <ul>
 * <li>dom filter support</li>
 * <li>all text nodes as CDATA feature</li>
 * <li>skip attribute default values feature</li>
 * <li>entity node reference feature</li>
 * </ul>
 * <p>
 *  
 *  Cannot use o.a.j.commons.xml.ToXmlContentHandler as that does not deal with namespaces properly
 */
public class XMLSerializer extends IndentXmlWriter {

    private OutputFormat format;

    private ElementInfo currentElement;

    public XMLSerializer(OutputStream output, OutputFormat format) throws UnsupportedEncodingException {
        super(output, StandardCharsets.UTF_8, format.getIndent());
        this.format = format;
    }

    // legacy method
    public void endElement(String name) throws SAXException {
        this.endElement(null, null, name);
    }

    // fix indentation
    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        lazyCloseStartElement();
        currentElement = new ElementInfo(currentElement, namespaces);
        writeIndent();
        write('<');
        write(currentElement.getQName(uri, localName, qName));

        for (Map.Entry<String, String> entry : namespaces.entrySet()) {
            write(' ');
            write("xmlns");
            String prefix = entry.getValue();
            if (prefix.length() > 0) {
                write(':');
                write(prefix);
            }
            write('=');
            write('"');
            char[] ch = entry.getKey().toCharArray();
            writeEscaped(ch, 0, ch.length, true);
            write('"');
        }
        
        // for backwards-compatibility accept null values
        if (atts != null) {
            for (int i = 0; i < atts.getLength(); i++) {
                if (format.isSplitAttributesByLineBreaks() && namespaces.size() + atts.getLength() > 1) {
                    write('\n');
                    writeIndent(true);
                } else {
                    write(' ');
                }
                write(currentElement.getQName(atts.getURI(i), atts.getLocalName(i), atts.getQName(i)));
                write('=');
                write('"');
                char[] ch = atts.getValue(i).toCharArray();
                writeEscaped(ch, 0, ch.length, true);
                write('"');
            }
        }

        namespaces.clear();
        inStartElement = true;
        indent();
    }

    @Override
    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
        outdent();
        // start original
        if (inStartElement) {
            write("/>");
            inStartElement = false;
        } else {
            writeIndent();
            write("</");
            write(currentElement.getQName(namespaceURI, localName, qName));
            write('>');
        }

        namespaces.clear();

        // Reset the position in the tree, to avoid endless stack overflow
        // chains (see TIKA-1070)
        currentElement = currentElement.parent;
        // end original
        write('\n');
    }
 
    // copied from super class to make it accessible (because all is private)
    private static class ElementInfo {

        private final ElementInfo parent;

        private final Map<String, String> namespaces;

        public ElementInfo(ElementInfo parent, Map<String, String> namespaces) {
            this.parent = parent;
            if (namespaces.isEmpty()) {
                this.namespaces = Collections.emptyMap();
            } else {
                this.namespaces = new HashMap<String, String>(namespaces);
            }
        }

        public String getPrefix(String uri) throws SAXException {
            String prefix = namespaces.get(uri);
            if (prefix != null) {
                return prefix;
            } else if (parent != null) {
                return parent.getPrefix(uri);
            } else if (uri == null || uri.length() == 0) {
                return "";
            } else {
                throw new SAXException("Namespace " + uri + " not declared");
            }
        }

        // generates qualified name from the prefix, fall back to given qName
        public String getQName(String uri, String localName, String qName)
                throws SAXException {
            // TODO: modified
            if (uri == null || uri.length() == 0 || localName == null || localName.length() == 0) {
                return qName;
            }
        
            String prefix = getPrefix(uri);
            if (prefix.length() > 0) {
                return prefix + ":" + localName;
            } else {
                return localName;
            }
        }

    }
    
    /**
     * Writes the given characters as-is followed by the given entity.
     *
     * @param ch character array
     * @param from start position in the array
     * @param to end position in the array
     * @param entity entity code
     * @return next position in the array,
     *         after the characters plus one entity
     * @throws SAXException if the characters could not be written
     */
    private int writeCharsAndEntity(char[] ch, int from, int to, String entity)
            throws SAXException {
        super.characters(ch, from, to - from);
        write('&');
        write(entity);
        write(';');
        return to + 1;
    }

    /**
     * Writes the given characters with XML meta characters escaped.
     *
     * @param ch character array
     * @param from start position in the array
     * @param to end position in the array
     * @param attribute whether the characters should be escaped as
     *                  an attribute value or normal character content
     * @throws SAXException if the characters could not be written
     */
    private void writeEscaped(char[] ch, int from, int to, boolean attribute)
            throws SAXException {
        int pos = from;
        while (pos < to) {
            if (ch[pos] == '<') {
                from = pos = writeCharsAndEntity(ch, from, pos, "lt");
            } else if (ch[pos] == '>') {
                from = pos = writeCharsAndEntity(ch, from, pos, "gt");
            } else if (ch[pos] == '&') {
                from = pos = writeCharsAndEntity(ch, from, pos, "amp");
            } else if (attribute && ch[pos] == '"') {
                from = pos = writeCharsAndEntity(ch, from, pos, "quot");
            } else {
                pos++;
            }
        }
        super.characters(ch, from, to - from);
    }

    private void lazyCloseStartElement() throws SAXException {
        if (inStartElement) {
            write(">\n");
            inStartElement = false;
        }
    }
   
}

