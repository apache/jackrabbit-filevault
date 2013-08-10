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

package org.apache.jackrabbit.vault.vlt.meta;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.PathFilter;
import org.apache.jackrabbit.vault.fs.api.PathMapping;
import org.apache.jackrabbit.vault.fs.config.VaultSettings;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.util.PlatformNameFormat;
import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.VltDirectory;
import org.apache.jackrabbit.vault.vlt.VltException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>Ignored</code>...
 */
public class Ignored implements PathFilter {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(Ignored.class);

    public static final String FILE_NAME = ".vltignore";

    private final VaultSettings settings;

    private final VltContext ctx;

    private final int rootLength;

    private final File scanRoot;

    private List<PathFilter> ignored;

    public Ignored(VltContext ctx, File scanRoot) {
        this.ctx = ctx;
        this.settings = ctx.getExportRoot().getMetaInf().getSettings();
        this.scanRoot = scanRoot;
        rootLength = ctx.getExportRoot().getJcrRoot().getAbsolutePath().length();
    }

    public List<PathFilter> getIgnored() {
        if (ignored == null) {
            long now = System.currentTimeMillis();
            ignored = new LinkedList<PathFilter>();
            try {
                scan(scanRoot);
            } catch (VltException e) {
                log.error("Error while scanning for " + FILE_NAME, e);
            } catch (IOException e) {
                log.error("Error while scanning for " + FILE_NAME, e);
            }
            log.info("scanned for .vltignore files below {} in {}ms", scanRoot, System.currentTimeMillis() - now);
        }
        return ignored;
    }


    public boolean matches(String path) {
        for (PathFilter p: getIgnored()) {
            if (p.matches(path)) {
                return true;
            }
        }
        return false;
    }

    public Ignored scan(File dir) throws VltException, IOException {
        for (File file: dir.listFiles()) {
            String name = file.getName();
            if (settings != null && settings.isIgnored(name)) {
                continue;
            }
            if (file.isDirectory()) {
                scan(file);
            } else if (name.equals(FILE_NAME)) {
                addIgnores(dir, file);
            }
        }
        return this;
    }

    private void addIgnores(File dir, File file) throws VltException, IOException {
        VltDirectory d = new VltDirectory(ctx, dir);
        String root = d.getAggregatePath();
        if (root == null) {
            root = dir.getAbsolutePath().substring(rootLength);
            root = PlatformNameFormat.getRepositoryPath(root);
            log.info("Unable to detect correct repository path for {}. guessed: {}", dir.getPath(), root);
        }
        for (Object o: FileUtils.readLines(file, "utf-8")) {
            addIgnored(root, o.toString());
        }
    }

    private void addIgnored(String root, String pattern) {
        if (pattern.startsWith("#")) {
            return;
        }
        root = root.replace('\\', '/');
        StringBuffer reg = new StringBuffer("^");
        reg.append(root).append("/");
        for (int i=0; i<pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c=='*') {
                reg.append(".*");
            } else if (c=='?') {
                reg.append(".");
            } else if (c=='.') {
                reg.append("\\.");
            } else {
                reg.append(c);
            }
        }
        reg.append("$");
        log.info("Adding ignored path: {}", reg.toString());
        ignored.add(new DefaultPathFilter(reg.toString()));
    }

    public boolean isAbsolute() {
        return true;
    }

    public void dump(DumpContext ctx, boolean isLast) {
        ctx.printf(isLast, "%s:", getClass().getSimpleName());
        ctx.indent(isLast);
        Iterator<PathFilter> iter = getIgnored().iterator();
        while (iter.hasNext()) {
            PathFilter e = iter.next();
            e.dump(ctx, !iter.hasNext());
        }
        ctx.outdent();
    }

    public PathFilter translate(PathMapping mapping) {
        return this;
    }
}