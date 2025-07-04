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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Properties;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.vault.fs.api.VaultInputSource;
import org.apache.jackrabbit.vault.fs.io.Archive;
import org.apache.jackrabbit.vault.packaging.InstallContext;
import org.apache.jackrabbit.vault.packaging.InstallHook;
import org.apache.jackrabbit.vault.packaging.InstallHookProcessor;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.VaultPackage;
import org.apache.jackrabbit.vault.util.Constants;
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

    private final TreeMap<String, Hook> hooks = new TreeMap<>();

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
                Enumeration<?> names = props.propertyNames();
                while (names.hasMoreElements()) {
                    String name = names.nextElement().toString();
                    if (name.startsWith(VaultPackage.PREFIX_INSTALL_HOOK)) {
                        String[] segs = Text.explode(name.substring(VaultPackage.PREFIX_INSTALL_HOOK.length()), '.');
                        if (segs.length == 0 || segs.length > 2 || !"class".equals(segs[1])) {
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
        Path jarFile = Files.createTempFile("vaulthook", ".jar");
        Hook hook = new Hook(input.getSystemId(), jarFile, classLoader);

        try (InputStream in = input.getByteStream()) {
            Files.copy(in, jarFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                hook.destroy();
            } catch (IOException ioeDuringDestroy) {
                e.addSuppressed(ioeDuringDestroy);
            }
            throw e;
        }
        initHook(hook);
    }

    private void initHook(Hook hook) throws IOException, PackageException {
        try {
            hook.init();
        } catch (IOException|PackageException e) {
            log.error("Error while initializing hook: {}", e.toString());
            try {
                hook.destroy();
            } catch (IOException ioeDuringDestroy) {
                e.addSuppressed(ioeDuringDestroy);
            }
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
            } catch (Throwable e) {
                // abort processing only for prepare and installed phase
                if (context.getPhase() == InstallContext.Phase.PREPARE || context.getPhase() == InstallContext.Phase.INSTALLED) {
                    log.warn("Hook {} threw exception. {} aborted.", hook.name, context.getPhase(), e);
                    return false;
                }
                log.warn("Hook {} threw exception. Ignored", hook.name, e);
            }
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        IOException ioException = null;
        for (Hook hook : hooks.values()) {
            try {
                hook.destroy();
            } catch (IOException e) {
                if (ioException == null) {
                    ioException = new IOException("Error while destroying one or more hooks. Look at suppressed exceptions for details!");
                }
                ioException.addSuppressed(e);
            }
        }
        if (ioException != null) {
            throw ioException;
        }
    }

    private class Hook {

        private final String name;

        private final Path jarFile;

        private ClassLoader parentClassLoader;

        private URLClassLoader urlClassLoader;

        private InstallHook hook;

        private String mainClassName;

        private Hook(String name, String mainClassName, ClassLoader parentClassLoader) {
            this.name = name;
            this.mainClassName = mainClassName;
            this.parentClassLoader = parentClassLoader;
            this.jarFile = null;
        }

        private Hook(String name, Path jarFile, ClassLoader parentClassLoader) {
            this.name = name;
            this.jarFile = jarFile;
            this.parentClassLoader = parentClassLoader;
        }

        private void destroy() throws IOException {
            parentClassLoader = null;
            hook = null;
            if (urlClassLoader != null) {
                urlClassLoader.close();
            }
            if (jarFile != null) {
                Files.deleteIfExists(jarFile);
            }
        }

        private void init() throws IOException, PackageException {
            try {
                if (jarFile != null) {
                    // open jar file and extract classname from manifest
                    try (JarFile jar = new JarFile(jarFile.toFile())) {
                        Manifest mf = jar.getManifest();
                        if (mf == null) {
                            throw new PackageException("hook jar file does not have a manifest: " + name);
                        }
                        mainClassName = mf.getMainAttributes().getValue("Main-Class");
                        if (mainClassName == null) {
                            throw new PackageException("hook manifest file does not have a Main-Class entry: " + name);
                        }
                    }
                    // create classloader
                    if (parentClassLoader == null) {
                        try {
                            // 1st fallback is the current classes classloader (the bundle classloader in the OSGi context)
                            urlClassLoader = URLClassLoader.newInstance(
                                    new URL[] { jarFile.toUri().toURL() },
                                    this.getClass().getClassLoader());
                            loadMainClass(urlClassLoader);
                        } catch (ClassNotFoundException cnfe) {
                            urlClassLoader.close();
                            // 2nd fallback is the thread context classloader
                            urlClassLoader = URLClassLoader.newInstance(
                                    new URL[] { jarFile.toUri().toURL() },
                                    Thread.currentThread().getContextClassLoader());
                            loadMainClass(urlClassLoader);
                        }
                    } else {
                        urlClassLoader = URLClassLoader.newInstance(
                                new URL[] { jarFile.toUri().toURL() },
                                parentClassLoader);
                        loadMainClass(urlClassLoader);
                    }
                } else {
                    // create classloader
                    if (parentClassLoader == null) {
                        try {
                            // 1st fallback is the current classes classloader (the bundle classloader in the OSGi context)
                            loadMainClass(this.getClass().getClassLoader());
                        } catch (ClassNotFoundException cnfe) {
                            // 2nd fallback is the thread context classloader
                            loadMainClass(Thread.currentThread().getContextClassLoader());
                        }
                    } else {
                        loadMainClass(parentClassLoader);
                    }
                }
            } catch (ClassNotFoundException cnfe) {
                throw new PackageException("hook's main class " + mainClassName + " not found: " + name, cnfe);
            }
        }

        private void loadMainClass(ClassLoader classLoader) throws PackageException, ClassNotFoundException {
            log.info("Loading Hook {}: Main-Class = {}", name, mainClassName);

            // find main class
            Class<?> clazz = classLoader.loadClass(mainClassName);
            if (!InstallHook.class.isAssignableFrom(clazz)) {
                throw new PackageException("hook's main class " + mainClassName + " does not implement the InstallHook interface: " + name);
            }
            // create instance
            try {
                hook = (InstallHook) clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new PackageException("hook's main class " + mainClassName + " could not be instantiated.", e);
            }
        }

        public InstallHook getHook() {
            return hook;
        }
    }
}
