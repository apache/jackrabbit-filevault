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
package org.apache.jackrabbit.vault.vlt.meta;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import org.apache.jackrabbit.vault.vlt.VltException;
import org.apache.jackrabbit.vault.vlt.meta.xml.XmlEntries;
import org.apache.jackrabbit.vault.vlt.meta.xml.zip.ZipMetaDir;

/**
 * {@code TextXMLEntries}...
 */
public class TextXMLEntries extends AbstractTestEntries {

    private File file = new File("target/vlt-test-entries.zip");

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (file.exists()) {
            file.delete();
        }
        dir = new ZipMetaDir(file);
        dir.create("/a/b/c");
        dir.close();
        dir = null;
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    protected void open() throws IOException, VltException {
        dir = new ZipMetaDir(file);
        entries = dir.getEntries();
    }


    public void testXSS() throws VltException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE entries [\n" +
                "   <!ENTITY % foo \"bar\">\n" +
                "]>\n" +
                "<entries path=\"/home/users/m/mCY2rm1YSMlKFlJ-NEN3\">\n" +
                " <entry name=\".content.xml\" rp=\"\" ap=\"/home/users/m/mCY2rm1YSMlKFlJ-NEN3\">\n" +
                "   <base date=\"2018-10-02T11:44:02.000+02:00\" md5=\"268b8e1f6d7b3fc9ec71226ee1a9dc70\" contentType=\"text/xml\" size=\"946\"/>\n" +
                "   <work date=\"2018-10-02T11:44:02.000+02:00\" md5=\"268b8e1f6d7b3fc9ec71226ee1a9dc70\" contentType=\"text/xml\" size=\"946\"/>\n" +
                " </entry>\n" +
                " <entry name=\"_rep_policy.xml\" rp=\"\" ap=\"/home/users/m/mCY2rm1YSMlKFlJ-NEN3/rep:policy\">\n" +
                "   <base date=\"2018-10-02T11:44:02.000+02:00\" md5=\"5a788decc1968551e2838bc46914f75a\" contentType=\"text/xml\" size=\"500\"/>\n" +
                "   <work date=\"2018-10-02T11:44:02.000+02:00\" md5=\"5a788decc1968551e2838bc46914f75a\" contentType=\"text/xml\" size=\"500\"/>\n" +
                " </entry>\n" +
                "</entries>";
        try {
            XmlEntries entries = XmlEntries.load(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            assertTrue(entries.hasEntry(".content.xml"));
            fail("XML entries with DTD should fail.");
        } catch (VltException e) {
            // ok
        }
    }
}