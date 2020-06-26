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

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
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

/** {@code RcpTask}... */
public class RcpTaskImpl implements Runnable, RcpTask, Serializable {

    /**
     * 
     */
    private static final long serialVersionUID = -63586992801753312L;

    /** default logger */
    private static final Logger log = LoggerFactory.getLogger(RcpTaskImpl.class);

    private final String id;

    private final RepositoryAddress src;

    private final Credentials srcCreds;

    private final String dst;

    private final boolean recursive;

    private List<String> excludes;

    private transient Result result;

    private transient RepositoryCopier rcp;

    private transient Thread thread;

    private transient Session srcSession;

    private transient Session dstSession;

    /** classloader used in the thread executing the task */
    private transient ClassLoader classLoader;

    private WorkspaceFilter filter;

    private static final class ResultImpl implements RcpTask.Result {

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

    public RcpTaskImpl(ClassLoader dynLoader, RepositoryAddress src, Credentials srcCreds, String dst, String id) {
        this(dynLoader, src, srcCreds, dst, id, (WorkspaceFilter) null, false);
    }

    public RcpTaskImpl(ClassLoader dynLoader, RepositoryAddress src, Credentials srcCreds, String dst, String id, List<String> excludes,
            boolean recursive) throws ConfigurationException {
        this(dynLoader, src, srcCreds, dst, id, createFilterForExcludes(excludes), recursive);
        this.excludes = excludes;
    }

    public RcpTaskImpl(ClassLoader dynLoader, RepositoryAddress src, Credentials srcCreds, String dst, String id, WorkspaceFilter srcFilter,
            boolean recursive) {
        this.src = src;
        this.dst = dst;
        this.srcCreds = srcCreds;
        this.id = id == null || id.length() == 0
                ? UUID.randomUUID().toString()
                : id;
        this.recursive = recursive;
        this.filter = srcFilter;
        initTransientData();
        this.classLoader = dynLoader;
    }

    private static WorkspaceFilter createFilterForExcludes(List<String> excludes) throws ConfigurationException {
        // could be done better
        DefaultWorkspaceFilter srcFilter = new DefaultWorkspaceFilter();
        PathFilterSet filterSet = new PathFilterSet("/");
        for (String path : excludes) {
            filterSet.addExclude(new DefaultPathFilter(path));
        }
        return srcFilter;
    }

    private void readObject(java.io.ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        initTransientData();
    }

    private void initTransientData() {
        rcp = new RepositoryCopier();
        rcp.setTracker(new ProgressTrackerListener() {
            public void onMessage(Mode mode, String action, String path) {
                log.info("{} {}", action, path);
            }

            public void onError(Mode mode, String path, Exception e) {
                log.error("{} {}", path, e.toString());
            }
        });
        if (filter != null) {
            rcp.setSourceFilter(filter);
        }
        result = new ResultImpl(Result.State.NEW);
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
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
        ClassLoader oldLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            srcSession = getSourceSession(src);
        } finally {
            Thread.currentThread().setContextClassLoader(oldLoader);
        }

        thread = new Thread(this, "Vault RCP Task - " + id);
        thread.setContextClassLoader(classLoader);
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
        log.info("Starting repository copy task id={}. From {} to {}.", new Object[] {
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((dst == null) ? 0 : dst.hashCode());
        result = prime * result + ((excludes == null) ? 0 : excludes.hashCode());
        result = prime * result + ((filter == null) ? 0 : filter.hashCode());
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + (recursive ? 1231 : 1237);
        result = prime * result + ((src == null) ? 0 : src.hashCode());
        result = prime * result + ((srcCreds == null) ? 0 : srcCreds.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        RcpTaskImpl other = (RcpTaskImpl) obj;
        if (dst == null) {
            if (other.dst != null)
                return false;
        } else if (!dst.equals(other.dst))
            return false;
        if (excludes == null) {
            if (other.excludes != null)
                return false;
        } else if (!excludes.equals(other.excludes))
            return false;
        if (filter == null) {
            if (other.filter != null)
                return false;
        } else if (!filter.equals(other.filter))
            return false;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (recursive != other.recursive)
            return false;
        if (src == null) {
            if (other.src != null)
                return false;
        } else if (!src.equals(other.src))
            return false;
        if (!areCredentialsEqual(srcCreds, other.srcCreds)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "RcpTaskImpl [" + (id != null ? "id=" + id + ", " : "") + (src != null ? "src=" + src + ", " : "")
                + (srcCreds != null ? "srcCreds=" + srcCreds + ", " : "") + (dst != null ? "dst=" + dst + ", " : "") + "recursive="
                + recursive + ", " + (excludes != null ? "excludes=" + excludes + ", " : "") + (filter != null ? "filter=" + filter : "")
                + "]";
    }

    /** @param credentials
     * @return */
    boolean areCredentialsEqual(Credentials credentials, Credentials otherCredentials) {
        if (credentials == null || otherCredentials == null) {
            if (otherCredentials != null || credentials != null) {
                return false;
            }
        } else {
            if (credentials.getClass() != otherCredentials.getClass()) {
                return false;
            }
            if (!(credentials instanceof SimpleCredentials)) {
                throw new IllegalArgumentException("Only equality check for SimpleCredentials supported!");
            }
            SimpleCredentials simpleCredentials = SimpleCredentials.class.cast(credentials);
            SimpleCredentials simpleOtherCredentials = SimpleCredentials.class.cast(otherCredentials);

            if (!Arrays.equals(simpleCredentials.getPassword(), simpleOtherCredentials.getPassword())) {
                return false;
            }
            if (!simpleCredentials.getUserID().equals(simpleOtherCredentials.getUserID())) {
                return false;
            }

            if (!Arrays.equals(simpleCredentials.getAttributeNames(), simpleOtherCredentials.getAttributeNames())) {
                return false;
            }

            for (String attributeName : simpleCredentials.getAttributeNames()) {
                if (!simpleCredentials.getAttribute(attributeName).equals(simpleOtherCredentials.getAttribute(attributeName))) {
                    return false;
                }
            }
        }
        return true;
    }
}