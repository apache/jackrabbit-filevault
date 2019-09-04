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
package org.apache.jackrabbit.vault.fs.io;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.jackrabbit.vault.fs.config.DefaultMetaInf;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests zip archives
 */
public class ZipArchiveTest {

    @Test
    public void testSmallArchiveViaStream() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/org/apache/jackrabbit/vault/packaging/integration/testpackages/atomic-counter-test.zip");
             ZipStreamArchive a = new ZipStreamArchive(in)) {
            a.open(true);
            Properties props = a.getMetaInf().getProperties();
            assertEquals("Package Name", "atomic-counter-test", props.getProperty("name"));
            Archive.Entry entry = a.getEntry("META-INF/vault/properties.xml");
            try (InputStream i = a.openInputStream(entry)) {
                DefaultMetaInf metaInf = new DefaultMetaInf();
                metaInf.loadProperties(i, "inputstream");
                assertEquals("Package Name", "atomic-counter-test", metaInf.getProperties().getProperty("name"));
            }
        }
    }

    @Test
    public void testSmallArchiveViaFile() throws IOException {
        File file = new File(getClass().getResource("/org/apache/jackrabbit/vault/packaging/integration/testpackages/atomic-counter-test.zip").getFile());
        try (ZipArchive a = new ZipArchive(file, false)) {
            a.open(true);
            Properties props = a.getMetaInf().getProperties();
            assertEquals("Package Name", "atomic-counter-test", props.getProperty("name"));
        }
    }
}