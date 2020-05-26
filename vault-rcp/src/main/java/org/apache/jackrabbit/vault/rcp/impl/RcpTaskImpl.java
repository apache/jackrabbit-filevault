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
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.ConfigurationException;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.fs.filter.DefaultPathFilter;
import org.apache.jackrabbit.vault.rcp.RcpTask;
import org.apache.jackrabbit.vault.util.RepositoryCopier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code RcpTask}...
 */
public class RcpTaskImpl implements Runnable, RcpTask {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(RcpTaskImpl.class);

    private final RcpTaskManagerImpl mgr;

    private final String id;

    private RepositoryCopier rcp = new RepositoryCopier();

    private final RepositoryAddress src;

    private final Credentials srcCreds;

    private final String dst;

    private final boolean recursive;

    private volatile Result result;

    private List<String> excludes;

    private Thread thread;

    private Session srcSession;

    private Session dstSession;
    
    private final static class ResultImpl implements RcpTask.Result {

        private final State state;
        private final Throwable throwable;

        public ResultImpl(State state) {
            this(state, null);
        }
        
        public ResultImpl(State state, Throwable throwable) {
            super();
            this.state = state;
            this.throwable = throwable;
        }

        @Override
        public State getState() {
            return state;
        }

        @Override
        public Throwable getThrowable() {
            return throwable;
        }
        
    }

    public RcpTaskImpl(RcpTaskManagerImpl mgr, RepositoryAddress src, Credentials srcCreds, String dst, String id) {
        this(mgr, src, srcCreds, dst, id, (WorkspaceFilter)null, false);
    }

    public RcpTaskImpl(RcpTaskManagerImpl mgr, RepositoryAddress src, Credentials srcCreds, String dst, String id, List<String> excludes, boolean recursive) throws ConfigurationException {
        this(mgr, src, srcCreds, dst, id, createFilterForExcludes(excludes), recursive);
        this.excludes = excludes;
    }

    private static WorkspaceFilter createFilterForExcludes(List<String> excludes) throws ConfigurationException {
        // could be done better
        DefaultWorkspaceFilter srcFilter = new DefaultWorkspaceFilter();
        PathFilterSet filterSet = new PathFilterSet("/");
        for (String path: excludes) {
            filterSet.addExclude(new DefaultPathFilter(path));
        }
        return srcFilter;
    }

    public RcpTaskImpl(RcpTaskManagerImpl mgr, RepositoryAddress src, Credentials srcCreds, String dst, String id, WorkspaceFilter srcFilter, boolean recursive) {
        this.mgr = mgr;
        this.src = src;
        this.dst = dst;
        this.srcCreds = srcCreds;
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
        this.recursive = recursive;
        if (srcFilter != null) {
            rcp.setSourceFilter(srcFilter);
        }
        result = new ResultImpl(Result.State.NEW);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public RepositoryCopier getRcp() {
        return rcp;
    }

    @Override
    public boolean stop() {
        // wait for thread
        if (result.getState() != Result.State.STOPPED && result.getState() != Result.State.STOPPING) {
            rcp.abort();
            int cnt = 3;
            while (thread != null && thread.isAlive() && cnt-- > 0) {
                result = new ResultImpl(Result.State.STOPPING);
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
            result = new ResultImpl(Result.State.STOPPED);
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

    @Override
    public boolean start(Session session) throws RepositoryException {
        if (result.getState() != Result.State.NEW) {
            throw new IllegalStateException("Unable to start task " + id + ". wrong state = " + result.getState());
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
        result = new ResultImpl(Result.State.RUNNING);
        log.info("Starting repository copy task id={}. From {} to {}.", new Object[]{
                id, src.toString(), dst
        });
        try {
            rcp.copy(srcSession, src.getPath(), dstSession, dst, recursive);
            result = new ResultImpl(Result.State.ENDED);
        } catch (Throwable e) {
            result = new ResultImpl(Result.State.ENDED, e);
        }
        // todo: notify manager that we ended.
    }

    @Override
    public Result getResult() {
        return result;
    }

    @Override
    public RepositoryAddress getSource() {
        return src;
    }

    @Override
    public String getDestination() {
        return dst;
    }

    @Override
    public boolean isRecursive() {
        return recursive;
    }

    @Override
    public List<String> getExcludes() {
        return excludes;
    }

}