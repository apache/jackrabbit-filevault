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
package org.apache.jackrabbit.vault.vlt;

import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link FileSystemNameValidator}.
 */
public class FileSystemNameValidatorTest {

    @Test
    public void testVltControlNameIsCaseInsensitiveAndExact() {
        Assert.assertTrue(FileSystemNameValidator.isVltControlName(".vlt"));
        Assert.assertTrue(FileSystemNameValidator.isVltControlName(".VLT"));
        Assert.assertTrue(FileSystemNameValidator.isVltControlName(".Vlt"));
        Assert.assertFalse(FileSystemNameValidator.isVltControlName("vlt"));
        Assert.assertFalse(FileSystemNameValidator.isVltControlName(".vltx"));
        Assert.assertFalse(FileSystemNameValidator.isVltControlName(".content.xml"));
        Assert.assertFalse(FileSystemNameValidator.isVltControlName(""));
        Assert.assertFalse(FileSystemNameValidator.isVltControlName(".. "));
        Assert.assertFalse(FileSystemNameValidator.isVltControlName(null));
    }

    @Test
    public void testVltControlNameCoversWindowsTrailingDotAndSpaceVariants() {
        // Windows silently strips trailing dots/spaces from a path segment on file creation,
        // so these variants must also be treated as reserved to avoid a same-severity bypass.
        Assert.assertTrue(FileSystemNameValidator.isVltControlName(".vlt."));
        Assert.assertTrue(FileSystemNameValidator.isVltControlName(".vlt "));
        Assert.assertTrue(FileSystemNameValidator.isVltControlName(".VLT.. "));
    }
}
