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
package org.apache.jackrabbit.vault.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * {@code PathUtilTest}...
 */
public class PathUtilTest {

    @Test
    public void testRelPathEquals() {
        assertEquals(".", PathUtil.getRelativeFilePath("/libs/components", "/libs/components", "/"));
    }

    @Test
    public void testRelPathSub() {
        assertEquals("text", PathUtil.getRelativeFilePath("/libs/components", "/libs/components/text", "/"));
    }

    @Test
    public void testRelPathParent() {
        assertEquals("../..", PathUtil.getRelativeFilePath("/libs/components/text", "/libs", "/"));
    }

    @Test
    public void testRelPathSibling() {
        assertEquals("../image", PathUtil.getRelativeFilePath("/libs/components/text", "/libs/components/image", "/"));
    }

    @Test
    public void testWindowRelPath() {
        assertEquals("foo\\bar", PathUtil.getRelativeFilePath("c:\\test\\root", "c:\\test\\root\\foo\\bar", "\\"));
    }
}
