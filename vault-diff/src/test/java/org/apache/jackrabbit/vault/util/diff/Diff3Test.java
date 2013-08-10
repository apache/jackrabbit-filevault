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
 * <code>Diff3Test</code>...
 */
public class Diff3Test extends TestCase {

    private static final String base = "a,b,c,d,e,f,g,";

    public void testModifyNothing() {
        doTest(base, base, base, base, false);
    }
    
    public void testModify() {
        String change = "a,b,c2,d,e,f,g,";
        doTest(base, change, base, change, false);
        doTest(base, base, change, change, false);
    }

    public void testInsert() {
        String change = "a,b,c,cc,d,e,f,g,";
        doTest(base, change, base, change, false);
        doTest(base, base, change, change, false);
    }

    public void testDelete() {
        String change = "a,b,f,g,";
        doTest(base, change, base, change, false);
        doTest(base, base, change, change, false);
    }

    public void testModifySame() {
        String change = "a,b,c2,d,e,f,g,";
        doTest(base, change, change, change, false);
    }

    public void testDeleteBoth() {
        String change = "a,b,f,g,";
        doTest(base, change, change, change, false);
    }

    public void testInsertBoth() {
        String change = "a,b,c,cc,ccc,d,e,f,g,";
        doTest(base, change, change, change, false);
    }

    public void testDeleteInclusive() {
        String left = "a,b,e,f,g,";
        String right = "a,b,g,";
        String result = "a,b," + conflict("c,d,e,f,", "e,f,", "") + "g,";
        doTest(base, left, right, result, true);
    }

    public void testDeleteAndInsertSame() {
        String left = "a,b,c2,e,f,g,";
        String right = "a,b,c2,g,";
        String result = "a,b," + conflict("c,d,e,f,", "c2,e,f,", "c2,") + "g,";
        doTest(base, left, right, result, true);
    }

    public void testModifyDifferent() {
        String left  = "a,b,c1,d1,e,f,g,";
        String right = "a,b,c2,d2,e,f,g,";
        String result = "a,b," + conflict("c,d,", "c1,d1,", "c2,d2,") + "e,f,g,";
        doTest(base, left, right, result, true);
    }

    public void testInsertLeftModifyRight() {
        String left  = "a,b,b1,c,d,e,f,g,";
        String right = "a,b,c2,d,e,f,g,";
        String result = "a,b," + conflict("c,", "b1,c,", "c2,") + "d,e,f,g,";
        doTest(base, left, right, result, true);
    }

    public void testDeleteLeftModifyRight() {
        String left = "a,b,e,f,g,";
        String right = "a,b,c2,d2,e,f,g,";
        String result = "a,b," + conflict("c,d,", "", "c2,d2,") + "e,f,g,";
        doTest(base, left, right, result, true);
    }

    public void testDeleteSameInsertDifferent() {
        String left = "a,b,b1,b2,g,";
        String right = "a,b,c1,g,";
        String result = "a,b," + conflict("c,d,e,f,", "b1,b2,", "c1,") + "g,";
        doTest(base, left, right, result, true);
    }

    public void testDeleteOverlapping() {
        String left = "a,f,g,";
        String right = "a,b,c,g,";
        String result = "a," + conflict("b,c,d,e,f,", "f,", "b,c,") + "g,";
        doTest(base, left, right, result, true);
    }

    public void testNoLastEOL() {
        String left = "a,b,c";
        String right = "a,b,c";
        String result = "a,b,c";
        doTest(base, left, right, result, false);
    }

    private String conflict(String base, String left, String right) {
        base = base.replaceAll(",", "\n");
        left = left.replaceAll(",", "\n");
        right = right.replaceAll(",", "\n");
        return Hunk3.getMarker(Hunk3.MARKER_L, null) + "\n" +
                left +
                Hunk3.getMarker(Hunk3.MARKER_B, null) + "\n" +
                base +
                Hunk3.getMarker(Hunk3.MARKER_M, null) + "\n" +
                right +
                Hunk3.getMarker(Hunk3.MARKER_R, null) + "\n";
    }

    private void doTest(String base, String left, String right, String result, boolean hasConflicts) {
        base = base.replaceAll(",", "\n");
        left = left.replaceAll(",", "\n");
        right = right.replaceAll(",", "\n");
        result = result.replaceAll(",", "\n");
        Document d1 = new Document(null, LineElementsFactory.create(null, base, false));
        Document d2 = new Document(null, LineElementsFactory.create(null, left, false));
        Document d3 = new Document(null, LineElementsFactory.create(null, right, false));

        DocumentDiff3 diff = d1.diff3(d2, d3);
        StringBuffer buf = new StringBuffer();
        diff.write(buf, DiffWriter.LS_UNIX, true);
        assertEquals("result", result, buf.toString());
        assertEquals("conflicts", hasConflicts, diff.hasConflicts());
    }
}