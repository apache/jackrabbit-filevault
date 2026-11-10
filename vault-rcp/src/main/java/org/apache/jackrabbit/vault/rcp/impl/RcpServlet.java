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
package org.apache.jackrabbit.vault.rcp.impl;

import javax.jcr.Credentials;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jackrabbit.spi2dav.ConnectionOptions;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.rcp.RcpTask;
import org.apache.jackrabbit.vault.rcp.RcpTaskManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
@Component(
        service = Servlet.class,
        property = {"service.vendor=The Apache Software Foundation", "sling.servlet.paths=" + RcpServlet.SERVLET_PATH})
public class RcpServlet extends SlingAllMethodsServlet {

    protected static final String SERVLET_PATH = "/system/jackrabbit/filevault/rcp";
    private static final long serialVersionUID = -4571680968447024900L;
    public static final String PARAM_SRC = "src";
    public static final String PARAM_SRC_CREDS = "srcCreds";
    public static final String PARAM_DST = "dst";
    public static final String PARAM_ID = "id";
    public static final String PARAM_BATCHSIZE = "batchsize";
    public static final String PARAM_CMD = "cmd";
    public static final String PARAM_RECURSIVE = "recursive";
    public static final String PARAM_STATE = "state";
    public static final String PARAM_UPDATE = "update";
    public static final String PARAM_NO_ORDERING = "noOrdering";
    public static final String PARAM_ONLY_NEWER = "onlyNewer";
    public static final String PARAM_THROTTLE = "throttle";
    public static final String PARAM_EXCLUDES = "excludes";
    public static final String PARAM_RESUME_FROM = "resumeFrom";
    public static final String PARAM_FILTER = "filter";
    // connection options
    public static final String PARAM_ALLOW_SELF_SIGNED_CERTIFICATE = "allowSelfSignedCertificate";
    public static final String PARAM_DISABLE_HOSTNAME_VERIFICATION = "disableHostnameVerification";
    public static final String PARAM_CONNECTION_TIMEOUT_MS = "connectionTimeoutMs";
    public static final String PARAM_REQUEST_TIMEOUT_MS = "requestTimeoutMs";
    public static final String PARAM_SOCKET_TIMEOUT_MS = "socketTimeoutMs";
    public static final String PARAM_USE_SYSTEM_PROPERTIES = "useSystemProperties";
    public static final String PARAM_PROXY_HOST = "proxyHost";
    public static final String PARAM_PROXY_PORT = "proxyPort";
    public static final String PARAM_PROXY_PROTOCOL = "proxyProtocol";
    public static final String PARAM_PROXY_USERNAME = "proxyUsername";
    public static final String PARAM_PROXY_PASSWORD = "proxyPassword";

    private static final JsonFactory JSON_FACTORY = new JsonFactory();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper(JSON_FACTORY);
    private static final TypeReference<Map<String, Object>> MAP_TYPE_REFERENCE =
            new TypeReference<Map<String, Object>>() {};

    /**
     * default logger
     */
    protected final Logger log = LoggerFactory.getLogger(RcpServlet.class);

    private final RcpTaskManager taskMgr;

    private Bundle bundle;

