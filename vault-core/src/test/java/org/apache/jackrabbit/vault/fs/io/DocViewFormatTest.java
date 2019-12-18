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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.util.ISO8601;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DocViewFormatTest {

    private File dir;
    private File docViewFile;

    @Before
    public void setup() throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");
        dir = new File(tempDir + File.separator + "DocViewFormatTest-"
                + ISO8601.format(Calendar.getInstance()).replace(":", "").replace(".", "").replace("-", ""));
        assert dir.mkdir();
        docViewFile = new File(dir, "malformed.xml");
        assert docViewFile.createNewFile();

        try (InputStream in = this.getClass().getResourceAsStream("DocViewFormat/malformed.xml")) {
            try (OutputStream out = new FileOutputStream(docViewFile)) {
                IOUtils.copy(in, out);
            }
        }
    }

    @After
    public void tearDown() {
        if (docViewFile != null) {
            if (!docViewFile.delete()) {
                docViewFile.deleteOnExit();
                dir.deleteOnExit();
            } else {
                if (!dir.delete()) {
                    dir.deleteOnExit();
                }
            }
        }
    }

    @Test
    public void testFormatting() throws IOException {
        List<Pattern> patterns = Collections.singletonList(Pattern.compile(".+\\.xml"));
        DocViewFormat format = new DocViewFormat();
        assertFalse("malformed.xml is expected to be malformed", format.format(dir, patterns, false).isEmpty());
        try (InputStream input = this.getClass().getResourceAsStream("DocViewFormat/formatted.xml")) {
            final String expected = IOUtils.toString(input, StandardCharsets.UTF_8);
            final String result = FileUtils.readFileToString(docViewFile, StandardCharsets.UTF_8);
            assertEquals(expected, result);
        }
        assertTrue("malformed.xml is expected to be formatted", format.format(dir, patterns, true).isEmpty());
    }
}
