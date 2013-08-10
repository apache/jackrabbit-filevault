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

package org.apache.jackrabbit.vault.vlt;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.vlt.meta.xml.zip.UpdateableZipFile;
import org.junit.Ignore;

/**
 * <code>Test</code>...
 */
@Ignore
public class Test {

    public static final String TEXT = "/*\n" +
            " * Licensed to the Apache Software Foundation (ASF) under one or more\n" +
            " * contributor license agreements.  See the NOTICE file distributed with\n" +
            " * this work for additional information regarding copyright ownership.\n" +
            " * The ASF licenses this file to You under the Apache License, Version 2.0\n" +
            " * (the \"License\"); you may not use this file except in compliance with\n" +
            " * the License.  You may obtain a copy of the License at\n" +
            " *\n" +
            " *      http://www.apache.org/licenses/LICENSE-2.0\n" +
            " *\n" +
            " * Unless required by applicable law or agreed to in writing, software\n" +
            " * distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
            " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
            " * See the License for the specific language governing permissions and\n" +
            " * limitations under the License.\n" +
            " */\n";
    
    public static void main(String[] args) throws IOException {
        File testFile = new File("test.dat");
        FileUtils.writeStringToFile(testFile, TEXT);

        UpdateableZipFile testZip = new UpdateableZipFile(new File("test.zip"));
        for (int i=0; i<100; i++) {
            String name = testFile.getName() + i;
            System.out.println("adding " + name);
            testZip.update(name, new FileInputStream(testFile));
            testZip.sync();
        }
        testZip.update("test.dat50", new FileInputStream(testFile));
        testZip.delete("test.dat1");
        testZip.close();
    }

    public static void putFile(File zip, File file, String name) throws IOException {
        // create tmp file
        File newZip = new File(zip.getName() + "." + System.currentTimeMillis());
        ZipOutputStream out = new ZipOutputStream(
                new BufferedOutputStream(new FileOutputStream(newZip)));
        out.setLevel(Deflater.NO_COMPRESSION);
        if (zip.exists()) {
            ZipFile zipFile = new ZipFile(zip, ZipFile.OPEN_DELETE | ZipFile.OPEN_READ);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().equals(name)) {
                    out.putNextEntry(entry);
                    copy(zipFile.getInputStream(entry), out);
                }
            }
            zipFile.close();
        }
        ZipEntry entry = new ZipEntry(name);
        entry.setSize(file.length());
        out.putNextEntry(entry);
        copy(new FileInputStream(file), out);
        out.close();
        zip.delete();
        newZip.renameTo(zip);
    }

    public static void copy(InputStream in, ZipOutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        in.close();
    }
}