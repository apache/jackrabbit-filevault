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
import java.io.PrintWriter;

import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.Dumpable;
import org.apache.jackrabbit.vault.util.MD5;
import org.apache.jackrabbit.vault.vlt.VltException;

import junit.framework.TestCase;

/**
 * <code>TestEntries</code>...
 */
public abstract class AbstractTestEntries extends TestCase {

    protected File file = new File("target/vlt-test-entries.zip");

    private boolean verbose;

    protected MetaDirectory dir;

    protected VltEntries entries;

    abstract protected void open() throws IOException, VltException;

    protected void close() throws IOException {
        if (entries != null) {
            entries = null;
        }
        if (dir != null) {
            dir.sync();
            dir.close();
        }
    }

    protected void reopen() throws IOException, VltException {
        close();
        open();
    }

    public void testRepoAddress() throws IOException, VltException {
        open();
        assertNull(dir.getRepositoryUrl());
        dir.setRepositoryUrl("http://localhost:8080");
        dir.sync();
        reopen();
        assertEquals("http://localhost:8080", dir.getRepositoryUrl());
    }

    public void testAddEntry() throws VltException, IOException {
        open();
        assertFalse(entries.hasEntry("foo.png"));
        VltEntry e = entries.update("foo.png", "/bla", "foo.png");
        reopen();
        assertTrue(entries.hasEntry("foo.png"));
    }

    public void testAddInfo() throws VltException, IOException {
        testAddEntry();

        assertTrue(entries.hasEntry("foo.png"));
        VltEntry e = entries.getEntry("foo.png");
        assertNull(e.base());
        VltEntryInfo base = e.create(VltEntryInfo.Type.BASE);
        e.put(base);
        reopen();
        e = entries.getEntry("foo.png");
        assertTrue(entries.hasEntry("foo.png"));
        assertNotNull(e.base());
    }

    public void testModifyInfo() throws VltException, IOException {
        testAddInfo();
        reopen();

        assertTrue(entries.hasEntry("foo.png"));
        VltEntry e = entries.getEntry("foo.png");
        VltEntryInfo base = e.base();
        assertNotNull(base);
        base.setContentType("text/plain");
        base.setDate(1000);
        base.setMd5(new MD5(2,3));
        base.setSize(4);
        reopen();
        e = entries.getEntry("foo.png");
        base = e.base();
        assertEquals("text/plain", base.getContentType());
        assertEquals(1000, base.getDate());
        assertEquals(new MD5(2,3), base.getMd5());
        assertEquals(4, base.getSize());
    }

    protected void tearDown() throws Exception {
        if (entries != null && verbose) {
            PrintWriter out = new PrintWriter(System.out);
            out.println("----------------------------------------------------------");
            if (entries instanceof Dumpable) {
                ((Dumpable) entries).dump(new DumpContext(out), true);
            }
            out.flush();
        }
    }
}