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
package org.apache.jackrabbit.vault.packaging.impl;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests {@link OsgiAwarePropertiesUtil#getBooleanProperty(String, boolean)}, in particular the
 * fallback to a caller-supplied default value when the underlying system/bundle-context property
 * is not set (used e.g. by {@code AbstractArchive#SHOULD_CREATE_STACK_TRACE} to default to
 * {@code true}).
 */
public class OsgiAwarePropertiesUtilTest {

    private static final String KEY = "test.OsgiAwarePropertiesUtilTest.flag";

    @After
    public void tearDown() {
        System.clearProperty(KEY);
    }

    @Test
    public void unsetPropertyReturnsDefaultValueTrue() {
        System.clearProperty(KEY);
        assertTrue(OsgiAwarePropertiesUtil.getBooleanProperty(KEY, true));
    }

    @Test
    public void unsetPropertyReturnsDefaultValueFalse() {
        System.clearProperty(KEY);
        assertFalse(OsgiAwarePropertiesUtil.getBooleanProperty(KEY, false));
    }

    @Test
    public void explicitTruePropertyOverridesDefaultFalse() {
        System.setProperty(KEY, "true");
        assertTrue(OsgiAwarePropertiesUtil.getBooleanProperty(KEY, false));
    }

    @Test
    public void explicitFalsePropertyOverridesDefaultTrue() {
        System.setProperty(KEY, "false");
        assertFalse(OsgiAwarePropertiesUtil.getBooleanProperty(KEY, true));
    }

    @Test
    public void unparsablePropertyValueIsTreatedAsFalseRegardlessOfDefault() {
        // matches java.lang.Boolean#parseBoolean semantics: anything other than "true" (ignoring case) is false
        System.setProperty(KEY, "not-a-boolean");
        assertFalse(OsgiAwarePropertiesUtil.getBooleanProperty(KEY, true));
    }

    @Test
    public void singleArgOverloadStillDefaultsToFalseWhenUnset() {
        // regression check: the original getBooleanProperty(String) overload must keep its
        // existing behavior (defaulting to false) and not be affected by the new overload.
        System.clearProperty(KEY);
        assertFalse(OsgiAwarePropertiesUtil.getBooleanProperty(KEY));
    }
}
