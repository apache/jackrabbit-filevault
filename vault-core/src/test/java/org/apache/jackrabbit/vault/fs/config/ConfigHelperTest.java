/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.fs.config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.jackrabbit.vault.fs.api.ItemFilter;
import org.apache.jackrabbit.vault.fs.api.VaultFsConfig;
import org.apache.jackrabbit.vault.fs.filter.IsNodeFilter;
import org.junit.Assert;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Tests type-safe instantiation by {@link ConfigHelper} and its use when loading config.xml.
 */
public class ConfigHelperTest {

    /**
     * Set by {@link Canary}'s static initializer; kept outside the canary class so it can be
     * read without triggering that initializer.
     */
    static final AtomicBoolean CANARY_INITIALIZED = new AtomicBoolean();

    /** Creates supported filters by their fully qualified names. */
    @Test
    public void testItemFilterClassIsCreated() throws Exception {
        ConfigHelper helper = new ConfigHelper();
        Object o = helper.create(element("include", IsNodeFilter.class.getName()), ItemFilter.class);
        Assert.assertTrue(o instanceof IsNodeFilter);
    }

    /** Rejects unrelated classes before their static initializers run. */
    @Test
    public void testNonItemFilterClassIsRefusedBeforeInstantiation() throws Exception {
        ConfigHelper helper = new ConfigHelper();
        try {
            helper.create(element("include", Canary.class.getName()), ItemFilter.class);
            Assert.fail("a configured class not implementing ItemFilter must be refused");
        } catch (ConfigurationException e) {
            Assert.assertTrue(e.getMessage().contains("not of expected type"));
        }
        Assert.assertFalse("the refused class must not have been statically initialized", CANARY_INITIALIZED.get());
    }

    /** Rejects the historic static-field configuration syntax. */
    @Test
    public void testStaticFieldAccessSyntaxIsRefused() throws Exception {
        ConfigHelper helper = new ConfigHelper();
        try {
            helper.create(element("include", "java.lang.System#out"), ItemFilter.class);
            Assert.fail("the 'Class#FIELD' syntax must be refused");
        } catch (ConfigurationException e) {
            Assert.assertTrue(e.getMessage().contains("Static field access syntax"));
        }
    }

    /** Retains the original untyped creation API for legitimate callers. */
    @Test
    public void testLegacyCreationStillWorks() throws Exception {
        Assert.assertTrue(new ConfigHelper().create(
                element("include", StringBuilder.class.getName())) instanceof StringBuilder);
    }

    /** Applies the static-field restriction to the original API too. */
    @Test(expected = ConfigurationException.class)
    public void testLegacyCreationRejectsStaticFieldAccess() throws Exception {
        new ConfigHelper().create(element("include", "java.lang.System#out"));
    }

    /** Rejects malformed field syntax even when it has no class name. */
    @Test(expected = ConfigurationException.class)
    public void testLeadingHashIsRejected() throws Exception {
        new ConfigHelper().create(element("include", "#out"), ItemFilter.class);
    }

    /** Retains text-valued configuration when the class attribute is empty. */
    @Test
    public void testEmptyClassReturnsText() throws Exception {
        Element elem = element("property", "");
        elem.setTextContent("value");
        Assert.assertEquals("value", new ConfigHelper().create(elem, String.class));
    }

    /** Retains text-valued configuration without a class attribute. */
    @Test
    public void testMissingClassReturnsText() throws Exception {
        Element elem = element("property", "");
        elem.removeAttribute("class");
        elem.setTextContent("value");
        Assert.assertEquals("value", new ConfigHelper().create(elem, String.class));
    }

    /** Resolves configured default classes before checking the expected type. */
    @Test
    @SuppressWarnings("unchecked")
    public void testDefaultClassIsCreated() throws Exception {
        ConfigHelper helper = new ConfigHelper();
        helper.getDefaultClasses().put("include", IsNodeFilter.class.getName());
        Assert.assertTrue(helper.create(element("include", ""), ItemFilter.class) instanceof IsNodeFilter);
    }

    /** Resolves short class names against the configured default package. */
    @Test
    @SuppressWarnings("unchecked")
    public void testShortClassNameIsCreated() throws Exception {
        ConfigHelper helper = new ConfigHelper();
        helper.getDefaultPackages().put("include", "org.apache.jackrabbit.vault.fs.filter");
        Assert.assertTrue(helper.create(element("include", "IsNodeFilter"), ItemFilter.class) instanceof IsNodeFilter);
    }

    /** Retains explicit mappings for legacy class names. */
    @Test
    public void testMappedClassIsCreated() throws Exception {
        ConfigHelper helper = new ConfigHelper();
        helper.getMappings().put("legacy.MissingFilter", IsNodeFilter.class.getName());
        Assert.assertTrue(helper.create(element("include", "legacy.MissingFilter"),
                ItemFilter.class) instanceof IsNodeFilter);
    }

    /** Rejects unsafe classes reached through the default-class lookup too. */
    @Test
    @SuppressWarnings("unchecked")
    public void testUnsafeDefaultClassIsRejected() throws Exception {
        ConfigHelper helper = new ConfigHelper();
        helper.getDefaultClasses().put("include", Canary.class.getName());
        try {
            helper.create(element("include", ""), ItemFilter.class);
            Assert.fail("default classes must satisfy the expected type");
        } catch (ConfigurationException e) {
            Assert.assertTrue(e.getMessage().contains("not of expected type"));
        }
        Assert.assertFalse(CANARY_INITIALIZED.get());
    }

