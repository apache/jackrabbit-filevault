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
package org.apache.jackrabbit.vault.packaging.registry.impl;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.registry.PackageTaskOptions;
import org.apache.jackrabbit.vault.packaging.registry.taskoption.ImportOptionsPackageTaskOption;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class PackageTaskOptionsSerializer {

    private static final String TAG_OPTIONS = "options";
    private static final String ATTR_TYPE = "type";
    private static final String TAG_ACHANDLING = "acHandling";
    private static final String TAG_IS_STRICT = "isStrict";
    
    enum Type {
        ImportOptions;

        static Type fromClass(PackageTaskOptions options) {
            if (options instanceof ImportOptionsPackageTaskOption) {
                return ImportOptions;
            } else {
                throw new IllegalStateException("Unsupported task option class " + options.getClass());
            }
        }
    }

    public PackageTaskOptions load(Element element) {
        Element childElement = getFirstElementByTagName(TAG_OPTIONS, element);
        if (childElement == null) {
            return null;
        }
        final PackageTaskOptions options;
        switch (Type.valueOf(childElement.getAttribute(ATTR_TYPE))) {
            case ImportOptions:
                options = loadImportOptions(childElement);
                break;
            default:
                throw new IllegalArgumentException("Wrong type used");
        }
        return options;
    }

    public void save(XMLStreamWriter writer, PackageTaskOptions options) throws XMLStreamException {
        if (options == null) {
            return;
        }
        writer.writeStartElement(TAG_OPTIONS);
        Type type = Type.fromClass(options);
        writer.writeAttribute(ATTR_TYPE, type.name());
        switch (type) {
            case ImportOptions:
                save(writer, (ImportOptionsPackageTaskOption) options);
                break;
        }
        writer.writeEndElement();
    }

    public void save(XMLStreamWriter writer, ImportOptionsPackageTaskOption options) throws XMLStreamException {
        writer.writeStartElement(TAG_IS_STRICT);
        writer.writeCharacters(Boolean.toString(options.getImportOptions().isStrict()));
        writer.writeEndElement();
        AccessControlHandling acHandling = options.getImportOptions().getAccessControlHandling();
        if (acHandling != null) {
            writer.writeStartElement(TAG_ACHANDLING);
            writer.writeCharacters(acHandling.toString());
            writer.writeEndElement();
        }
    }

    public ImportOptionsPackageTaskOption loadImportOptions(Element element) {
        ImportOptions options = new ImportOptions();
        Element childElement = getFirstElementByTagName(TAG_IS_STRICT, element);
        if (childElement != null) {
            options.setStrict(Boolean.parseBoolean(childElement.getTextContent()));
        }
        childElement = getFirstElementByTagName(TAG_ACHANDLING, element);
        if (childElement != null) {
            options.setAccessControlHandling(AccessControlHandling.valueOf(childElement.getTextContent()));
        }
        return new ImportOptionsPackageTaskOption(options);
    }

    private static final Element getFirstElementByTagName(String name, Element element) {
        NodeList nodeList = element.getElementsByTagName(name);
        if (nodeList.getLength() == 0) {
            return null;
        }
        return (Element)nodeList.item(0);
    }
}
