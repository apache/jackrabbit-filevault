/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.jackrabbit.vault.packaging.events.impl;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.events.PackageEvent;
import org.apache.jackrabbit.vault.packaging.events.PackageEventListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Packaging observation helper
 */
@Component(
        service = PackageEventDispatcher.class,
        property = {"service.vendor=The Apache Software Foundation"},
        reference = {
            @Reference(
                    name = "packageEventListener",
                    service = PackageEventListener.class,
                    cardinality = ReferenceCardinality.MULTIPLE,
                    policy = ReferencePolicy.DYNAMIC,
                    bind = "bindPackageEventListener",
                    unbind = "unbindPackageEventListener")
        })
public class PackageEventDispatcherImpl implements PackageEventDispatcher {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(PackageEventDispatcherImpl.class);

    /**
     * the registered listener
     */
    private ConcurrentHashMap<Object, PackageEventListener> listeners =
            new ConcurrentHashMap<Object, PackageEventListener>();

    /**
     * Bind a new listener
     * @param listener the processor
     * @param props service properties
     */
    public void bindPackageEventListener(PackageEventListener listener, Map<String, Object> props) {
        // public for testing
        listeners.put(props.get("component.id"), listener);
        log.debug("Registering package event listener {}", listener.getClass().getName());
    }

    /**
     * Unbinds a listener
     * @param listener the processor
     * @param props service properties
     */
    protected void unbindPackageEventListener(PackageEventListener listener, Map<String, Object> props) {
        if (listeners.remove(props.get("component.id")) != null) {
            log.debug(
                    "Unregistering package event listener {}",
                    listener.getClass().getName());
        } else {
            log.warn(
                    "unable to unregister package event listener {}",
                    listener.getClass().getName());
        }
    }

    public void dispatch(@NotNull PackageEvent.Type type, @NotNull PackageId id, @Nullable PackageId[] related) {
        final EventImpl event = new EventImpl(type, id, related);
        for (PackageEventListener l : listeners.values()) {
            try {
                l.onPackageEvent(event);
            } catch (Throwable e) {
                // ignore
            }
        }
    }

    private static final class EventImpl implements PackageEvent {

        private final Type type;

        private final PackageId id;

        private final PackageId[] related;

        public EventImpl(Type type, PackageId id, PackageId[] related) {
            this.type = type;
            this.id = id;
            this.related = related;
        }

        @NotNull
        @Override
        public Type getType() {
            return type;
        }

        @NotNull
        @Override
        public PackageId getId() {
            return id;
        }

        @Nullable
        @Override
        public PackageId[] getRelatedIds() {
            return related;
        }

        @Override
        public String toString() {
            return "EventImpl [type=" + type + ", id=" + id + ", related=" + Arrays.toString(related) + "]";
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            result = prime * result + Arrays.hashCode(related);
            result = prime * result + ((type == null) ? 0 : type.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            EventImpl other = (EventImpl) obj;
            if (id == null) {
                if (other.id != null) return false;
            } else if (!id.equals(other.id)) return false;
            if (!Arrays.equals(related, other.related)) return false;
            if (type != other.type) return false;
            return true;
        }
    }
}
