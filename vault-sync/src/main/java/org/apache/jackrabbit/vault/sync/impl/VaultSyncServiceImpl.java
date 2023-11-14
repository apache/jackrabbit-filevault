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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.UnsupportedRepositoryOperationException;
import javax.jcr.observation.Event;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;

import org.apache.jackrabbit.util.Text;
import org.apache.sling.jcr.api.SlingRepository;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code VaultSyncServiceImpl}...
 */
@Component(
        immediate = true,
        property = {"service.vendor=The Apache Software Foundation"}
)
@Designate(ocd = VaultSyncServiceImpl.Config.class)
public class VaultSyncServiceImpl implements EventListener, Runnable {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(VaultSyncServiceImpl.class);

    public static final String SYNC_SPECS = "vault.sync.syncroots";

    public static final String SYNC_ENABLED = "vault.sync.enabled";

    private Session session;

    private SyncHandler[] syncHandlers = new SyncHandler[0];

    private boolean enabled;

    private long checkDelay;

    private Thread fsCheckThread;

    private final Lock waitLock = new ReentrantLock();

    private final Condition waitCondition = waitLock.newCondition();

    @ObjectClassDefinition(
            name = "Vault Sync Service"
    )
    @interface Config {

        @AttributeDefinition(
                name = "Sync filesystem directories"
        )
        String[] vault_sync_syncroots() default {};

        @AttributeDefinition(
                name = "FS check interval (seconds)"
        )
        int vault_sync_fscheckinterval() default 5;

        @AttributeDefinition(
                name = "Enabled"
        )
        boolean vault_sync_enabled() default false;


    }

    @Activate
    public VaultSyncServiceImpl(@Reference SlingRepository slingRepository, Config config) throws RepositoryException {
        this(slingRepository.loginAdministrative(null), 
                config.vault_sync_enabled(), 
                config.vault_sync_fscheckinterval() * 1000l, 
                Arrays.stream(config.vault_sync_syncroots()).map(File::new).collect(Collectors.toList()));
    }

    VaultSyncServiceImpl(Session session, boolean isEnabled, long fsCheckIntervalMilliseconds, Collection<File> syncRoots) throws RepositoryException {
        List<SyncHandler> newSyncSpecs = new LinkedList<>();
        for (File syncRoot : syncRoots) {
            SyncHandler spec = new SyncHandler(syncRoot);
            newSyncSpecs.add(spec);
            log.info("Added sync specification: {}", spec);
        }
        this.session = session;
        syncHandlers = newSyncSpecs.toArray(new SyncHandler[newSyncSpecs.size()]);
        enabled = isEnabled;
        checkDelay = fsCheckIntervalMilliseconds;

        log.info("Vault Sync service is {}", enabled ? "enabled" : "disabled");
        if (enabled) {
            // set up observation listener
            session.getWorkspace().getObservationManager().addEventListener(
                    this,
                    Event.NODE_ADDED | Event.NODE_REMOVED | Event.PROPERTY_CHANGED | Event.PROPERTY_ADDED | Event.PROPERTY_REMOVED,
                    "/",
                    true /* isDeep */,
                    null /* uuid */,
                    null /* nodeTypeName */,
                    true /* noLocal */
            );
            fsCheckThread = new Thread(this, "Vault Sync Thread");
            fsCheckThread.setDaemon(true);
            fsCheckThread.start();
        }

    }

    @Deactivate
    protected void deactivate() throws RepositoryException {
        waitLock.lock();
        try {
            enabled = false;
            waitCondition.signalAll();
        } finally {
            waitLock.unlock();
        }
        if (fsCheckThread != null) {
            try {
                fsCheckThread.join();
            } catch (InterruptedException e) {
                log.warn("error while waiting for thread to terminate", e);
                fsCheckThread.interrupt();
            }
            fsCheckThread = null;
        }
        // session is still accessed in fsCheckThread, therefore close it last
        if (session != null) {
            session.getWorkspace().getObservationManager().removeEventListener(this);
            session.logout();
            session = null;
        }
    }

    public void run() {
        waitLock.lock();
        try {
            while (enabled) {
                SyncHandler[] specs = syncHandlers;
                try {
                    for (SyncHandler spec : specs) {
                        spec.prepareForSync();
                    }
                    waitLock.unlock();
                    for (SyncHandler spec : specs) {
                        try {
                            spec.sync(session);
                        } catch (RepositoryException e) {
                            log.warn("Error during sync", e);
                        } catch (IOException e) {
                            log.warn("Error during sync", e);
                        }
                    }
                } finally {
                    waitLock.lock();
                }
                try {
                    waitCondition.await(checkDelay, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    log.warn("interrupted while waiting.");
                    Thread.currentThread().interrupt();
                }
            }
        } finally {
            waitLock.unlock();
        }
    }

    public void onEvent(EventIterator events) {
        try {
            Set<String> modified = new HashSet<String>();
            Set<String> deleted = new HashSet<String>();
            while (events.hasNext()) {
                Event evt = events.nextEvent();
                String path = evt.getPath();
                if (evt.getType() == Event.PROPERTY_ADDED
                        || evt.getType() == Event.PROPERTY_CHANGED
                        || evt.getType() == Event.PROPERTY_REMOVED) {
                    path = Text.getRelativeParent(path, 1);
                }
                // currently we only support nt:files, so we can ignore everything below jcr:content
                int idx = path.indexOf("/jcr:content");
                if (idx >= 0) {
                    path = path.substring(0, idx);
                }
                if (evt.getType() == Event.NODE_REMOVED) {
                    deleted.add(evt.getIdentifier());
                    modified.add(path);
                } else if (evt.getType() == Event.NODE_ADDED) {
                    if (deleted.contains(evt.getIdentifier())) {
                        modified.add(path + "/");
                    } else {
                        modified.add(path);
                    }
                } else {
                    modified.add(path);
                }
                log.debug("Received JCR event {} leading to the following modifications: {}", evt, String.join(",", modified));
            }
            waitLock.lock();
            try {
                for (String path: modified) {
                    SyncHandler spec = getSyncHandler(path);
                    if (spec != null) {
                        spec.registerPendingJcrChange(path);
                    }
                }
                waitCondition.signalAll();
            } finally {
                waitLock.unlock();
            }
        } catch (RepositoryException e) {
            log.warn("Error while processing events", e);
        }
    }

    private SyncHandler getSyncHandler(String path) {
        for (SyncHandler spec : syncHandlers) {
            if (spec.covers(path)) {
                return spec;
            }
        }
        return null;
    }
}