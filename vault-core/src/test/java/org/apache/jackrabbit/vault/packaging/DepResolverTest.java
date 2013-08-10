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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

/**
 * <code>DepResolverTest</code>...
 */
public class DepResolverTest extends TestCase {

    public static PackageId P1 = PackageId.fromString("foo:pack1:1.0");
    public static PackageId P2 = PackageId.fromString("foo:pack2:1.0");
    public static PackageId P3 = PackageId.fromString("foo:pack3:1.0");
    public static PackageId P4 = PackageId.fromString("foo:pack4:1.0");
    public static PackageId P5 = PackageId.fromString("foo:pack5:1.0");

    public static Dependency D1 = Dependency.fromString("foo:pack1:1.0");
    public static Dependency D2 = Dependency.fromString("foo:pack2:1.0");
    public static Dependency D3 = Dependency.fromString("foo:pack3:1.0");
    public static Dependency D4 = Dependency.fromString("foo:pack4:1.0");
    public static Dependency D5 = Dependency.fromString("foo:pack5:1.0");

    public void testLinear() throws CyclicDependencyException {
        Map<PackageId, Dependency[]> deps = new LinkedHashMap<PackageId, Dependency[]>();
        // p1 -> p2, p3
        deps.put(P1, new Dependency[]{D2, D3});
        // p2 -> p4
        deps.put(P2, new Dependency[]{D4});
        // p3 -> p5
        deps.put(P3, new Dependency[]{D5});
        // p4 -> p5
        deps.put(P4, new Dependency[]{D5});
        // p5
        deps.put(P5, Dependency.EMPTY);
        
        // expect: p5, p4, p2, p3, p1
        PackageId[] expect = new PackageId[]{P5, P4, P2, P3, P1};
        List<PackageId> result = DependencyUtil.resolve(deps);
        assertEquals("package list", expect, result);
    }

    public void testLinear2() throws CyclicDependencyException {
        Map<PackageId, Dependency[]> deps = new LinkedHashMap<PackageId, Dependency[]>();
        // p3 -> p4, p5
        deps.put(P3, new Dependency[]{D4, D5});
        // p4 -> p2, p5
        deps.put(P4, new Dependency[]{D2, D5});
        // p1 -> p2, p3, p4
        deps.put(P1, new Dependency[]{D2, D3, D4});
        // p2
        deps.put(P2, Dependency.EMPTY);
        // p5
        deps.put(P5, Dependency.EMPTY);

        // expect: p5, p4, p2, p3, p1
        PackageId[] expect = new PackageId[]{P2, P5, P4, P3, P1};
        List<PackageId> result = DependencyUtil.resolve(deps);
        assertEquals("package list", expect, result);
    }

    public void testMissing() throws Exception {
        Map<PackageId, Dependency[]> deps = new LinkedHashMap<PackageId, Dependency[]>();
        // p3 -> p4, p5
        deps.put(P3, new Dependency[]{D4, D5});
        // p4 -> p2, p5
        deps.put(P4, new Dependency[]{D2, D5});
        // p1 -> p2, p3, p4
        deps.put(P1, new Dependency[]{D2, D3, D4});
        // p2
        deps.put(P2, Dependency.EMPTY);

        // expect: p4, p2, p3, p1
        PackageId[] expect = new PackageId[]{P2, P4, P3, P1};
        List<PackageId> result = DependencyUtil.resolve(deps);
        assertEquals("package list", expect, result);
    }

    public void testCircular() throws Exception {
        Map<PackageId, Dependency[]> deps = new LinkedHashMap<PackageId, Dependency[]>();
        // p1 -> p2
        deps.put(P1, new Dependency[]{D2});
        // p2 -> p3, p4
        deps.put(P2, new Dependency[]{D3, D4});
        // p4 -> p1, p5
        deps.put(P4, new Dependency[]{D1, D5});
        try {
            DependencyUtil.resolve(deps);
            fail("Expected cyclic dep exception.");
        } catch (CyclicDependencyException e) {
            // ignore
        }
    }

    private void assertEquals(String msg, PackageId[] expect, List<PackageId> result) {
        String expStr = "";
        for (PackageId pid: expect) {
            expStr+=pid.toString() + "\n";
        }
        String resStr = "";
        for (PackageId pid: result) {
            resStr+=pid.toString() + "\n";
        }
        assertEquals(msg, expStr, resStr);
    }
}