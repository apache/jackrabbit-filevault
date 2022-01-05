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
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

import javax.jcr.Credentials;
import javax.jcr.Session;
import javax.jcr.SimpleCredentials;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.spi2dav.ConnectionOptions;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.fs.config.DefaultWorkspaceFilter;
import org.apache.jackrabbit.vault.rcp.RcpTask;
import org.apache.jackrabbit.vault.rcp.RcpTaskManager;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.io.JSONWriter;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
@Component(service = Servlet.class,
        property = {
                "service.vendor=The Apache Software Foundation",
                "sling.servlet.paths=" + RcpServlet.SERVLET_PATH
        }
)
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
    

    /**
     * default logger
     */
    protected final Logger log = LoggerFactory.getLogger(RcpServlet.class);

    @Reference
    private RcpTaskManager taskMgr;
    
    private Bundle bundle;

    @Activate
    protected void activate(BundleContext context){
        bundle = context.getBundle();
    }

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        try {
            if ("json".equals(request.getRequestPathInfo().getExtension()) && "info".equals(request.getRequestPathInfo().getSelectorString())) {
                writeInfoJson(response.getWriter());
            } else {
                String taskId = request.getRequestPathInfo().getSuffix();
                JSONWriter w = new JSONWriter(response.getWriter());
                w.setTidy(true);
    
                if (taskId != null) {
                    taskId = taskId.substring(1);
                    RcpTask task = taskMgr.getTask(taskId);
    
                    if (task != null) {
                        write(w, task);
                    } else {
                        // return empty object
                        w.object().endObject();
                    }
                } else {
                    w.object();
                    w.key("tasks").array();
                    for (RcpTask task: taskMgr.getTasks().values()) {
                        write(w, task);
                    }
                    w.endArray();
                    w.endObject();
                }
            }
        } catch (JSONException e) {
            throw new IOException(e.toString());
        }
    }

    private void writeInfoJson(Writer writer) throws JSONException {
        JSONWriter w = new JSONWriter(writer);
        w.setTidy(true);
        w.object();
        w.key(Constants.BUNDLE_SYMBOLICNAME).value(bundle.getSymbolicName());
        w.key(Constants.BUNDLE_VERSION).value(bundle.getVersion().toString());
        w.key(Constants.BUNDLE_VENDOR).value(bundle.getHeaders().get(Constants.BUNDLE_VENDOR));
        w.endObject();
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {
        String json = IOUtils.toString(request.getReader());
        JSONObject data;
        try {
            data = new JSONObject(json);
        } catch (JSONException e) {
            log.error("Error while reading json: {}", e.toString());
            response.setStatus(500);
            return;
        }
        String cmd = data.optString(PARAM_CMD, "");
        RcpTask task;
        final String id = data.optString(PARAM_ID, null);;
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
                    address = new RepositoryAddress(
                            new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment())
                    );
                }
                if (srcCreds != null && srcCreds.length() > 0) {
                    creds = createCredentials(srcCreds);
                }
                Boolean recursive = null;
                if (data.has(PARAM_RECURSIVE)) {
                    recursive = data.optBoolean(PARAM_RECURSIVE, false);
                }

                ConnectionOptions.Builder connectionOptionsBuilder = ConnectionOptions.builder();
                connectionOptionsBuilder.useSystemProperties(data.optBoolean(PARAM_USE_SYSTEM_PROPERTIES));
                connectionOptionsBuilder.allowSelfSignedCertificates(data.optBoolean(PARAM_ALLOW_SELF_SIGNED_CERTIFICATE));
                connectionOptionsBuilder.disableHostnameVerification(data.optBoolean(PARAM_DISABLE_HOSTNAME_VERIFICATION));
                int connectionTimeoutMs = data.optInt(PARAM_CONNECTION_TIMEOUT_MS, -1);
                connectionOptionsBuilder.connectionTimeoutMs(connectionTimeoutMs);
                int requestTimeoutMs = data.optInt(PARAM_REQUEST_TIMEOUT_MS, -1);
                connectionOptionsBuilder.requestTimeoutMs(requestTimeoutMs);
                int socketTimeoutMs = data.optInt(PARAM_SOCKET_TIMEOUT_MS, -1);
                connectionOptionsBuilder.socketTimeoutMs(socketTimeoutMs);

                if (data.has(PARAM_PROXY_HOST)) {
                    connectionOptionsBuilder.proxyHost(data.getString(PARAM_PROXY_HOST));
                    if (data.has(PARAM_PROXY_PORT)) {
                        connectionOptionsBuilder.proxyPort(data.getInt(PARAM_PROXY_PORT));
                    }
                    if (data.has(PARAM_PROXY_PROTOCOL)) {
                        connectionOptionsBuilder.proxyProtocol(data.getString(PARAM_PROXY_PROTOCOL));
                    }
                    if (data.has(PARAM_PROXY_USERNAME)) {
                        connectionOptionsBuilder.proxyUsername(data.getString(PARAM_PROXY_USERNAME));
                        if (data.has(PARAM_PROXY_PASSWORD)) {
                            connectionOptionsBuilder.proxyPassword(data.getString(PARAM_PROXY_PASSWORD));
                        }
                    }
                }
                if (data.has(PARAM_EXCLUDES)) {
                    List<String> excludeList = new LinkedList<>();
                    JSONArray excludes = data.getJSONArray(PARAM_EXCLUDES);
                    for (int idx = 0; idx < excludes.length(); idx++) {
                        excludeList.add(excludes.getString(idx));
                    }
                    if (isEdit) {
                        task = taskMgr.editTask(id, address, connectionOptionsBuilder.build(), creds, dst, excludeList, null, recursive);
                    } else {
                        task = taskMgr.addTask(address, connectionOptionsBuilder.build(), creds, dst, id, excludeList, recursive);
                    }
                } else {
                    final WorkspaceFilter filter;
                    if (data.has(PARAM_FILTER)) {
                        DefaultWorkspaceFilter filterImpl = new DefaultWorkspaceFilter();
                        filterImpl.load(IOUtils.toInputStream(data.getString(PARAM_FILTER), StandardCharsets.UTF_8));
                        filter = filterImpl;
                    } else {
                        filter = null;
                    }
                    if (isEdit) {
                        task = taskMgr.editTask(id, address, connectionOptionsBuilder.build(), creds, dst, null, filter, recursive);
                    } else {
                        task = taskMgr.addTask(address, connectionOptionsBuilder.build(), creds, dst, id, filter, recursive);
                    }
                }

                // add additional data
                if (data.has(PARAM_BATCHSIZE)) {
                    task.getRcp().setBatchSize((int) data.getLong(PARAM_BATCHSIZE));
                }
                if (data.has(PARAM_UPDATE)) {
                    task.getRcp().setUpdate(data.optBoolean(PARAM_UPDATE, false));
                }
                if (data.has(PARAM_ONLY_NEWER)) {
                    task.getRcp().setOnlyNewer(data.optBoolean(PARAM_ONLY_NEWER, false));
                }
                if (data.has(PARAM_NO_ORDERING)) {
                    task.getRcp().setNoOrdering(data.optBoolean(PARAM_NO_ORDERING, false));
                }
                if (data.has(PARAM_THROTTLE)) {
                    task.getRcp().setThrottle(data.getLong(PARAM_THROTTLE));
                }
                if (data.has(PARAM_RESUME_FROM)) {
                    task.getRcp().setResumeFrom(data.getString(PARAM_RESUME_FROM));
                }
                if (isEdit) {
                    response.setStatus(HttpServletResponse.SC_OK);
                } else {
                    response.setStatus(HttpServletResponse.SC_CREATED);
                }
                String path = SERVLET_PATH + "/" + task.getId();
                response.setHeader("Location", path);

            // ---------------------------------------------------------------------------------------------< start >---
            } else if ("start".equals(cmd)) {
                if (id == null || id.length() == 0) {
                    throw new IllegalArgumentException("Need task id.");
                }
                task = taskMgr.getTasks().get(id);
                if (task == null) {
                    throw new IllegalArgumentException("No such task with id='" + id + "'");
                }
                task.start(request.getResourceResolver().adaptTo(Session.class));

            // ----------------------------------------------------------------------------------------------< stop >---
            } else if ("stop".equals(cmd)) {
                if (id == null || id.length() == 0) {
                    throw new IllegalArgumentException("Need task id.");
                }
                task = taskMgr.getTasks().get(id);
                if (task == null) {
                    throw new IllegalArgumentException("No such task with id='" + id + "'");
                }
                task.stop();

            // --------------------------------------------------------------------------------------------< remove >---
            } else if ("remove".equals(cmd)) {
                if (id == null || id.length() == 0) {
                    throw new IllegalArgumentException("Need task id.");
                }
                if (!taskMgr.removeTask(id)) {
                    throw new IllegalArgumentException("No such task with id='" + id + "'");
                }
            // --------------------------------------------------------------------------------------------< remove >---
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
            JSONWriter w = new JSONWriter(response.getWriter());
            w.setTidy(true);
            w.object();
            w.key("status").value("ok");
            w.key("id").value(id);
            w.endObject();

        } catch (Exception e) {
            log.error("Error while executing command {}", cmd, e);
            response.setContentType("application/json");
            response.setCharacterEncoding("utf-8");
            response.setStatus(500);
            JSONWriter w = new JSONWriter(response.getWriter());
            w.setTidy(true);
            try {
                w.object();
                w.key("status").value("error");
                w.key("message").value("Error while executing '" + cmd + "': " + e.getMessage());
                w.endObject();
            } catch (JSONException e1) {
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
                    credentialsAsString.substring(idx+1).toCharArray());
        }
        return creds;
    }

    private static void write(JSONWriter w, RcpTask rcpTask) throws JSONException {
        w.object();
        w.key(RcpServlet.PARAM_ID).value(rcpTask.getId());
        w.key(RcpServlet.PARAM_SRC).value(rcpTask.getSource().toString());
        w.key(RcpServlet.PARAM_DST).value(rcpTask.getDestination());
        w.key(RcpServlet.PARAM_RECURSIVE).value(rcpTask.isRecursive());
        w.key(RcpServlet.PARAM_BATCHSIZE).value(rcpTask.getRcp().getBatchSize());
        w.key(RcpServlet.PARAM_UPDATE).value(rcpTask.getRcp().isUpdate());
        w.key(RcpServlet.PARAM_ONLY_NEWER).value(rcpTask.getRcp().isOnlyNewer());
        w.key(RcpServlet.PARAM_NO_ORDERING).value(rcpTask.getRcp().isNoOrdering());
        w.key(RcpServlet.PARAM_THROTTLE).value(rcpTask.getRcp().getThrottle());
        w.key(RcpServlet.PARAM_RESUME_FROM).value(rcpTask.getRcp().getResumeFrom());
        if (rcpTask.getExcludes() != null) {
            if (rcpTask.getExcludes().size() > 0) {
                w.key(RcpServlet.PARAM_EXCLUDES).array();
                for (String exclude: rcpTask.getExcludes()) {
                    w.value(exclude);
                }
                w.endArray();
            }
        } else {
            if (rcpTask.getFilter() != null) {
                w.key(RcpServlet.PARAM_FILTER).value(rcpTask.getFilter().getSourceAsString());
            }
        }
        w.key(PARAM_USE_SYSTEM_PROPERTIES).value(rcpTask.getConnectionOptions().isUseSystemPropertes());
        w.key(PARAM_DISABLE_HOSTNAME_VERIFICATION).value(rcpTask.getConnectionOptions().isDisableHostnameVerification());
        w.key(PARAM_ALLOW_SELF_SIGNED_CERTIFICATE).value(rcpTask.getConnectionOptions().isAllowSelfSignedCertificates());
        w.key(PARAM_CONNECTION_TIMEOUT_MS).value(rcpTask.getConnectionOptions().getConnectionTimeoutMs());
        w.key(PARAM_REQUEST_TIMEOUT_MS).value(rcpTask.getConnectionOptions().getRequestTimeoutMs());
        w.key(PARAM_SOCKET_TIMEOUT_MS).value(rcpTask.getConnectionOptions().getSocketTimeoutMs());
        if (rcpTask.getConnectionOptions().getProxyHost() != null) {
            w.key(PARAM_PROXY_HOST).value(rcpTask.getConnectionOptions().getProxyHost());
            w.key(PARAM_PROXY_PORT).value(rcpTask.getConnectionOptions().getProxyPort());
            if (rcpTask.getConnectionOptions().getProxyProtocol() != null) {
                w.key(PARAM_PROXY_HOST).value(rcpTask.getConnectionOptions().getProxyProtocol());
            }
            if (rcpTask.getConnectionOptions().getProxyUsername() != null) {
                w.key(PARAM_PROXY_USERNAME).value(rcpTask.getConnectionOptions().getProxyUsername());
            }
        }
        w.key("status").object();
        w.key(RcpServlet.PARAM_STATE).value(rcpTask.getResult().getState().name());
        w.key("currentPath").value(rcpTask.getRcp().getCurrentPath());
        w.key("lastSavedPath").value(rcpTask.getRcp().getLastKnownGood());
        w.key("totalNodes").value(rcpTask.getRcp().getTotalNodes());
        w.key("totalSize").value(rcpTask.getRcp().getTotalSize());
        w.key("currentSize").value(rcpTask.getRcp().getCurrentSize());
        w.key("currentNodes").value(rcpTask.getRcp().getCurrentNumNodes());
        w.key("error").value(rcpTask.getResult().getThrowable() == null ? "" : rcpTask.getResult().getThrowable().toString());
        w.endObject();
        w.endObject();
    }
}

