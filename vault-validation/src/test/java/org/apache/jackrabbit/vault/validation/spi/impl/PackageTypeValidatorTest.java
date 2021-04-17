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
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.apache.jackrabbit.vault.util.DocViewNode;
import org.apache.jackrabbit.vault.validation.AnyValidationMessageMatcher;
import org.apache.jackrabbit.vault.validation.ValidationExecutorTest;
import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.util.NodeContextImpl;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * @see <a href="https://issues.apache.org/jira/browse/JCRVLT-170">JCRVLT-170</a>
 */
@RunWith(MockitoJUnitRunner.class)
public class PackageTypeValidatorTest {

    @Mock
    ValidationContext parentContainerContext;
    
    @Mock
    PackageProperties parentContainerProperties;

    @Mock
    WorkspaceFilter filter;
    
    @Mock
    PackageProperties properties;

    private PackageTypeValidator validator;
    
    @Before
    public void setUp() {
        Mockito.when(parentContainerContext.getProperties()).thenReturn(parentContainerProperties);
        Mockito.when(filter.covers(ArgumentMatchers.anyString())).thenReturn(Boolean.TRUE);
    }

    @Test
    public void testMixedPackageType() {
        validator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.ERROR, false, false, false, false, PackageType.MIXED, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, null);
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/apps/some/node", Paths.get(""), Paths.get(""))), AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.validate(filter), AnyValidationMessageMatcher.noValidationInCollection());
        ValidationExecutorTest.assertViolation(validator.validate(properties), new ValidationMessage(ValidationMessageSeverity.WARN, PackageTypeValidator.MESSAGE_NO_PACKAGE_TYPE_SET));
        // test mixed package type
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.MIXED);
        ValidationExecutorTest.assertViolation(validator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_LEGACY_TYPE, PackageType.MIXED.toString())));
        // validate sling:OsgiConfig node
        DocViewNode node = new DocViewNode("someconfigpid", "someconfigpid", null, Collections.emptyMap(), null, "sling:OsgiConfig");
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/apps/config/someconfigpid", Paths.get(""), Paths.get(""))), AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.validate(node, new NodeContextImpl("/apps/config/someconfigpid", Paths.get(""), Paths.get("")), true), AnyValidationMessageMatcher.noValidationInCollection());
        
        // validate sub packages of type Content
        Mockito.when(parentContainerProperties.getPackageType()).thenReturn(PackageType.MIXED);
        PackageTypeValidator subPackageValidator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, false, PackageType.CONTENT, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, parentContainerContext);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.CONTENT);
        MatcherAssert.assertThat(subPackageValidator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
        // validate sub packages of type Application
        subPackageValidator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, false, PackageType.APPLICATION, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, parentContainerContext);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.APPLICATION);
        MatcherAssert.assertThat(subPackageValidator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testContentPackageType() {
        validator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, false, PackageType.CONTENT, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, null);
        ValidationExecutorTest.assertViolation(validator.validate(new NodeContextImpl("/apps/some/node", Paths.get(""), Paths.get(""))), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_APP_CONTENT, PackageType.CONTENT, "'apps' or 'libs'")));
        ValidationExecutorTest.assertViolation(validator.validate(new NodeContextImpl("/apps", Paths.get(""), Paths.get(""))), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_APP_CONTENT, PackageType.CONTENT, "'apps' or 'libs'")));
        ValidationExecutorTest.assertViolation(validator.validate(new NodeContextImpl("/libs/some/node", Paths.get(""), Paths.get(""))), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_APP_CONTENT, PackageType.CONTENT, "'apps' or 'libs'")));
        ValidationExecutorTest.assertViolation(validator.validate(new NodeContextImpl("/libs",  Paths.get(""), Paths.get(""))), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_APP_CONTENT, PackageType.CONTENT, "'apps' or 'libs'")));
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/content/is/allowed", Paths.get(""), Paths.get(""))), AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/etc/packages/some/sub/package.zip", Paths.get(""), Paths.get(""))), AnyValidationMessageMatcher.noValidationInCollection());
        
        ValidationExecutorTest.assertViolation(validator.validate(new NodeContextImpl("/apps/install/muybundle-123.jar", Paths.get(""), Paths.get(""))), 
                new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_APP_CONTENT, PackageType.CONTENT, "'apps' or 'libs'")),
                new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_OSGI_BUNDLE_OR_CONFIG, PackageType.CONTENT, "/apps/install/muybundle-123.jar"))
                );
        MatcherAssert.assertThat(validator.validate(filter), AnyValidationMessageMatcher.noValidationInCollection());
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.CONTENT);
        MatcherAssert.assertThat(validator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
        Mockito.when(parentContainerProperties.getPackageType()).thenReturn(PackageType.CONTENT);
        // validate sub packages of type Content
        PackageTypeValidator subPackageValidator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, false, PackageType.CONTENT, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, parentContainerContext);
        MatcherAssert.assertThat(subPackageValidator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
        // validate sling:OsgiConfig node
        DocViewNode node = new DocViewNode("someconfigpid", "someconfigpid", null, Collections.emptyMap(), null, "sling:OsgiConfig");
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/content/config/someconfigpid", Paths.get(""), Paths.get(""))), AnyValidationMessageMatcher.noValidationInCollection());
        ValidationExecutorTest.assertViolation(
                validator.validate(node, new NodeContextImpl("/content/config/someconfigpid", Paths.get(""), Paths.get("")), true),
                new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_OSGI_BUNDLE_OR_CONFIG, PackageType.CONTENT, "/content/config/someconfigpid")));
        
        // validate sub packages of type Application
        subPackageValidator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, false, PackageType.APPLICATION, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, parentContainerContext);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.APPLICATION);
        ValidationExecutorTest.assertViolation(subPackageValidator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_UNSUPPORTED_SUB_PACKAGE_OF_TYPE, PackageType.CONTENT, PackageType.CONTENT, PackageType.APPLICATION)));
    }

    @Test
    public void testContainerPackageType() {
        validator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, false, PackageType.CONTAINER, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, null);
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/apps/some/node", Paths.get("some", "file1"), Paths.get("base"))), AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/libs/some/node", Paths.get("some", "file2"), Paths.get("base"))), AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/content/some/node", Paths.get("some", "file3"), Paths.get("base"))), AnyValidationMessageMatcher.noValidationInCollection());
        ValidationExecutorTest.assertViolation(validator.done(), 
                new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_NO_OSGI_BUNDLE_OR_CONFIG_OR_SUB_PACKAGE, PackageType.CONTAINER), "/apps/some/node", Paths.get("some", "file1"), Paths.get("base"), null),
                new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_NO_OSGI_BUNDLE_OR_CONFIG_OR_SUB_PACKAGE, PackageType.CONTAINER), "/libs/some/node", Paths.get("some", "file2"), Paths.get("base"), null),
                new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_NO_OSGI_BUNDLE_OR_CONFIG_OR_SUB_PACKAGE, PackageType.CONTAINER), "/content/some/node", Paths.get("some", "file3"), Paths.get("base"), null)
                );
        validator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, false, PackageType.CONTAINER, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, null);
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/apps/install.runmode", Paths.get("runmode"), Paths.get("base"))), AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/apps/install.runmode/somebundle.jar", Paths.get("runmode", "somebundle.jar"), Paths.get("base"))), AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/etc/packages/some/sub/package.zip", Paths.get(""), Paths.get(""))), AnyValidationMessageMatcher.noValidationInCollection());
        // this node is outside the allowed parents
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/apps/install.runmode2", Paths.get("apps", "install", "runmode2"), Paths.get("base"))), AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.validate(filter), AnyValidationMessageMatcher.noValidationInCollection());
        ValidationExecutorTest.assertViolation(validator.done(), 
                new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_NO_OSGI_BUNDLE_OR_CONFIG_OR_SUB_PACKAGE, PackageType.CONTAINER), "/apps/install.runmode2", Paths.get("apps", "install", "runmode2"), Paths.get("base"), null)
        );
        
        validator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, false, PackageType.CONTAINER, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, null);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.CONTAINER);
        MatcherAssert.assertThat(validator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());

        Mockito.when(parentContainerProperties.getPackageType()).thenReturn(PackageType.CONTAINER);
        
        // validate sub packages of type Mixed
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.MIXED);
        PackageTypeValidator subPackageValidator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, false, PackageType.MIXED, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, parentContainerContext);
        ValidationExecutorTest.assertViolation(subPackageValidator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_UNSUPPORTED_SUB_PACKAGE_OF_TYPE, PackageType.CONTAINER, StringUtils.join(new String[] {PackageType.APPLICATION.toString(),PackageType.CONTENT.toString(),PackageType.CONTAINER.toString()}, ", "),  PackageType.MIXED)));

        // validate sub packages of type Content
        subPackageValidator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, false,  PackageType.CONTENT, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, parentContainerContext);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.CONTENT);
        MatcherAssert.assertThat(subPackageValidator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());

        // validate sub packages of type Container
        subPackageValidator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, false, PackageType.CONTAINER, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, parentContainerContext);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.APPLICATION);
        MatcherAssert.assertThat(subPackageValidator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());

        // validate sub packages of type Application
        subPackageValidator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, false, PackageType.APPLICATION, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, parentContainerContext);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.APPLICATION);
        MatcherAssert.assertThat(subPackageValidator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());

        // validate sling:OsgiConfig node
        DocViewNode node = new DocViewNode("someconfigpid", "someconfigpid", null, Collections.emptyMap(), null, "sling:OsgiConfig");
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/apps/config/someconfigpid", Paths.get(""), Paths.get(""))), AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.validate(node, new NodeContextImpl("/apps/config/someconfigpid", Paths.get(""), Paths.get("")), true), AnyValidationMessageMatcher.noValidationInCollection());
        
        // make sure no dependencies
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.CONTAINER);
        Mockito.when(properties.getDependencies()).thenReturn(new Dependency[] { Dependency.fromString("some/group:artifact:1.0.0") });
        ValidationExecutorTest.assertViolation(validator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_DEPENDENCY, PackageType.CONTAINER, "some/group:artifact:1.0.0")));
    }

    @Test
    public void testApplicationPackageType() throws IOException, ConfigurationException {
        validator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, false, PackageType.APPLICATION, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, null);
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/apps/some/script", Paths.get(""), Paths.get(""))), AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/libs",  Paths.get(""), Paths.get(""))), AnyValidationMessageMatcher.noValidationInCollection());
        
        ValidationExecutorTest.assertViolation(validator.validate(new NodeContextImpl("/content/some/node",  Paths.get(""), Paths.get(""))), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_NO_APP_CONTENT_FOUND, PackageType.APPLICATION, "'apps' or 'libs'")));
        ValidationExecutorTest.assertViolation(validator.validate(new NodeContextImpl("/etc/something",  Paths.get(""), Paths.get(""))), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_NO_APP_CONTENT_FOUND, PackageType.APPLICATION, "'apps' or 'libs'")));
        ValidationExecutorTest.assertViolation(validator.validate(new NodeContextImpl("/conf/something",  Paths.get(""), Paths.get(""))), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_NO_APP_CONTENT_FOUND, PackageType.APPLICATION, "'apps' or 'libs'")));
        
        // no bundles/sub packages
        ValidationExecutorTest.assertViolation(validator.validate(new NodeContextImpl("/apps/install/mybundle.jar", Paths.get(""), Paths.get(""))), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_OSGI_BUNDLE_OR_CONFIG, PackageType.APPLICATION, "/apps/install/mybundle.jar")));
        ValidationExecutorTest.assertViolation(validator.validate(new NodeContextImpl("/apps/install/config.cfg", Paths.get(""), Paths.get(""))), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_OSGI_BUNDLE_OR_CONFIG, PackageType.APPLICATION, "/apps/install/config.cfg")));
        
        // mutable node
        ValidationExecutorTest.assertViolation(validator.validate(new NodeContextImpl("/oak:index/testindex", Paths.get(""), Paths.get(""))), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_NO_APP_CONTENT_FOUND, PackageType.APPLICATION, "'apps' or 'libs'")));
        
        // no hooks
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.APPLICATION);
        MatcherAssert.assertThat(validator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());

        // validation of regular properties should not lead to issues
        MatcherAssert.assertThat(validator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());

        // with hooks
        Map<String, String> hooks = Collections.singletonMap("key", "com.example.ExternalHook");
        Mockito.when(properties.getExternalHooks()).thenReturn(hooks);
        ValidationExecutorTest.assertViolation(validator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_PACKAGE_HOOKS, PackageType.APPLICATION, hooks)));
        ValidationExecutorTest.assertViolation(validator.validateMetaInfPath(Paths.get("vault", "hooks", "myhook.jar"), Paths.get(""), false), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_PACKAGE_HOOKS, PackageType.APPLICATION, Paths.get("vault", "hooks", "myhook.jar"))));
        
        // with regular filter
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream("/simple-filter.xml")) {
            filter.load(input);
        }
        MatcherAssert.assertThat(validator.validate(filter), AnyValidationMessageMatcher.noValidationInCollection());
        // with filters with include/exclude
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml")) {
            filter.load(input);
        }
        ValidationExecutorTest.assertViolation(validator.validate(filter), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_FILTER_HAS_INCLUDE_EXCLUDES, PackageType.APPLICATION)));
        
        // validate sling:OsgiConfig node
        DocViewNode node = new DocViewNode("someconfigpid", "someconfigpid", null, Collections.emptyMap(), null, "sling:OsgiConfig");
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/apps/config/someconfigpid", Paths.get(""), Paths.get(""))), AnyValidationMessageMatcher.noValidationInCollection());
        ValidationExecutorTest.assertViolation(
                validator.validate(node, new NodeContextImpl("/apps/config/someconfigpid", Paths.get(""), Paths.get("")), true),
                new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_OSGI_BUNDLE_OR_CONFIG, PackageType.APPLICATION, "/apps/config/someconfigpid")));

        // validate sub packages of type Content
        Mockito.when(parentContainerProperties.getPackageType()).thenReturn(PackageType.APPLICATION);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.CONTENT);
        PackageTypeValidator subPackageValidator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, false, PackageType.CONTENT, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, parentContainerContext);
        ValidationExecutorTest.assertViolation(subPackageValidator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_UNSUPPORTED_SUB_PACKAGE, PackageType.APPLICATION)));
        // validate sub packages of type Application
        subPackageValidator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, false, PackageType.CONTENT, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, parentContainerContext);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.APPLICATION);
        Mockito.when(properties.getExternalHooks()).thenReturn(Collections.emptyMap());
        ValidationExecutorTest.assertViolation(subPackageValidator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_UNSUPPORTED_SUB_PACKAGE, PackageType.APPLICATION)));
    }

    @Test
    public void testApplicationPackageTypeWithAllowedOakIndex() throws IOException, ConfigurationException {
        Set<String> immutableRootNodeNames = new HashSet<>(Arrays.asList("oak:index"));
        validator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, false, PackageType.APPLICATION, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, immutableRootNodeNames, null);
        MatcherAssert.assertThat(validator.validate(new NodeContextImpl("/oak:index/myindex", Paths.get(""), Paths.get(""))), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testApplicationPackageTypeWithAllowedHook() throws IOException, ConfigurationException {
        validator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, true, PackageType.APPLICATION, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, null);
        // with hooks
        Map<String, String> hooks = Collections.singletonMap("key", "com.example.ExternalHook");
        Mockito.when(properties.getExternalHooks()).thenReturn(hooks);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.APPLICATION);
        MatcherAssert.assertThat(validator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
        MatcherAssert.assertThat(validator.validateMetaInfPath(Paths.get("vault", "hooks", "myhook.jar"), Paths.get(""), false), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testApplicationPackageTypeWithAllowedComplexFilters() throws IOException, ConfigurationException {
        validator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, true, false, PackageType.APPLICATION, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, null);
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        // with filters with include/exclude
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml")) {
            filter.load(input);
        }
        MatcherAssert.assertThat(validator.validate(filter), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testMutableContentProhibited() {
        validator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.INFO, ValidationMessageSeverity.INFO, true, false, false, false, PackageType.APPLICATION, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, null);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.MIXED);
        ValidationExecutorTest.assertViolation(validator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_PROHIBITED_MUTABLE_PACKAGE_TYPE, PackageType.MIXED)));
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.CONTENT);
        ValidationExecutorTest.assertViolation(validator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_PROHIBITED_MUTABLE_PACKAGE_TYPE, PackageType.CONTENT)));
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.APPLICATION);
        MatcherAssert.assertThat(validator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.CONTAINER);
        MatcherAssert.assertThat(validator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testImmutableContentProhibited() {
        validator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.INFO, ValidationMessageSeverity.INFO, false, true, false, false, PackageType.APPLICATION, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, null);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.MIXED);
        ValidationExecutorTest.assertViolation(validator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_PROHIBITED_IMMUTABLE_PACKAGE_TYPE, PackageType.MIXED)));
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.CONTENT);
        MatcherAssert.assertThat(validator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.APPLICATION);
        ValidationExecutorTest.assertViolation(validator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_PROHIBITED_IMMUTABLE_PACKAGE_TYPE, PackageType.APPLICATION)));
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.CONTAINER);
        ValidationExecutorTest.assertViolation(validator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_PROHIBITED_IMMUTABLE_PACKAGE_TYPE, PackageType.CONTAINER)));
    }

    @Test
    public void testIsOsgiBundleOrConfigurationFile() {
        validator = new PackageTypeValidator(filter, ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, ValidationMessageSeverity.INFO, false, false, false, false, PackageType.CONTENT, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_ADDITIONAL_JCR_INSTALLER_FILE_NODE_PATH_REGEX, PackageTypeValidatorFactory.DEFAULT_IMMUTABLE_ROOT_NODE_NAMES, null);
        Assert.assertTrue(validator.isOsgiBundleOrConfiguration("/apps/install/mybundle-123.jar", true));
        Assert.assertTrue(validator.isOsgiBundleOrConfiguration("/apps/config/mmyconfig-123.cfg.json", true));
        Assert.assertTrue(validator.isOsgiBundleOrConfiguration("/apps/config/mmyconfig-123.cfg", true));
        
        Assert.assertFalse(validator.isOsgiBundleOrConfiguration("/apps/config/mmyconfig-123.json", true));
        Assert.assertTrue(validator.isOsgiBundleOrConfiguration("/apps/config/mmyconfig-123.config", true));
        Assert.assertTrue(validator.isOsgiBundleOrConfiguration("/apps/level2/config/mybundle-123.jar", true));
        // osgi:configNodes are detected on a different level
        Assert.assertFalse(validator.isOsgiBundleOrConfiguration("/apps/level2/config/myconfig-123.xml", true));
        Assert.assertTrue(validator.isOsgiBundleOrConfiguration("/apps/level2/config/myconfig-123.xml", false));
        Assert.assertTrue(validator.isOsgiBundleOrConfiguration("/apps/install.runmode1.runmode2/mybundle-123.jar", true)); // with run modes
        Assert.assertTrue(validator.isOsgiBundleOrConfiguration("/apps/install.runmode1.runmode2/12/mybundle-123.jar", true)); // with start level
        // below level 4
        Assert.assertFalse(validator.isOsgiBundleOrConfiguration("/apps/level2/level3/level4/l5/install/mybundle-123.jar", true));
        
    }
}
