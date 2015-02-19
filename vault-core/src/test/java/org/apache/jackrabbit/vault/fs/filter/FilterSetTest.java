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

package org.apache.jackrabbit.vault.fs.filter;

import org.apache.jackrabbit.vault.fs.api.FilterSet;
import org.apache.jackrabbit.vault.fs.api.ImportMode;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;

import junit.framework.TestCase;

/**
 *
 */
public class FilterSetTest extends TestCase {

    /**
     * Test if 2 filter sets are not equal if they only differ in root path (JCRVLT-81)
     */
    public void testEqualsNoEntries() {
        FilterSet f1 = new PathFilterSet("/foo").seal();
        FilterSet f2 = new PathFilterSet("/bar").seal();

        assertFalse("filter set with different roots must no be equal", f1.equals(f2));
    }

    /**
     * Test if 2 filter sets equals does work if they are not sealed (JCRVLT-81)
     */
    public void testEqualsNoSeal() {
        FilterSet f1 = new PathFilterSet("/foo");
        FilterSet f2 = new PathFilterSet("/foo");

        assertTrue("filter set with same roots must be equal", f1.equals(f2));
    }

    /**
     * Test if 2 filter sets are equal if they only differ in import mode. this is a condition that has undefined
     * behavior
     */
    public void testEqualsImportMode() {
        FilterSet f1 = new PathFilterSet("/foo");
        FilterSet f2 = new PathFilterSet("/foo");
        f1.setImportMode(ImportMode.MERGE);
        f2.setImportMode(ImportMode.REPLACE);

        assertTrue("filter set that only differ in import-mode must be equal", f1.equals(f2));
    }

    /**
     * Test if 2 filter sets are equal
     */
    public void testEquals() {
        PathFilterSet f1 = new PathFilterSet("/foo");
        f1.addInclude(new DefaultPathFilter("/foo/bar(/.*)?"));
        f1.seal();

        PathFilterSet f2 = new PathFilterSet("/foo");
        f2.addInclude(new DefaultPathFilter("/foo/bar(/.*)?"));
        f2.seal();

        assertTrue("filter must be equal", f1.equals(f2));
    }

    /**
     * Test if 2 filter sets are not equal if the filter differs in sign.
     */
    public void testNotEquals() {
        PathFilterSet f1 = new PathFilterSet("/foo");
        f1.addInclude(new DefaultPathFilter("/foo/bar(/.*)?"));
        f1.seal();

        PathFilterSet f2 = new PathFilterSet("/foo");
        f2.addExclude(new DefaultPathFilter("/foo/bar(/.*)?"));
        f2.seal();

        assertFalse("filter must not be equal", f1.equals(f2));
    }

    /**
     * Test if 2 filter sets are not equal if the filter differs pattern
     */
    public void testNotEquals2() {
        PathFilterSet f1 = new PathFilterSet("/foo");
        f1.addInclude(new DefaultPathFilter("/foo/bar(/.*)?"));
        f1.seal();

        PathFilterSet f2 = new PathFilterSet("/foo");
        f2.addInclude(new DefaultPathFilter("/foo/bar/2(/.*)?"));
        f2.seal();

        assertFalse("filter must not be equal", f1.equals(f2));
    }

}