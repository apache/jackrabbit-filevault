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
package org.apache.jackrabbit.vault.validation.spi.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.LinkedList;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.PackageInfo;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.packaging.impl.DefaultPackageInfo;
import org.apache.jackrabbit.vault.validation.AnyValidationMessageMatcher;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.xml.sax.SAXException;

@RunWith(MockitoJUnitRunner.class)
public class DependencyValidatorTest {

    private DependencyValidator validator;

    @Mock
    private PackageProperties properties;

    private Collection<PackageInfo> resolvedPackageInfos;

    @Before
    public void setUp() throws ParserConfigurationException, SAXException {
        Dependency[] dependencies = Dependency.fromString("group1:package1:[0,1)", "group2:package2:(2,3)");
        Mockito.when(properties.getDependencies()).thenReturn(dependencies);
        resolvedPackageInfos = new LinkedList<>();
        validator = new DependencyValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.ERROR, resolvedPackageInfos);
    }

    @Test
    public void testWithResolvedNonOverlappingDependency() {
        resolvedPackageInfos.add(getPackageInfo("group1:package1:0.1", "/filter.xml", null));
        resolvedPackageInfos.add(getPackageInfo("group2:package2:2.9", "/simple-package/META-INF/vault/filter.xml", null));
        
        MatcherAssert.assertThat(validator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testValidateWithUnresolvedDependencies() {
        resolvedPackageInfos.add(getPackageInfo("group1:package1:0.1", "/filter.xml", null));
        MatcherAssert.assertThat(validator.validate(properties), 
                Matchers.contains(new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(String.format(DependencyValidator.MESSAGE_UNRESOLVED_DEPENDENCY, "group2:package2:(2,3)")))));
    }

    @Test
    public void testValidateWithDependenciesWithOverlappingFilterRoots() {
        resolvedPackageInfos.add(getPackageInfo("group1:package1:0.1", "/simple-package/META-INF/vault/filter.xml", null));
        resolvedPackageInfos.add(getPackageInfo("group2:package2:2.9", "/simple-package/META-INF/vault/filter.xml", null));
        MatcherAssert.assertThat(validator.validate(properties), 
                Matchers.contains(new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(String.format(String.format(DependencyValidator.MESSAGE_DEPENDENCIES_WITH_OVERLAPPING_FILTERS,
                        "group2:package2:2.9", "/etc/project1", "group1:package1:0.1"))))));
    }

    private PackageInfo getPackageInfo(String packageId, String filterResourceName, PackageType packageType) {
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream(filterResourceName)) {
            if (input == null) {
                throw new IllegalArgumentException("Could not find resource file at " + filterResourceName);
            }
            filter.load(input);
        } catch (IOException | ConfigurationException e) {
            throw new IllegalArgumentException("Could not load filter file");
        }
        return new DefaultPackageInfo(PackageId.fromString(packageId), filter, packageType);
    }
}
