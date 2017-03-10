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

import javax.jcr.Session;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.sling.SlingServlet;
import org.apache.jackrabbit.vault.fs.api.RepositoryAddress;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.commons.json.JSONArray;
import org.apache.sling.commons.json.JSONException;
import org.apache.sling.commons.json.JSONObject;
import org.apache.sling.commons.json.io.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 */
@SlingServlet(paths = {
        "/system/jackrabbit/filevault/rcp"
})
public class RcpServlet extends SlingAllMethodsServlet {

    private static final long serialVersionUID = -4571680968447024900L;
    public static final String PARAM_SRC = "src";
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

    /**
     * default logger
     */
    protected final Logger log = LoggerFactory.getLogger(RcpServlet.class);

    @Reference
    private RcpTaskManager taskMgr;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        String taskId = request.getRequestPathInfo().getSuffix();
        try {
            JSONWriter w = new JSONWriter(response.getWriter());
            w.setTidy(true);

            if (taskId != null) {
                taskId = taskId.substring(1);
                RcpTask task = taskMgr.getTask(taskId);

                if (task != null) {
                    task.write(w);
                } else {
                    // return empty object
                    w.object().endObject();
                }
            } else {
                w.object();
                w.key("tasks").array();
                for (RcpTask task: taskMgr.getTasks().values()) {
                    task.write(w);
                }
                w.endArray();
                w.endObject();
            }
        } catch (JSONException e) {
            throw new IOException(e.toString());
        }

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
        RcpTask task = null;
        try {
            // --------------------------------------------------------------------------------------------< create >---
            if ("create".equals(cmd)) {
                String src = data.optString(PARAM_SRC, "");
                String dst = data.optString(PARAM_DST, "");
                String id = data.optString(PARAM_ID, null);
                RepositoryAddress address = new RepositoryAddress(src);
                task = taskMgr.addTask(address, dst, id);

                // add additional data
                if (data.has(PARAM_BATCHSIZE)) {
                    task.getRcp().setBatchSize((int) data.getLong(PARAM_BATCHSIZE));
                }
                task.setRecursive(data.optBoolean(PARAM_RECURSIVE, false));
                task.getRcp().setUpdate(data.optBoolean(PARAM_UPDATE, false));
                task.getRcp().setOnlyNewer(data.optBoolean(PARAM_ONLY_NEWER, false));
                task.getRcp().setNoOrdering(data.optBoolean(PARAM_NO_ORDERING, false));
                if (data.has(PARAM_THROTTLE)) {
                    task.getRcp().setThrottle(data.getLong(PARAM_THROTTLE));
                }
                if (data.has(PARAM_EXCLUDES)) {
                    JSONArray excludes = data.getJSONArray(PARAM_EXCLUDES);
                    for (int idx = 0; idx < excludes.length(); idx++) {
                        task.addExclude(excludes.getString(idx));
                    }
                }
                if (data.has(PARAM_RESUME_FROM)) {
                    task.getRcp().setResumeFrom(data.getString(PARAM_RESUME_FROM));
                }
                response.setStatus(HttpServletResponse.SC_CREATED);
                String path = "/libs/granite/packaging/rcp.tasks/" + task.getId();
                response.setHeader("Location", path);

            // ---------------------------------------------------------------------------------------------< start >---
            } else if ("start".equals(cmd)) {
                String id = data.optString(PARAM_ID, null);
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
                String id = data.optString(PARAM_ID, null);
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
                String id = data.optString(PARAM_ID, null);
                if (id == null || id.length() == 0) {
                    throw new IllegalArgumentException("Need task id.");
                }
                task = taskMgr.getTasks().get(id);
                if (task == null) {
                    throw new IllegalArgumentException("No such task with id='" + id + "'");
                }
                task.remove();

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
            if (task != null) {
                w.key("id").value(task.getId());
            }
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

}

