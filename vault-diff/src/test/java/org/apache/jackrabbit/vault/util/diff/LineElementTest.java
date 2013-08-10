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

import junit.framework.TestCase;

/**
 * <code>LineElementTest</code>...
 */
public class LineElementTest extends TestCase {

    public void testLineElements() {
        String[] lines = new String[]{
                "a\n",
                "b\n",
                "c\r\n",
                "\n",
                "f",
        };
        test(lines);
    }

    public void testLargeLineElements() {
        String[] lines = new String[4];
        StringBuffer buf = new StringBuffer(10001);
        for (int i=0; i<10000; i++) {
            buf.append('c');
        }
        buf.append('\n');
        String line = buf.toString();
        for (int i=0;i<lines.length; i++) {
            lines[i] = line;
        }
        test(lines);
    }

    public void testManyLineElements() {
        String[] lines = new String[1024];
        StringBuffer buf = new StringBuffer(32);
        for (int i=0; i<32; i++) {
            buf.append('c');
        }
        buf.append('\n');
        String line = buf.toString();
        for (int i=0;i<lines.length; i++) {
            lines[i] = line;
        }
        test(lines);
    }

    private void test(String[] lines) {
        StringBuffer text = new StringBuffer();
        for (int i=0; i<lines.length; i++) {
            text.append(lines[i]);
        }
        Document.Element[] elems = LineElementsFactory.create(null, text.toString(), false).getElements();
        assertEquals(lines.length, elems.length);
        for (int i=0;i <lines.length; i++) {
            assertEquals(lines[i], elems[i].toString());
        }
    }
}