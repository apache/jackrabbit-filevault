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

package org.apache.jackrabbit.vault.util;

import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.jcr.Credentials;
import javax.jcr.ImportUUIDBehavior;
import javax.jcr.NamespaceException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.Repository;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.nodetype.NodeType;

import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.io.AutoSave;
import org.apache.jackrabbit.vault.fs.spi.ProgressTracker;
import org.apache.jackrabbit.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Repository Copier that copies content from a source to a destination repository.
 */
public class RepositoryCopier {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(RepositoryCopier.class);

    protected ProgressTrackerListener tracker;

    private transient int numNodes = 0;

    private transient int totalNodes = 0;

    private transient long totalSize = 0;

    private transient long currentSize = 0;

    private transient long start = 0;

    private transient String lastKnownGood;

    private transient String currentPath;

    private transient String cqLastModified;

    private volatile boolean abort;

    /** actual settings used by the copy process */
    private int batchSize = 1024;

    private long throttle = 0;

    private transient String resumeFrom;

    private WorkspaceFilter srcFilter;

    private Map<String, String> prefixMapping = new HashMap<>();

    private boolean onlyNewer;

    private boolean update;

    private boolean noOrdering;

    private CredentialsProvider credentialsProvider;

    public void setTracker(ProgressTrackerListener tracker) {
        this.tracker = tracker;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public long getThrottle() {
        return throttle;
    }

    public void setThrottle(long throttle) {
        this.throttle = throttle;
    }

    public void setSourceFilter(WorkspaceFilter srcFilter) {
        this.srcFilter = srcFilter;
    }

    public void setOnlyNewer(boolean onlyNewer) {
        this.onlyNewer = onlyNewer;
    }

    public void setUpdate(boolean update) {
        this.update = update;
    }

    public boolean isNoOrdering() {
        return noOrdering;
    }

    public void setNoOrdering(boolean noOrdering) {
        this.noOrdering = noOrdering;
    }

    public boolean isOnlyNewer() {
        return onlyNewer;
    }

    public boolean isUpdate() {
        return update;
    }

    public WorkspaceFilter getSrcFilter() {
        return srcFilter;
    }

    public String getResumeFrom() {
        return resumeFrom;
    }

    public void setResumeFrom(String resumeFrom) {
        this.resumeFrom = resumeFrom;
    }

    public String getLastKnownGood() {
        return lastKnownGood;
    }

    public String getCurrentPath() {
        return currentPath;
    }

    public int getCurrentNumNodes() {
        return numNodes;
    }

    public int getTotalNodes() {
        return totalNodes;
    }

    public long getTotalSize() {
        return totalSize;
    }

    public long getCurrentSize() {
        return currentSize;
    }

    public void abort() {
        abort = true;
    }

    public void copy(RepositoryAddress src, RepositoryAddress dst, boolean recursive) throws RepositoryException {
        track("", "Copy %s to %s (%srecursive)", src, dst, recursive ? "" : "non-");

        Session srcSession = null;
        Session dstSession = null;
        try {
            RepositoryProvider repProvider = new RepositoryProvider();
            Repository srcRepo;
            try {
                srcRepo = repProvider.getRepository(src);
            } catch (RepositoryException e) {
                throw new RepositoryException("Error while retrieving source repository " + src, e);
            }
            Repository dstRepo;
            try {
                dstRepo = repProvider.getRepository(dst);
            } catch (RepositoryException e) {
                throw new RepositoryException("Error while retrieving destination repository " + dst, e);
            }

            try {
                Credentials srcCreds = src.getCredentials();
                if (srcCreds == null && credentialsProvider != null) {
                    srcCreds = credentialsProvider.getCredentials(src);
                }
                srcSession = srcRepo.login(srcCreds, src.getWorkspace());
            } catch (RepositoryException e) {
                throw new RepositoryException("Could not log into source repository " + src, e);
            }

            try {
                Credentials dstCreds = dst.getCredentials();
                if (dstCreds == null && credentialsProvider != null) {
                    dstCreds = credentialsProvider.getCredentials(dst);
                }
                dstSession = dstRepo.login(dstCreds, dst.getWorkspace());
            } catch (RepositoryException e) {
                throw new RepositoryException("Could not log into destination repository " + dst, e);
            }
            copy(srcSession, src.getPath(), dstSession, dst.getPath(), recursive);
        } finally {
            if (srcSession != null) {
                srcSession.logout();
            }
            if (dstSession != null) {
                dstSession.logout();
            }
        }
    }

    public void copy(Session srcSession, String srcPath, Session dstSession, String dstPath, boolean recursive) throws RepositoryException {
        if (srcSession == null || dstSession == null) {
            throw new IllegalArgumentException("no src or dst session provided");
        }

        // get root nodes
        String dstParent = Text.getRelativeParent(dstPath, 1);
        String dstName = checkNameSpace(Text.getName(dstPath), srcSession, dstSession);
        Node srcRoot;
        try {
            srcRoot = srcSession.getNode(srcPath);
        } catch (RepositoryException e) {
            throw new RepositoryException("Error while retrieving source node " + srcPath, e);
        }
        Node dstRoot;
        try {
            dstRoot = dstSession.getNode(dstParent);
        } catch (RepositoryException e) {
            throw new RepositoryException("Error while retrieving destination parent node " + dstParent, e);
        }
        // check if the cq namespace exists
        try {
            cqLastModified = srcSession.getNamespacePrefix("http://www.day.com/jcr/cq/1.0") + ":lastModified";
        } catch (RepositoryException e) {
            // ignore
            log.debug("Haven't found cq namespace", e);
        }
        numNodes = 0;
        totalNodes = 0;
        currentSize = 0;
        totalSize = 0;
        start = System.currentTimeMillis();
        
        AutoSave autoSave = new AutoSave();
        autoSave.setThreshold(getBatchSize());
        autoSave.setTracker(new ProgressTracker(tracker));
        copy(autoSave, srcRoot, dstRoot, dstName, recursive);
        if (numNodes > 0) {
            track("", "Saving %d nodes...", numNodes);
            autoSave.save(dstSession, false);
            track("", "Done.");
        }
        long end = System.currentTimeMillis();
        track("", "Copy completed. %d nodes in %dms. %d bytes", totalNodes, end-start, totalSize);
    }

    private void copy(AutoSave autoSave, Node src, Node dstParent, String dstName, boolean recursive)
            throws RepositoryException {
        if (abort) {
            return;
        }
        String path = src.getPath();
        currentPath = path;
        String dstPath = dstParent.getPath() + "/" + dstName;
        if (srcFilter != null && !srcFilter.contains(path)) {
            track(path, "------ I");
            return;
        }

        boolean skip = false;
        if (resumeFrom != null) {
            if (path.equals(resumeFrom)) {
                // found last node, resuming
                resumeFrom = null;
            } else {
                skip = true;
            }
        }

        // check for special node that need sysview import handling
        boolean useSysView = src.getDefinition().isProtected();
        Node dst;
        boolean isNew = false;
        boolean overwrite = update;
        if (dstParent.hasNode(dstName)) {
            dst = dstParent.getNode(dstName);
            if (skip) {
                track(path, "------ S");
            } else if (overwrite) {
                if (onlyNewer && dstName.equals("jcr:content")) {
                    if (isNewer(src, dst)) {
                        track(dstPath, "%06d U", ++totalNodes);
                    } else {
                        overwrite = false;
                        recursive = false;
                        track(dstPath, "%06d -", ++totalNodes);
                    }
                } else {
                    track(dstPath, "%06d U", ++totalNodes);
                }
                if (useSysView) {
                    dst = sysCopy(src, dstParent, dstName);
                }
            } else {
                track(dstPath, "%06d -", ++totalNodes);
            }
        } else {
            try {
                if (skip) {
                    track(path, "------ S");
                    dst = null;
                } else if (useSysView) {
                    dst = sysCopy(src, dstParent, dstName);
                } else {
                    dst = dstParent.addNode(dstName, src.getPrimaryNodeType().getName());
                }
                track(dstPath, "%06d A", ++totalNodes);
                isNew = true;
            } catch (RepositoryException e) {
                if (log.isDebugEnabled()) {
                    log.debug("Error while adding node {} (ignored)", dstPath, e);
                } else {
                    log.warn("Error while adding node {} (ignored): {}", dstPath, e.getMessage());
                }
                return;
            }
        }
        if (useSysView) {
            if (!skip) {
                // track changes
                trackTree(dst, isNew);
            }
        } else {
            Set<String> names = new HashSet<String>();
            if (!skip && (overwrite || isNew)) {
                if (!isNew) {
                    for (NodeType nt: dst.getMixinNodeTypes()) {
                        names.add(nt.getName());
                    }
                    // add mixins
                    for (NodeType nt: src.getMixinNodeTypes()) {
                        String mixName = checkNameSpace(nt.getName(), src.getSession(), dst.getSession());
                        if (!names.remove(mixName)) {
                            dst.addMixin(nt.getName());
                        }
                    }
                    // handle removed mixins
                    for (String mix: names) {
                        dst.removeMixin(mix);
                    }
                } else {
                    // add mixins
                    for (NodeType nt: src.getMixinNodeTypes()) {
                        dst.addMixin(checkNameSpace(nt.getName(), src.getSession(), dst.getSession()));
                    }
                }

                // add properties
                names.clear();
                if (!isNew) {
                    PropertyIterator iter = dst.getProperties();
                    while (iter.hasNext()) {
                        names.add(checkNameSpace(iter.nextProperty().getName(), src.getSession(), dst.getSession()));
                    }
                }
                PropertyIterator iter = src.getProperties();
                while (iter.hasNext()) {
                    Property p = iter.nextProperty();
                    String pName = checkNameSpace(p.getName(), src.getSession(), dst.getSession());
                    names.remove(pName);
                    // ignore protected
                    if (p.getDefinition().isProtected()) {
                        continue;
                    }
                    // remove destination property to avoid type clashes
                    if (dst.hasProperty(pName)) {
                        dst.getProperty(pName).remove();
                    }
                    if (p.getDefinition().isMultiple()) {
                        Value[] vs = p.getValues();
                        dst.setProperty(pName, vs);
                        for (long s: p.getLengths()) {
                            totalSize+=s;
                            currentSize+=s;
                        }
                    } else {
                        Value v = p.getValue();
                        dst.setProperty(pName, v);
                        long s= p.getLength();
                        totalSize+=s;
                        currentSize+=s;
                    }
                }
                // remove obsolete properties
                for (String pName: names) {
                    try {
                        // ignore protected. should not happen, unless the primary node type changes.
                        Property dstP = dst.getProperty(pName);
                        if (dstP.getDefinition().isProtected()) {
                            continue;
                        }
                        dstP.remove();
                    } catch (RepositoryException e) {
                        // ignore
                    }
                }
            }

            // descend
            if (recursive && dst != null) {
                names.clear();
                if (overwrite && !isNew) {
                    NodeIterator niter = dst.getNodes();
                    while (niter.hasNext()) {
                        names.add(checkNameSpace(niter.nextNode().getName(), src.getSession(), dst.getSession()));
                    }
                }
                NodeIterator niter = src.getNodes();
                while (niter.hasNext()) {
                    Node child = niter.nextNode();
                    String cName = checkNameSpace(child.getName(), src.getSession(), dst.getSession());
                    names.remove(cName);
                    copy(autoSave, child, dst, cName, true);
                }
                if (resumeFrom == null) {
                    // check if we need to order
                    if (overwrite && !isNew && !noOrdering && src.getPrimaryNodeType().hasOrderableChildNodes()) {
                        niter = src.getNodes();
                        while (niter.hasNext()) {
                            Node child = niter.nextNode();
                            String name = child.getName();
                            if (dst.hasNode(name)) {
                                dst.orderBefore(name, null);
                            }
                        }
                    }

                    // remove obsolete child nodes
                    for (String name: names) {
                        try {
                            Node cNode = dst.getNode(name);
                            track(cNode.getPath(), "%06d D", ++totalNodes);
                            cNode.remove();
                        } catch (RepositoryException e) {
                            // ignore
                        }
                    }
                }
            }
        }

        if (!skip) {
            numNodes++;
            autoSave.modified(1);
        }

        // check for save
        if (autoSave.needsSave()) {
            track("", "Intermediate saving %d nodes (%d kB)...", numNodes, currentSize/1000);
            long now = System.currentTimeMillis();
            autoSave.save(dst.getSession(), true);
            long end = System.currentTimeMillis();
            track("", "Done in %d ms. Total time: %d, total nodes %d, %d kB", end-now, end-start, totalNodes, totalSize/1000);
            lastKnownGood = currentPath;
            numNodes = 0;
            currentSize = 0;
            if (throttle > 0) {
                track("", "Throttling enabled. Waiting %d second%s...", throttle, throttle == 1 ? "" : "s");
                try {
                    Thread.sleep(throttle * 1000);
                } catch (InterruptedException e) {
                    log.warn("Interrupted while waiting", e);
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private Node sysCopy(Node src, Node dstParent, String dstName) throws RepositoryException {
        try {
            ContentHandler handler = dstParent.getSession().getImportContentHandler(dstParent.getPath(), ImportUUIDBehavior.IMPORT_UUID_CREATE_NEW);
            src.getSession().exportSystemView(src.getPath(), handler, true, false);
            return dstParent.getNode(dstName);
        } catch (SAXException e) {
            throw new RepositoryException("Unable to perform sysview copy", e);
        }
    }

    private void trackTree(Node node, boolean isNew) throws RepositoryException {
        NodeIterator iter = node.getNodes();
        while (iter.hasNext()) {
            Node child = iter.nextNode();
            if (isNew) {
                track(child.getPath(), "%06d A", ++totalNodes);
            } else {
                track(child.getPath(), "%06d U", ++totalNodes);
            }
            trackTree(child, isNew);
        }
    }
    /**
     * Checks if {@code src} node is newer than {@code dst} node.
     * this only applies if the nodes have either a "jcr:lastModified" or
     * "cq:lastModified" property.
     *
     * @param src source node
     * @param dst destination node
     * @return {@code true} if src is newer than dst node or if the
     *         nodes could not be compared
     */
    private boolean isNewer(Node src, Node dst) {
        try {
            Calendar srcDate = null;
            Calendar dstDate = null;
            if (cqLastModified != null && src.hasProperty(cqLastModified) && dst.hasProperty(cqLastModified)) {
                srcDate = src.getProperty(cqLastModified).getDate();
                dstDate = dst.getProperty(cqLastModified).getDate();
            } else if (src.hasProperty(JcrConstants.JCR_LASTMODIFIED) && dst.hasProperty(JcrConstants.JCR_LASTMODIFIED)) {
                srcDate = src.getProperty(JcrConstants.JCR_LASTMODIFIED).getDate();
                dstDate = dst.getProperty(JcrConstants.JCR_LASTMODIFIED).getDate();
            }
            return srcDate == null || dstDate == null || srcDate.after(dstDate);
        } catch (RepositoryException e) {
            log.error("Unable to compare dates: {}", e.toString());
            return true;
        }
    }

    private String checkNameSpace(String name, Session srcSession, Session dstSession) {
        try {
            int idx = name.indexOf(':');
            if (idx > 0) {
                String prefix = name.substring(0, idx);
                String mapped = prefixMapping.get(prefix);
                if (mapped == null) {
                    String uri = srcSession.getNamespaceURI(prefix);
                    try {
                        mapped = dstSession.getNamespacePrefix(uri);
                    } catch (NamespaceException e) {
                        mapped = prefix;
                        int i=0;
                        while (i>=0) {
                            try {
                                dstSession.getWorkspace().getNamespaceRegistry().registerNamespace(mapped, uri);
                                i=-1;
                            } catch (NamespaceException e1) {
                                mapped = prefix + i++;
                            }
                        }
                    }
                    prefixMapping.put(prefix, mapped);
                }
                if (mapped.equals(prefix)) {
                    return name;
                } else {
                    return mapped + name.substring(idx);
                }
            }
        } catch (RepositoryException e) {
            log.error("Error processing namespace for {}: {}", name, e.toString());
        }
        return name;
    }

    private void track(String path, String fmt, Object ... args) {
        if (tracker != null) {
            tracker.onMessage(ProgressTrackerListener.Mode.TEXT, String.format(Locale.ENGLISH, fmt, args), path);
        }
    }

    public void setCredentialsProvider(CredentialsProvider credentialsProvider) {
        this.credentialsProvider = credentialsProvider;
    }

    public CredentialsProvider getCredentialsProvider() {
        return credentialsProvider;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (abort ? 1231 : 1237);
        result = prime * result + batchSize;
        result = prime * result + (noOrdering ? 1231 : 1237);
        result = prime * result + (onlyNewer ? 1231 : 1237);
        result = prime * result + ((prefixMapping == null) ? 0 : prefixMapping.hashCode());
        result = prime * result + ((resumeFrom == null) ? 0 : resumeFrom.hashCode());
        result = prime * result + ((srcFilter == null) ? 0 : srcFilter.hashCode());
        result = prime * result + (int) (throttle ^ (throttle >>> 32));
        result = prime * result + ((tracker == null) ? 0 : tracker.hashCode());
        result = prime * result + (update ? 1231 : 1237);
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
        RepositoryCopier other = (RepositoryCopier) obj;
        if (abort != other.abort)
            return false;
        if (batchSize != other.batchSize)
            return false;
        if (noOrdering != other.noOrdering)
            return false;
        if (onlyNewer != other.onlyNewer)
            return false;
        if (prefixMapping == null) {
            if (other.prefixMapping != null)
                return false;
        } else if (!prefixMapping.equals(other.prefixMapping))
            return false;
        if (resumeFrom == null) {
            if (other.resumeFrom != null)
                return false;
        } else if (!resumeFrom.equals(other.resumeFrom))
            return false;
        if (srcFilter == null) {
            if (other.srcFilter != null)
                return false;
        } else if (!srcFilter.equals(other.srcFilter))
            return false;
        if (throttle != other.throttle)
            return false;
        if (tracker == null) {
            if (other.tracker != null)
                return false;
        } else if (!tracker.equals(other.tracker))
            return false;
        if (update != other.update)
            return false;
        return true;
    }

}