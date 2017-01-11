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
package org.apache.jackrabbit.vault.packaging.events.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.References;
import org.apache.felix.scr.annotations.Service;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.events.PackageEvent;
import org.apache.jackrabbit.vault.packaging.events.PackageEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Packaging observation helper
 */
@Component(immediate=true)
@References({
        @Reference(name="packageEventListener",
                referenceInterface = PackageEventListener.class,
                cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
                policy = ReferencePolicy.DYNAMIC)
})
@Service
public class PackageEventDispatcherImpl implements PackageEventDispatcher {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(PackageEventDispatcherImpl.class);

    /**
     * the registered listener
     */
    private ConcurrentHashMap<Object, PackageEventListener> listeners = new ConcurrentHashMap<Object, PackageEventListener>();

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
            log.debug("Unregistering package event listener {}", listener.getClass().getName());
        } else {
            log.warn("unable to unregister package event listener {}", listener.getClass().getName());
        }
    }

    public void dispatch(@Nonnull PackageEvent.Type type, @Nonnull PackageId id, @Nullable PackageId[] related) {
        final EventImpl event = new EventImpl(type, id, related);
        for (PackageEventListener l: listeners.values()) {
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

        @Nonnull
        @Override
        public Type getType() {
            return type;
        }

        @Nonnull
        @Override
        public PackageId getId() {
            return id;
        }

        @CheckForNull
        @Override
        public PackageId[] getRelatedIds() {
            return related;
        }
    }

}

