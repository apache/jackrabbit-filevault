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
package org.apache.jackrabbit.vault.fs.io;

import java.io.IOException;
import java.util.Objects;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.jackrabbit.vault.fs.impl.io.DocViewSAXHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * This is a thread-safe sax parser which deals with docview files and passes them to a given {@link DocViewParserHandler}.
 * 
 */
public class DocViewParser {

	
	/** Thrown in case the XML is not <a href="https://www.w3.org/TR/REC-xml/#sec-well-formed">well-formed</a>
	 * or no valid docview format */
    public static final class XmlParseException extends Exception {

        /**
         * 
         */
        private static final long serialVersionUID = 1L;
        private final String nodePath;
        private final int lineNumber;
        private final int columnNumber;

        public XmlParseException(String message, String nodePath, int lineNumber, int columnNumber) {
            super(message);
            this.nodePath = nodePath;
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
        }
      
        public XmlParseException(Throwable cause, String nodePath, int lineNumber, int columnNumber) {
            super(cause.getMessage(), cause);
            this.nodePath = nodePath;
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
        }
        
        public XmlParseException(Throwable cause, String nodePath, Locator locator) {
            this(cause, nodePath, locator.getLineNumber(), locator.getColumnNumber());
        }
        
        public String getNodePath() {
			return nodePath;
		}

		public int getLineNumber() {
        	return lineNumber;
        }
        
        public int getColumnNumber() {
        	return columnNumber;
        }

		@Override
		public int hashCode() {
			return Objects.hash(nodePath, columnNumber, lineNumber, getMessage());
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			XmlParseException other = (XmlParseException) obj;
			return Objects.equals(nodePath, other.nodePath) && columnNumber == other.columnNumber && lineNumber == other.lineNumber && getMessage().equals(other.getMessage());
		}
        
        
    }

    private SAXParser createSaxParser() throws ParserConfigurationException, SAXException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://xml.org/sax/features/namespace-prefixes", false);
        SAXParser parser = factory.newSAXParser();
        parser.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        parser.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return parser;
    }

    /**
     * 
     * @param inputSource
     * @param handler
     * @throws IOException 
     * @throws XmlParseException 
     */
    public void parse(String rootNodePath, InputSource inputSource, DocViewParserHandler handler) throws IOException, XmlParseException {
        final SAXParser parser;
        try {
            parser = createSaxParser();
        } catch (ParserConfigurationException|SAXException e) {
            throw new IllegalStateException("Could not create SAX parser" + e.getMessage(), e);
        }
        DocViewSAXHandler docViewSaxHandler = new DocViewSAXHandler(handler, rootNodePath);
        try {
            parser.parse(inputSource, docViewSaxHandler);
        } catch (SAXException|IllegalArgumentException e) {
            throw new XmlParseException(e, docViewSaxHandler.getCurrentPath(), docViewSaxHandler.getDocumentLocator());
        }
    }
}
