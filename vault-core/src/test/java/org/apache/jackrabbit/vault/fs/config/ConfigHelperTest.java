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

import javax.xml.parsers.DocumentBuilderFactory;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.jackrabbit.vault.fs.api.ItemFilter;
import org.apache.jackrabbit.vault.fs.filter.IsNodeFilter;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests that {@link ConfigHelper#create(Element, Class)} never instantiates (and thereby
 * never statically initializes) a configured class that is not of the expected type, and
 * refuses the {@code Class#FIELD} static field access syntax. The class attribute may stem
 * from an untrusted package's {@code META-INF/vault/config.xml}.
 */
public class ConfigHelperTest {

    /**
     * Set by {@link Canary}'s static initializer; kept outside the canary class so it can be
     * read without triggering that initializer.
     */
    static final AtomicBoolean CANARY_INITIALIZED = new AtomicBoolean();

    /** Not an {@link ItemFilter}; records whether it was ever statically initialized. */
    public static class Canary {
        static {
            CANARY_INITIALIZED.set(true);
        }
    }

    private static Element element(String name, String className) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element elem = doc.createElement(name);
        elem.setAttribute("class", className);
        return elem;
    }

    @Test
    public void testItemFilterClassIsCreated() throws Exception {
        ConfigHelper helper = new ConfigHelper();
        Object o = helper.create(element("include", IsNodeFilter.class.getName()), ItemFilter.class);
        assertTrue(o instanceof IsNodeFilter);
    }

    @Test
    public void testNonItemFilterClassIsRefusedBeforeInstantiation() throws Exception {
        ConfigHelper helper = new ConfigHelper();
        try {
            helper.create(element("include", Canary.class.getName()), ItemFilter.class);
            fail("a configured class not implementing ItemFilter must be refused");
        } catch (ConfigurationException e) {
            // expected
        }
        assertFalse("the refused class must not have been statically initialized", CANARY_INITIALIZED.get());
    }

    @Test
    public void testStaticFieldAccessSyntaxIsRefused() throws Exception {
        ConfigHelper helper = new ConfigHelper();
        try {
            helper.create(element("include", "java.lang.System#out"), ItemFilter.class);
            fail("the 'Class#FIELD' syntax must be refused");
        } catch (ConfigurationException e) {
            // expected
        }
    }
}
