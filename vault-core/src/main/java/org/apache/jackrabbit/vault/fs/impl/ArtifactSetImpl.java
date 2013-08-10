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

package org.apache.jackrabbit.vault.fs.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.Property;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.vault.fs.PropertyValueArtifact;
import org.apache.jackrabbit.vault.fs.SerializerArtifact;
import org.apache.jackrabbit.vault.fs.api.Artifact;
import org.apache.jackrabbit.vault.fs.api.ArtifactIterator;
import org.apache.jackrabbit.vault.fs.api.ArtifactSet;
import org.apache.jackrabbit.vault.fs.api.ArtifactType;
import org.apache.jackrabbit.vault.fs.api.DumpContext;
import org.apache.jackrabbit.vault.fs.api.Dumpable;
import org.apache.jackrabbit.vault.fs.api.ItemFilter;
import org.apache.jackrabbit.vault.fs.api.ItemFilterSet;
import org.apache.jackrabbit.vault.fs.io.Serializer;

/**
 * Implements a set of artifacts. The artifacts can be retrieved via an
 * {@link ArtifactIterator}. Special artifacts like {@link ArtifactType#PRIMARY}
 * and {@link ArtifactType#DIRECTORY} can be retrieved individually. This set
 * only allows one of those artifacts per type.
 *
 * All modifications to this set have no influence on the persisted data.
 *
 */
public class ArtifactSetImpl implements Dumpable, ArtifactSet {

    /**
     * the list of artifacts
     */
    private final List<Artifact> artifacts = new LinkedList<Artifact>();

    /**
     * the list of removed
     */
    private List<Artifact> removed;
    
    /**
     * Set of artifacts that are only allowed once
     */
    private final Map<ArtifactType, Artifact> single = new EnumMap<ArtifactType, Artifact>(ArtifactType.class);

    /**
     * list of hint artifacts
     */
    private Set<Artifact> hints;

    /**
     * The item filter set that defines the coverage of this artifact set
     */
    private ItemFilterSet coverageFilter = (ItemFilterSet) new ItemFilterSet().addInclude(ItemFilter.ALL);

    public ItemFilterSet getCoverage() {
        return coverageFilter;
    }

    /**
     * Sets the item filter set that defines the coverage of the items in
     * this artifact set.
     * @param filter the item filter set.
     */
    public void setCoverage(ItemFilterSet filter) {
        this.coverageFilter = filter;
    }

    public void addAll(Collection<? extends Artifact> artifacts) {
         for (Artifact artifact: artifacts) {
             add(artifact);
         }
     }

     public void addAll(ArtifactSet artifacts) {
        addAll(((ArtifactSetImpl) artifacts).artifacts);
    }

    public void add(Artifact artifact) {
        ArtifactType type = artifact.getType();
        if (type == ArtifactType.PRIMARY || type == ArtifactType.DIRECTORY) {
            if (single.containsKey(type)) {
                throw new IllegalArgumentException("Only 1 " + type + " artifact allowed.");
            }
            single.put(type, artifact);
            artifacts.add(artifact);
        } else if (type == ArtifactType.HINT) {
            if (hints == null) {
                hints = new HashSet<Artifact>();
            }
            hints.add(artifact);
        } else {
            artifacts.add(artifact);
        }
    }

    /**
     * Adds an {@link SerializerArtifact} with the given name, type and
     * serializer.
     *
     * @param parent the parent artifact
     * @param name the name of the artifact
     * @param ext the extension of the artifact
     * @param type the type of the artifact
     * @param ser the serializer of the artifact
     * @param lastModified the last modified date
     * @return the added artifact
     */
    public SerializerArtifact add(Artifact parent, String name, String ext,
                                  ArtifactType type, Serializer ser,
                                  long lastModified) {
        SerializerArtifact a = new SerializerArtifact(parent, name, ext, type, ser, lastModified);
        add(a);
        return a;
    }

