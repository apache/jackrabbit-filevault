/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.util.xml.serialize;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import com.ctc.wstx.api.WstxOutputProperties;
import com.ctc.wstx.stax.WstxOutputFactory;
import com.sun.xml.txw2.output.IndentingXMLStreamWriter;

/** StAX XML Stream Writer filter. Adds the following functionality:
 * <ul>
 * <li>optional line break before each attribute</li>
 * <li>new line at end</li>
 * <li>indentation for elements and comments</li>
 * </ul>
 */
public class FormattingXmlStreamWriter implements XMLStreamWriter, AutoCloseable {

    private final Writer rawWriter;
    private final XMLStreamWriter writer;
    private final OutputFormat output;
    private final IndentingXMLStreamWriter elementIndentingXmlWriter;

    int numNamespaceDeclarations = 0;
    int numAttributes = 0;
    private int depth = 0;
    private Attribute bufferedAttribute;

    public static FormattingXmlStreamWriter create(OutputStream output, OutputFormat format)
            throws XMLStreamException, FactoryConfigurationError {
        // always use WoodstoX
        XMLOutputFactory factory = new WstxOutputFactory();
        factory.setProperty(WstxOutputProperties.P_USE_DOUBLE_QUOTES_IN_XML_DECL, true);
        return new FormattingXmlStreamWriter(factory, output, format);
    }

    private FormattingXmlStreamWriter(XMLOutputFactory factory, OutputStream output, OutputFormat format)
            throws XMLStreamException, FactoryConfigurationError {
        this(factory.createXMLStreamWriter(output, StandardCharsets.UTF_8.name()), format);
    }

    private FormattingXmlStreamWriter(XMLStreamWriter writer, OutputFormat output) {
        this.output = output;
        this.writer = writer;
        this.rawWriter = (Writer) writer.getProperty(WstxOutputProperties.P_OUTPUT_UNDERLYING_WRITER);
        if (this.rawWriter == null) {
            throw new IllegalStateException("Could not get underlying writer!");
        }
        this.elementIndentingXmlWriter = new IndentingXMLStreamWriter(writer);
        this.elementIndentingXmlWriter.setIndentStep(output.getIndent());
    }

    @Override
    public void writeEndDocument() throws XMLStreamException {
        // nothing can be written after writeEndDocument() has been called, therefore call the additional new line
        // before
        elementIndentingXmlWriter.writeEndDocument();
        addLineBreak(true);
    }

    @Override
    public void writeStartElement(String localName) throws XMLStreamException {
        onStartElement();
        elementIndentingXmlWriter.writeStartElement(localName);
    }

