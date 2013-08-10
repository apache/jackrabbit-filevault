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

package org.apache.jackrabbit.vault.fs.api;

import junit.framework.TestCase;

/**
 * <code>RepAddrTest</code>...
 *
 */
public class RepositoryAddressTest extends TestCase {

    public void testEmpty() throws Exception {
        RepositoryAddress ra = new RepositoryAddress("url://localhost:1234/");
        assertEquals("scheme", "url", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/", ra.getSpecificURI().getPath());
        assertEquals("workspace", null, ra.getWorkspace());
        assertEquals("path", "/", ra.getPath());
        assertEquals("toString", "url://localhost:1234/-/jcr:root", ra.toString());
    }

    public void testSpaces() throws Exception {
        RepositoryAddress ra = new RepositoryAddress("url://localhost:1234/-/jcr:root/etc/packages/a%20b");
        assertEquals("scheme", "url", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/", ra.getSpecificURI().getPath());
        assertEquals("workspace", null, ra.getWorkspace());
        assertEquals("path", "/etc/packages/a b", ra.getPath());
        assertEquals("toString", "url://localhost:1234/-/jcr:root/etc/packages/a%20b", ra.toString());
    }

    public void testReallyEmpty() throws Exception {
        RepositoryAddress ra = new RepositoryAddress("url://localhost:1234");
        assertEquals("scheme", "url", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/", ra.getSpecificURI().getPath());
        assertEquals("workspace", null, ra.getWorkspace());
        assertEquals("path", "/", ra.getPath());
        assertEquals("toString", "url://localhost:1234/-/jcr:root", ra.toString());
    }

    public void testWspOnly() throws Exception {
        RepositoryAddress ra = new RepositoryAddress("url://localhost:1234/workspace");
        assertEquals("scheme", "url", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/", ra.getSpecificURI().getPath());
        assertEquals("workspace", "workspace", ra.getWorkspace());
        assertEquals("path", "/", ra.getPath());
        assertEquals("toString", "url://localhost:1234/workspace/jcr:root", ra.toString());
    }

    public void testWspOnlyTrailingSlash() throws Exception {
        RepositoryAddress ra = new RepositoryAddress("url://localhost:1234/workspace/");
        assertEquals("scheme", "url", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/", ra.getSpecificURI().getPath());
        assertEquals("workspace", "workspace", ra.getWorkspace());
        assertEquals("path", "/", ra.getPath());
        assertEquals("toString", "url://localhost:1234/workspace/jcr:root", ra.toString());
    }

    public void testPrefix() throws Exception {
        RepositoryAddress ra = new RepositoryAddress("http://localhost:1234/pfx1/pfx2/workspace");
        assertEquals("scheme", "http", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/pfx1/pfx2", ra.getSpecificURI().getPath());
        assertEquals("workspace", "workspace", ra.getWorkspace());
        assertEquals("path", "/", ra.getPath());
        assertEquals("toString", "http://localhost:1234/pfx1/pfx2/workspace/jcr:root", ra.toString());
    }

    public void testRoot() throws Exception {
        RepositoryAddress ra = new RepositoryAddress("http://localhost:1234/pfx1/pfx2/workspace/jcr:root");
        assertEquals("scheme", "http", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/pfx1/pfx2", ra.getSpecificURI().getPath());
        assertEquals("workspace", "workspace", ra.getWorkspace());
        assertEquals("path", "/", ra.getPath());
        assertEquals("toString", "http://localhost:1234/pfx1/pfx2/workspace/jcr:root", ra.toString());
    }

    public void testRootTrailingSlash() throws Exception {
        RepositoryAddress ra = new RepositoryAddress("http://localhost:1234/pfx1/pfx2/workspace/jcr:root/");
        assertEquals("scheme", "http", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/pfx1/pfx2", ra.getSpecificURI().getPath());
        assertEquals("workspace", "workspace", ra.getWorkspace());
        assertEquals("path", "/", ra.getPath());
        assertEquals("toString", "http://localhost:1234/pfx1/pfx2/workspace/jcr:root", ra.toString());
    }

    public void testPath() throws Exception {
        RepositoryAddress ra = new RepositoryAddress("http://localhost:1234/pfx1/pfx2/workspace/jcr:root/foo/bar");
        assertEquals("scheme", "http", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/pfx1/pfx2", ra.getSpecificURI().getPath());
        assertEquals("workspace", "workspace", ra.getWorkspace());
        assertEquals("path", "/foo/bar", ra.getPath());
        assertEquals("toString", "http://localhost:1234/pfx1/pfx2/workspace/jcr:root/foo/bar", ra.toString());
    }

    public void testPathTrailingSlash() throws Exception {
        RepositoryAddress ra = new RepositoryAddress("http://localhost:1234/pfx1/pfx2/workspace/jcr:root/foo/bar/");
        assertEquals("scheme", "http", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/pfx1/pfx2", ra.getSpecificURI().getPath());
        assertEquals("workspace", "workspace", ra.getWorkspace());
        assertEquals("path", "/foo/bar", ra.getPath());
        assertEquals("toString", "http://localhost:1234/pfx1/pfx2/workspace/jcr:root/foo/bar", ra.toString());
    }

    public void testResolve() throws Exception {
        RepositoryAddress ra = 
                new RepositoryAddress("http://localhost:1234/pfx1/pfx2/workspace")
                .resolve("/foo/bar");
        assertEquals("scheme", "http", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/pfx1/pfx2", ra.getSpecificURI().getPath());
        assertEquals("workspace", "workspace", ra.getWorkspace());
        assertEquals("path", "/foo/bar", ra.getPath());
        assertEquals("toString", "http://localhost:1234/pfx1/pfx2/workspace/jcr:root/foo/bar", ra.toString());
    }

    public void testResolveSpecial() throws Exception {
        RepositoryAddress ra =
                new RepositoryAddress("http://localhost:1234/pfx1/pfx2/workspace")
                .resolve("/foo bar/bar");
        assertEquals("scheme", "http", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/pfx1/pfx2", ra.getSpecificURI().getPath());
        assertEquals("workspace", "workspace", ra.getWorkspace());
        assertEquals("path", "/foo bar/bar", ra.getPath());
        assertEquals("toString", "http://localhost:1234/pfx1/pfx2/workspace/jcr:root/foo%20bar/bar", ra.toString());
    }

    public void testResolveDefaultWsp() throws Exception {
        RepositoryAddress ra =
                new RepositoryAddress("http://localhost:1234/pfx1/pfx2/-")
                .resolve("/foo/bar");
        assertEquals("scheme", "http", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/pfx1/pfx2", ra.getSpecificURI().getPath());
        assertEquals("workspace", null, ra.getWorkspace());
        assertEquals("path", "/foo/bar", ra.getPath());
        assertEquals("toString", "http://localhost:1234/pfx1/pfx2/-/jcr:root/foo/bar", ra.toString());
    }

    public void testResolveRel() throws Exception {
        RepositoryAddress ra = 
                new RepositoryAddress("http://localhost:1234/pfx1/pfx2/workspace/jcr:root/foo")
                .resolve("bar");
        assertEquals("scheme", "http", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/pfx1/pfx2", ra.getSpecificURI().getPath());
        assertEquals("workspace", "workspace", ra.getWorkspace());
        assertEquals("path", "/foo/bar", ra.getPath());
        assertEquals("toString", "http://localhost:1234/pfx1/pfx2/workspace/jcr:root/foo/bar", ra.toString());
    }

    public void testResolveRelSpecial() throws Exception {
        RepositoryAddress ra =
                new RepositoryAddress("http://localhost:1234/pfx1/pfx2/workspace/jcr:root/foo%20bar")
                .resolve("foo bar");
        assertEquals("scheme", "http", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/pfx1/pfx2", ra.getSpecificURI().getPath());
        assertEquals("workspace", "workspace", ra.getWorkspace());
        assertEquals("path", "/foo bar/foo bar", ra.getPath());
        assertEquals("toString", "http://localhost:1234/pfx1/pfx2/workspace/jcr:root/foo%20bar/foo%20bar", ra.toString());
    }

    public void testResolveRelDefaultWsp() throws Exception {
        RepositoryAddress ra =
                new RepositoryAddress("http://localhost:1234/pfx1/pfx2/-/jcr:root/foo")
                .resolve("bar");
        assertEquals("scheme", "http", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/pfx1/pfx2", ra.getSpecificURI().getPath());
        assertEquals("workspace", null, ra.getWorkspace());
        assertEquals("path", "/foo/bar", ra.getPath());
        assertEquals("toString", "http://localhost:1234/pfx1/pfx2/-/jcr:root/foo/bar", ra.toString());
    }

    public void testDefaultWorkspace() throws Exception {
        RepositoryAddress ra =
                new RepositoryAddress("http://localhost:1234/pfx1/pfx2/-/jcr:root/foo");
        assertEquals("scheme", "http", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/pfx1/pfx2", ra.getSpecificURI().getPath());
        assertEquals("workspace", null, ra.getWorkspace());
        assertEquals("path", "/foo", ra.getPath());
        assertEquals("toString", "http://localhost:1234/pfx1/pfx2/-/jcr:root/foo", ra.toString());
    }

    public void testRmiBWC() throws Exception {
        RepositoryAddress ra =
                new RepositoryAddress("rmi://localhost:1234/crx");
        assertEquals("scheme", "rmi", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/crx", ra.getSpecificURI().getPath());
        assertEquals("workspace", null, ra.getWorkspace());
        assertEquals("path", "/", ra.getPath());
        assertEquals("toString", "rmi://localhost:1234/crx/-/jcr:root", ra.toString());
    }

    public void testRmiBWC2() throws Exception {
        RepositoryAddress ra =
                new RepositoryAddress("rmi://localhost:1234/crx/crx.default");
        assertEquals("scheme", "rmi", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/crx", ra.getSpecificURI().getPath());
        assertEquals("workspace", "crx.default", ra.getWorkspace());
        assertEquals("path", "/", ra.getPath());
        assertEquals("toString", "rmi://localhost:1234/crx/crx.default/jcr:root", ra.toString());
    }

    public void testRmiBWC3() throws Exception {
        RepositoryAddress ra =
                new RepositoryAddress("rmi://localhost:1234/crx/crx.default/foo/bar");
        assertEquals("scheme", "rmi", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 1234, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/crx", ra.getSpecificURI().getPath());
        assertEquals("workspace", "crx.default", ra.getWorkspace());
        assertEquals("path", "/foo/bar", ra.getPath());
        assertEquals("toString", "rmi://localhost:1234/crx/crx.default/jcr:root/foo/bar", ra.toString());
    }

    public void testHttpConvenience1() throws Exception {
        RepositoryAddress ra =
                new RepositoryAddress("http://localhost:8080/");
        assertEquals("scheme", "http", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 8080, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/crx/server", ra.getSpecificURI().getPath());
        assertEquals("workspace", null, ra.getWorkspace());
        assertEquals("path", "/", ra.getPath());
        assertEquals("toString", "http://localhost:8080/crx/server/-/jcr:root", ra.toString());
    }

    public void testHttpConvenience2() throws Exception {
        RepositoryAddress ra =
                new RepositoryAddress("http://localhost:8080/crx");
        assertEquals("scheme", "http", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 8080, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/crx/server", ra.getSpecificURI().getPath());
        assertEquals("workspace", null, ra.getWorkspace());
        assertEquals("path", "/", ra.getPath());
        assertEquals("toString", "http://localhost:8080/crx/server/-/jcr:root", ra.toString());
    }

    public void testHttpConvenience3() throws Exception {
        RepositoryAddress ra =
                new RepositoryAddress("http://localhost:8080/crx/server");
        assertEquals("scheme", "http", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 8080, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/crx/server", ra.getSpecificURI().getPath());
        assertEquals("workspace", null, ra.getWorkspace());
        assertEquals("path", "/", ra.getPath());
        assertEquals("toString", "http://localhost:8080/crx/server/-/jcr:root", ra.toString());
    }

    public void testHttpConvenience4() throws Exception {
        RepositoryAddress ra =
                new RepositoryAddress("http://localhost:8080/-/jcr:root");
        assertEquals("scheme", "http", ra.getSpecificURI().getScheme());
        assertEquals("host", "localhost", ra.getSpecificURI().getHost());
        assertEquals("port", 8080, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/", ra.getSpecificURI().getPath());
        assertEquals("workspace", null, ra.getWorkspace());
        assertEquals("path", "/", ra.getPath());
        assertEquals("toString", "http://localhost:8080/-/jcr:root", ra.toString());
    }

    public void testRelative() throws Exception {
        RepositoryAddress ra = new RepositoryAddress("/");
        assertEquals("scheme", null, ra.getSpecificURI().getScheme());
        assertEquals("host", null, ra.getSpecificURI().getHost());
        assertEquals("port", -1, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/", ra.getSpecificURI().getPath());
        assertEquals("workspace", null, ra.getWorkspace());
        assertEquals("path", "/", ra.getPath());
        assertEquals("toString", "/-/jcr:root", ra.toString());
    }

    public void testRelative1() throws Exception {
        RepositoryAddress ra = new RepositoryAddress("/wsp/");
        assertEquals("scheme", null, ra.getSpecificURI().getScheme());
        assertEquals("host", null, ra.getSpecificURI().getHost());
        assertEquals("port", -1, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/", ra.getSpecificURI().getPath());
        assertEquals("workspace", "wsp", ra.getWorkspace());
        assertEquals("path", "/", ra.getPath());
        assertEquals("toString", "/wsp/jcr:root", ra.toString());
    }

    public void testRelative2() throws Exception {
        RepositoryAddress ra = new RepositoryAddress("/wsp/bar");
        assertEquals("scheme", null, ra.getSpecificURI().getScheme());
        assertEquals("host", null, ra.getSpecificURI().getHost());
        assertEquals("port", -1, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/", ra.getSpecificURI().getPath());
        assertEquals("workspace", "wsp", ra.getWorkspace());
        assertEquals("path", "/bar", ra.getPath());
        assertEquals("toString", "/wsp/jcr:root/bar", ra.toString());
    }

    public void testRelative3() throws Exception {
        RepositoryAddress ra = new RepositoryAddress("/wsp/foo/bar");
        assertEquals("scheme", null, ra.getSpecificURI().getScheme());
        assertEquals("host", null, ra.getSpecificURI().getHost());
        assertEquals("port", -1, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/", ra.getSpecificURI().getPath());
        assertEquals("workspace", "wsp", ra.getWorkspace());
        assertEquals("path", "/foo/bar", ra.getPath());
        assertEquals("toString", "/wsp/jcr:root/foo/bar", ra.toString());
    }

    public void testRelative4() throws Exception {
        RepositoryAddress ra = new RepositoryAddress("/-/foo/bar");
        assertEquals("scheme", null, ra.getSpecificURI().getScheme());
        assertEquals("host", null, ra.getSpecificURI().getHost());
        assertEquals("port", -1, ra.getSpecificURI().getPort());
        assertEquals("prefix", "/", ra.getSpecificURI().getPath());
        assertEquals("workspace", null, ra.getWorkspace());
        assertEquals("path", "/foo/bar", ra.getPath());
        assertEquals("toString", "/-/jcr:root/foo/bar", ra.toString());
    }

}