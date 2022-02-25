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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

import javax.jcr.Session;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.BoundedInputStream;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.spi.commons.namespace.SessionNamespaceResolver;
import org.apache.jackrabbit.vault.fs.impl.io.DocViewSAXHandler;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.annotation.versioning.ProviderType;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * This is a thread-safe SAX parser which deals with <a href="https://jackrabbit.apache.org/filevault/docview.html">FileVault Document View XML files</a>
 * and passes them to a given {@link DocViewParserHandler}.
 * 
 */
@ProviderType
public final class DocViewParser {

    private final @Nullable NamespaceResolver resolver;

    public static final int MAX_NUM_BYTES_TO_READ_FOR_DOCVIEW_DETECTION = 1024;

    public DocViewParser() {
        this((NamespaceResolver)null);
    }

    /**
     * 
     * @param session uses the namespace from the session for resolving otherwise unknown namespace prefixes in docview files
     */
    public DocViewParser(@NotNull Session session) {
        this(new SessionNamespaceResolver(session));
    }

    public DocViewParser(@Nullable NamespaceResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * Thrown in case the XML is not
     * <a href="https://www.w3.org/TR/REC-xml/#sec-well-formed">well-formed</a> or
     * no valid docview format.
     */
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

    /**
     *
     * Checks if the given {@link InputSource} is complying with the Document View XML format.
     * @param source the source to analyze
     * @return {@code true} in case the given source is Document View XML format
     * @throws IOException if an I/O error occurs
     */
    public static boolean isDocView(InputSource source) throws IOException {
        String encoding = source.getEncoding() != null ? source.getEncoding() : StandardCharsets.UTF_8.name();
        try (Reader reader = (source.getCharacterStream() != null ? source.getCharacterStream() : 
               new InputStreamReader(source.getByteStream(), encoding))) {
            return isDocView(reader);
        }
    }

    /**
     * Checks if the given {@link Reader} is complying with the Document View XML format.
     * Don't forget to reset the reader or use a new reader before parsing the xml.
     * @param reader the reader from which to read the XML
     * @return {@code true} in case the given source is Document View XML format
     * @throws IOException
     */
    private static boolean isDocView(Reader reader) throws IOException {
        // read a couple of chars...1024 should be enough, assume 1 char = 1 byte
        char[] buffer = new char[MAX_NUM_BYTES_TO_READ_FOR_DOCVIEW_DETECTION];
        int pos = 0;
        while (pos<buffer.length) {
            int read = reader.read(buffer, pos, buffer.length - pos);
            if (read < 0) {
                break;
            }
            pos+=read;
        }
        String str = new String(buffer, 0, pos);
        // check for docview
        return str.contains("<jcr:root") && str.contains("\"http://www.jcp.org/jcr/1.0\"");
    }


    /**
     * Converts the given file path to the absolute root node path given that {@link InputStream} is complying with the Document View XML format.
     * @param input the given input is automatically reset after this method returns
     * @param filePath the file path of the file containing the potential docview xml, must be relative to the jcr_root directory
     * @return either the absolute repository path of the root node of the given docview xml or {@code null} if no docview xml given
     * @throws IOException */
    public static @Nullable String getDocumentViewXmlRootNodePath(InputStream input, Path filePath) throws IOException {
        if (filePath.isAbsolute()) {
            throw new IllegalArgumentException("The filePath parameter must be given as relative path!");
        }
        Path name = filePath.getFileName();
        Path rootPath = null;
        int nameCount = filePath.getNameCount();
        if (name.equals(Paths.get(Constants.DOT_CONTENT_XML))) {
            // get parent path
            if (nameCount > 1) {
                rootPath = filePath.getParent();
            } else {
                rootPath = Paths.get("");
            } 
            // correct suffix matching
        } else if (name.toString().endsWith(".xml")) {

            input.mark(MAX_NUM_BYTES_TO_READ_FOR_DOCVIEW_DETECTION);
            // analyze content
            // this closes the input source internally, therefore protect against closing
            // make sure to initialize the SLF4J logger appropriately (for the XmlAnalyzer)
            try {
                if (DocViewParser.isDocView(new InputStreamReader(new BoundedInputStream(input, MAX_NUM_BYTES_TO_READ_FOR_DOCVIEW_DETECTION), StandardCharsets.US_ASCII))) { // make sure only 1 byte = character is used
                    //  remove .xml extension
                    String fileName = filePath.getFileName().toString();
                    fileName = fileName.substring(0, fileName.length() - ".xml".length());
                    if (nameCount > 1) {
                        rootPath = filePath.getParent().resolve(fileName);
                    } else {
                        rootPath = Paths.get(fileName);
                    }
                }
            } finally {
                input.reset();
            }
        }
        if (rootPath == null) {
            return null;
        }
        String platformPath = FilenameUtils.separatorsToUnix(rootPath.toString());
        return "/" + PlatformNameFormat.getRepositoryPath(platformPath, true);
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
     * Parses a FileVault Document View XML file and calls the given handler for each parsed node.
     * @param rootNodePath the path of the root node of the given docview xml
     * @param inputSource the source of the docview xml
     * @param handler the callback handler which gets the deserialized node information
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
        DocViewSAXHandler docViewSaxHandler = new DocViewSAXHandler(handler, rootNodePath, resolver);
        try {
            parser.parse(inputSource, docViewSaxHandler);
        } catch (SAXException|IllegalArgumentException e) {
            throw new XmlParseException(e, docViewSaxHandler.getCurrentPath(), docViewSaxHandler.getDocumentLocator());
        }
    }
}
