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
package org.apache.jackrabbit.vault.vlt.actions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;

import org.apache.jackrabbit.commons.JcrUtils;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.sync.impl.VaultSyncServiceImpl;
import org.apache.jackrabbit.vault.util.Text;
import org.apache.jackrabbit.vault.vlt.VltContext;
import org.apache.jackrabbit.vault.vlt.VltException;

/**
 * <code>Checkout</code>...
 */
public class Sync extends AbstractAction {

    public enum Command {
        STATUS,
        ST,
        REGISTER,
        UNREGISTER,
        INIT,
        INSTALL
    }

    private static final String[] INSTALL_ROOT = {"libs", "crx", "vault", "install"};
    private static final String CFG_NODE_NAME = VaultSyncServiceImpl.class.getName();
    private static final String CFG_NODE_PATH = "/libs/crx/vault/config/" + CFG_NODE_NAME;
    private static final String CFG_ROOTS = VaultSyncServiceImpl.SYNC_SPECS;
    private static final String CFG_ENABLED = VaultSyncServiceImpl.SYNC_ENABLED;
    private RepositoryAddress mountPoint;

    private File localDir;

    private final Command cmd;

    private boolean force;

    public Sync(Command cmd, RepositoryAddress mountPoint, File localDir) {
        this.cmd = cmd;
        this.mountPoint = mountPoint;
        this.localDir = localDir;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public void run(VltContext ctx) throws VltException {
        if (mountPoint == null) {
            mountPoint = ctx.getMountpoint();
        }
        if (mountPoint == null) {
            throw ctx.error(ctx.getCwd().getAbsolutePath(), "No remote specified and not in vlt checkout.");
        }

        // currently we just read the config node, assuming it's at the correct location
        Session s = null;
        try {
            s = ctx.login(mountPoint);
            switch (cmd) {
                case STATUS:
                case ST:
                    status(ctx, s);
                    break;
                case REGISTER:
                    register(ctx, s, null);
                    break;
                case UNREGISTER:
                    unregister(ctx, s);
                    break;
                case INSTALL:
                    install(ctx, s);
                    break;
                case INIT:
                    init(ctx, s);
            }
        } catch (RepositoryException e) {
            throw new VltException("Error while performing command", e);
        } finally {
            if (s != null) {
                s.logout();
            }
        }
    }

    private void init(VltContext ctx, Session s) throws VltException, RepositoryException {
        // check if in vlt checkout
        if (ctx.getExportRoot().isValid()) {
            ctx.getStdout().printf("Starting initialization of sync service in existing vlt checkout %s for %s%n",
                    ctx.getExportRoot().getJcrRoot().getAbsolutePath(),
                    mountPoint);
            // check if config is present, assume installed
            Config cfg = new Config(s);
            if (!cfg.load(ctx)) {
                force = true;
                install(ctx, s);
            }
            register(ctx, s, true);
            ctx.getStdout().printf(
                    "%nThe directory %1$s is now enabled for syncing.%n" +
                    "You might perform a 'sync-once' by setting the%n" +
                    "appropriate flag in the %1$s/.vlt-sync-config.properties file.%n%n",
                    localDir.getAbsolutePath());
        } else {
            ctx.getStdout().printf("Starting initialization of sync service in a non vlt checkout directory %s for %s%n",
                    localDir.getAbsolutePath(),
                    mountPoint);
            // check if empty
            if (localDir.listFiles().length > 0) {
                throw new VltException("Aborting initialization since directory is not empty.");
            }
            // check if config is present, assume installed
            Config cfg = new Config(s);
            if (!cfg.load(ctx)) {
                force = true;
                install(ctx, s);
            }
            register(ctx, s, true);
            ctx.getStdout().printf(
                    "%nThe directory %1$s is now enabled for syncing.%n" +
                    "You need to configure the filter %1$s/.vlt-sync-filter.xml to setup the%n" +
                    "proper paths. You might also perform a 'sync-once' by setting the%n" +
                    "appropriate flag in the %1$s/.vlt-sync-config.properties file.%n%n",
                    localDir.getAbsolutePath());
        }
    }

    private void status(VltContext ctx, Session s) throws RepositoryException {
        Config cfg = new Config(s);
        if (!cfg.load(ctx)) {
            ctx.getStdout().println("No sync-service configured at " + CFG_NODE_PATH);
            return;
        }
        ctx.getStdout().println("Listing sync status for " + mountPoint);
        ctx.getStdout().println("- Sync service is " + (cfg.enabled ? "enabled." : "disabled."));
        if (cfg.roots.isEmpty()) {
            ctx.getStdout().println("- No sync directories configured.");
        } else {
            for (String path : cfg.roots) {
                ctx.getStdout().println("- syncing directory: " + path);
            }
        }
    }

    private void register(VltContext ctx, Session s, Boolean enable) throws RepositoryException {
        Config cfg = new Config(s);
        if (!cfg.load(ctx)) {
            ctx.getStdout().println("No sync-service configured at " + CFG_NODE_PATH);
            return;
        }
        for (String path: cfg.roots) {
            // need to check canonical path
            try {
                File f = new File(path).getCanonicalFile();
                if (f.equals(localDir)) {
                    ctx.getStdout().println("Directory is already synced: " + localDir.getAbsolutePath());
                    return;
                }
            } catch (IOException e) {
                // ignore
            }
        }
        cfg.roots.add(localDir.getAbsolutePath());
        if (enable != null) {
            cfg.enabled = enable;
        }
        cfg.save(ctx);
        ctx.getStdout().println("Added new sync directory: " + localDir.getAbsolutePath());
    }

    private void unregister(VltContext ctx, Session s) throws RepositoryException {
        Config cfg = new Config(s);
        if (!cfg.load(ctx)) {
            ctx.getStdout().println("No sync-service configured at " + CFG_NODE_PATH);
            return;
        }
        boolean found = false;
        for (String path: cfg.roots) {
            // need to check canonical path
            try {
                File f = new File(path).getCanonicalFile();
                if (f.equals(localDir)) {
                    found = true;
                    break;
                }
            } catch (IOException e) {
                // ignore
            }
        }
        if (!found) {
            ctx.getStdout().println("Directory is not registered: " + localDir.getAbsolutePath());
            return;
        }
        cfg.roots.remove(localDir.getAbsolutePath());
        cfg.save(ctx);
        ctx.getStdout().println("Removed sync directory: " + localDir.getAbsolutePath());
    }

    private void install(VltContext ctx, Session s) throws RepositoryException, VltException {
        // get sync jar
        URLClassLoader cl = (URLClassLoader) VaultSyncServiceImpl.class.getClassLoader();
        URL resource = null;
        for (URL url: cl.getURLs()) {
            if (url.getPath().matches(".*/vault-sync-.*\\.jar")) {
                resource = url;
                break;
            }
        }
        if (resource == null) {
            throw new VltException("Unable to find vault-sync.jar library.");
        }
        String jarName = Text.getName(resource.getPath());
        ctx.getStdout().println("Preparing to install " + jarName + "...");

        Node root = s.getRootNode();
        for (String name: INSTALL_ROOT) {
            root = JcrUtils.getOrAddFolder(root, name);
        }
        // check if already a bundle is installed
        for (Node child: JcrUtils.getChildNodes(root)) {
            if (child.getName().startsWith("vault-sync-")) {
                if (force) {
                    ctx.getStdout().println("Detected existing bundle: " + child.getName() + ". Updating");
                    break;
                } else {
                    ctx.getStdout().println("Detected existing bundle: " + child.getName() + ". Aborting installation. Specify --force to update.");
                    return;
                }
            }
        }
        InputStream in = null;
        try {
            in = resource.openStream();
            if (root.hasNode(jarName)) {
                root.getNode(jarName).remove();
            }
            JcrUtils.putFile(root, jarName, "application/octet-stream", in, Calendar.getInstance());
        } catch (IOException e) {
            throw new VltException("Error while installing bundle", e);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
        ctx.getStdout().println("Updated bundle: " + jarName);

        // update config
        root = JcrUtils.getOrAddFolder(root.getParent(), "config");
        if (!root.hasNode(CFG_NODE_NAME)) {
            root.addNode(CFG_NODE_NAME, "sling:OsgiConfig");
            Config cfg = new Config(s);
            cfg.enabled = true;
            cfg.save(ctx);
            ctx.getStdout().println("Created new config at " + CFG_NODE_PATH);
        }
        s.save();
    }

    private static class Config {

        private final Session s;

        private boolean enabled = false;

        private Set<String> roots = new LinkedHashSet<String>();

        private Config(Session s) {
            this.s = s;
        }

        public boolean load(VltContext ctx) throws RepositoryException {
            if (!s.nodeExists(CFG_NODE_PATH)) {
                return false;
            }
            Node cfgNode = s.getNode(CFG_NODE_PATH);
            if (cfgNode.hasProperty(CFG_ENABLED)) {
                enabled = cfgNode.getProperty(CFG_ENABLED).getBoolean();
            }
            if (cfgNode.hasProperty(CFG_ROOTS)) {
                Property roots = cfgNode.getProperty(CFG_ROOTS);
                for (Value v : roots.getValues()) {
                    this.roots.add(v.getString());
                }
            }
            return true;
        }

        public void save(VltContext ctx) throws RepositoryException {
            // assume node exists
            Node cfgNode = s.getNode(CFG_NODE_PATH);
            cfgNode.setProperty(CFG_ENABLED, enabled);
            Value[] vals = new Value[roots.size()];
            int i=0;
            for (String path: roots) {
                vals[i++] = s.getValueFactory().createValue(path);
            }
            cfgNode.setProperty(CFG_ROOTS, vals);
            s.save();
        }
    }
}