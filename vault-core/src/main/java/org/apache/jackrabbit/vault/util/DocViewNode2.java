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
package org.apache.jackrabbit.vault.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.jcr.NamespaceException;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.NameResolver;
import org.apache.jackrabbit.spi.commons.conversion.ParsingNameResolver;
import org.apache.jackrabbit.spi.commons.name.NameConstants;
import org.apache.jackrabbit.spi.commons.name.NameFactoryImpl;
import org.apache.jackrabbit.spi.commons.namespace.NamespaceResolver;
import org.apache.jackrabbit.spi.commons.namespace.SessionNamespaceResolver;
import org.apache.jackrabbit.util.ISO9075;
import org.jetbrains.annotations.NotNull;

/**
 * Helper class that represents an immutable JCR node abstraction encapsulating multiple
 * {@link org.apache.jackrabbit.vault.util.DocViewProperty2} properties.
 * @see <a href="https://jackrabbit.apache.org/filevault/docview.html">FileVault Document View Format</a>
 * @since 3.6.0
 */
public class DocViewNode2 {

    private final @NotNull Name name;
    private final int index;
    private final @NotNull Map<Name, DocViewProperty2> properties;

    public DocViewNode2(@NotNull Name name, @NotNull Collection<DocViewProperty2> properties) {
        this(name, 0, properties);
    }

    public DocViewNode2(@NotNull Name name, int index, @NotNull Collection<DocViewProperty2> properties) {
        Objects.requireNonNull(name, "name must not be null");
        this.name = name;
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        this.index = index;
        Objects.requireNonNull(properties, "properties must not be null");
        this.properties = properties.stream().collect(Collectors.toMap(
                DocViewProperty2::getName, 
                Function.identity(),
                (existing, replacement) -> existing,
                LinkedHashMap::new // keep order of properties in the map
                ));
    }

    public @NotNull DocViewNode2 cloneWithDifferentProperties(@NotNull Collection<DocViewProperty2> properties) {
        return new DocViewNode2(name, index, properties);
    }

    @SuppressWarnings("unchecked")
    public static @NotNull DocViewNode2 fromNode(@NotNull Node node, boolean isRoot, boolean useBinaryReferences) throws RepositoryException {
        return fromNode(node, isRoot, JcrUtils.in((Iterator<Property>)node.getProperties()), useBinaryReferences);
    }

    public static @NotNull DocViewNode2 fromNode(@NotNull Node node, boolean isRoot, @NotNull Iterable<Property> properties, boolean useBinaryReferences) throws RepositoryException {
        NameResolver nameResolver = new ParsingNameResolver(NameFactoryImpl.getInstance(), new SessionNamespaceResolver(node.getSession()));
        final Name nodeName;
        if (isRoot) {
            nodeName = NameConstants.JCR_ROOT;
        } else {
            nodeName = nameResolver.getQName(node.getName());
        }
        Collection<DocViewProperty2> docViewProps = new ArrayList<>();
        for (Property property : properties) {
            Name propertyName =  nameResolver.getQName(property.getName());
            boolean sort = propertyName.equals(NameConstants.JCR_MIXINTYPES);
            docViewProps.add(DocViewProperty2.fromProperty(property, sort, useBinaryReferences));
        }
        int index = node.getIndex();
        if (index == 1) {
            index = 0; // we use 0 here, if no index necessary
        }
        return new DocViewNode2(nodeName, index, docViewProps);
    }

    /** 
     * 
     * @return the name of the {@link Node} represented by this class
     */
    public @NotNull Name getName() {
        return name;
    }

    /**
     * 
     * @return 0, except if there is a same-name sibling in the docview. In that case the index gives the 1-based order of the SNS nodes.
     * @see <a href="https://s.apache.org/jcr-2.0-spec/22_Same-Name_Siblings.html#22.2%20Addressing%20Same-Name%20Siblings%20by%20Path">Same-Name Siblings</a>
     */
    public int getIndex() {
        return index;
    }

    /**
     * 
     * @return the name suffixed by an index as outlined in <a href="https://s.apache.org/jcr-2.0-spec/22_Same-Name_Siblings.html#22.2%20Addressing%20Same-Name%20Siblings%20by%20Path">Addressing Same-Name Siblings by Path</a> in case there is a same-name sibling, otherwise the same value as for {@link #getName()}.
     */
    public @NotNull Name getSnsAwareName() {
        if (index > 0) {
            return NameFactoryImpl.getInstance().create(name.getNamespaceURI(), name.getLocalName() + "[" + getIndex() + "]");
        } else {
            return name;
        }
    }

    /**
     * 
     * @return all direct properties of the node represented by this object
     */
    public @NotNull Collection<DocViewProperty2> getProperties() {
        return Collections.unmodifiableCollection(properties.values());
    }

