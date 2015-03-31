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
package org.apache.jackrabbit.vault.rcp.impl;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.jcr.Credentials;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.vault.davex.DAVExRepositoryFactory;
import org.apache.jackrabbit.vault.fs.api.PathFilterSet;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.util.RepositoryCopier;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.io.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <code>RcpTask</code>...
 */
public class RcpTask implements Runnable {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(RcpTask.class);

    enum STATE {
        NEW,
        RUNNING,
        ENDED,
        STOPPING,
        STOPPED
    }
    private final RcpTaskManagerImpl mgr;

    private final String id;

    private RepositoryCopier rcp = new RepositoryCopier();

    private final RepositoryAddress src;

    private final String dst;

    private boolean recursive;

    private File logFile;

    private volatile STATE state = STATE.NEW;

    private Exception error = null;

    private List<String> excludes = new ArrayList<String>();

    private Thread thread;

    private Session srcSession;

    private Session dstSession;

    public RcpTask(RcpTaskManagerImpl mgr, RepositoryAddress src, String dst, String id) {
        this.mgr = mgr;
        this.src = src;
        this.dst = dst;
        this.id = id == null || id.length() == 0
                ? UUID.randomUUID().toString()
                : id;
        rcp.setTracker(new ProgressTrackerListener(){
            public void onMessage(Mode mode, String action, String path) {
                log.info("{} {}", action, path);
            }

            public void onError(Mode mode, String path, Exception e) {
                log.error("{} {}", path, e.toString());
            }
        });
    }

    public String getId() {
        return id;
    }

    public RepositoryCopier getRcp() {
        return rcp;
    }

    public boolean stop() {
        // wait for thread
        if (state != STATE.STOPPED && state != STATE.STOPPING) {
            rcp.abort();
            int cnt = 3;
            while (thread != null && thread.isAlive() && cnt-- > 0) {
                state = STATE.STOPPING;
                log.info("Stopping task {}...", id);
                try {
                    thread.join(10000);
                } catch (InterruptedException e) {
                    log.error("Error while waiting for thread: " + thread.getName(), e);
                }
                if (thread.isAlive()) {
                    // try to interrupt the thread
                    thread.interrupt();
                }
            }
            state = STATE.STOPPED;
            thread = null;
            if (srcSession != null) {
                srcSession.logout();
                srcSession = null;
            }
            if (dstSession != null) {
                dstSession.logout();
                dstSession = null;
            }
            log.info("Stopping task {}...done", id);
        }
        return true;
    }

    public boolean remove() {
        stop();
        mgr.remove(this);
        return true;
    }

    public boolean start(Session session) throws RepositoryException {
        if (state != STATE.NEW) {
            throw new IllegalStateException("Unable to start task " + id + ". wrong state = " + state);
        }
        // clone session
        dstSession = session.impersonate(new SimpleCredentials(session.getUserID(), new char[0]));
        ClassLoader dynLoader = mgr.getDynamicClassLoader();
        ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(dynLoader);
        try {
            srcSession = getSourceSession(src);
        } finally {
            Thread.currentThread().setContextClassLoader(oldLoader);
        }

        thread  = new Thread(this, "Vault RCP Task - " + id);
        thread.setContextClassLoader(dynLoader);
        thread.start();
        return true;
    }

    private Session getSourceSession(RepositoryAddress src) throws RepositoryException {
        DAVExRepositoryFactory factory = new DAVExRepositoryFactory();
        Repository srcRepo;
        try {
            srcRepo = factory.createRepository(src);
        } catch (RepositoryException e) {
            log.error("Error while retrieving src repository {}: {}", src, e.toString());
            throw e;
        }
        try {
            Credentials srcCreds = src.getCredentials();
            String wsp = src.getWorkspace();
            if (wsp == null) {
                return srcRepo.login(srcCreds);
            } else {
                return srcRepo.login(srcCreds, wsp);
            }
        } catch (RepositoryException e) {
            log.error("Error while logging in src repository {}: {}", src, e.toString());
            throw e;
        }
    }

    public void run() {
        state = STATE.RUNNING;
        log.info("Starting repository copy task id={}. From {} to {}.", new Object[]{
                id, src.toString(), dst
        });
        try {
            rcp.copy(srcSession, src.getPath(), dstSession, dst, recursive);
            state = STATE.ENDED;
        } catch (Exception e) {
            error = e;
        } finally {
            state = STATE.ENDED;
        }
        // todo: notify manager that we ended.
    }

    public STATE getState() {
        return state;
    }

    public RepositoryAddress getSource() {
        return src;
    }

    public String getDestination() {
        return dst;
    }

    public void setRecursive(boolean b) {
        this.recursive = b;
    }

    public void addExclude(String exclude) {
        excludes.add(exclude);
        // could be done better
        DefaultWorkspaceFilter srcFilter = new DefaultWorkspaceFilter();
        PathFilterSet filterSet = new PathFilterSet("/");
        for (String path: excludes) {
            filterSet.addExclude(new DefaultPathFilter(path));
        }
        srcFilter.add(filterSet);
        rcp.setSourceFilter(srcFilter);

    }

    public void write(JSONWriter w) throws JSONException {
        w.object();
        w.key(RcpServlet.PARAM_ID).value(id);
        w.key(RcpServlet.PARAM_SRC).value(src.toString());
        w.key(RcpServlet.PARAM_DST).value(dst);
        w.key(RcpServlet.PARAM_RECURSIVE).value(recursive);
        w.key(RcpServlet.PARAM_BATCHSIZE).value(rcp.getBatchSize());
        w.key(RcpServlet.PARAM_UPDATE).value(rcp.isUpdate());
        w.key(RcpServlet.PARAM_ONLY_NEWER).value(rcp.isOnlyNewer());
        w.key(RcpServlet.PARAM_NO_ORDERING).value(rcp.isNoOrdering());
        w.key(RcpServlet.PARAM_THROTTLE).value(rcp.getThrottle());
        w.key(RcpServlet.PARAM_RESUME_FROM).value(rcp.getResumeFrom());
        if (excludes.size() > 0) {
            w.key(RcpServlet.PARAM_EXCLUDES).array();
            for (String exclude: excludes) {
                w.value(exclude);
            }
            w.endArray();
        }
        w.key("status").object();
        w.key(RcpServlet.PARAM_STATE).value(state.name());
        w.key("currentPath").value(rcp.getCurrentPath());
        w.key("lastSavedPath").value(rcp.getLastKnownGood());
        w.key("totalNodes").value(rcp.getTotalNodes());
        w.key("totalSize").value(rcp.getTotalSize());
        w.key("currentSize").value(rcp.getCurrentSize());
        w.key("currentNodes").value(rcp.getCurrentNumNodes());
        w.key("error").value(error == null ? "" : error.toString());
        w.endObject();
        w.endObject();
    }
}