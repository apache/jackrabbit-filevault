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

package org.apache.jackrabbit.vault.packaging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * {@code VersionRangeTest}...
 */
public class VersionRangeTest {

    private final Version v09 = Version.create("0.9");
    private final Version v1 = Version.create("1.0");
    private final Version v11 = Version.create("1.1");
    private final Version v2 = Version.create("2.0");
    private final Version v21 = Version.create("2.1");
    private final Version v1s = Version.create("1.0-SNAPSHOT");

    @Test
    public void testInfinite() {
        assertTrue("Infinite range includes all versions", VersionRange.INFINITE.isInRange(v1));
    }

    @Test
    public void testLowerBoundIncl() {
        VersionRange vr = new VersionRange(v1, true, null, false);
        assertTrue("[1.0,] includes 1.0", vr.isInRange(v1));
        assertTrue("[1.0,] includes 2.0", vr.isInRange(v2));
        assertFalse("[1.0,] excludes 0.9", vr.isInRange(v09));
    }

    @Test
    public void testLowerBoundExcl() {
        VersionRange vr = new VersionRange(v1, false, null, false);
        assertFalse("(1.0,] excludes 1.0", vr.isInRange(v1));
        assertTrue("(1.0,] includes 2.0", vr.isInRange(v2));
        assertFalse("(1.0,] excludes 0.9", vr.isInRange(v09));
    }

    @Test
    public void testUpperBoundIncl() {
        VersionRange vr = new VersionRange(null, false, v2, true);
        assertTrue("[,2.0] includes 1.0", vr.isInRange(v1));
        assertTrue("[,2.0] includes 2.0", vr.isInRange(v2));
        assertFalse("[,2.0] excludes 2.1", vr.isInRange(v21));
    }

    @Test
    public void testUpperBoundExcl() {
        VersionRange vr = new VersionRange(null, false, v2, false);
        assertTrue("[,2.0) includes 1.0", vr.isInRange(v1));
        assertFalse("[,2.0) excludes 2.0", vr.isInRange(v2));
        assertFalse("[,2.0) excludes 2.1", vr.isInRange(v21));
    }

    @Test
    public void testRangeInclIncl() {
        VersionRange vr = new VersionRange(v1, true, v2, true);
        assertFalse("[1.0,2.0] excludes 0.9", vr.isInRange(v09));
        assertTrue("[1.0,2.0] includes 1.0", vr.isInRange(v1));
        assertTrue("[1.0,2.0] includes 1.1", vr.isInRange(v11));
        assertTrue("[1.0,2.0] includes 2.0", vr.isInRange(v2));
        assertFalse("[1.0,2.0] excludes 2.1", vr.isInRange(v21));
    }

    @Test
    public void testRangeExclIncl() {
        VersionRange vr = new VersionRange(v1, false, v2, true);
        assertFalse("(1.0,2.0] excludes 0.9", vr.isInRange(v09));
        assertFalse("(1.0,2.0] includes 1.0", vr.isInRange(v1));
        assertTrue("(1.0,2.0] includes 1.1", vr.isInRange(v11));
        assertTrue("(1.0,2.0] includes 2.0", vr.isInRange(v2));
        assertFalse("(1.0,2.0] excludes 2.1", vr.isInRange(v21));
    }

    @Test
    public void testRangeInclExcl() {
        VersionRange vr = new VersionRange(v1, true, v2, false);
        assertFalse("[1.0,2.0) excludes 0.9", vr.isInRange(v09));
        assertTrue("[1.0,2.0) includes 1.0", vr.isInRange(v1));
        assertTrue("[1.0,2.0) includes 1.1", vr.isInRange(v11));
        assertFalse("[1.0,2.0) includes 2.0", vr.isInRange(v2));
        assertFalse("[1.0,2.0) excludes 2.1", vr.isInRange(v21));
    }

    @Test
    public void testRangeExclExcl() {
        VersionRange vr = new VersionRange(v1, false, v2, false);
        assertFalse("(1.0,2.0) excludes 0.9", vr.isInRange(v09));
        assertFalse("(1.0,2.0) includes 1.0", vr.isInRange(v1));
        assertTrue("(1.0,2.0) includes 1.1", vr.isInRange(v11));
        assertFalse("(1.0,2.0) includes 2.0", vr.isInRange(v2));
        assertFalse("(1.0,2.0) excludes 2.1", vr.isInRange(v21));
    }

