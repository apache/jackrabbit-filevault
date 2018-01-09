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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nonnull;
import javax.jcr.RepositoryException;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.SerializationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code CompressionUtil} is a utility class that allows to evaluate
 * the compressibility of artifacts.
 */
public final class CompressionUtil {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(CompressionUtil.class);

    /**
     * If set to true the compression level can be changed for individual jar entries, otherwise false.
     */
    public static final boolean ENV_SUPPORTS_COMPRESSION_LEVEL_CHANGE = checkEnvironmentSupportsCompressionSwitch();

    /**
     * Minimum length to run the auto-detection algorithm in Byte.
     */
    private static final long MIN_AUTO_DETECTION_LENGTH = 115 * 1024;

    /**
     * Length of the sample (in byte) peeked from the artifact for running the auto-detection algorithm.
     */
    private static final int SAMPLE_LENGTH = 256;

    // TODO extend the MIME type lists

    /**
     * List of well known mime types identifying to compressed formats.
     */
    private static final Set<String> INCOMPRESSIBLE_MIME_TYPES = new HashSet<String>(Arrays.asList(
            "image/gif",
            "image/jpeg",
            "image/png",
            "multipart/x-gzip",
            "video/mp4",
            "application/gzip",
            "application/java-archive",
            "application/mp4",
            "application/x-7z-compressed",
            "application/x-compressed",
            "application/x-gzip",
            "application/x-rar-compressed",
            "application/zip",
            "application/zlib",
            "audio/mpeg"
    ));

    /**
     * List of well known mime types identifying to non compressed formats.
     */
    private static final Set<String> COMPRESSIBLE_MIME_TYPES = new HashSet<String>(Arrays.asList(
            "application/xml",
            "application/java",
            "application/json",
            "application/javascript",
            "application/ecmascript"
    ));

    /**
     * Estimates if the provided artifact is compressible.
     *
     * @param artifact the artifact to be tested for compressibility
     * @return A negative integer, a positive integer or zero depending on whether the artifact
     * is estimated to be incompressible, compressible or if the estimate did not run.
     */
    public static int isCompressible(@Nonnull Artifact artifact) {

        if (SerializationType.GENERIC == artifact.getSerializationType()) {

            /*
             * Test for known content types
             */
            String contentType = artifact.getContentType();
            if (contentType != null) {
                contentType = contentType.toLowerCase();
                if (isCompressibleContentType(contentType)) {
                    return 1;
                }
                if (isIncompressibleContentType(contentType)) {
                    return -1;
                }
            }

            /*
             * Apply compressibility prediction heuristic on a sample of the artifact
             *
             * The heuristic is tested only if the expected cost of running the heuristic
             * is smaller than 3% of the expected cost of compressing the artifact, such
             * that the extra cost is reasonable in the worst case.
             *
             *
             * The compression throughput is assumed to be 80 MB/s or less.
             * The cost of peeking a sample and running the heuristic is expected to be 150μs or less.
             *
             * The artifact size threshold is thus set to 115KB.
             *
             * A better improved implementation may measure those values and self tune for a
             * specific runtime.
             */
            long contentLength = artifact.getContentLength();
            if (contentLength > MIN_AUTO_DETECTION_LENGTH) {
                return seemsCompressible(artifact);
            }
        }
        return 0;
    }

    static boolean isCompressibleContentType(@Nonnull String mimeType) {
        return mimeType.startsWith("text/") || COMPRESSIBLE_MIME_TYPES.contains(mimeType);
    }

    static boolean isIncompressibleContentType(@Nonnull String mimeType) {
        return INCOMPRESSIBLE_MIME_TYPES.contains(mimeType);
    }

    static int seemsCompressible(@Nonnull Artifact artifact) {
        InputStream stream = null;
        try {
            stream = artifact.getInputStream();
            byte[] sample = IOUtils.toByteArray(stream, SAMPLE_LENGTH);
            return isCompressible(sample, SAMPLE_LENGTH) ? 1 : -1;
        } catch (RepositoryException | IOException e) {
            log.warn(e.getMessage(), e);
        } finally {
            IOUtils.closeQuietly(stream);
        }
        return 0;
    }

    /**
     * This algorithm estimates the entropy of the high nibbles.
     * <p>
     * Credits to Thomas Mueller's for this solution, shared on StackOverflow at
     * <a href="http://bit.ly/2mKsp0v">How To Efficiently Predict If Data Is Compressible</a>
     *
     * @param data the data to be tested for compressibility
     * @param len  the length of the data to be tested
     * @return {@code true} if the data sample is compressible,
     * {@code false} otherwise.
     */
    private static boolean isCompressible(byte[] data, int len) {
        // the number of bytes with
        // high nibble 0, 1,.., 15
        int[] sum = new int[16];
        for (int i = 0; i < len; i++) {
            int x = (data[i] & 255) >> 4;
            sum[x]++;
        }
        // see wikipedia to understand this formula :-)
        int r = 0;
        for (int x : sum) {
            long v = ((long) x << 32) / len;
            r += 63 - Long.numberOfLeadingZeros(v + 1);
        }
        return len * r < 438 * len;
    }

    /**
     * Check if writing a JarOutputStream supports switching the compression level for individual
     * JarEntries. There are known issues with recent zlib versions and java which might result in broken
     * packages being exported, when they contain already compressed binary entries according to CompressionUtil.
     * <p/>
     * for more information see:
     * https://issues.apache.org/jira/browse/JCRVLT-257
     * https://github.com/madler/zlib/issues/305
     *
     * @return {@code true} if the environment supports switching compression levels
     */
    private static boolean checkEnvironmentSupportsCompressionSwitch() {
        Throwable exception = null;
        ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();
        byte[] nullBytes = new byte[1024 * 1024];
        try (ZipOutputStream zipOut = new ZipOutputStream(byteArrayOut)) {
            zipOut.setLevel(Deflater.BEST_SPEED);
            zipOut.putNextEntry(new ZipEntry("deflated.bin"));
            zipOut.write(nullBytes);
            zipOut.closeEntry();
            zipOut.putNextEntry(new ZipEntry("stored.bin"));
            zipOut.setLevel(Deflater.BEST_COMPRESSION);
            zipOut.write(nullBytes);
            zipOut.closeEntry();
        } catch (Throwable e) {
            exception = e;
        }
        if (exception == null) {
            try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(byteArrayOut.toByteArray()))) {
                for (int i = 0; i < 2; i++) {
                    zipIn.getNextEntry();
                    while (zipIn.read(nullBytes) >= 0) {
                    }
                }
            } catch (Throwable e) {
                exception = e;
            }
        }

        if (exception != null) {
            log.info("The current environment doesn't support switching compression level for individual JarEntries, see JCRVLT-257");
            return false;
        }
        return true;
    }
}
