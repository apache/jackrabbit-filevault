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

import java.io.File;
import java.io.IOException;

import org.apache.jackrabbit.vault.vlt.VltException;
import org.apache.jackrabbit.vault.vlt.meta.xml.zip.ZipMetaDir;

/**
 * <code>TextXMLEntries</code>...
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


}