    @Test
    public void testRangeWithEmptyVersionParameter() {
        VersionRange vr = new VersionRange(v1, true, v2, true);
        assertFalse("[1.0,2.0] excludes empty version", vr.isInRange(Version.EMPTY));
    }

    /**
     * A snapshot precedes the released version, so 1.0-SNAPSHOT is not contained in the range [1.0,2.0)
     * - but the boundary can be a snapshot.
     *
     * @see <a href="https://github.com/apache/maven/blob/maven-3.8.6/maven-artifact/src/test/java/org/apache/maven/artifact/versioning/VersionRangeTest.java#L657">Maven VersionRangeTest.java</a>
     */
    @Test
    public void testRangeSnapshots() {
        VersionRange vr = VersionRange.fromString("[1.0,2.0)");
        assertFalse("[1.0,2.0) excludes 1.0-SNAPSHOT", vr.isInRange(v1s));

        vr = VersionRange.fromString("[1.0-SNAPSHOT,2.0)");
        assertTrue("[1.0-SNAPSHOT,2.0) includes 1.0-SNAPSHOT", vr.isInRange(v1s));
    }

    @Test
    public void testRangeInvalid() {
        try {
            new VersionRange(v2, false, v1, false);
            fail("invalid range (2.0,1.0) must fail");
        } catch (Exception e) {
            // ignore
        }
    }

    @Test
    public void testRangeInvalid2() {
        try {
            new VersionRange(v1, false, v1, false);
            fail("invalid range (1.0,1.0) must fail");
        } catch (Exception e) {
            // ignore
        }
    }

    @Test
    public void testParse() {
        VersionRange vr = VersionRange.fromString("[1.0,2.0]");
        assertEquals(v1, vr.getLow());
        assertEquals(v2, vr.getHigh());
        assertEquals(true, vr.isLowInclusive());
        assertEquals(true, vr.isHighInclusive());
    }

    @Test
    public void testParse2() {
        VersionRange vr = VersionRange.fromString("(1.0,2.0)");
        assertEquals(v1, vr.getLow());
        assertEquals(v2, vr.getHigh());
        assertEquals(false, vr.isLowInclusive());
        assertEquals(false, vr.isHighInclusive());
    }

    @Test
    public void testParse3() {
        VersionRange vr = VersionRange.fromString("1.0");
        assertEquals(v1, vr.getLow());
        assertEquals(null, vr.getHigh());
        assertEquals(true, vr.isLowInclusive());
    }

    @Test
    public void testParse4() {
        VersionRange vr = VersionRange.fromString("(1.0,]");
        assertEquals(v1, vr.getLow());
        assertEquals(null, vr.getHigh());
        assertEquals(false, vr.isLowInclusive());
    }

    @Test
    public void testParse5() {
        VersionRange vr = VersionRange.fromString("[,2.0]");
        assertEquals(null, vr.getLow());
        assertEquals(v2, vr.getHigh());
        assertEquals(true, vr.isHighInclusive());
    }

    @Test
    public void testParseEmptyString() {
        VersionRange vr = VersionRange.fromString("");
        assertSame(VersionRange.INFINITE, vr); // always return same instance
    }

    @Test
    public void testToString() {
        VersionRange vr = new VersionRange(v1, true, v2, true);
        assertEquals("[1.0,2.0]", vr.toString());
    }

    @Test
    public void testToString2() {
        VersionRange vr = new VersionRange(v1, false, v2, false);
        assertEquals("(1.0,2.0)", vr.toString());
    }

    @Test
    public void testToString3() {
        VersionRange vr = new VersionRange(v1, false, null, false);
        assertEquals("(1.0,)", vr.toString());
    }

    @Test
    public void testToString4() {
        VersionRange vr = new VersionRange(v1, true, null, false);
        assertEquals("1.0", vr.toString());
    }

    @Test
    public void testToString5() {
        VersionRange vr = new VersionRange(null, false, v2, true);
        assertEquals("[,2.0]", vr.toString());
    }

    @Test
    public void testToString6() {
        VersionRange vr = new VersionRange(null, false, v2, false);
        assertEquals("[,2.0)", vr.toString());
    }
}
