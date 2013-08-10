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
package org.apache.jackrabbit.vault.util.diff;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;

import junit.framework.TestCase;

/**
 * <code>DiffTest</code>...
 */
public class DiffTest extends TestCase {

    public void testBaseLeft0() throws IOException {
        doTest("base.txt", "left.txt", 0, "base-left-0.txt");
    }

    public void testBaseLeft1() throws IOException {
        doTest("base.txt", "left.txt", 1, "base-left-1.txt");
    }

    public void testBaseLeft2() throws IOException {
        doTest("base.txt", "left.txt", 2, "base-left-2.txt");
    }

    public void testBaseLeft100() throws IOException {
        doTest("base.txt", "left.txt", 100, "base-left-100.txt");
    }

    private void doTest(String baseName, String leftName, int numCtx, String resultName)
            throws IOException {
        String base = getResource(baseName);
        String left = getResource(leftName);
        String result = getResource(resultName);

        Document d1 = new Document(null, LineElementsFactory.create(null, base, false));
        Document d2 = new Document(null, LineElementsFactory.create(null, left, false));

        DocumentDiff diff = d1.diff(d2);
        StringBuffer buf = new StringBuffer();
        diff.write(buf, DiffWriter.LS_UNIX, numCtx);
        assertEquals("result", result, buf.toString());

    }
    
    private String getResource(String name) throws IOException {
        InputStream in = this.getClass().getClassLoader().getResourceAsStream(name);
        return IOUtils.toString(in);
    }
}