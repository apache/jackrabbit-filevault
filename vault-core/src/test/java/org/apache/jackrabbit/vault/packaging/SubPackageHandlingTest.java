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

package org.apache.jackrabbit.vault.packaging;

import junit.framework.TestCase;

/**
 * <code>SubPackageHandlingTest</code>...
 */
public class SubPackageHandlingTest extends TestCase {

    public void testDefault() {
        SubPackageHandling sp = new SubPackageHandling();
        assertEquals(SubPackageHandling.Option.INSTALL, sp.getOption(PackageId.fromString("foo:bar")));
        assertEquals(SubPackageHandling.Option.INSTALL, sp.getOption(PackageId.fromString("foo:bar:1.0")));
    }

    public void testStaticDefault() {
        SubPackageHandling sp = SubPackageHandling.DEFAULT;
        assertEquals(SubPackageHandling.Option.INSTALL, sp.getOption(PackageId.fromString("foo:bar")));
        assertEquals(SubPackageHandling.Option.INSTALL, sp.getOption(PackageId.fromString("foo:bar:1.0")));
    }

    public void testMatchAll() {
        SubPackageHandling sp = new SubPackageHandling();
        sp.getEntries().add(new SubPackageHandling.Entry("*", "*", SubPackageHandling.Option.ADD));
        assertEquals(SubPackageHandling.Option.ADD, sp.getOption(PackageId.fromString("foo:bar")));
        assertEquals(SubPackageHandling.Option.ADD, sp.getOption(PackageId.fromString("foo:bar:1.0")));
    }

    public void testMatchAllGroup() {
        SubPackageHandling sp = new SubPackageHandling();
        sp.getEntries().add(new SubPackageHandling.Entry("foo", "*", SubPackageHandling.Option.ADD));
        assertEquals(SubPackageHandling.Option.ADD, sp.getOption(PackageId.fromString("foo:bar")));
        assertEquals(SubPackageHandling.Option.ADD, sp.getOption(PackageId.fromString("foo:baz")));
        assertEquals(SubPackageHandling.Option.INSTALL, sp.getOption(PackageId.fromString("noo:bar:1.0")));
    }

    public void testMatchAllPackage() {
        SubPackageHandling sp = new SubPackageHandling();
        sp.getEntries().add(new SubPackageHandling.Entry("*", "bar", SubPackageHandling.Option.ADD));
        assertEquals(SubPackageHandling.Option.ADD, sp.getOption(PackageId.fromString("foo:bar")));
        assertEquals(SubPackageHandling.Option.ADD, sp.getOption(PackageId.fromString("loo:bar")));
        assertEquals(SubPackageHandling.Option.INSTALL, sp.getOption(PackageId.fromString("foo:baz:1.0")));
    }

    public void testMatchSpecific() {
        SubPackageHandling sp = new SubPackageHandling();
        sp.getEntries().add(new SubPackageHandling.Entry("foo", "bar", SubPackageHandling.Option.ADD));
        assertEquals(SubPackageHandling.Option.ADD, sp.getOption(PackageId.fromString("foo:bar")));
        assertEquals(SubPackageHandling.Option.INSTALL, sp.getOption(PackageId.fromString("zoo:bar")));
        assertEquals(SubPackageHandling.Option.INSTALL, sp.getOption(PackageId.fromString("foo:baz:1.0")));
    }

    public void testMatchOrder() {
        SubPackageHandling sp = new SubPackageHandling();
        sp.getEntries().add(new SubPackageHandling.Entry("foo", "*", SubPackageHandling.Option.ADD));
        sp.getEntries().add(new SubPackageHandling.Entry("foo", "bar", SubPackageHandling.Option.EXTRACT));
        assertEquals(SubPackageHandling.Option.ADD, sp.getOption(PackageId.fromString("foo:baz")));
        assertEquals(SubPackageHandling.Option.EXTRACT, sp.getOption(PackageId.fromString("foo:bar")));
    }

    public void testParse() {
        assertEquals("", SubPackageHandling.fromString("").getString());
        assertEquals("foo:bar", SubPackageHandling.fromString("foo:bar").getString());
        assertEquals("*:bar", SubPackageHandling.fromString("*:bar").getString());
        assertEquals("*:bar", SubPackageHandling.fromString(":bar").getString());
        assertEquals("*:bar", SubPackageHandling.fromString("bar").getString());
        assertEquals("foo:*", SubPackageHandling.fromString("foo:*").getString());
        assertEquals("*:*", SubPackageHandling.fromString("*:*").getString());
        assertEquals("foo:bar;add", SubPackageHandling.fromString("foo:bar;add").getString());
        assertEquals("foo:bar;extract", SubPackageHandling.fromString("foo:bar;extract").getString());
        assertEquals("foo:bar;ignore", SubPackageHandling.fromString("foo:bar;ignore").getString());
        assertEquals("foo:bar", SubPackageHandling.fromString("foo:bar;illegal").getString());
        assertEquals("foo:bar", SubPackageHandling.fromString("foo:bar;install").getString());
        assertEquals("*:*,foo:bar;add", SubPackageHandling.fromString("*;install,foo:bar;add").getString());
    }
}