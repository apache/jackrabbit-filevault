/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.vlt.meta;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.jackrabbit.vault.vlt.VltException;
import org.apache.jackrabbit.vault.vlt.meta.xml.XmlEntries;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reproduces GH-13: a working copy's {@code entries.xml} is trusted bytes-on-disk, but a
 * poisoned copy (e.g. planted by a swapped {@code .vlt} control zip, see GH-12) can carry
 * attacker-controlled {@code name} attributes on {@code <entry>} and on its
 * {@code mine}/{@code theirs}/{@code base} children. Those names are later bound to
 * {@code java.io.File} via plain concatenation ({@code VltFile}, {@code XmlEntry.revertConflict},
 * {@code XmlEntry.delete}), so a traversal name must be rejected before it is ever loaded.
 */
class XmlEntriesTraversalTest {

    private static final String HEADER =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<entries path=\"/content\">\n";

    private static final String FOOTER = "</entries>";

    @Test
    void entryNameWithTraversalSegmentsIsRejected() {
        String xml = HEADER + " <entry name=\"../../../../tmp/evil.txt\" rp=\"\" ap=\"/content/evil.txt\">\n"
                + "   <work date=\"2018-10-02T11:44:02.000+02:00\" md5=\"268b8e1f6d7b3fc9ec71226ee1a9dc70\" size=\"4\"/>\n"
                + " </entry>\n"
                + FOOTER;
        assertThrows(VltException.class, () -> loadEntries(xml));
    }

    @Test
    void entryNameWithPathSeparatorIsRejected() {
        String xml = HEADER + " <entry name=\"sub/evil.txt\" rp=\"\" ap=\"/content/evil.txt\">\n"
                + "   <work date=\"2018-10-02T11:44:02.000+02:00\" md5=\"268b8e1f6d7b3fc9ec71226ee1a9dc70\" size=\"4\"/>\n"
                + " </entry>\n"
                + FOOTER;
        assertThrows(VltException.class, () -> loadEntries(xml));
    }

    @Test
    void entryNamedDotVltIsRejected() {
        // an entry literally named the control zip would let a later sink overwrite it.
        String xml = HEADER + " <entry name=\".vlt\" rp=\"\" ap=\"/content/.vlt\">\n"
                + "   <work date=\"2018-10-02T11:44:02.000+02:00\" md5=\"268b8e1f6d7b3fc9ec71226ee1a9dc70\" size=\"4\"/>\n"
                + " </entry>\n"
                + FOOTER;
        assertThrows(VltException.class, () -> loadEntries(xml));
    }

    @Test
    void conflictInfoNameWithTraversalSegmentsIsRejected() {
        // the "mine"/"theirs"/"base" name attributes are exactly what XmlEntry.revertConflict
        // and XmlEntry.delete bind to java.io.File via new File(dir, info.getName()).
        String xml = HEADER + " <entry name=\"safe.txt\" rp=\"\" ap=\"/content/safe.txt\">\n"
                + "   <base date=\"2018-10-02T11:44:02.000+02:00\" md5=\"268b8e1f6d7b3fc9ec71226ee1a9dc70\" size=\"4\"/>\n"
                + "   <mine name=\"../../../../tmp/evil.mine\" date=\"2018-10-02T11:44:02.000+02:00\" md5=\"268b8e1f6d7b3fc9ec71226ee1a9dc70\" size=\"4\"/>\n"
                + "   <theirs name=\"safe.txt.theirs\" date=\"2018-10-02T11:44:02.000+02:00\" md5=\"268b8e1f6d7b3fc9ec71226ee1a9dc70\" size=\"4\"/>\n"
                + " </entry>\n"
                + FOOTER;
        assertThrows(VltException.class, () -> loadEntries(xml));
    }

    @Test
    void plainEntryAndConflictNamesAreStillAccepted() throws VltException {
        String xml = HEADER + " <entry name=\"safe.txt\" rp=\"\" ap=\"/content/safe.txt\">\n"
                + "   <base date=\"2018-10-02T11:44:02.000+02:00\" md5=\"268b8e1f6d7b3fc9ec71226ee1a9dc70\" size=\"4\"/>\n"
                + "   <mine name=\"safe.txt.mine\" date=\"2018-10-02T11:44:02.000+02:00\" md5=\"268b8e1f6d7b3fc9ec71226ee1a9dc70\" size=\"4\"/>\n"
                + "   <theirs name=\"safe.txt.theirs\" date=\"2018-10-02T11:44:02.000+02:00\" md5=\"268b8e1f6d7b3fc9ec71226ee1a9dc70\" size=\"4\"/>\n"
                + " </entry>\n"
                + FOOTER;
        XmlEntries entries = loadEntries(xml);
        assertTrue(entries.hasEntry("safe.txt"));
    }

    private static XmlEntries loadEntries(String xml) throws VltException {
        return XmlEntries.load(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
