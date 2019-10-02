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
package org.apache.jackrabbit.vault.packaging.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.impl.PackagePropertiesImpl;

/**
 * Implementing {@link PackageProperties} on top of an existing {@link File} or {@link InputStream}.
 */
public class DefaultPackageProperties extends PackagePropertiesImpl {

    private final Properties properties;

    public static DefaultPackageProperties fromFile(Path path) throws InvalidPropertiesFormatException, IOException {
        return new DefaultPackageProperties(path);
    }

    /**
     * <p> The specified stream remains open after this method returns.
     * @param input
     * @return the package properties being exposed through this input
     * @throws IOException 
     */
    public static DefaultPackageProperties fromInputStream(InputStream input) throws InvalidPropertiesFormatException, IOException {
        return new DefaultPackageProperties(input);
    }

    protected DefaultPackageProperties(InputStream input) throws IOException {
        properties = getPropertiesMap(input);
    }
    
    protected DefaultPackageProperties(Path path) throws IOException {
        try (InputStream fileInput = Files.newInputStream(path)) {
            properties = getPropertiesMap(fileInput);
        }
    }

    @Override
    protected Properties getPropertiesMap() {
        return properties;
    }

    protected static Properties getPropertiesMap(InputStream input) throws InvalidPropertiesFormatException, IOException {
        Properties propertyMap = new Properties();
        propertyMap.loadFromXML(new CloseShieldInputStream(input));
        return propertyMap;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((properties == null) ? 0 : properties.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DefaultPackageProperties other = (DefaultPackageProperties) obj;
        if (properties == null) {
            if (other.properties != null)
                return false;
        } else if (!properties.equals(other.properties))
            return false;
        return true;
    }

    @Override
    public String toString() {
        return "DefaultPackageProperties [properties=" + properties + "]";
    }

    
}
