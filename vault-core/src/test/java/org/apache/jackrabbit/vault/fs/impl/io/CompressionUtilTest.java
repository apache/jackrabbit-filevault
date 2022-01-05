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
package org.apache.jackrabbit.vault.fs.impl.io;

import static org.apache.jackrabbit.vault.fs.impl.io.CompressionUtil.isCompressible;
import static org.apache.jackrabbit.vault.fs.impl.io.CompressionUtil.isCompressibleContentType;
import static org.apache.jackrabbit.vault.fs.impl.io.CompressionUtil.isIncompressibleContentType;
import static org.apache.jackrabbit.vault.fs.impl.io.CompressionUtil.seemsCompressible;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Random;

import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

public class CompressionUtilTest {

    private static final Random RAND = new Random();

    private static final String COMPRESSIBLE_MIME_TYPE = "text/plain";

    private static final String INCOMPRESSIBLE_MIME_TYPE = "image/png";

    private static final String UNKNOWN_MIME_TYPE = "unkown/unknown";

    @Test
    public void testCompressibilityByMimeType() {
        assertTrue(isIncompressibleContentType(INCOMPRESSIBLE_MIME_TYPE));
        assertFalse(isIncompressibleContentType(UNKNOWN_MIME_TYPE));
        assertFalse(isIncompressibleContentType(COMPRESSIBLE_MIME_TYPE));
        assertTrue(isCompressibleContentType(COMPRESSIBLE_MIME_TYPE));
        assertFalse(isCompressibleContentType(UNKNOWN_MIME_TYPE));
    }

    @Test
    @Ignore("Because it does not always succeed due to usage of random bytes")
    public void testCompressibilityEstimation()
            throws IOException, RepositoryException {
        assertTrue(seemsCompressible(newArtifact(incompressibleData(50*1024), null)) < 0);
        assertTrue(seemsCompressible(newArtifact(compressibleData(50*1024), null)) > 0);
    }

    @Test
    @Ignore("Because it does not always succeed due to usage of random bytes")
    public void testCompressibility()
            throws IOException, RepositoryException {
        byte[] comp256KB = compressibleData(256*1024);
        byte[] incomp256KB = incompressibleData(256*1024);
        assertTrue(isCompressible(newArtifact(comp256KB, COMPRESSIBLE_MIME_TYPE)) > 0);
        assertTrue(isCompressible(newArtifact(comp256KB, UNKNOWN_MIME_TYPE)) > 0);
        assertTrue(isCompressible(newArtifact(comp256KB, null)) > 0);
        assertTrue(isCompressible(newArtifact(new byte[10], null)) == 0);
        assertTrue(isCompressible(newArtifact(incomp256KB, UNKNOWN_MIME_TYPE)) < 0);
        assertTrue(isCompressible(newArtifact(incomp256KB, INCOMPRESSIBLE_MIME_TYPE)) < 0);
    }

    private Artifact newArtifact(byte[] data, String contentType)
            throws IOException, RepositoryException {
        Artifact artifact = Mockito.mock(Artifact.class);
        InputStream inputStream = new ByteArrayInputStream(data);
        Mockito.when(artifact.getInputStream()).thenReturn(inputStream);
        Mockito.when(artifact.getContentLength()).thenReturn((long) data.length);
        Mockito.when(artifact.getContentType()).thenReturn(contentType);
        Mockito.when(artifact.getSerializationType()).thenReturn(SerializationType.GENERIC);
        return artifact;
    }

    private byte[] compressibleData(int length) {
        byte[] data = new byte[length];
        Arrays.fill(data, (byte)42);
        return data;
    }

    private byte[] incompressibleData(int length) {
        byte[] data = new byte[length];
        RAND.nextBytes(data);
        return data;
    }
}