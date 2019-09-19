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
package org.apache.jackrabbit.filevault.validation.spi.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

import org.apache.jackrabbit.filevault.validation.AnyValidationMessageMatcher;
import org.apache.jackrabbit.filevault.validation.ValidationExecutorTest;
import org.apache.jackrabbit.filevault.validation.spi.ValidationContext;
import org.apache.jackrabbit.filevault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.filevault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.PackageProperties;
import org.apache.jackrabbit.vault.packaging.PackageType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
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
    WorkspaceFilter filter;
    
    @Mock
    PackageProperties properties;

    private PackageTypeValidator validator;
    
    @Before
    public void setUp() {
    }

    @Test
    public void testNullPackageType() {
        validator = new PackageTypeValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, null, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, null);
        Assert.assertThat(validator.validate("/apps/some/node"), AnyValidationMessageMatcher.noValidationInCollection());
        Assert.assertThat(validator.validate(filter), AnyValidationMessageMatcher.noValidationInCollection());
        ValidationExecutorTest.assertViolation(validator.validate(properties), new ValidationMessage(ValidationMessageSeverity.WARN, PackageTypeValidator.MESSAGE_NO_PACKAGE_TYPE_SET));
        // validate sub packages of type null
        PackageTypeValidator subPackageValidator = new PackageTypeValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, PackageType.CONTENT, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, parentContainerContext);
        ValidationExecutorTest.assertViolation(validator.validate(properties), new ValidationMessage(ValidationMessageSeverity.WARN, PackageTypeValidator.MESSAGE_NO_PACKAGE_TYPE_SET));
        // validate sub packages of type Content
        subPackageValidator = new PackageTypeValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, null, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, parentContainerContext);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.CONTENT);
        Assert.assertThat(subPackageValidator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
        // validate sub packages of type Application
        subPackageValidator = new PackageTypeValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, PackageType.APPLICATION, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, parentContainerContext);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.APPLICATION);
        Assert.assertThat(subPackageValidator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testMixedPackageType() {
        validator = new PackageTypeValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, PackageType.MIXED, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, null);
        Assert.assertThat(validator.validate("/apps/some/node"), AnyValidationMessageMatcher.noValidationInCollection());
        Assert.assertThat(validator.validate(filter), AnyValidationMessageMatcher.noValidationInCollection());
        ValidationExecutorTest.assertViolation(validator.validate(properties), new ValidationMessage(ValidationMessageSeverity.WARN, PackageTypeValidator.MESSAGE_NO_PACKAGE_TYPE_SET));
        // test mixed package type
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.MIXED);
        ValidationExecutorTest.assertViolation(validator.validate(properties), new ValidationMessage(ValidationMessageSeverity.WARN, String.format(PackageTypeValidator.MESSAGE_LEGACY_TYPE, PackageType.MIXED.toString())));
        
        // validate sub packages of type Content
        Mockito.when(parentContainerContext.getPackageType()).thenReturn(PackageType.MIXED);
        PackageTypeValidator subPackageValidator = new PackageTypeValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, PackageType.CONTENT, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, parentContainerContext);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.CONTENT);
        Assert.assertThat(subPackageValidator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
        // validate sub packages of type Application
        subPackageValidator = new PackageTypeValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, PackageType.APPLICATION, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, parentContainerContext);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.APPLICATION);
        Assert.assertThat(subPackageValidator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
    }

    @Test
    public void testIsOsgiBundleOrConfiguration() {
        validator = new PackageTypeValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, PackageType.CONTENT, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, null);
        Assert.assertTrue(validator.isOsgiBundleOrConfiguration("/apps/install/mybundle-123.jar"));
        Assert.assertTrue(validator.isOsgiBundleOrConfiguration("/apps/config/mmyconfig-123.cfg.json"));
        Assert.assertTrue(validator.isOsgiBundleOrConfiguration("/apps/config/mmyconfig-123.cfg"));
        
        Assert.assertFalse(validator.isOsgiBundleOrConfiguration("/apps/config/mmyconfig-123.json"));
        Assert.assertTrue(validator.isOsgiBundleOrConfiguration("/apps/config/mmyconfig-123.config"));
        Assert.assertTrue(validator.isOsgiBundleOrConfiguration("/apps/level2/config/mybundle-123.jar"));
        // osgi:configNodes may have arbitrary names with extension .xml
        Assert.assertTrue(validator.isOsgiBundleOrConfiguration("/apps/level2/config/myconfig-123.xml"));
        Assert.assertTrue(validator.isOsgiBundleOrConfiguration("/apps/install.runmode1.runmode2/mybundle-123.jar")); // with run modes
        Assert.assertTrue(validator.isOsgiBundleOrConfiguration("/apps/install.runmode1.runmode2/12/mybundle-123.jar")); // with start level
        // below level 4
        Assert.assertFalse(validator.isOsgiBundleOrConfiguration("/apps/level2/level3/level4/l5/install/mybundle-123.jar"));
        
    }

    @Test
    public void testContentPackageType() {
        validator = new PackageTypeValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, PackageType.CONTENT, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, null);
        ValidationExecutorTest.assertViolation(validator.validate("/apps/some/node"), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_APP_CONTENT, PackageType.CONTENT, "/apps/some/node")));
        ValidationExecutorTest.assertViolation(validator.validate("/apps"), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_APP_CONTENT, PackageType.CONTENT, "/apps")));
        ValidationExecutorTest.assertViolation(validator.validate("/libs/some/node"), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_APP_CONTENT, PackageType.CONTENT, "/libs/some/node")));
        ValidationExecutorTest.assertViolation(validator.validate("/libs"), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_APP_CONTENT, PackageType.CONTENT, "/libs")));
        Assert.assertThat(validator.validate("/content/is/allowed"), AnyValidationMessageMatcher.noValidationInCollection());
        Assert.assertThat(validator.validate("/etc/packages/some/sub/package.zip"), AnyValidationMessageMatcher.noValidationInCollection());
        
        ValidationExecutorTest.assertViolation(validator.validate("/apps/install/muybundle-123.jar"), 
                new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_APP_CONTENT, PackageType.CONTENT, "/apps/install/muybundle-123.jar")),
                new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_OSGI_BUNDLE_OR_CONFIG, PackageType.CONTENT, "/apps/install/muybundle-123.jar"))
                );
        Assert.assertThat(validator.validate(filter), AnyValidationMessageMatcher.noValidationInCollection());
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.CONTENT);
        Assert.assertThat(validator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
        Mockito.when(parentContainerContext.getPackageType()).thenReturn(PackageType.CONTENT);
        // validate sub packages of type Content
        PackageTypeValidator subPackageValidator = new PackageTypeValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, PackageType.CONTENT, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, parentContainerContext);
        Assert.assertThat(subPackageValidator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
        // validate sub packages of type Application
        subPackageValidator = new PackageTypeValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, PackageType.APPLICATION, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, parentContainerContext);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.APPLICATION);
        ValidationExecutorTest.assertViolation(subPackageValidator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_UNSUPPORTED_SUB_PACKAGE_OF_TYPE, PackageType.CONTENT, PackageType.CONTENT, PackageType.APPLICATION)));
    }

    @Test
    public void testContainerPackageType() {
        validator = new PackageTypeValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, PackageType.CONTAINER, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, null);
        ValidationExecutorTest.assertViolation(validator.validate("/apps/some/node"), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_NO_OSGI_BUNDLE_OR_CONFIG_OR_SUB_PACKAGE, PackageType.CONTAINER, "/apps/some/node")));
        ValidationExecutorTest.assertViolation(validator.validate("/libs/some/node"), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_NO_OSGI_BUNDLE_OR_CONFIG_OR_SUB_PACKAGE, PackageType.CONTAINER, "/libs/some/node")));
        ValidationExecutorTest.assertViolation(validator.validate("/content/some/node"), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_NO_OSGI_BUNDLE_OR_CONFIG_OR_SUB_PACKAGE, PackageType.CONTAINER, "/content/some/node")));
        
        Assert.assertThat(validator.validate("/apps/install.runmode/somebundle.jar"), AnyValidationMessageMatcher.noValidationInCollection());
        Assert.assertThat(validator.validate("/etc/packages/some/sub/package.zip"), AnyValidationMessageMatcher.noValidationInCollection());
        Assert.assertThat(validator.validate(filter), AnyValidationMessageMatcher.noValidationInCollection());
        
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.CONTAINER);
        Assert.assertThat(validator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
        // validate sub packages of type Content
        Mockito.when(parentContainerContext.getPackageType()).thenReturn(PackageType.CONTAINER);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.CONTENT);
        PackageTypeValidator subPackageValidator = new PackageTypeValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, PackageType.CONTENT, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, parentContainerContext);
        ValidationExecutorTest.assertViolation(subPackageValidator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_UNSUPPORTED_SUB_PACKAGE_OF_TYPE, PackageType.CONTAINER, PackageType.APPLICATION, PackageType.CONTENT)));
        // validate sub packages of type Application
        subPackageValidator = new PackageTypeValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, PackageType.CONTENT, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, parentContainerContext);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.APPLICATION);
        Assert.assertThat(subPackageValidator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
    
        // make sure no dependencies
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.CONTAINER);
        Mockito.when(properties.getDependencies()).thenReturn(new Dependency[] { Dependency.fromString("some/group:artifact:1.0.0") });
        ValidationExecutorTest.assertViolation(validator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_DEPENDENCY, PackageType.CONTAINER, "some/group:artifact:1.0.0")));
    }

    @Test
    public void testApplicationPackageType() throws IOException, ConfigurationException {
        validator = new PackageTypeValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, PackageType.APPLICATION, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, null);
        Assert.assertThat(validator.validate("/apps/some/script"), AnyValidationMessageMatcher.noValidationInCollection());
        Assert.assertThat(validator.validate("/libs"), AnyValidationMessageMatcher.noValidationInCollection());
        
        ValidationExecutorTest.assertViolation(validator.validate("/content/some/node"), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_NO_APP_CONTENT_FOUND, PackageType.APPLICATION, "/content/some/node")));
        ValidationExecutorTest.assertViolation(validator.validate("/etc/something"), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_NO_APP_CONTENT_FOUND, PackageType.APPLICATION, "/etc/something")));
        ValidationExecutorTest.assertViolation(validator.validate("/conf/something"), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_NO_APP_CONTENT_FOUND, PackageType.APPLICATION, "/conf/something")));
        
        // no bundles/sub packages
        ValidationExecutorTest.assertViolation(validator.validate("/apps/install/mybundle.jar"), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_OSGI_BUNDLE_OR_CONFIG, PackageType.APPLICATION, "/apps/install/mybundle.jar")));
        ValidationExecutorTest.assertViolation(validator.validate("/apps/install/config.cfg"), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_OSGI_BUNDLE_OR_CONFIG, PackageType.APPLICATION, "/apps/install/config.cfg")));
        
        // no hooks
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.APPLICATION);
        Assert.assertThat(validator.validate(properties), AnyValidationMessageMatcher.noValidationInCollection());
        // with hooks
        Map<String, String> hooks = Collections.singletonMap("key", "com.example.ExtenalHook");
        Mockito.when(properties.getExternalHooks()).thenReturn(hooks);
        ValidationExecutorTest.assertViolation(validator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_PACKAGE_HOOKS, PackageType.APPLICATION, hooks)));
        
        // with regular filter
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        try (InputStream input = this.getClass().getResourceAsStream("/simple-filter.xml")) {
            filter.load(input);
        }
        Assert.assertThat(validator.validate(filter), AnyValidationMessageMatcher.noValidationInCollection());
        // with filters with include/exclude
        try (InputStream input = this.getClass().getResourceAsStream("/filter.xml")) {
            filter.load(input);
        }
        ValidationExecutorTest.assertViolation(validator.validate(filter), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_FILTER_HAS_INCLUDE_EXCLUDES, PackageType.APPLICATION)));
        
        // validate sub packages of type Content
        Mockito.when(parentContainerContext.getPackageType()).thenReturn(PackageType.APPLICATION);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.CONTENT);
        PackageTypeValidator subPackageValidator = new PackageTypeValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, PackageType.CONTENT, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, parentContainerContext);
        ValidationExecutorTest.assertViolation(subPackageValidator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_UNSUPPORTED_SUB_PACKAGE, PackageType.APPLICATION)));
        // validate sub packages of type Application
        subPackageValidator = new PackageTypeValidator(ValidationMessageSeverity.ERROR, ValidationMessageSeverity.WARN, PackageType.CONTENT, PackageTypeValidatorFactory.DEFAULT_JCR_INSTALLER_NODE_PATH_REGEX, parentContainerContext);
        Mockito.when(properties.getPackageType()).thenReturn(PackageType.APPLICATION);
        Mockito.when(properties.getExternalHooks()).thenReturn(Collections.emptyMap());
        ValidationExecutorTest.assertViolation(subPackageValidator.validate(properties), new ValidationMessage(ValidationMessageSeverity.ERROR, String.format(PackageTypeValidator.MESSAGE_UNSUPPORTED_SUB_PACKAGE, PackageType.APPLICATION)));
    }
}
