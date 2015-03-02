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

package org.apache.jackrabbit.vault.packaging.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.Properties;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.InstallHook;
import org.apache.jackrabbit.vault.packaging.InstallHookProcessor;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.util.Constants;
import org.apache.jackrabbit.vault.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * processor for install hooks
 */
public class InstallHookProcessorImpl implements InstallHookProcessor {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(InstallHookProcessorImpl.class);

    private final TreeMap<String, Hook> hooks = new TreeMap<String, Hook>();

    public void registerHooks(Archive archive, ClassLoader classLoader) throws PackageException {
        try {
            Archive.Entry root = archive.getRoot();
            root = root.getChild(Constants.META_INF);
            if (root == null) {
                log.warn("Archive {} does not have a {} directory.", archive, Constants.META_INF);
                return;
            }
            root = root.getChild(Constants.VAULT_DIR);
            if (root == null) {
                log.warn("Archive {} does not have a {} directory.", archive, Constants.VAULT_DIR);
                return;
            }
            root = root.getChild(Constants.HOOKS_DIR);
            if (root == null) {
                log.debug("Archive {} does not have a {} directory.", archive, Constants.HOOKS_DIR);
            } else {
                for (Archive.Entry entry : root.getChildren()) {
                    // only respect .jar files
                    if (entry.getName().endsWith(".jar")) {
                        registerHook(archive.getInputSource(entry), classLoader);
                    }
                }
            }
            
            // also look for external hooks in properties
            // currently only the format: "installhook.{name}.class" is supported
            Properties props = archive.getMetaInf().getProperties();
            if (props != null) {
                Enumeration names = props.propertyNames();
                while (names.hasMoreElements()) {
                    String name = names.nextElement().toString();
                    if (name.startsWith(VaultPackage.PREFIX_INSTALL_HOOK)) {
                        String[] segs = Text.explode(name.substring(VaultPackage.PREFIX_INSTALL_HOOK.length()), '.');
                        if (segs.length == 0 || segs.length > 2 || !segs[1].equals("class")) {
                            throw new PackageException("Invalid installhook property: " + name);
                        }
                        Hook hook = new Hook(segs[0], props.getProperty(name), classLoader);
                        initHook(hook);
                    }
                }
            }
        } catch (IOException e) {
            throw new PackageException("I/O Error while registering hooks", e);
        }
    }

    public void registerHook(VaultInputSource input, ClassLoader classLoader) throws IOException, PackageException {
        // first we need to spool the jar file to disk.
        File jarFile = File.createTempFile("vaulthook", ".jar");
        Hook hook = new Hook(input.getSystemId(), jarFile, classLoader);

        OutputStream out = null;
        InputStream in = input.getByteStream();
        try {
            out = FileUtils.openOutputStream(jarFile);
            IOUtils.copy(in, out);
        } catch (IOException e) {
            hook.destroy();
            throw e;
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
        initHook(hook);
    }

    private void initHook(Hook hook) throws IOException, PackageException {
        try {
            hook.init();
        } catch (IOException e) {
            log.error("Error while initializing hook: {}", e.toString());
            hook.destroy();
            throw e;
        } catch (PackageException e) {
            log.error("Error while initializing hook: {}", e.toString());
            hook.destroy();
            throw e;
        }
        hooks.put(hook.name, hook);
        log.info("Hook {} registered.", hook.name);
    }

    public boolean hasHooks() {
        return !hooks.isEmpty();
    }
    
    public boolean execute(InstallContext context) {
        for (Hook hook : hooks.values()) {
            try {
                hook.getHook().execute(context);
            } catch (PackageException e) {
                // abort processing only for prepare phase
                if (context.getPhase() == InstallContext.Phase.PREPARE) {
                    log.warn("Hook " + hook.name +" threw package exception. Prepare aborted.", e);
                    return false;
                }
                log.warn("Hook " + hook.name +" threw package exception. Ignored", e);
            } catch (Throwable e) {
                log.warn("Hook " + hook.name +" threw runtime exception.", e);
            }
            // if in end phase, shutdown hooks
            if (context.getPhase() == InstallContext.Phase.END) {
                hook.destroy();
            }
        }
        return true;
    }

    private class Hook {

        private final String name;

        private final File jarFile;

        private ClassLoader classLoader;

        private ClassLoader parentClassLoader;

        private InstallHook hook;

        private String mainClassName;

        private Hook(String name, String mainClassName, ClassLoader parentClassLoader) {
            this.name = name;
            this.mainClassName = mainClassName;
            this.parentClassLoader = parentClassLoader;
            this.jarFile = null;
        }

        private Hook(String name, File jarFile, ClassLoader parentClassLoader) {
            this.name = name;
            this.jarFile = jarFile;
            this.parentClassLoader = parentClassLoader;
        }

        private void destroy() {
            parentClassLoader = null;
            classLoader = null;
            hook = null;
            if (jarFile != null) {
                FileUtils.deleteQuietly(jarFile);
            }
        }

        private void init() throws IOException, PackageException {
            // create classloader
            if (parentClassLoader == null) {
                parentClassLoader = Thread.currentThread().getContextClassLoader();
            }

            if (jarFile != null) {
                // open jar file and get manifest
                JarFile jar = new JarFile(jarFile);
                Manifest mf = jar.getManifest();
                if (mf == null) {
                    throw new PackageException("hook jar file does not have a manifest: " + name);
                }
                mainClassName = mf.getMainAttributes().getValue("Main-Class");
                if (mainClassName == null) {
                    throw new PackageException("hook manifest file does not have a Main-Class entry: " + name);
                }
                classLoader = URLClassLoader.newInstance(
                    new URL[]{jarFile.toURL()},
                    parentClassLoader);
            } else {
                classLoader = parentClassLoader;
            }
            loadMainClass();
        }

        private void loadMainClass() throws PackageException {
            log.info("Loading Hook {}: Main-Class = {}", name, mainClassName);

            // find main class
            Class clazz;
            try {
                clazz = classLoader.loadClass(mainClassName);
            } catch (ClassNotFoundException e) {
                throw new PackageException("hook's main class " + mainClassName + " not found: " + name, e);
            }
            if (!InstallHook.class.isAssignableFrom(clazz)) {
                throw new PackageException("hook's main class " + mainClassName + " does not implement the InstallHook interface: " + name);
            }
            // create instance
            try {
                hook = (InstallHook) clazz.newInstance();
            } catch (Exception e) {
                throw new PackageException("hook's main class " + mainClassName + " could not be instantiated.", e);
            }
        }

        public InstallHook getHook() {
            return hook;
        }
    }
}
