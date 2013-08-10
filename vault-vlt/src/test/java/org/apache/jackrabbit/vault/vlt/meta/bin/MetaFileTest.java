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

package org.apache.jackrabbit.vault.vlt.meta.bin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.DumpContext;

import junit.framework.TestCase;

/**
 * <code>MetaFileTest</code>...
 */
public class MetaFileTest extends TestCase {

    private PrintWriter log = new PrintWriter(System.out);

    private DumpContext dump = new DumpContext(log);

    private File file;

    public MetaFileTest() {
        log = new PrintWriter(new StringWriter());
    }

    protected void setUp() throws Exception {
         file = new File("target/testFile.vlt");
    }

    public void testEmpty() throws IOException {
        file.delete();
        BinMetaFile m = new BinMetaFile(file);
        HeaderBlock h = m.open();
        h.dump(dump, false);
        m.close();
    }

    public void testExisting() throws IOException {
        file.delete();
        BinMetaFile m = new BinMetaFile(file);
        HeaderBlock h = m.open();
        h.dump(dump, false);
        m.close();

        m = new BinMetaFile(file);
        h = m.open();
        h.dump(dump, false);
        m.close();
    }

    public void testHeaderProps() throws IOException {
        log.println("-------------------------------------------");
        log.println("testHeaderProps()");
        log.println("-------------------------------------------");
        BinMetaFile m = new BinMetaFile(file);
        HeaderBlock header = m.open();
        PropertiesBlock pBlk = m.getLinkedBlock(header, 0, PropertiesBlock.class, true);
        pBlk.setProperty("foo", "bar");
        pBlk.setProperty("hello", "world");
        m.sync();
        m.dump(dump, false);
        log.println("-------------------------------------------");
        pBlk.setProperty("larum", "ipsum");
        m.sync();
        m.dump(dump, false);
        m.close();
    }

    public void testNewEntry() throws IOException {
        log.println("-------------------------------------------");
        log.println("testNewEntry()");
        log.println("-------------------------------------------");
        BinMetaFile m = new BinMetaFile(file);
        HeaderBlock header = m.open();
        EntryBlock blk = new EntryBlock("myentry", "aggPath", "relPath");
        m.add(blk);
        m.sync();
        m.dump(dump, false);
        log.println("-------------------------------------------");
        InfoBlock e = m.getLinkedBlock(blk, EntryBlock.ID_WORK, InfoBlock.class, true);
        e.setContentType("text/plain");
        m.sync();
        m.dump(dump, false);
        m.close();
    }

    public void testUpdateEntry() throws IOException {
        log.println("-------------------------------------------");
        log.println("testUpdateEntry()");
        log.println("-------------------------------------------");
        BinMetaFile m = new BinMetaFile(file);
        HeaderBlock header = m.open();
        EntryBlock blk = m.getEntryBlock("myentry");
        InfoBlock e = m.getLinkedBlock(blk, EntryBlock.ID_WORK, InfoBlock.class, true);
        e.setSize(1234);
        m.sync();
        m.dump(dump, false);
        log.println("-------------------------------------------");
        e.setContentType("text/html");
        m.sync();
        m.dump(dump, false);
        m.close();
    }

    public void testWriteData() throws IOException {
        log.println("-------------------------------------------");
        log.println("testWriteData()");
        log.println("-------------------------------------------");
        BinMetaFile m = new BinMetaFile(file);
        HeaderBlock header = m.open();
        EntryBlock blk = m.getEntryBlock("myentry");
        InfoBlock info = m.getLinkedBlock(blk, EntryBlock.ID_WORK, InfoBlock.class, true);
        DataBlock data = m.createDataBlock("hello, world".getBytes());
        m.linkBlock(info, InfoBlock.ID_DATA, data);
        m.sync();
        m.dump(dump, false);
        m.close();
    }

    public void testReadData() throws IOException {
        log.println("-------------------------------------------");
        log.println("testReadData()");
        log.println("-------------------------------------------");
        BinMetaFile m = new BinMetaFile(file);
        HeaderBlock header = m.open();
        EntryBlock blk = m.getEntryBlock("myentry");
        InfoBlock info = m.getLinkedBlock(blk, EntryBlock.ID_WORK, InfoBlock.class, true);
        DataBlock data = m.getLinkedBlock(info, InfoBlock.ID_DATA, DataBlock.class, true);
        String s = new String(m.getBytes(data));
        log.println(s);
        InputStream in = m.getInputStream(data);
        s = IOUtils.toString(in);
        log.println(s);
        m.close();
    }
    
    public void testDeleteEntry() throws IOException {
        log.println("-------------------------------------------");
        log.println("testDeleteEntry()");
        log.println("-------------------------------------------");
        BinMetaFile m = new BinMetaFile(file);
        HeaderBlock header = m.open();
        EntryBlock blk = m.getEntryBlock("myentry");
        m.delete(blk);
        m.sync();
        m.dump(dump, false);
        m.close();
    }

    protected void tearDown() throws Exception {
        log.flush();
    }
}