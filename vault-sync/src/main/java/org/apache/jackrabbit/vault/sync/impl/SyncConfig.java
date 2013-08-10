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
package org.apache.jackrabbit.vault.sync.impl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>SyncConfig</code>...
 */
public class SyncConfig {


    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(SyncConfig.class);

    private static final String LINE_ENDING = System.getProperty("line.separator");

    private static final String DEFAULT_CONFIG = "default-config.properties";

    public static final String PROP_SYNC_ONCE = "sync-once";

    public static final String PROP_FILTER = "filter";

    public static final String PROP_DISABLED = "disabled";

    public static final String PROP_SYNC_LOG = "sync-log";

    private final File file;

    private final LinkedHashMap<String, Line> lines = new LinkedHashMap<String, Line>();

    private SyncMode syncOnce;

    private String syncLog;

    private Boolean disabled;

    public SyncConfig(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public SyncMode getSyncOnce() {
        return syncOnce;
    }

    public void setSyncOnce(SyncMode syncOnce) {
        this.syncOnce = syncOnce;
    }

    public String getSyncLog() {
        return syncLog;
    }

    public void setSyncLog(String syncLog) {
        this.syncLog = syncLog;
    }

    public Boolean getDisabled() {
        return disabled;
    }

    public void setDisabled(Boolean disabled) {
        this.disabled = disabled;
    }

    public boolean isDisabled() {
        return disabled == null ? false : disabled;
    }

    public void load() throws IOException {
        if (!file.isFile()) {
            return;
        }
        Reader r = null;
        try {
            r = new InputStreamReader(FileUtils.openInputStream(file), "utf-8");
            load(r);
        } finally {
            IOUtils.closeQuietly(r);
        }
    }
    private void load(Reader r) throws IOException {
        BufferedReader br = new BufferedReader(r);
        int lineNo=1;
        lines.clear();
        String line;
        while ((line = br.readLine()) != null) {
            Line l = new Line(line, lineNo++);
            lines.put(l.name, l);
        }

        // update internals
        String sm = getString(PROP_SYNC_ONCE, "");
        syncOnce = null;
        if (sm.length() > 0) {
            try {
                syncOnce = SyncMode.valueOf(sm.toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown syncOnce value: " + e);
            }
        }
        syncLog = getString(PROP_SYNC_LOG, null);
        disabled = getBoolean(PROP_DISABLED, false);
    }

    private String getString(String name, String defaultValue) {
        Line l = lines.get(name);
        if (l == null || l.value == null) {
            return defaultValue;
        } else {
            return l.value;
        }
    }

    private boolean getBoolean(String name, boolean defaultValue) {
        Line l = lines.get(name);
        if (l == null || l.value == null) {
            return defaultValue;
        } else {
            return l.value.equals("true");
        }
    }

    private void setProperty(String name, String value) {
        Line l = lines.get(name);
        if (l != null) {
            l.setValue(value);
        }
    }

    public void save() throws IOException {
        setProperty(PROP_SYNC_ONCE, syncOnce == null ? "" : syncOnce.name().toLowerCase());
        setProperty(PROP_SYNC_LOG, syncLog);
        setProperty(PROP_DISABLED, String.valueOf(disabled));

        Writer w = null;
        try {
            w = new OutputStreamWriter(FileUtils.openOutputStream(file), "utf-8");
            for (Line l: lines.values()) {
                w.write(l.getLine());
                w.write(LINE_ENDING);
            }
        } finally {
            IOUtils.closeQuietly(w);
        }
    }

    public void init() throws IOException {
        if (!file.exists()) {
            // load default config
            InputStream in = SyncConfig.class.getResourceAsStream(DEFAULT_CONFIG);
            if (in == null) {
                log.error("Unable to load default config.");
            } else {
                Reader r = null;
                try {
                    r = new InputStreamReader(in, "utf-8");
                    load(r);
                } finally {
                    IOUtils.closeQuietly(r);
                }
                try {
                    save();
                } catch (IOException e) {
                    log.warn("Unable to save initial config: " + e.toString());
                }
            }
        } else {
            load();
        }
    }


    private class Line {

        private final String name;

        private String line;

        private String value;

        private Line(String name, String value) {
            this.name = name;
            setValue(value);
        }

        public void setValue(String value) {
            this.value = value;
            line = name + "=" + value;
        }

        public String getLine() {
            return line;
        }

        private Line(String line, int lineNo) {
            this.line = line;

            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                name = "comment-" + UUID.randomUUID();
            } else if (trimmed.length() == 0) {
                name = "blank-" + UUID.randomUUID();
            } else {
                int idx = trimmed.indexOf('=');
                if (idx < 0) {
                    log.warn("Syntax error in {}, line {}: Name/Value pair expected.", file.getAbsolutePath(), lineNo);
                    name = "error-" + UUID.randomUUID();
                    return;
                }
                name = trimmed.substring(0, idx).trim();
                value = trimmed.substring(idx + 1);
            }
        }
    }
}