    /** Reports a missing default package instead of guessing one. */
    @Test(expected = ConfigurationException.class)
    public void testShortClassWithoutDefaultPackageIsRejected() throws Exception {
        new ConfigHelper().create(element("include", "IsNodeFilter"), ItemFilter.class);
    }

    /** Preserves the class-resolution cause for unknown qualified names. */
    @Test
    public void testMissingClassPreservesCause() throws Exception {
        try {
            new ConfigHelper().create(element("include", "missing.UnknownFilter"), ItemFilter.class);
            Assert.fail("unknown classes must be rejected");
        } catch (ConfigurationException e) {
            Assert.assertTrue(e.getCause() instanceof ClassNotFoundException);
        }
    }

    /** Wraps failures to instantiate an abstract filter. */
    @Test
    public void testAbstractClassPreservesCause() throws Exception {
        try {
            new ConfigHelper().create(element("include", AbstractFilter.class.getName()), ItemFilter.class);
            Assert.fail("abstract classes cannot be instantiated");
        } catch (ConfigurationException e) {
            Assert.assertTrue(e.getCause() instanceof InstantiationException);
        }
    }

    /** Wraps inaccessible constructors using the branch's reflection API. */
    @Test
    public void testInaccessibleConstructorPreservesCause() throws Exception {
        try {
            new ConfigHelper().create(element("include", PrivateFilter.class.getName()), ItemFilter.class);
            Assert.fail("private constructors cannot be invoked");
        } catch (ConfigurationException e) {
            Assert.assertTrue(e.getCause() instanceof IllegalAccessException);
        }
    }

    /** Rejects a missing config element explicitly. */
    @Test(expected = IllegalArgumentException.class)
    public void testNullElementIsRejected() throws Exception {
        new ConfigHelper().create(null, ItemFilter.class);
    }

    /** Rejects a missing expected type explicitly. */
    @Test(expected = IllegalArgumentException.class)
    public void testNullExpectedTypeIsRejected() throws Exception {
        new ConfigHelper().create(element("include", IsNodeFilter.class.getName()), null);
    }

    /** Checks include classes before initializing them through the config parser. */
    @Test
    public void testConfigIncludeRejectsUnrelatedClass() throws Exception {
        verifyRejectedConfig("include", Canary.class.getName(), "not of expected type");
        Assert.assertFalse(CANARY_INITIALIZED.get());
    }

    /** Checks exclude classes before initializing them through the config parser. */
    @Test
    public void testConfigExcludeRejectsUnrelatedClass() throws Exception {
        verifyRejectedConfig("exclude", Canary.class.getName(), "not of expected type");
        Assert.assertFalse(CANARY_INITIALIZED.get());
    }

    /** Rejects field references in include elements at the config entry point. */
    @Test
    public void testConfigIncludeRejectsStaticFieldAccess() throws Exception {
        verifyRejectedConfig("include", "java.lang.System#out", "Static field access syntax");
    }

    /** Rejects field references in exclude elements at the config entry point. */
    @Test
    public void testConfigExcludeRejectsStaticFieldAccess() throws Exception {
        verifyRejectedConfig("exclude", "java.lang.System#out", "Static field access syntax");
    }

    /** Retains fully qualified include and short-name exclude filter configuration. */
    @Test
    public void testConfigAcceptsItemFilters() throws Exception {
        VaultFsConfig config = loadConfig(
                "<include class=\"" + IsNodeFilter.class.getName() + "\" isNode=\"true\"/>"
                        + "<exclude class=\"IsNodeFilter\" isNode=\"false\"/>");
        Assert.assertEquals(1, config.getAggregators().size());
    }

    /** Keeps the branch's bundled default configuration loadable. */
    @Test
    public void testDefaultConfigStillLoads() throws Exception {
        VaultFsConfig config = AbstractVaultFsConfig.load(
                AbstractVaultFsConfig.class.getResourceAsStream("defaultConfig-1.1.xml"), "default config");
        Assert.assertFalse(config.getAggregators().isEmpty());
        Assert.assertFalse(config.getHandlers().isEmpty());
    }

    private static Element element(String name, String className) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element elem = doc.createElement(name);
        elem.setAttribute("class", className);
        return elem;
    }

    private static VaultFsConfig loadConfig(String filters) throws Exception {
        String xml = "<vaultfs version=\"1.1\"><aggregates><aggregate type=\"generic\"><matches>"
                + filters + "</matches></aggregate></aggregates></vaultfs>";
        return AbstractVaultFsConfig.load(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), "test config");
    }

    private static void verifyRejectedConfig(String elementName, String className, String message) throws Exception {
        try {
            loadConfig("<" + elementName + " class=\"" + className + "\"/>");
            Assert.fail("unsafe config classes must be rejected");
        } catch (ConfigurationException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains(message));
        }
    }

    /** Not an {@link ItemFilter}; records whether it was ever statically initialized. */
    public static class Canary {
        static {
            CANARY_INITIALIZED.set(true);
        }
    }

    /** Supplies an assignable class that cannot be instantiated. */
    public abstract static class AbstractFilter extends IsNodeFilter {
    }

    private static class PrivateFilter extends IsNodeFilter {
        private PrivateFilter() {
        }
    }
}
