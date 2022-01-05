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
package org.apache.jackrabbit.vault.packaging.registry.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.jcr.Session;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.vault.fs.api.ProgressTrackerListener;
import org.apache.jackrabbit.vault.packaging.CyclicDependencyException;
import org.apache.jackrabbit.vault.packaging.Dependency;
import org.apache.jackrabbit.vault.packaging.DependencyException;
import org.apache.jackrabbit.vault.packaging.NoSuchPackageException;
import org.apache.jackrabbit.vault.packaging.PackageException;
import org.apache.jackrabbit.vault.packaging.PackageId;
import org.apache.jackrabbit.vault.packaging.registry.DependencyReport;
import org.apache.jackrabbit.vault.packaging.registry.ExecutionPlan;
import org.apache.jackrabbit.vault.packaging.registry.ExecutionPlanBuilder;
import org.apache.jackrabbit.vault.packaging.registry.PackageRegistry;
import org.apache.jackrabbit.vault.packaging.registry.PackageTask;
import org.apache.jackrabbit.vault.packaging.registry.PackageTaskBuilder;
import org.apache.jackrabbit.vault.packaging.registry.PackageTaskOptions;
import org.apache.jackrabbit.vault.packaging.registry.RegisteredPackage;
import org.apache.jackrabbit.vault.util.RejectingEntityResolver;
import org.apache.jackrabbit.vault.util.xml.serialize.FormattingXmlStreamWriter;
import org.apache.jackrabbit.vault.util.xml.serialize.OutputFormat;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * {@code ExecutionPlanBuilderImpl}...
 */
public class ExecutionPlanBuilderImpl implements ExecutionPlanBuilder {

    /**
     * default logger
     */
    private static final Logger log = LoggerFactory.getLogger(ExecutionPlanBuilderImpl.class);

    private static final String ATTR_VERSION = "version";
    private static final String TAG_EXECUTION_PLAN = "executionPlan";
    private static final String TAG_TASK = "task";
    private static final String ATTR_CMD = "cmd";
    private static final String ATTR_PACKAGE_ID = "packageId";

    public static final double SUPPORTED_VERSION = 1.0;

    protected double version = SUPPORTED_VERSION;

    private final List<TaskBuilder> tasks = new LinkedList<>();

    private final PackageRegistry registry;

    private final PackageTaskOptionsSerializer optionsSerializer;

    private Session session;

    private ProgressTrackerListener listener;

    private ExecutionPlanImpl plan;

    private Set<PackageId> externalPackages = Collections.emptySet();

    ExecutionPlanBuilderImpl(PackageRegistry registry) {
        this.registry = registry;
        optionsSerializer = new PackageTaskOptionsSerializer();
    }

    @NotNull
    @Override
    public ExecutionPlanBuilder save(@NotNull OutputStream out) throws IOException, PackageException {
        validate();
        try (FormattingXmlStreamWriter writer = FormattingXmlStreamWriter.create(out, new OutputFormat(4, false))) {
            writer.writeStartDocument();
            writer.writeStartElement(TAG_EXECUTION_PLAN);
            writer.writeAttribute(ATTR_VERSION, String.valueOf(version));
            for (PackageTask task: plan.getTasks()) {
                writer.writeStartElement(TAG_TASK);
                writer.writeAttribute(ATTR_CMD, task.getType().name().toLowerCase());
                writer.writeAttribute(ATTR_PACKAGE_ID, task.getPackageId().toString());
                optionsSerializer.save(writer, task.getOptions());
                writer.writeEndElement();
            }
            writer.writeEndElement();
            writer.writeEndDocument();
        } catch (XMLStreamException e) {
            throw new IllegalStateException(e);
        }
        return this;
    }

