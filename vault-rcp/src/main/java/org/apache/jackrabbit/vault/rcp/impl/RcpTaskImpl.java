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

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import javax.jcr.Credentials;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;

import org.apache.jackrabbit.spi2dav.ConnectionOptions;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code RcpTask}... */
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE,
isGetterVisibility = JsonAutoDetect.Visibility.NONE,
setterVisibility = JsonAutoDetect.Visibility.NONE,
creatorVisibility = JsonAutoDetect.Visibility.ANY,
fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class RcpTaskImpl implements Runnable, RcpTask {

    /** default logger */
    private static final Logger log = LoggerFactory.getLogger(RcpTaskImpl.class);

    private final String id;

    private final RepositoryAddress src;

    @JsonIgnore
    private Credentials srcCreds;

    private final String dst;

    private final boolean recursive;

    private List<String> excludes;

    private transient Result result;

    private final RepositoryCopier rcp;

    private transient Thread thread;

    private transient Session srcSession;

    private transient Session dstSession;

    /** classloader used in the thread executing the task */
    private transient ClassLoader classLoader;

    WorkspaceFilter filter;

    private final ConnectionOptions connectionOptions;

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

    public RcpTaskImpl(ClassLoader classLoader, RepositoryAddress src, ConnectionOptions connectionOptions, Credentials srcCreds, String dst, String id, List<String> excludes,
            @Nullable Boolean recursive) throws ConfigurationException {
        this(classLoader, src, connectionOptions, srcCreds, dst, id, createFilterForExcludes(excludes), recursive);
        this.excludes = excludes;
    }

    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RcpTaskImpl(@JsonProperty("classLoader") ClassLoader dynLoader, @JsonProperty("source") RepositoryAddress src, @JsonProperty("connectionOptions") ConnectionOptions connectionOptions, @JsonProperty("srcCreds") Credentials srcCreds, @JsonProperty("destination") String dst, @JsonProperty("id") String id, @JsonProperty("filter") WorkspaceFilter srcFilter,
            @JsonProperty("recursive") @Nullable Boolean recursive) {
        this.src = src;
        this.dst = dst;
        this.srcCreds = srcCreds;
        this.id = id == null || id.length() == 0
                ? UUID.randomUUID().toString()
                : id;
        this.recursive = recursive != null ? recursive : false;
        this.classLoader = dynLoader;
        this.connectionOptions = connectionOptions;
        this.filter = srcFilter;
        rcp = new RepositoryCopier();
        rcp.setTracker(new ProgressTrackerListener() {
            public void onMessage(Mode mode, String action, String path) {
                log.info("{} {}", action, path);
            }

            public void onError(Mode mode, String path, Exception e) {
                log.error("{} {}", path, e.toString());
            }
        });
        if (srcFilter != null) {
            rcp.setSourceFilter(srcFilter);
        }
        result = new ResultImpl(Result.State.NEW);
    }

    // additional constructor for editing existing tasks, all arguments are optional except the first one
    public RcpTaskImpl(@NotNull RcpTaskImpl oldTask, @Nullable RepositoryAddress src, @Nullable ConnectionOptions connectionOptions, @Nullable Credentials srcCreds, @Nullable String dst, @Nullable List<String> excludes, @Nullable WorkspaceFilter srcFilter,
            @Nullable Boolean recursive) {
        this.src = src != null ? src : oldTask.src;
        this.connectionOptions = connectionOptions != null ? connectionOptions : oldTask.connectionOptions;
        this.dst = dst != null ? dst : oldTask.dst;
        this.srcCreds = srcCreds != null ? srcCreds : oldTask.srcCreds;
        this.id = oldTask.id;
        this.recursive = recursive != null ? recursive : oldTask.recursive;
        this.excludes = excludes != null ? excludes : oldTask.excludes;
        this.filter = srcFilter != null ? srcFilter : oldTask.filter;
        // leave all other fields untouched
        this.classLoader = oldTask.classLoader;
        this.rcp = oldTask.rcp;
        this.result = oldTask.result;
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
        if (result.getState() != Result.State.STOPPED && result.getState() != Result.State.STOPPING && result.getState() != Result.State.NEW) {
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
        if (result.getState() == Result.State.RUNNING || result.getState() == Result.State.STOPPING) {
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
            srcRepo = factory.createRepository(src, connectionOptions);
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
            log.error("Error while executing RCP task {}", getId(), e);
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
    public ConnectionOptions getConnectionOptions() {
        return connectionOptions;
    }

    Credentials getSourceCredentials() {
        return srcCreds;
    }

    public void setSourceCredentials(Credentials srcCreds) {
        this.srcCreds = srcCreds;
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
    public WorkspaceFilter getFilter() {
        return filter;
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
        if (!areFiltersEqual(filter, other.filter)) {
            return false;
        }
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
        // equals for RCP
        if (!areRepositoryCopiersEqual(rcp, other.rcp)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "RcpTaskImpl [" + (id != null ? "id=" + id + ", " : "") + (src != null ? "src=" + src + ", " : "")
                + (srcCreds != null ? "srcCreds=" + srcCreds + ", " : "") + (dst != null ? "dst=" + dst + ", " : "") + "recursive="
                + recursive + ", " + (excludes != null ? "excludes=" + excludes + ", " : "") + (filter != null ? "filter=" + filter.getSourceAsString() + ", "  : "") + (rcp != null ? "rcp=" + repositoryCopierToString(rcp) + ", " : "") + "]";
    }

    /** @param credentials
     * @return */
    static boolean areCredentialsEqual(Credentials credentials, Credentials otherCredentials) {
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
    
    /** Cannot rely on RepositoryCopier.equals() as not implemented in older versions of FileVault */
    static boolean areRepositoryCopiersEqual(RepositoryCopier rcp, RepositoryCopier otherRcp) {
        if (rcp == null || otherRcp == null) {
            if (otherRcp != null || rcp != null) {
                return false;
            }
        } else {
            if (rcp.getBatchSize() != otherRcp.getBatchSize()) {
                return false;
            }
            if (rcp.getThrottle() != otherRcp.getThrottle()) {
                return false;
            }
            if (rcp.isOnlyNewer() != otherRcp.isOnlyNewer()) {
                return false;
            }
            if (rcp.isUpdate() != otherRcp.isUpdate()) {
                return false;
            }
            if (rcp.isNoOrdering() != otherRcp.isNoOrdering()) {
                return false;
            }
        }
        return true;
    }
    
    /** Cannot rely on RepositoryCopier.equals() as not implemented in older versions of FileVault */
    static boolean areFiltersEqual(WorkspaceFilter filter, WorkspaceFilter otherFilter) {
        if (filter == null || otherFilter == null) {
            if (otherFilter != null || filter != null) {
                return false;
            }
        } else {
            if (!filter.getSourceAsString().equals(otherFilter.getSourceAsString())) {
                return false;
            }
        }
        return true;
    }

    static String repositoryCopierToString(RepositoryCopier rcp) {
        return "RepositoryCopier [batchSize=" + rcp.getBatchSize() + ", onlyNewer="+ rcp.isOnlyNewer() + ", update=" + rcp.isUpdate() + ", noOrdering=" + rcp.isNoOrdering() + ", throttle=" + rcp.getThrottle() + "]";
    }
}