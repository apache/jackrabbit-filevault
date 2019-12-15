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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

public class PackagePropertiesImplTest {

    class SimplePackageProperties extends PackagePropertiesImpl {
        final Properties properties;

        public SimplePackageProperties(Map<String, String> properties) {
            this.properties = new Properties();
            this.properties.putAll(properties);
        }
        
        @Override
        protected Properties getPropertiesMap() {
            return properties;
        }
    }

    @Test
    public void testGetDependenciesLocations() throws URISyntaxException {
        PackageProperties packageProperties = new SimplePackageProperties(Collections.singletonMap(PackageProperties.NAME_DEPENDENCIES_LOCATIONS, "group1:name1:1.0=maven:com.example.mygroupid:myartifactId:1.0.0:zip"));
        Assert.assertThat(packageProperties.getDependenciesLocations(), Matchers.equalTo(Collections.singletonMap(PackageId.fromString("group1:name1:1.0"), new URI("maven:com.example.mygroupid:myartifactId:1.0.0:zip"))));
        
        packageProperties = new SimplePackageProperties(Collections.singletonMap(PackageProperties.NAME_DEPENDENCIES_LOCATIONS, "group1:name1:1.0=maven:com.example.mygroupid:myartifactId:1.0.0:zip,group2:name2:2.0=maven:com.example.mygroupid2:myartifactId2:2.0.0:zip,"));
        Map<PackageId, URI> expectedDependenciesLocations = new HashMap<>();
        expectedDependenciesLocations.put(PackageId.fromString("group1:name1:1.0"), new URI("maven:com.example.mygroupid:myartifactId:1.0.0:zip"));
        expectedDependenciesLocations.put(PackageId.fromString("group2:name2:2.0"), new URI("maven:com.example.mygroupid2:myartifactId2:2.0.0:zip"));
        
        Assert.assertThat(packageProperties.getDependenciesLocations(), Matchers.equalTo(expectedDependenciesLocations));
    }

    @Test
    public void testGetInvalidDependenciesLocations() throws URISyntaxException {
        PackageProperties packageProperties = new SimplePackageProperties(Collections.singletonMap(PackageProperties.NAME_DEPENDENCIES_LOCATIONS, "group1:name1:1.0maven:com.example.mygroupid:myartifactId:1.0.0:zip"));
        // no key=value format
        Assert.assertThat(packageProperties.getDependenciesLocations(), Matchers.equalTo(Collections.emptyMap()));
        // invalid key (pid)
        packageProperties = new SimplePackageProperties(Collections.singletonMap(PackageProperties.NAME_DEPENDENCIES_LOCATIONS, "=maven:com.example.mygroupid:myartifactId:1.0.0:zip"));
        Assert.assertThat(packageProperties.getDependenciesLocations(), Matchers.equalTo(Collections.emptyMap()));
        // invalid value (uri)
        packageProperties = new SimplePackageProperties(Collections.singletonMap(PackageProperties.NAME_DEPENDENCIES_LOCATIONS, "group1:name1:1.0=maven:invalid uri"));
        Assert.assertThat(packageProperties.getDependenciesLocations(), Matchers.equalTo(Collections.emptyMap()));
    }

    @Test
    public void testGetNotSetDependenciesLocations() throws URISyntaxException {
        PackageProperties packageProperties = new SimplePackageProperties(Collections.emptyMap());
        Assert.assertThat(packageProperties.getDependenciesLocations(), Matchers.equalTo(Collections.emptyMap()));
    }
}