    @Override
    public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
        onStartElement();
        elementIndentingXmlWriter.writeStartElement(namespaceURI, localName);
    }

    @Override
    public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        onStartElement();
        elementIndentingXmlWriter.writeStartElement(prefix, localName, namespaceURI);
    }

    @Override
    public void writeEndElement() throws XMLStreamException {
        // is it new element or
        flushBufferedAttribute();
        depth--;
        elementIndentingXmlWriter.writeEndElement();
    }

    private void onStartElement() throws XMLStreamException {
        flushBufferedAttribute();
        numNamespaceDeclarations = 0;
        numAttributes = 0;
        depth++;
    }

    @Override
    public void writeNamespace(String prefix, String namespaceURI) throws XMLStreamException {
        numNamespaceDeclarations++;
        elementIndentingXmlWriter.writeNamespace(prefix, namespaceURI);
    }

    @Override
    public void writeAttribute(String localName, String value) throws XMLStreamException {
        if (onAttribute(null, null, localName, value)) {
            elementIndentingXmlWriter.writeAttribute(localName, value);
        }
    }

    @Override
    public void writeAttribute(String prefix, String namespaceURI, String localName, String value)
            throws XMLStreamException {
        if (onAttribute(prefix, namespaceURI, localName, value)) {
            elementIndentingXmlWriter.writeAttribute(prefix, namespaceURI, localName, value);
        }
    }

    @Override
    public void writeAttribute(String namespaceURI, String localName, String value) throws XMLStreamException {
        if (onAttribute(null, namespaceURI, localName, value)) {
            elementIndentingXmlWriter.writeAttribute(namespaceURI, localName, value);
        }
    }

    private final class Attribute {
        private final String prefix;
        private final String namespaceURI;
        private final String localName;
        private final String value;

        public Attribute(String prefix, String namespaceURI, String localName, String value) {
            super();
            this.prefix = prefix;
            this.namespaceURI = namespaceURI;
            this.localName = localName;
            this.value = value;
        }

        public void write(XMLStreamWriter writer) throws XMLStreamException {
            if (prefix == null) {
                if (namespaceURI == null) {
                    writer.writeAttribute(localName, value);
                } else {
                    writer.writeAttribute(namespaceURI, localName, value);
                }
            } else {
                writer.writeAttribute(prefix, namespaceURI, localName, value);
            }
        }
    }

    private boolean onAttribute(String prefix, String namespaceURI, String localName, String value)
            throws XMLStreamException {
        numAttributes++;
        if (output.isSplitAttributesByLineBreaks()) {
            // if the amount of namespace declarations + attributes is bigger than 1
            if (numNamespaceDeclarations + numAttributes > 1) {
                if (bufferedAttribute != null) {
                    addLineBreak(true);
                    indent(true);
                    flushBufferedAttribute();
                }
                addLineBreak(true);
                indent(true);
            } else {
                bufferedAttribute = new Attribute(prefix, namespaceURI, localName, value);
                // buffer attributes to wait for the next ones
                return false;
            }
        }
        return true;
    }

    private boolean flushBufferedAttribute() throws XMLStreamException {
        if (bufferedAttribute != null) {
            bufferedAttribute.write(writer);
            bufferedAttribute = null;
            return true;
        }
        return false;
    }

    private void indent(boolean isAttribute) throws XMLStreamException {
        // writeCharacters does close the current element and changes the state!
        // Stax2.writeSpace cannot be used either due to https://github.com/FasterXML/woodstox/issues/95
        // instead write directly to underlying writer
        try {
            writer.flush();
            if (depth > 0) {
                for (int i = 0; i < depth; i++) {
                    final String indent;
                    if (isAttribute && i == depth - 1) {
                        // leave out one space as that is automatically added by any XMLStreamWriter between any two
                        // attributes
                        indent = output.getIndent()
                                .substring(0, output.getIndent().length() - 1);
                    } else {
                        indent = output.getIndent();
                    }
                    rawWriter.write(indent);
                }
            }
            rawWriter.flush();
        } catch (IOException e) {
            throw new XMLStreamException("Could not indent attribute", e);
        }
    }

    private void addLineBreak(boolean keepState) throws XMLStreamException {
        if (keepState) {
            try {
                writer.flush();
                rawWriter.write('\n');
                rawWriter.flush();
            } catch (IOException e) {
                throw new XMLStreamException("Could not add line break", e);
            }
        } else {
            writeCharacters("\n");
        }
    }

    @Override
    public void writeComment(String data) throws XMLStreamException {
        flushBufferedAttribute();
        addLineBreak(false);
        indent(false);
        elementIndentingXmlWriter.writeComment(data);
    }

    public void close() throws XMLStreamException {
        elementIndentingXmlWriter.close();
    }

    public void setIndentStep(String s) {
        elementIndentingXmlWriter.setIndentStep(s);
    }

    public void flush() throws XMLStreamException {
        elementIndentingXmlWriter.flush();
    }

    public void writeDefaultNamespace(String namespaceURI) throws XMLStreamException {
        elementIndentingXmlWriter.writeDefaultNamespace(namespaceURI);
    }

    public void writeProcessingInstruction(String target) throws XMLStreamException {
        elementIndentingXmlWriter.writeProcessingInstruction(target);
    }

    public void writeProcessingInstruction(String target, String data) throws XMLStreamException {
        elementIndentingXmlWriter.writeProcessingInstruction(target, data);
    }

    public void writeStartDocument() throws XMLStreamException {
        elementIndentingXmlWriter.writeStartDocument();
    }

    public void writeStartDocument(String version) throws XMLStreamException {
        elementIndentingXmlWriter.writeStartDocument(version);
    }

    public void writeDTD(String dtd) throws XMLStreamException {
        elementIndentingXmlWriter.writeDTD(dtd);
    }

    public void writeStartDocument(String encoding, String version) throws XMLStreamException {
        elementIndentingXmlWriter.writeStartDocument(encoding, version);
    }

    public void writeEntityRef(String name) throws XMLStreamException {
        elementIndentingXmlWriter.writeEntityRef(name);
    }

    public void writeEmptyElement(String namespaceURI, String localName) throws XMLStreamException {
        elementIndentingXmlWriter.writeEmptyElement(namespaceURI, localName);
    }

    public String getPrefix(String uri) throws XMLStreamException {
        return elementIndentingXmlWriter.getPrefix(uri);
    }

    public void setPrefix(String prefix, String uri) throws XMLStreamException {
        elementIndentingXmlWriter.setPrefix(prefix, uri);
    }

    public void writeEmptyElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        elementIndentingXmlWriter.writeEmptyElement(prefix, localName, namespaceURI);
    }

    public void setDefaultNamespace(String uri) throws XMLStreamException {
        elementIndentingXmlWriter.setDefaultNamespace(uri);
    }

    public void writeEmptyElement(String localName) throws XMLStreamException {
        elementIndentingXmlWriter.writeEmptyElement(localName);
    }

    public void setNamespaceContext(NamespaceContext context) throws XMLStreamException {
        elementIndentingXmlWriter.setNamespaceContext(context);
    }

    public NamespaceContext getNamespaceContext() {
        return elementIndentingXmlWriter.getNamespaceContext();
    }

    public Object getProperty(String name) throws IllegalArgumentException {
        return elementIndentingXmlWriter.getProperty(name);
    }

    public void writeCharacters(String text) throws XMLStreamException {
        elementIndentingXmlWriter.writeCharacters(text);
    }

    public void writeCharacters(char[] text, int start, int len) throws XMLStreamException {
        elementIndentingXmlWriter.writeCharacters(text, start, len);
    }

    public void writeCData(String data) throws XMLStreamException {
        elementIndentingXmlWriter.writeCData(data);
    }

    public String toString() {
        return elementIndentingXmlWriter.toString();
    }
}
