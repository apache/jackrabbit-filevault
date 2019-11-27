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

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import javax.xml.stream.FactoryConfigurationError;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import com.ctc.wstx.api.WstxOutputProperties;
import com.ctc.wstx.stax.WstxOutputFactory;

/** StAX XML Stream Writer filter. Adds the following functionality:
 * <ul>
 * <li>optional line break before each attribute</li>
 * <li>new line at end</li>
 * <li>indentation for elements and comments</li>
 * </ul> 
 */
public class FormattingXmlStreamWriter extends com.sun.xml.txw2.output.IndentingXMLStreamWriter implements AutoCloseable {

    private final Writer rawWriter;
    private final XMLStreamWriter writer;
    private final OutputFormat output;

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
        super(writer);
        super.setIndentStep(output.getIndent());
        this.output = output;
        this.writer = writer;
        this.rawWriter = (Writer) writer.getProperty(WstxOutputProperties.P_OUTPUT_UNDERLYING_WRITER);
        if (this.rawWriter == null) {
            throw new IllegalStateException("Could not get underlying writer!");
        }
    }

    @Override
    public void writeEndDocument() throws XMLStreamException {
        // nothing can be written after writeEndDocument() has been called, therefore call the additional new line before
        super.writeEndDocument();
        addLineBreak(true);
    }

    @Override
    public void writeStartElement(String localName) throws XMLStreamException {
        onStartElement();
        super.writeStartElement(localName);
    }

    @Override
    public void writeStartElement(String namespaceURI, String localName) throws XMLStreamException {
        onStartElement();
        super.writeStartElement(namespaceURI, localName);
    }

    @Override
    public void writeStartElement(String prefix, String localName, String namespaceURI) throws XMLStreamException {
        onStartElement();
        super.writeStartElement(prefix, localName, namespaceURI);
    }

    @Override
    public void writeEndElement() throws XMLStreamException {
        // is it new element or 
        flushBufferedAttribute();
        depth--;
        super.writeEndElement();
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
        super.writeNamespace(prefix, namespaceURI);
    }

    @Override
    public void writeAttribute(String localName, String value) throws XMLStreamException {
        if (onAttribute(null, null, localName, value)) {
            super.writeAttribute(localName, value);
        }
    }

    @Override
    public void writeAttribute(String prefix, String namespaceURI, String localName, String value) throws XMLStreamException {
        if (onAttribute(prefix, namespaceURI, localName, value)) {
            super.writeAttribute(prefix, namespaceURI, localName, value);
        }
    }

    @Override
    public void writeAttribute(String namespaceURI, String localName, String value) throws XMLStreamException {
        if (onAttribute(null, namespaceURI, localName, value)) {
            super.writeAttribute(namespaceURI, localName, value);
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

    private boolean onAttribute(String prefix, String namespaceURI, String localName, String value) throws XMLStreamException {
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
                        // leave out one space as that is automatically added by any XMLStreamWriter between any two attributes
                        indent = output.getIndent().substring(0, output.getIndent().length() - 1);
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
        super.writeComment(data);
        //addLineBreak(false);
    }
}
