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
package org.apache.jackrabbit.vault.util;

import static org.junit.Assert.assertEquals;

import java.io.StringWriter;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.junit.Test;

/**
 *
 */
public class HtmlProgressListenerTest {

    @Test
    public void testSimpleOnMessage() {
        StringWriter out = new StringWriter();
        HtmlProgressListener l = new HtmlProgressListener(out);
        l.setNoScrollTo(true);
        l.onMessage(ProgressTrackerListener.Mode.PATHS, "A", "/content/foo");
        assertEquals("<span class=\"A\"><b>A</b>&nbsp;/content/foo</span><br>\r\n", out.toString());
    }

    @Test
    public void testXSSOnMessage() {
        StringWriter out = new StringWriter();
        HtmlProgressListener l = new HtmlProgressListener(out);
        l.setNoScrollTo(true);
        l.onMessage(ProgressTrackerListener.Mode.PATHS, "A", "<script>alert('hello');</script>");
        assertEquals("<span class=\"A\"><b>A</b>&nbsp;&lt;script&gt;alert(&apos;hello&apos;);&lt;/script&gt;</span><br>\r\n", out.toString());
    }

    @Test
    public void testSimpleOnError() {
        StringWriter out = new StringWriter();
        HtmlProgressListener l = new HtmlProgressListener(out);
        l.setNoScrollTo(true);
        l.onError(ProgressTrackerListener.Mode.PATHS, "/content/foo", new Exception("Test Exception"));
        assertEquals("<span class=\"E\"><b>E</b>&nbsp;/content/foo (java.lang.Exception: Test Exception)</span><br>\r\n", out.toString());
    }

    @Test
    public void testXSSOnError() {
        StringWriter out = new StringWriter();
        HtmlProgressListener l = new HtmlProgressListener(out);
        l.setNoScrollTo(true);
        l.onError(ProgressTrackerListener.Mode.PATHS, "/content/foo", new Exception("<script>alert('hello');</script>"));
        assertEquals("<span class=\"E\"><b>E</b>&nbsp;/content/foo (java.lang.Exception: &lt;script&gt;alert(&apos;hello&apos;);&lt;/script&gt;)</span><br>\r\n", out.toString());
    }


}