    @NotNull
    @Override
    public ExecutionPlanBuilder load(@NotNull InputStream in) throws IOException {
        tasks.clear();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver(new RejectingEntityResolver());
            Document document = builder.parse(in);
            Element doc = document.getDocumentElement();
            if (!TAG_EXECUTION_PLAN.equals(doc.getNodeName())) {
                throw new IOException("<" + TAG_EXECUTION_PLAN + "> expected.");
            }
            String v = doc.getAttribute(ATTR_VERSION);
            if (v == null || "".equals(v)) {
                v = "1.0";
            }
            version = Double.parseDouble(v);
            if (version > SUPPORTED_VERSION) {
                throw new IOException("version " + version + " not supported.");
            }
            read(doc);
        } catch (ParserConfigurationException e) {
            throw new IOException("Unable to create configuration XML parser", e);
        } catch (SAXException e) {
            throw new IOException("Configuration file syntax error.", e);
        } finally {
            IOUtils.closeQuietly(in);
        }
        return this;
    }

    private void read(Element elem) throws IOException {
        NodeList nl = elem.getChildNodes();
        for (int i=0; i<nl.getLength(); i++) {
            Node child = nl.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                if (!TAG_TASK.equals(child.getNodeName())) {
                    throw new IOException("<" + TAG_TASK + "> expected.");
                }
                readTask((Element) child);
            }
        }
    }

    private void readTask(Element elem) throws IOException {
        PackageTask.Type type = PackageTask.Type.valueOf(elem.getAttribute(ATTR_CMD).toUpperCase());
        PackageId id = PackageId.fromString(elem.getAttribute(ATTR_PACKAGE_ID));
        
        PackageTaskBuilder packageTaskBuilder = addTask().with(id);
        PackageTaskOptions options = optionsSerializer.load(elem);
        if (options != null) {
            packageTaskBuilder.withOptions(options);
        }
        packageTaskBuilder.with(type);
    }

    @NotNull
    @Override
    public PackageTaskBuilder addTask() {
        TaskBuilder task = new TaskBuilder();
        tasks.add(task);
        plan = null; // invalidate potential plan
        return task;
    }

    @NotNull
    @Override
    public ExecutionPlanBuilder with(@NotNull Session session) {
        this.session = session;
        return this;
    }

    @NotNull
    @Override
    public ExecutionPlanBuilder with(@NotNull ProgressTrackerListener listener) {
        this.listener = listener;
        return this;
    }

    @NotNull
    @Override
    public ExecutionPlanBuilder validate() throws IOException, PackageException {
        Map<PackageId, PackageTask> installTasks = new HashMap<>();
        Map<PackageId, PackageTask> uninstallTasks = new HashMap<>();
        Map<PackageId, PackageTask> removeTasks = new HashMap<>();
        List<PackageTask> packageTasks = new ArrayList<>(tasks.size());
        for (TaskBuilder task: tasks) {
            if (task.id == null || task.type == null) {
                throw new PackageException("task builder must have package id and type defined.");
            }
            if (!registry.contains(task.id)) {
                throw new NoSuchPackageException("No such package: " + task.id);
            }
            PackageTaskImpl pTask = new PackageTaskImpl(task.id, task.type, task.option);
            // very simple task resolution: uninstall -> remove -> install/extract
            switch (task.type) {
                case INSTALL:
                case EXTRACT:
                    installTasks.put(task.id, pTask);
                    break;
                case UNINSTALL:
                    uninstallTasks.put(task.id, pTask);
                    break;
                case REMOVE:
                    removeTasks.put(task.id, pTask);
                    break;
            }
        }

        for (PackageId id: uninstallTasks.keySet().toArray(new PackageId[uninstallTasks.size()])) {
            resolveUninstall(id, packageTasks, uninstallTasks, new HashSet<>(), uninstallTasks.get(id).getOptions());
        }

        // todo: validate remove
        packageTasks.addAll(removeTasks.values());

        for (PackageId id: installTasks.keySet().toArray(new PackageId[installTasks.size()])) {
            PackageTask task = installTasks.get(id);
            resolveInstall(id, packageTasks, installTasks, new HashSet<>(), task.getType(), task.getOptions());
        }

        for (PackageTask task: packageTasks) {
            log.info("- {}", task);
        }

        plan = new ExecutionPlanImpl(packageTasks);
        return this;
    }

    private void resolveInstall(PackageId id, List<PackageTask> packageTasks, Map<PackageId, PackageTask> installTasks, Set<PackageId> resolved, PackageTask.Type type, @Nullable PackageTaskOptions option) throws IOException, PackageException {
        if (resolved.contains(id)) {
            throw new CyclicDependencyException("Package has cyclic dependencies: " + id);
        }
        resolved.add(id);
        DependencyReport report = registry.analyzeDependencies(id, false);
        if (report.getUnresolvedDependencies().length > 0) {
            throw new DependencyException("Package " + id + " has unresolved dependencies: " + Dependency.toString(report.getUnresolvedDependencies()));
        }
        for (PackageId depId: report.getResolvedDependencies()) {
            // if the package task is already present, continue resolution
            if (installTasks.get(depId) == PackageTaskImpl.MARKER) {
                continue;
            }
            // if the package is already installed, continue resolution
            try (RegisteredPackage pkg = registry.open(depId)) {
                if (pkg == null || pkg.isInstalled()) {
                    continue;
                }
            }
            resolveInstall(depId, packageTasks, installTasks, resolved, type, option);
        }
        PackageTask task = installTasks.get(id);
        if (task == PackageTaskImpl.MARKER) {
            // task was added during resolution
            return;
        }
        if (!externalPackages.contains(id)) {
            if (task == null) {
                // package is not registered in plan, but need to be installed
                // due to dependency
                task = new PackageTaskImpl(id, type, option);
            }
            packageTasks.add(task);
        }
        // mark as processed
        installTasks.put(id, PackageTaskImpl.MARKER);
    }

    private void resolveUninstall(PackageId id, List<PackageTask> packageTasks, Map<PackageId, PackageTask> uninstallTasks, Set<PackageId> resolved, @Nullable PackageTaskOptions option) throws IOException, PackageException {
        if (resolved.contains(id)) {
            throw new CyclicDependencyException("Package has cyclic dependencies: " + id);
        }
        resolved.add(id);
        for (PackageId depId: registry.usage(id)) {
            // if the package task is already present, continue resolution
            if (uninstallTasks.get(depId) == PackageTaskImpl.MARKER) {
                continue;
            }
            // if the package is already uninstalled, continue resolution
            try (RegisteredPackage pkg = registry.open(depId)) {
                if (pkg == null || !pkg.isInstalled()) {
                    continue;
                }
            }
            resolveUninstall(depId, packageTasks, uninstallTasks, resolved, option);
        }
        PackageTask task = uninstallTasks.get(id);
        if (task == PackageTaskImpl.MARKER) {
            // task was added during resolution
            return;
        }
        if (task == null) {
            // package is not registered in plan, but need to be installed due to dependency
            task = new PackageTaskImpl(id, PackageTask.Type.UNINSTALL, option);
        }
        packageTasks.add(task);
        // mark as processed
        uninstallTasks.put(id, PackageTaskImpl.MARKER);
    }

    @NotNull
    @Override
    public ExecutionPlan execute() throws IOException, PackageException {
        if (plan == null) {
            validate();
        }
        // check if session is present or if no task needs it
        if (session == null) {
            for (PackageTask task: plan.getTasks()) {
                if (task.getType() != PackageTask.Type.REMOVE) {
                    throw new PackageException("Session not set in builder, but " + task + " task requires it.");
                }
            }
        }
        return plan.with(registry).with(session).with(listener).execute();
    }

    private class TaskBuilder implements PackageTaskBuilder {
        private PackageId id;
        private PackageTask.Type type;
        private PackageTaskOptions option;

        public PackageTaskBuilder with(@NotNull PackageId id) {
            this.id = id;
            return this;
        }

        @NotNull
        @Override
        public ExecutionPlanBuilder with(@NotNull PackageTask.Type type) {
            this.type = type;
            return ExecutionPlanBuilderImpl.this;
        }

        @Override
        @NotNull
        public PackageTaskBuilder withOptions(@NotNull PackageTaskOptions option) {
            this.option = option;
            return this;
        }
    }

    @Override
    public ExecutionPlanBuilder with(Set<PackageId> externalPackages) {
        this.externalPackages = new HashSet<>(externalPackages);
        return ExecutionPlanBuilderImpl.this;
    }

    @Override
    public Set<PackageId> preview() throws IOException, PackageException {
        validate();
        if (plan.getTasks().isEmpty()) {
            return Collections.emptySet();
        } else {
            Set<PackageId> packages = new HashSet<>();
            for(PackageTask task : plan.getTasks()) {
                packages.add(task.getPackageId());
            }
            return packages;
        }
    }
}
