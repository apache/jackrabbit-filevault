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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

import org.apache.jackrabbit.vault.util.QNameComparator;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

/** 
 * SAX filter which
 * <ol>
 * <li>orders attributes alphabetically</li>
 * <li>strips ignorable whitespace (even without schema/DTD) -> all characters in FileVault because all elements are supposed to be empty</li>
 * <li>removes leading comments (prior to root element) due to bug in SAX2StAXStreamWriter in Java</li>
 * </ol>
 */
public class NormalizingSaxFilter extends XMLFilterImpl implements LexicalHandler {

    private final class AttributeComparator implements Comparator<Attribute> {

        private final QNameComparator nameComparator;

        public AttributeComparator() {
            nameComparator = new QNameComparator();
        }

        @Override
        public int compare(Attribute o1, Attribute o2) {
            return nameComparator.compare(o1.getName(), o2.getName());
        }
    }

    private static final class Attribute {
        private final QName name;
        private final String value;
        private final String type;

        public Attribute(Attributes attributes, int i) {
            this(getQNameFromAttribute(attributes, i), attributes.getValue(i), attributes.getType(i));
        }

        private static QName getQNameFromAttribute(Attributes attributes, int i) {
            String qName = attributes.getQName(i);
            String prefix = XMLConstants.DEFAULT_NS_PREFIX;
            final String localPart;
            String nameParts[] = qName.split(":", 2);
            if (nameParts.length > 1) {
                prefix = nameParts[0];
                localPart = nameParts[1];
            } else {
                localPart = nameParts[0];
            }
            return new QName(attributes.getURI(i), localPart, prefix);
        }

        public Attribute(QName name, String value, String type) {
            super();
            this.name = name;
            this.value = value;
            this.type = type;
        }

        public QName getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public String getType() {
            return type;
        }

        public void addToAttributes(AttributesImpl attributes) {
            StringBuilder qName = new StringBuilder();
            if (getName().getPrefix().length() > 0) {
                qName.append(getName().getPrefix()).append(":");
            }
            qName.append(getName().getLocalPart());
            attributes.addAttribute(getName().getNamespaceURI(), getName().getLocalPart(),
                    qName.toString(), getType(), getValue());
        }
    }

    private static final String SAX_PROPERTY_LEXICAL_HANDLER = "http://xml.org/sax/properties/lexical-handler";
    private LexicalHandler lexicalHandler;
    boolean hasReachedRootElement;

    public NormalizingSaxFilter(XMLReader parent) {
        super(parent);
        lexicalHandler = null;
    }

    @Override
    public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
        if (name.equals(SAX_PROPERTY_LEXICAL_HANDLER)) {
            this.lexicalHandler = (LexicalHandler) value;
            super.setProperty(name, this);
        } else {
            super.setProperty(name, value);
        }
    }

    @Override
    public void parse(InputSource input) throws SAXException, IOException {
        hasReachedRootElement = false;
        super.parse(input);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        hasReachedRootElement = true;
        List<Attribute> attributeList = new LinkedList<>();
        for (int i = 0; i < atts.getLength(); i++) {
            Attribute attribute = new Attribute(atts, i);
            attributeList.add(attribute);
        }
        Collections.sort(attributeList, new AttributeComparator());
        super.startElement(uri, localName, qName, getAttributesFromList(attributeList));
    }

    /**
     * Filter out ignorable whitespace (for FileVault all elements are empty, i.e. every character is ignorable)
     */
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        // filter out everything
    }

    AttributesImpl getAttributesFromList(List<Attribute> attributeList) {
        AttributesImpl attributes = new AttributesImpl();
        for (Attribute attribute : attributeList) {
            attribute.addToAttributes(attributes);
        }
        return attributes;
    }

    @Override
    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        if (lexicalHandler != null) {
            lexicalHandler.startDTD(name, publicId, systemId);
        }
    }

    /********* Start Lexical Handler *********/
    @Override
    public void endDTD() throws SAXException {
        if (lexicalHandler != null) {
            lexicalHandler.endDTD();
        }
    }

    @Override
    public void startEntity(String name) throws SAXException {
        if (lexicalHandler != null) {
            lexicalHandler.startEntity(name);
        }
    }

    @Override
    public void endEntity(String name) throws SAXException {
        if (lexicalHandler != null) {
            lexicalHandler.endEntity(name);
        }
    }

    @Override
    public void startCDATA() throws SAXException {
        if (lexicalHandler != null) {
            lexicalHandler.startCDATA();
        }
    }

    @Override
    public void endCDATA() throws SAXException {
        if (lexicalHandler != null) {
            lexicalHandler.endCDATA();
        }
    }

    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {
        // prevent comment before first element due to a bug in Java
        if (!hasReachedRootElement) {
            return;
        }
        if (lexicalHandler != null) {
            lexicalHandler.comment(ch, start, length);
        }
    }
    /********* End Lexical Handler *********/
}