    @Activate
    public RcpServlet(BundleContext context, @Reference RcpTaskManager taskMgr) {
        bundle = context.getBundle();
        this.taskMgr = taskMgr;
    }

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        try {
            if ("json".equals(request.getRequestPathInfo().getExtension())
                    && "info".equals(request.getRequestPathInfo().getSelectorString())) {
                writeInfoJson(response.getWriter());
            } else {
                String taskId = request.getRequestPathInfo().getSuffix();
                writeTaskJson(response.getWriter(), taskId);
            }
        } catch (IOException e) {
            throw new ServletException("Error writing JSON", e);
        }
    }

    void writeTaskJson(Writer writer, String taskId) throws IOException {
        try (JsonGenerator generator = JSON_FACTORY.createGenerator(writer)) {
            if (taskId != null) {
                taskId = taskId.substring(1);
                RcpTask task = taskMgr.getTask(taskId);

                if (task != null) {
                    write(generator, task);
                } else {
                    // return empty object
                    generator.writeStartObject();
                    generator.writeEndObject();
                }
            } else {
                generator.writeStartObject();
                generator.writeArrayFieldStart("tasks");
                for (RcpTask task : taskMgr.getTasks().values()) {
                    write(generator, task);
                }
                generator.writeEndArray();
                generator.writeEndObject();
            }
        }
    }

    void writeInfoJson(Writer writer) throws IOException {
        try (JsonGenerator generator = JSON_FACTORY.createGenerator(writer)) {
            generator.writeStartObject();
            generator.writeStringField(Constants.BUNDLE_SYMBOLICNAME, bundle.getSymbolicName());
            generator.writeStringField(
                    Constants.BUNDLE_VERSION, bundle.getVersion().toString());
            generator.writeStringField(
                    Constants.BUNDLE_VENDOR, bundle.getHeaders().get(Constants.BUNDLE_VENDOR));
            generator.writeEndObject();
        }
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        TypedMapWrapper data;
        try {
            Map<String, Object> parsed;
            try (JsonParser parser = JSON_FACTORY.createParser(request.getInputStream())) {
                parsed = OBJECT_MAPPER.readValue(parser, MAP_TYPE_REFERENCE);
            }
            data = new TypedMapWrapper(parsed);
        } catch (IOException e) {
            log.error("Error while reading json", e);
            response.setStatus(500);
            return;
        }
        String cmd = data.optString(PARAM_CMD, "");
        RcpTask task;
        final String id = data.optString(PARAM_ID, null);
        ;
        try {
            // --------------------------------------------------------------------------------------------< create >---
            boolean isEdit = "edit".equals(cmd);
            if (isEdit || "create".equals(cmd)) {
                if (isEdit) {
                    if (id == null || id.length() == 0) {
                        throw new IllegalArgumentException("Need task id.");
                    }
                }
                String src = data.optString(PARAM_SRC, "");
                if (isEdit && (src == null || src.length() == 0)) {
                    throw new IllegalArgumentException("Need src.");
                }
                String dst = data.optString(PARAM_DST, "");
                String srcCreds = data.optString(PARAM_SRC_CREDS, null);

                RepositoryAddress address = new RepositoryAddress(src);
                Credentials creds = address.getCredentials();
                if (creds != null) {
                    // remove creds from repository address to prevent logging
                    URI uri = address.getURI();
                    address = new RepositoryAddress(new URI(
                            uri.getScheme(),
                            null,
                            uri.getHost(),
                            uri.getPort(),
                            uri.getPath(),
                            uri.getQuery(),
                            uri.getFragment()));
                }
                if (srcCreds != null && srcCreds.length() > 0) {
                    creds = createCredentials(srcCreds);
                }
                Boolean recursive = null;
                if (data.containsKey(PARAM_RECURSIVE)) {
                    recursive = data.optBoolean(PARAM_RECURSIVE, false);
                }

                ConnectionOptions.Builder connectionOptionsBuilder = ConnectionOptions.builder();
                connectionOptionsBuilder.useSystemProperties(data.optBoolean(PARAM_USE_SYSTEM_PROPERTIES, false));
                connectionOptionsBuilder.allowSelfSignedCertificates(
                        data.optBoolean(PARAM_ALLOW_SELF_SIGNED_CERTIFICATE, false));
                connectionOptionsBuilder.disableHostnameVerification(
                        data.optBoolean(PARAM_DISABLE_HOSTNAME_VERIFICATION, false));
                int connectionTimeoutMs = data.optInt(PARAM_CONNECTION_TIMEOUT_MS, -1);
                connectionOptionsBuilder.connectionTimeoutMs(connectionTimeoutMs);
                int requestTimeoutMs = data.optInt(PARAM_REQUEST_TIMEOUT_MS, -1);
                connectionOptionsBuilder.requestTimeoutMs(requestTimeoutMs);
                int socketTimeoutMs = data.optInt(PARAM_SOCKET_TIMEOUT_MS, -1);
                connectionOptionsBuilder.socketTimeoutMs(socketTimeoutMs);

                if (data.containsKey(PARAM_PROXY_HOST)) {
                    connectionOptionsBuilder.proxyHost(data.getString(PARAM_PROXY_HOST));
                    if (data.containsKey(PARAM_PROXY_PORT)) {
                        connectionOptionsBuilder.proxyPort(data.getInt(PARAM_PROXY_PORT));
                    }
                    if (data.containsKey(PARAM_PROXY_PROTOCOL)) {
                        connectionOptionsBuilder.proxyProtocol(data.getString(PARAM_PROXY_PROTOCOL));
                    }
                    if (data.containsKey(PARAM_PROXY_USERNAME)) {
                        connectionOptionsBuilder.proxyUsername(data.getString(PARAM_PROXY_USERNAME));
                        if (data.containsKey(PARAM_PROXY_PASSWORD)) {
                            connectionOptionsBuilder.proxyPassword(data.getString(PARAM_PROXY_PASSWORD));
                        }
                    }
                }
                if (data.containsKey(PARAM_EXCLUDES)) {
                    List<String> excludeList = data.getStringList(PARAM_EXCLUDES);
                    if (isEdit) {
                        task = taskMgr.editTask(
                                id,
                                address,
                                connectionOptionsBuilder.build(),
                                creds,
                                dst,
                                excludeList,
                                null,
                                recursive);
                    } else {
                        task = taskMgr.addTask(
                                address, connectionOptionsBuilder.build(), creds, dst, id, excludeList, recursive);
                    }
                } else {
                    final WorkspaceFilter filter;
                    if (data.containsKey(PARAM_FILTER)) {
                        DefaultWorkspaceFilter filterImpl = new DefaultWorkspaceFilter();
                        try (InputStream is = new ByteArrayInputStream(
                                data.getString(PARAM_FILTER).getBytes(StandardCharsets.UTF_8))) {
                            filterImpl.load(is);
                        }
                        filter = filterImpl;
                    } else {
                        filter = null;
                    }
                    if (isEdit) {
                        task = taskMgr.editTask(
                                id, address, connectionOptionsBuilder.build(), creds, dst, null, filter, recursive);
                    } else {
                        task = taskMgr.addTask(
                                address, connectionOptionsBuilder.build(), creds, dst, id, filter, recursive);
                    }
                }

                // add additional data
                if (data.containsKey(PARAM_BATCHSIZE)) {
                    task.getRcp().setBatchSize((int) data.getLong(PARAM_BATCHSIZE));
                }
                if (data.containsKey(PARAM_UPDATE)) {
                    task.getRcp().setUpdate(data.optBoolean(PARAM_UPDATE, false));
                }
                if (data.containsKey(PARAM_ONLY_NEWER)) {
                    task.getRcp().setOnlyNewer(data.optBoolean(PARAM_ONLY_NEWER, false));
                }
                if (data.containsKey(PARAM_NO_ORDERING)) {
                    task.getRcp().setNoOrdering(data.optBoolean(PARAM_NO_ORDERING, false));
                }
                if (data.containsKey(PARAM_THROTTLE)) {
                    task.getRcp().setThrottle(data.getLong(PARAM_THROTTLE));
                }
                if (data.containsKey(PARAM_RESUME_FROM)) {
                    task.getRcp().setResumeFrom(data.getString(PARAM_RESUME_FROM));
                }
                if (isEdit) {
                    response.setStatus(HttpServletResponse.SC_OK);
                } else {
                    response.setStatus(HttpServletResponse.SC_CREATED);
                }
                String path = SERVLET_PATH + "/" + task.getId();
                response.setHeader("Location", path);

                // ---------------------------------------------------------------------------------------------< start
                // >---
            } else if ("start".equals(cmd)) {
                if (id == null || id.length() == 0) {
                    throw new IllegalArgumentException("Need task id.");
                }
                task = taskMgr.getTasks().get(id);
                if (task == null) {
                    throw new IllegalArgumentException("No such task with id='" + id + "'");
                }
                task.start(request.getResourceResolver().adaptTo(Session.class));

                // ----------------------------------------------------------------------------------------------< stop
                // >---
            } else if ("stop".equals(cmd)) {
                if (id == null || id.length() == 0) {
                    throw new IllegalArgumentException("Need task id.");
                }
                task = taskMgr.getTasks().get(id);
                if (task == null) {
                    throw new IllegalArgumentException("No such task with id='" + id + "'");
                }
                task.stop();

                // --------------------------------------------------------------------------------------------< remove
                // >---
            } else if ("remove".equals(cmd)) {
                if (id == null || id.length() == 0) {
                    throw new IllegalArgumentException("Need task id.");
                }
                if (!taskMgr.removeTask(id)) {
                    throw new IllegalArgumentException("No such task with id='" + id + "'");
                }
                // --------------------------------------------------------------------------------------------< remove
                // >---
            } else if ("set-credentials".equals(cmd)) {
                // only add the credentials for a certain task id
                if (id == null || id.length() == 0) {
                    throw new IllegalArgumentException("Need task id.");
                }
                String srcCreds = data.optString(PARAM_SRC_CREDS, "");
                final Credentials credentials;
                if (srcCreds.isEmpty()) {
                    credentials = null;
                } else {
                    credentials = createCredentials(srcCreds);
                }
                taskMgr.setSourceCredentials(id, credentials);
            } else {
                throw new IllegalArgumentException("Invalid command.");
            }
            // default response
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            try (JsonGenerator generator = JSON_FACTORY.createGenerator(response.getWriter())) {
                generator.writeStartObject();
                generator.writeStringField("status", "ok");
                if (id == null) {
                    generator.writeNullField("id");
                } else {
                    generator.writeStringField("id", id);
                }
                generator.writeEndObject();
            }

        } catch (Exception e) {
            log.error("Error while executing command {}", cmd, e);
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            response.setStatus(500);
            try {
                try (JsonGenerator generator = JSON_FACTORY.createGenerator(response.getWriter())) {
                    generator.writeStartObject();
                    generator.writeStringField("status", "error");
                    generator.writeStringField("message", "Error while executing '" + cmd + "': " + e.getMessage());
                    generator.writeEndObject();
                }
            } catch (IOException e1) {
                // ignore
            }
        }
    }

    Credentials createCredentials(String credentialsAsString) {
        Credentials creds;
        int idx = credentialsAsString.indexOf(':');
        if (idx < 0) {
            creds = new SimpleCredentials(credentialsAsString, new char[0]);
        } else {
            creds = new SimpleCredentials(
                    credentialsAsString.substring(0, idx),
                    credentialsAsString.substring(idx + 1).toCharArray());
        }
        return creds;
    }

    private static void write(JsonGenerator generator, RcpTask rcpTask) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(RcpServlet.PARAM_ID, rcpTask.getId());
        generator.writeStringField(RcpServlet.PARAM_SRC, rcpTask.getSource().toString());
        generator.writeStringField(RcpServlet.PARAM_DST, rcpTask.getDestination());
        generator.writeBooleanField(RcpServlet.PARAM_RECURSIVE, rcpTask.isRecursive());
        generator.writeNumberField(RcpServlet.PARAM_BATCHSIZE, rcpTask.getRcp().getBatchSize());
        generator.writeBooleanField(RcpServlet.PARAM_UPDATE, rcpTask.getRcp().isUpdate());
        generator.writeBooleanField(
                RcpServlet.PARAM_ONLY_NEWER, rcpTask.getRcp().isOnlyNewer());
        generator.writeBooleanField(
                RcpServlet.PARAM_NO_ORDERING, rcpTask.getRcp().isNoOrdering());
        generator.writeNumberField(RcpServlet.PARAM_THROTTLE, rcpTask.getRcp().getThrottle());
        if (rcpTask.getRcp().getResumeFrom() == null) {
            generator.writeNullField(RcpServlet.PARAM_RESUME_FROM);
        } else {
            generator.writeStringField(
                    RcpServlet.PARAM_RESUME_FROM, rcpTask.getRcp().getResumeFrom());
        }
        if (rcpTask.getExcludes() != null) {
            if (rcpTask.getExcludes().size() > 0) {
                generator.writeArrayFieldStart(RcpServlet.PARAM_EXCLUDES);
                for (String exclude : rcpTask.getExcludes()) {
                    generator.writeString(exclude);
                }
                generator.writeEndArray();
            }
        } else {
            if (rcpTask.getFilter() != null) {
                generator.writeStringField(
                        RcpServlet.PARAM_FILTER, rcpTask.getFilter().getSourceAsString());
            }
        }
        generator.writeBooleanField(
                PARAM_USE_SYSTEM_PROPERTIES, rcpTask.getConnectionOptions().isUseSystemPropertes());
        generator.writeBooleanField(
                PARAM_DISABLE_HOSTNAME_VERIFICATION,
                rcpTask.getConnectionOptions().isDisableHostnameVerification());
        generator.writeBooleanField(
                PARAM_ALLOW_SELF_SIGNED_CERTIFICATE,
                rcpTask.getConnectionOptions().isAllowSelfSignedCertificates());
        generator.writeNumberField(
                PARAM_CONNECTION_TIMEOUT_MS, rcpTask.getConnectionOptions().getConnectionTimeoutMs());
        generator.writeNumberField(
                PARAM_REQUEST_TIMEOUT_MS, rcpTask.getConnectionOptions().getRequestTimeoutMs());
        generator.writeNumberField(
                PARAM_SOCKET_TIMEOUT_MS, rcpTask.getConnectionOptions().getSocketTimeoutMs());
        if (rcpTask.getConnectionOptions().getProxyHost() != null) {
            generator.writeStringField(
                    PARAM_PROXY_HOST, rcpTask.getConnectionOptions().getProxyHost());
            generator.writeNumberField(
                    PARAM_PROXY_PORT, rcpTask.getConnectionOptions().getProxyPort());
            if (rcpTask.getConnectionOptions().getProxyProtocol() != null) {
                generator.writeStringField(
                        PARAM_PROXY_HOST, rcpTask.getConnectionOptions().getProxyProtocol());
            }
            if (rcpTask.getConnectionOptions().getProxyUsername() != null) {
                generator.writeStringField(
                        PARAM_PROXY_USERNAME, rcpTask.getConnectionOptions().getProxyUsername());
            }
        }
        generator.writeObjectFieldStart("status");
        generator.writeStringField(
                RcpServlet.PARAM_STATE, rcpTask.getResult().getState().name());
        if (rcpTask.getRcp().getCurrentPath() == null) {
            generator.writeNullField("currentPath");
        } else {
            generator.writeStringField("currentPath", rcpTask.getRcp().getCurrentPath());
        }
        if (rcpTask.getRcp().getLastKnownGood() == null) {
            generator.writeNullField("lastSavedPath");
        } else {
            generator.writeStringField("lastSavedPath", rcpTask.getRcp().getLastKnownGood());
        }
        generator.writeNumberField("totalNodes", rcpTask.getRcp().getTotalNodes());
        generator.writeNumberField("totalSize", rcpTask.getRcp().getTotalSize());
        generator.writeNumberField("currentSize", rcpTask.getRcp().getCurrentSize());
        generator.writeNumberField("currentNodes", rcpTask.getRcp().getCurrentNumNodes());
        generator.writeStringField(
                "error",
                rcpTask.getResult().getThrowable() == null
                        ? ""
                        : rcpTask.getResult().getThrowable().toString());
        generator.writeEndObject();
        generator.writeEndObject();
    }
}
