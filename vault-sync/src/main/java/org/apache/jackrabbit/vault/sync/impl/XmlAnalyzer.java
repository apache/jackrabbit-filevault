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

package org.apache.jackrabbit.vault.sync.impl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

/**
 * Analyzes a xml source and guesses the type. the following types are
 * recognized:
 * <ul>
 * <li> {@link org.apache.jackrabbit.vault.fs.api.SerializationType#GENERIC} if the source is not a valid XML
 * <li> {@link org.apache.jackrabbit.vault.fs.api.SerializationType#XML_GENERIC} if the XML type is not known. eg. a user-xml
 * <li> {@link org.apache.jackrabbit.vault.fs.api.SerializationType#XML_DOCVIEW} if the XML is a docview serialization
 * </ul>
 * Please note, that the docview serialization is recognized if the first
 * element contains a jcr:primaryType attribute.
 *
 * Note: this is a copy of the package internal org.apache.jackrabbit.vault.impl.io.XmlAnalyzer
 */
public class XmlAnalyzer {

    /**
     * the default logger
     */
    private static final Logger log = LoggerFactory.getLogger(XmlAnalyzer.class);

    private XmlAnalyzer() {
    }

    /**
     * Analyzes the given source.
     *
     * @param source the source to analyze
     * @return the serialization type
     * @throws java.io.IOException if an I/O error occurs
     */
    public static SerializationType analyze(InputSource source) throws IOException {
        Reader r = source.getCharacterStream();
        SerializationType type = SerializationType.UNKOWN;
        if (r == null) {
            if (source.getEncoding() == null) {
                r = new InputStreamReader(source.getByteStream());
            } else {
                r = new InputStreamReader(source.getByteStream(), source.getEncoding());
            }
        }
        try {
            // read a couple of chars...1024 should be enough
            char[] buffer = new char[1024];
            int pos = 0;
            while (pos<buffer.length) {
                int read = r.read(buffer, pos, buffer.length - pos);
                if (read < 0) {
                    break;
                }
                pos+=read;
            }
            String str = new String(buffer, 0, pos);
            // check for docview
            if (str.contains("<jcr:root ") && str.contains("\"http://www.jcp.org/jcr/1.0\"")) {
                type = SerializationType.XML_DOCVIEW;
            } else if (str.contains("<?xml ")) {
                type = SerializationType.XML_GENERIC;
            } else {
                type = SerializationType.GENERIC;
            }
        } finally {
            IOUtils.closeQuietly(r);
        }
        log.debug("Analyzed {}. Type = {}", source.getSystemId(), type);
        return type;
    }

}