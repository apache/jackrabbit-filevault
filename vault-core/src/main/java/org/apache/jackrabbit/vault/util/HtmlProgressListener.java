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

import java.io.IOException;
import java.io.Writer;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;

/**
 * <code>HtmlProgrressTrackerListener</code>...
 *
 */
public class HtmlProgressListener implements ProgressTrackerListener {

    private final Writer out;

    private boolean noScrollTo;

    private long scrollDelay = 1000;

    private long lastScrolled = 0;

    public HtmlProgressListener(Writer out) {
        this.out = out;
    }

    public boolean isNoScrollTo() {
        return noScrollTo;
    }

    public HtmlProgressListener setNoScrollTo(boolean noScrollTo) {
        this.noScrollTo = noScrollTo;
        return this;
    }

    public long getScrollDelay() {
        return scrollDelay;
    }

    public HtmlProgressListener setScrollDelay(long scrollDelay) {
        this.scrollDelay = scrollDelay;
        return this;
    }

    public void onError(Mode mode, String path, Exception e) {
        print(mode, "E", path, e.toString());
    }

    public void onMessage(Mode mode, String action, String path) {
        print(mode, action, path, null);
    }

    private void print(Mode mode, String action, String path, String msg) {
        try {
            out.write("<span class=\"");
            out.write(action);
            out.write("\">");
            out.write("<b>");
            out.write(action);
            out.write("</b>&nbsp;");
            out.write(path);
            if (msg != null) {
                out.write(" (");
                out.write(msg);
                out.write(")");
            }
            out.write("</span><br>\r\n");
            if (!noScrollTo) {
                long now = System.currentTimeMillis();
                if (now > lastScrolled + scrollDelay) {
                    lastScrolled = now;
                    out.write("<script>\r\n");
                    out.write("window.scrollTo(0, 1000000);\r\n");
                    out.write("</script>\r\n");
                }
            }
            out.flush();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}