    public @NotNull Optional<DocViewProperty2> getProperty(Name name) {
        return Optional.ofNullable(properties.get(name));
    }

    public boolean hasProperty(Name name) {
        return properties.containsKey(name);
    }

    public @NotNull Collection<String> getPropertyValues(@NotNull Name name) {
        DocViewProperty2 prop = properties.get(name);
        return prop == null ? Collections.emptyList() : prop.getStringValues();
    }

    public @NotNull Optional<String> getPropertyValue(@NotNull Name name) {
        DocViewProperty2 prop = properties.get(name);
        if (prop == null) {
            return Optional.empty();
        } else {
            return prop.getStringValue();
        }
    }

    public @NotNull Optional<String> getPrimaryType() {
        return getPropertyValue(NameConstants.JCR_PRIMARYTYPE);
    }

    public @NotNull Collection<String> getMixinTypes() {
        return getPropertyValues(NameConstants.JCR_MIXINTYPES);
    }

    public @NotNull Optional<String> getIdentifier() {
        return getPropertyValue(NameConstants.JCR_UUID);
    }

    @Override
    public String toString() {
        return "DocViewNode2 [" + (name != null ? "name=" + name + ", " : "") + "index=" + index + ", "
                + (properties != null ? "properties=" + properties : "") + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, name, properties);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DocViewNode2 other = (DocViewNode2) obj;
        return index == other.index && areNamesEqual(name, other.name) && Objects.equals(properties, other.properties);
    }

    static boolean areNamesEqual(@NotNull Name name, @NotNull Name otherName) {
        return Objects.equals(name.getLocalName(), otherName.getLocalName()) && Objects.equals(name.getNamespaceURI(), otherName.getNamespaceURI());
    }

    /**
     * Writes the node's start tag (including the attributes for the properties and optionally the namespace declarations) to the given {@link XMLStreamWriter}.
     * Use the following writer for properly formatting the output according to FileVault standards:
     * {@code FormattingXmlStreamWriter.create(out, new DocViewFormat().getXmlOutputFormat())}.
     * 
     * @param writer the XMLStreamWriter to write to
     * @param nsResolver the namespace resolver to use for retrieving prefixes for namespace URIs of {@link #getName()} and {@link DocViewProperty2#getName()}
     * @param namespacePrefixes the namespace prefixes for which to emit namespace declarations in this node
     * @throws NamespaceException in case no prefix is defined for the namespace URI of a name (either node's or property's)
     * @throws XMLStreamException
     * @since 3.6.2
     */
    public void writeStart(@NotNull XMLStreamWriter writer, @NotNull NamespaceResolver nsResolver, @NotNull Iterable<String> namespacePrefixes) throws NamespaceException, XMLStreamException {
        // sort properties
        Set<DocViewProperty2> props = new TreeSet<>(new DocViewProperty2Comparator(nsResolver));
        props.addAll(properties.values());
        // start element (node)
        // with namespace?
        String namespaceUri = getName().getNamespaceURI();
        String localName = getName().getLocalName();
        if (getIndex() > 1) {
            localName += "[" + getIndex() + "]";
        }
        String encodedLocalName = ISO9075.encode(localName);
       
        if (namespaceUri.length()>0) {
            writer.writeStartElement(nsResolver.getPrefix(namespaceUri), encodedLocalName, namespaceUri);
        } else {
            writer.writeStartElement(encodedLocalName);
        }
        for (String namespacePrefix : namespacePrefixes) {
            if (Name.NS_XML_PREFIX.equals(namespacePrefix)) {
                // skip 'xml' prefix as this would be an illegal namespace declaration
                continue;
            }
            writer.writeNamespace(namespacePrefix, nsResolver.getURI(namespacePrefix));
        }
        for (DocViewProperty2 prop: props) {
            String attributeLocalName = ISO9075.encode(prop.getName().getLocalName());
            String attributeNamespaceUri = prop.getName().getNamespaceURI();
            if (attributeNamespaceUri.length()>0) {
                writer.writeAttribute(nsResolver.getPrefix(attributeNamespaceUri), attributeNamespaceUri, attributeLocalName, 
                        prop.formatValue());
            } else {
                writer.writeAttribute(attributeLocalName, prop.formatValue());
            }
        }
    }

    /**
     * Writes the node's end tag to the given {@link XMLStreamWriter}.
     * Use the following writer for properly formatting the output according to FileVault standards:
     * {@code FormattingXmlStreamWriter.create(out, new DocViewFormat().getXmlOutputFormat())}.
     * @param writer the XMLStreamWriter to write to
     * @throws XMLStreamException
     * @since 3.6.2
     */
    public static void writeEnd(@NotNull XMLStreamWriter writer) throws XMLStreamException {
        writer.writeEndElement();
    }
}