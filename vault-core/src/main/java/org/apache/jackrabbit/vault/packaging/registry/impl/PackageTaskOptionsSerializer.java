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

import java.util.function.Consumer;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.io.AccessControlHandling;
import org.apache.jackrabbit.vault.fs.io.ImportOptions;
import org.apache.jackrabbit.vault.packaging.DependencyHandling;
import org.apache.jackrabbit.vault.packaging.registry.PackageTaskOptions;
import org.apache.jackrabbit.vault.packaging.registry.taskoption.ImportOptionsPackageTaskOption;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class PackageTaskOptionsSerializer {

    private static final String TAG_OPTIONS = "options";
    private static final String ATTR_TYPE = "type";
    private static final String TAG_AC_HANDLING = "acHandling";
    private static final String TAG_IS_STRICT = "isStrict";
    private static final String TAG_AUTO_SAVE_THRESHOLD = "autoSaveThreshold";
    private static final String TAG_DEPENDENCY_HANDLING = "dependencyHandling";
    private static final String TAG_CUG_HANDLING = "cugHandling";
    private static final String TAG_NON_RECURSIVE = "nonRecursive";
    private static final String TAG_DRY_RUN = "dryRun";
    private static final String TAG_IMPORT_MODE = "importMode";
    
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
                saveImportOptions(writer, (ImportOptionsPackageTaskOption) options);
                break;
        }
        writer.writeEndElement();
    }

    public void saveImportOptions(XMLStreamWriter writer, ImportOptionsPackageTaskOption options) throws XMLStreamException {
        ImportOptions importOptions = options.getImportOptions();
        writeOption(writer, TAG_IS_STRICT, Boolean.class, importOptions.isStrict());
        writeOption(writer, TAG_AC_HANDLING, AccessControlHandling.class, importOptions.getAccessControlHandling());
        writeOption(writer, TAG_CUG_HANDLING, AccessControlHandling.class, importOptions.getCugHandling());
        writeOption(writer, TAG_AUTO_SAVE_THRESHOLD, Integer.class, importOptions.getAutoSaveThreshold());
        writeOption(writer, TAG_DEPENDENCY_HANDLING, DependencyHandling.class, importOptions.getDependencyHandling());
        writeOption(writer, TAG_NON_RECURSIVE, Boolean.class, importOptions.isNonRecursive());
        writeOption(writer, TAG_DRY_RUN, Boolean.class, importOptions.isDryRun());
        writeOption(writer, TAG_IMPORT_MODE, ImportMode.class, importOptions.getImportMode());
    }

    public ImportOptionsPackageTaskOption loadImportOptions(Element element) {
        ImportOptions options = new ImportOptions();
        readOption(element, TAG_IS_STRICT, Boolean.class, options::setStrict);
        readOption(element, TAG_AC_HANDLING, AccessControlHandling.class, options::setAccessControlHandling);
        readOption(element, TAG_CUG_HANDLING, AccessControlHandling.class, options::setCugHandling);
        readOption(element, TAG_AUTO_SAVE_THRESHOLD, Integer.class, options::setAutoSaveThreshold);
        readOption(element, TAG_DEPENDENCY_HANDLING, DependencyHandling.class, options::setDependencyHandling);
        readOption(element, TAG_NON_RECURSIVE, Boolean.class, options::setNonRecursive);
        readOption(element, TAG_DRY_RUN, Boolean.class,  options::setDryRun);
        readOption(element, TAG_IMPORT_MODE, ImportMode.class,  options::setImportMode);
        return new ImportOptionsPackageTaskOption(options);
    }

    private <T> void writeOption(XMLStreamWriter writer, String tagElement, Class<T> type, T value) throws XMLStreamException {
        if (value != null) {
            writer.writeStartElement(tagElement);
            if (type.equals(Boolean.class)) {
                writer.writeCharacters(Boolean.toString((Boolean)value));
            } else if (type.isEnum()) {
                writer.writeCharacters(((Enum<?>)value).name());
            } else if (type.equals(Integer.class)) {
                writer.writeCharacters(Integer.toString((Integer)value));
            }
            writer.writeEndElement();
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <T> void readOption(Element element, String tagName, Class<T> type, Consumer<T> consumer) {
        Element childElement = getFirstElementByTagName(tagName, element);
        if (childElement != null) {
            if (type.equals(Boolean.class)) {
                consumer.accept((T)Boolean.valueOf(childElement.getTextContent()));
            } else if (type.isEnum()) {
                consumer.accept((T)Enum.valueOf((Class)type, childElement.getTextContent()));
            } else if (type.equals(Integer.class)) {
                consumer.accept((T)Integer.valueOf(childElement.getTextContent()));
            }
        }
    }

    private static final Element getFirstElementByTagName(String name, Element element) {
        NodeList nodeList = element.getElementsByTagName(name);
        if (nodeList.getLength() == 0) {
            return null;
        }
        return (Element)nodeList.item(0);
    }
}
