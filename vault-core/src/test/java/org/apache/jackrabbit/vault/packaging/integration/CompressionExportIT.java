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
package org.apache.jackrabbit.vault.packaging.integration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.Random;
import java.util.zip.Deflater;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.packaging.ExportOptions;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertTrue;

/**
 * Test cases for custom compression level on export.
 * This test case requires a long time to run, thus it is disabled by default.
 */
@Ignore
public class CompressionExportIT extends IntegrationTestBase {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(CompressionExportIT.class);

    private static final Random RAND = new Random();

    private static final String TEST_PARENT_PATH = "/tmp/testCompressionExport";

    private static final String COMPRESSIBLE_MIME_TYPE = "text/plain";

    private static final String INCOMPRESSIBLE_MIME_TYPE = "image/png";

    private static final String UNKNOWN_MIME_TYPE = "unkown/unknown";

    private static final int NB_WARMUP_ITERATIONS = 100;

    private static final int NB_TEST_ITERATIONS = 10000;

    @Before
    public void setup() throws RepositoryException, PackageException, IOException {
        JcrUtils.getOrCreateByPath(TEST_PARENT_PATH, "nt:unstructured", admin);
        admin.save();
    }

    @After
    public void after() throws RepositoryException {
        if (admin.nodeExists(TEST_PARENT_PATH)) {
            admin.getNode(TEST_PARENT_PATH).remove();
            admin.save();
        }
    }

    @Test
    public void test100KB_to_120KB() throws RepositoryException, IOException {
        for (int size = 100 * 1024 ; size < 120 * 1024 ; size += 10 * 1024) {
            runTestAndAssertGains(size);
        }
    }

    @Test
    public void test1MB() throws RepositoryException, IOException {
        runTestAndAssertGains(1024 * 1024);
    }

    @Test
    public void test10MB() throws RepositoryException, IOException {
        runTestAndAssertGains(10 * 1024 * 1024);
    }

    private void runTestAndAssertGains(int size)
            throws RepositoryException, IOException {
        compareWithAndWithoutOptimization(storeFile(true, COMPRESSIBLE_MIME_TYPE, size));
        compareWithAndWithoutOptimization(storeFile(false, INCOMPRESSIBLE_MIME_TYPE, size));
        compareWithAndWithoutOptimization(storeFile(true, UNKNOWN_MIME_TYPE, size));
        compareWithAndWithoutOptimization(storeFile(false, UNKNOWN_MIME_TYPE, size));
    }

    private void compareWithAndWithoutOptimization(String path)
            throws IOException, RepositoryException {
        SizeDuration noOptimization = measureExportDuration(path, Deflater.DEFAULT_COMPRESSION);
        SizeDuration withOptimization = measureExportDuration(path, 6); // level 6 is used for the DEFAULT_COMPRESSION strategy
        float durationGain = (noOptimization.duration - withOptimization.duration) / (float) noOptimization.duration;
        float sizeGain = (noOptimization.size - withOptimization.size) / (float) noOptimization.size;
        log.info("Path {} duration gain: {}, size gain: {}", new Object[]{path, durationGain, sizeGain});
        // assert the optimization does not imply a decrease of throughput larger than 3%
        assertTrue(noOptimization.duration * 1.03f > withOptimization.duration);
    }

    private SizeDuration measureExportDuration(String nodePath, int level)
            throws IOException, RepositoryException {
        ExportOptions opts = buildExportOptions(nodePath, level);
        log.info("Warmup for path {} and compression level {}",
                new Object[]{nodePath, level});
        exportMultipleTimes(opts, NB_WARMUP_ITERATIONS);
        log.info("Run for path {} and compression level {}",
                new Object[]{nodePath, level});
        long start = System.nanoTime();
        long size = exportMultipleTimes(opts, NB_TEST_ITERATIONS);
        long stop = System.nanoTime();
        SizeDuration sd = new SizeDuration(size, stop - start);
        float rate = (sd.size / (float)sd.duration * 1000);
        log.info("Ran for path {} and compression level {} in {} ns produced {} B ({} MB/s)",
                new Object[]{nodePath, level, sd.duration, sd.size, rate});
        return sd;
    }

    private long exportMultipleTimes(ExportOptions opts, int times)
            throws IOException, RepositoryException {
        long size = 0;
        for (int i = 0 ; i < times ; i++) {
            WriteCountOutputStream outputStream = new WriteCountOutputStream();
            packMgr.assemble(admin, opts, outputStream);
            size += outputStream.size();
        }
        return size;
    }

    private ExportOptions buildExportOptions(String nodePath, int level) {
        ExportOptions opts = new ExportOptions();
        opts.setCompressionLevel(level);
        DefaultMetaInf inf = new DefaultMetaInf();
        DefaultWorkspaceFilter filter = new DefaultWorkspaceFilter();
        filter.add(new PathFilterSet(nodePath));
        inf.setFilter(filter);
        Properties props = new Properties();
        props.setProperty(VaultPackage.NAME_GROUP, "jackrabbit/test");
        props.setProperty(VaultPackage.NAME_NAME, "test-compression-package");
        inf.setProperties(props);
        opts.setMetaInf(inf);
        return opts;
    }


    private String storeFile(boolean compressible, String mimeType, int size)
            throws RepositoryException {
        String path = String.format("%s/%s", TEST_PARENT_PATH, fileName(compressible, mimeType, size));
        Node node = JcrUtils.getOrCreateByPath(path, "nt:unstructured", admin);
        byte[] data = compressible ? compressibleData(size) : incompressibleData(size);
        JcrUtils.putFile(node, "file", mimeType, new ByteArrayInputStream(data));
        admin.save();
        return node.getPath();
    }

    private String fileName(boolean compressible, String mimeType, int size) {
        return String.format("%s_%s_%s", mimeType, size, compressible ? "compressible" : "incompressible");
    }

    private byte[] compressibleData(int length) {
        byte[] data = new byte[length];
        Arrays.fill(data, (byte)42); // low entropy data
        return data;
    }

    private byte[] incompressibleData(int length) {
        byte[] data = new byte[length];
        RAND.nextBytes(data); // high entropy data
        return data;
    }

    public class WriteCountOutputStream extends OutputStream {

        long size = 0;

        @Override
        public void write(int b) throws IOException {
            size++;
        }

        @Override
        public void write(byte[] b) throws IOException {
            size += b.length;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            size += len;
        }

        public long size() {
            return size;
        }
    }

    private class SizeDuration {

        long size;

        long duration;

        SizeDuration(long size, long duration) {
            this.duration = duration;
            this.size = size;
        }
    }

}
