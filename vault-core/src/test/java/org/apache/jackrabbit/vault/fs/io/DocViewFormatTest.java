package org.apache.jackrabbit.vault.fs.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DocViewFormatTest {

    private File dir;
    private File docViewFile;

    @Before
    public void setup() throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");
        dir = new File(tempDir + File.separator + "DocViewFormatTest" + new Date().toString());
        assert dir.mkdir();
        docViewFile = new File(dir.getPath() + File.separator + "malformed.xml");
        assert docViewFile.createNewFile();

        try (InputStream in = this.getClass().getClassLoader()
                .getResourceAsStream("org/apache/jackrabbit/vault/fs/io/DocViewFormat/malformed.xml")) {
            try (OutputStream out = new FileOutputStream(docViewFile)) {
                IOUtils.copy(in, out);
            }
        }
    }

    @After
    public void tearDown() {
        if (!docViewFile.delete()) {
            docViewFile.deleteOnExit();
            dir.deleteOnExit();
        } else {
            if (!dir.delete()) {
                dir.deleteOnExit();
            }
        }
    }

    @Test
    public void testFormatting() throws IOException {
        List<Pattern> patterns = Arrays.asList(Pattern.compile(".+\\.xml"));
        assertFalse("malformed.xml is expected to be malformed", DocViewFormat.checkFormat(dir, patterns).isEmpty());
        DocViewFormat.format(dir, patterns);
        assertTrue("malformed.xml is expected to be formatted", DocViewFormat.checkFormat(dir, patterns).isEmpty());
    }
}
