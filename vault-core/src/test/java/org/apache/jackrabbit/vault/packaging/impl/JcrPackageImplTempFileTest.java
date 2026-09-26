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

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.Property;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.jackrabbit.vault.packaging.registry.impl.JcrPackageRegistry;
import org.apache.jackrabbit.vault.util.JcrConstants;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression test: {@link JcrPackageImpl#getPackage(boolean)} used to leak its
 * "vaultpack*.zip" temp file on disk if copying the JCR binary into it failed
 * before the {@code ZipVaultPackage} (and thus the file's owner) was constructed.
 */
public class JcrPackageImplTempFileTest {

    /**
     * Lists the "vaultpack*.zip" files currently present in the system temp directory.
     */
    private Set<String> listVaultPackTempFiles() {
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        String[] names = tmpDir.list((dir, name) -> name.startsWith("vaultpack") && name.endsWith(".zip"));
        return new HashSet<>(Arrays.asList(names == null ? new String[0] : names));
    }

    /**
     * Returns the single new file that appeared in the system temp dir since {@code before} was captured.
     */
    private File findNewTempFile(Set<String> before) {
        Set<String> after = listVaultPackTempFiles();
        after.removeAll(before);
        assertEquals("expected exactly one new vaultpack temp file, found: " + after, 1, after.size());
        return new File(System.getProperty("java.io.tmpdir"), after.iterator().next());
    }

    @Test
    public void tempFileIsRemovedWhenPackageCreationFails() throws Exception {
        JcrPackageRegistry mgr = mock(JcrPackageRegistry.class);
        Node node = mock(Node.class);
        Node contentNode = mock(Node.class);
        Property dataProperty = mock(Property.class);
        Binary binary = mock(Binary.class);
        when(node.getNode(eq(JcrConstants.JCR_CONTENT))).thenReturn(contentNode);
        when(contentNode.getProperty(eq(JcrConstants.JCR_DATA))).thenReturn(dataProperty);
        // force the file-archive branch regardless of the reported length
        when(dataProperty.getLength()).thenReturn(Long.MAX_VALUE);
        when(dataProperty.getBinary()).thenReturn(binary);

        // capture the temp file's name at the moment it is read (it is created on disk
        // by then, but hasn't been deleted yet), then simulate a broken source stream.
        Set<String> before = listVaultPackTempFiles();
        File[] capturedTmpFile = new File[1];
        InputStream failingStream = mock(InputStream.class);
        Answer<Integer> failOnFirstRead = invocation -> {
            capturedTmpFile[0] = findNewTempFile(before);
            throw new IOException("boom");
        };
        when(failingStream.read(any(byte[].class))).thenAnswer(failOnFirstRead);
        when(failingStream.read(any(byte[].class), anyInt(), anyInt())).thenAnswer(failOnFirstRead);
        when(binary.getStream()).thenReturn(failingStream);

        JcrPackageImpl jcrPackage = new JcrPackageImpl(mgr, node);
        try {
            jcrPackage.getPackage(true);
            fail("expected IOException to be propagated");
        } catch (IOException expected) {
            // expected
        }

        assertNotNull("temp file was never created/read", capturedTmpFile[0]);
        assertFalse("temp file should have been cleaned up after failed package creation", capturedTmpFile[0].exists());
    }
}
