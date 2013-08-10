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

package org.apache.jackrabbit.vault.fs.impl.io;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.util.xml.serialize.OutputFormat;
import org.apache.jackrabbit.vault.util.xml.serialize.XMLSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * FilteredXMLSerializer extends {@link XMLSerializer} by adding the possibility to
 * set an element filter for specific namespace URIs and/or raw names.
 *
 */
public class FilteredXMLSerializer extends XMLSerializer {

    private static Logger log = LoggerFactory.getLogger(FilteredXMLSerializer.class);

    private Filter filter = new DefaultFilter();

    public FilteredXMLSerializer(OutputStream outputStream, OutputFormat outputFormat) throws RepositoryException {
        super(outputStream, outputFormat);
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
    }

    public Filter getFilter() {
        return filter;
    }

    public void startElement(String namespaceURI, String localName, String rawName, Attributes attrs) throws SAXException {
        if (filter.isFilteredNamespace(namespaceURI) || filter.isFilteredRawName(rawName)) {
            log.debug("Filtering: {" + namespaceURI + "}" + localName + " -> " + rawName);
        } else {
            AttributesImpl fAttrs = new AttributesImpl(attrs);
            for (int i = 0; i < fAttrs.getLength(); i++) {
                String uri = fAttrs.getURI(i);
                if (filter.isFilteredNamespace(uri)) {
                    fAttrs.removeAttribute(i);
                }
            }
            super.startElement(namespaceURI, localName, rawName, fAttrs);
        }
    }

    public void endElement(String namespaceURI, String localName, String rawName) throws SAXException {
        if (filter.isFilteredNamespace(namespaceURI) || filter.isFilteredRawName(rawName)) {
            log.debug("Filtering: {" + namespaceURI + "}" + localName + " -> " + rawName);
        } else {
            super.endElement(namespaceURI, localName, rawName);
        }
    }

    public void endElementIO(String namespaceURI, String localName, String rawName) throws IOException {
        if (filter.isFilteredNamespace(namespaceURI) || filter.isFilteredRawName(rawName)) {
            log.debug("Filtering: {" + namespaceURI + "}" + localName + " -> " + rawName);
        } else {
            super.endElementIO(namespaceURI, localName, rawName);
        }
    }

    public interface Filter {

        public void addNamespaceFilter(String namespaceURI);

        public void removeNamespaceFilter(String namespaceURI);

        public String[] getNamespaceFilters();

        public void addRawNameFilter(String rawName);

        public void removeRawNameFilter(String rawName);

        public String[] getRawNameFilters();

        public boolean isFilteredNamespace(String namespaceURI);

        public boolean isFilteredRawName(String rawName);
    }

    /**
     * Default filter with no entries.
     */
    public static class DefaultFilter implements Filter {

        private Set<String> namespaces = new HashSet<String>();
        private Set<String> rawNames = new HashSet<String>();

        protected DefaultFilter() {
        }

        public void addNamespaceFilter(String namespaceURI) {
            namespaces.add(namespaceURI);
        }

        public void removeNamespaceFilter(String namespaceURI) {
            namespaces.remove(namespaceURI);
        }

        public String[] getNamespaceFilters() {
            return namespaces.toArray(new String[namespaces.size()]);
        }

        public void addRawNameFilter(String rawName) {
            rawNames.add(rawName);
        }

        public void removeRawNameFilter(String rawName) {
            rawNames.remove(rawName);
        }

        public String[] getRawNameFilters() {
            return rawNames.toArray(new String[rawNames.size()]);
        }

        public boolean isFilteredNamespace(String namespaceURI) {
            return namespaces.contains(namespaceURI);
        }

        public boolean isFilteredRawName(String rawName) {
            return rawNames.contains(rawName);
        }
    }
}
