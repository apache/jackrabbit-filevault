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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.jackrabbit.vault.vlt.meta.xml.XmlEntries;
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

    @Test
    public void testSafeFileNameAcceptsPlainSingleSegmentNames() {
        Assert.assertTrue(FileSystemNameValidator.isSafeFileName("foo.png"));
        Assert.assertTrue(FileSystemNameValidator.isSafeFileName(".content.xml"));
        Assert.assertTrue(FileSystemNameValidator.isSafeFileName("foo.mine"));
    }

    @Test
    public void testEscapedBackslashNamesLoadFromWorkingCopyMetadata() throws Exception {
        String[] repositoryNames = {
            "test1\\something",
            "test1%5csomething",
            "\\leading",
            "trailing\\",
            "two\\back\\slashes",
            "cq:test1\\something"
        };
        for (String repositoryName : repositoryNames) {
            String platformName = PlatformNameFormat.getPlatformName(repositoryName);
            Assert.assertTrue(platformName, FileSystemNameValidator.isSafeFileName(platformName));
            String xml = "<entries path=\"/content\"><entry name=\"" + platformName
                    + "\" rp=\"\" ap=\"/content/" + repositoryName + "\">"
                    + "<base/><mine name=\"" + platformName + ".mine\"/>"
                    + "<theirs name=\"" + platformName + ".theirs\"/></entry></entries>";
            XmlEntries entries = XmlEntries.load(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            Assert.assertTrue(platformName, entries.hasEntry(platformName));
            Assert.assertEquals(repositoryName, PlatformNameFormat.getRepositoryName(platformName));
        }
    }

    @Test
    public void testSafeFileNameRejectsNullAndEmpty() {
        Assert.assertFalse(FileSystemNameValidator.isSafeFileName(null));
        Assert.assertFalse(FileSystemNameValidator.isSafeFileName(""));
    }

    @Test
    public void testSafeFileNameRejectsDotAndDotDotSegments() {
        Assert.assertFalse(FileSystemNameValidator.isSafeFileName("."));
        Assert.assertFalse(FileSystemNameValidator.isSafeFileName(".."));
    }

    @Test
    public void testSafeFileNameRejectsTraversalAndSeparators() {
        Assert.assertFalse(FileSystemNameValidator.isSafeFileName("../../etc/passwd"));
        Assert.assertFalse(FileSystemNameValidator.isSafeFileName("sub/evil"));
        Assert.assertFalse(FileSystemNameValidator.isSafeFileName("sub\\evil"));
        Assert.assertFalse(FileSystemNameValidator.isSafeFileName("..\\..\\evil"));
    }

    @Test
    public void testSafeFileNameRejectsDriveLetterAndAlternateDataStream() {
        // NTFS alternate data stream / drive-letter addressing via ':'.
        Assert.assertFalse(FileSystemNameValidator.isSafeFileName("c:evil"));
        Assert.assertFalse(FileSystemNameValidator.isSafeFileName("foo.txt:stream"));
    }

    @Test
    public void testSafeFileNameRejectsVltControlName() {
        Assert.assertFalse(FileSystemNameValidator.isSafeFileName(".vlt"));
        Assert.assertFalse(FileSystemNameValidator.isSafeFileName(".VLT"));
    }
}