    /**
     * Adds an {@link PropertyValueArtifact} with the given name, type and
     * serializer
     *
     * @param parent parent artifact
     * @param relPath the name of the artifact
     * @param ext the extension
     * @param type the type of the artifact
     * @param prop the property of the artifact
     * @param lastModified the last modified date. can be 0.
     *
     * @return a collection of artifacts
     * @throws RepositoryException if an error occurs.
     */
    public Collection<PropertyValueArtifact> add(Artifact parent, String relPath,
                 String ext, ArtifactType type, Property prop, long lastModified)
            throws RepositoryException {
        Collection<PropertyValueArtifact> a =
                PropertyValueArtifact.create(parent, relPath, ext, type, prop, lastModified);
        addAll(a);
        return a;
    }


    public Artifact getPrimaryData() {
        return single.get(ArtifactType.PRIMARY);
    }

    public Artifact getDirectory() {
        return single.get(ArtifactType.DIRECTORY);
    }

    public boolean isEmpty() {
        return artifacts.isEmpty();
    }

    public int size() {
        return artifacts.size();
    }

    /**
     * Removes the artifact from this set.
     *
     * @param a the artifact to remove
     * @return <code>true</code> if the artifact was removed.
     */
    public boolean remove(Artifact a) {
        if (artifacts.remove(a)) {
            single.remove(a.getType());
            if (removed == null) {
                removed = new LinkedList<Artifact>();
                removed.add(a);
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Puts the given artifact to this set. In comparison to the add operations,
     * an already existing artifact is replaced. If the type of the newly
     * artifact is {@link ArtifactType#BINARY} it equivalent with the
     * same name is replaced.
     *
     * @param a the artifact to put
     * @return the previous artifact or <code>null</code>
     */
    public Artifact put(Artifact a) {
        Artifact prev = null;
        int idx = artifacts.indexOf(a);
        if (idx >=0) {
            prev = artifacts.remove(idx);
        }
        single.put(a.getType(), a);
        artifacts.add(a);
        return prev;
    }

    /**
     * Returns the number of artifacts in this set that have the given type.
     * @param type the artifact type
     * @return the number of artifacts in this set that have the given type.
     */
    public int size(ArtifactType type) {
        int num = 0;
        if (type == ArtifactType.HINT) {
            num = hints == null ? 0 : hints.size();
        } else {
            for (Artifact a: artifacts) {
                if (a.getType() == type) {
                    num++;
                }
            }
        }
        return num;
    }

    /**
     * Returns a collection of all artifacts that have the given type.
     * @param type the type of the artifacts to return
     * @return the artifacts
     */
    public Collection<Artifact> values(ArtifactType type) {
        List<Artifact> ret = new LinkedList<Artifact>();
        if (type == ArtifactType.HINT) {
            if (hints != null) {
                ret.addAll(hints);
            }
        } else {
            for (Artifact a: artifacts) {
                if (type == null || a.getType() == type) {
                    ret.add(a);
                }
            }
        }
        return ret;
    }

    /**
     * Returns a collection of all artifacts
     * @return the artifacts
     */
    public Collection<Artifact> values() {
        return Collections.unmodifiableList(artifacts);
    }

    /**
     * Returns the collection of removed artifacts
     * @return the removed artifacts
     */
    public Collection<Artifact> removed() {
        if (removed == null) {
            return Collections.emptyList();
        } else {
            return Collections.unmodifiableList(removed);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
        StringBuilder buf = new StringBuilder("ArtifactSet={");
        Iterator<Artifact> iter = artifacts.iterator();
        while (iter.hasNext()) {
            buf.append(iter.next());
            if (iter.hasNext()) {
                buf.append(", ");
            }
        }
        buf.append('}');
        return buf.toString();
    }

    /**
     * Returns the artifact with the given path or <code>null</code> if it does
     * not exist.
     * @param path the name of the artifact.
     * @return the desired artifact or <code>null</code>
     */
    public Artifact getArtifact(String path) {
        for (Artifact a: artifacts) {
            if (a.getRelativePath().equals(path)) {
                return a;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    public void dump(DumpContext ctx, boolean isLast) {
        ctx.println(isLast, "Artifacts");
        ctx.indent(isLast);
        for (Artifact a: artifacts) {
            a.dump(ctx, false);
        }
        ctx.println(true, "Coverage");
        ctx.indent(true);
        coverageFilter.dump(ctx, true);
        ctx.outdent();
        ctx.outdent();
    }

}