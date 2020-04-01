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
package org.apache.jackrabbit.vault.validation.spi.impl.nodetype;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.Manifest;

import javax.jcr.NamespaceException;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NoSuchNodeTypeException;

import org.apache.commons.lang3.StringUtils;
import org.apache.jackrabbit.JcrConstants;
import org.apache.jackrabbit.commons.cnd.ParseException;
import org.apache.jackrabbit.jcr2spi.nodetype.EffectiveNodeType;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.vault.validation.spi.ValidationContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.apache.jackrabbit.vault.validation.spi.Validator;
import org.apache.jackrabbit.vault.validation.spi.ValidatorFactory;
import org.apache.jackrabbit.vault.validation.spi.ValidatorSettings;
import org.apache.jackrabbit.vault.validation.spi.util.classloaderurl.URLFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@MetaInfServices
public class NodeTypeValidatorFactory implements ValidatorFactory {

    public static final String OPTION_CNDS = "cnds";
    /** The default node type to assume if no other node type is given */
    public static final String OPTION_DEFAULT_NODE_TYPES = "defaultNodeType";
    public static final String OPTION_SEVERITY_FOR_UNKNOWN_NODETYPES = "severityForUnknownNodetypes";

    static final @NotNull String DEFAULT_DEFAULT_NODE_TYPE = JcrConstants.NT_FOLDER;

    static final @NotNull ValidationMessageSeverity DEFAULT_SEVERITY_FOR_UNKNOWN_NODETYPE = ValidationMessageSeverity.WARN;

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeTypeValidatorFactory.class);

    @Override
    public @Nullable Validator createValidator(@NotNull ValidationContext context, @NotNull ValidatorSettings settings) {

        String cndUrls = settings.getOptions().get(OPTION_CNDS);
        // either load map from classloader, from filesystem or from generic url
        if (StringUtils.isBlank(cndUrls)) {
            cndUrls = this.getClass().getClassLoader().getResource("default-nodetypes.cnd").toString();
            LOGGER.warn("Using default nodetypes, consider specifying the nodetypes from the distribution you use!");
        }

        final String defaultNodeType;
        if (settings.getOptions().containsKey(OPTION_DEFAULT_NODE_TYPES)) {
            defaultNodeType = settings.getOptions().get(OPTION_DEFAULT_NODE_TYPES);
        } else {
            defaultNodeType = DEFAULT_DEFAULT_NODE_TYPE;
        }

        final @NotNull ValidationMessageSeverity severityForUnknownNodetypes;
        if (settings.getOptions().containsKey(OPTION_SEVERITY_FOR_UNKNOWN_NODETYPES)) {
            String optionValue = settings.getOptions().get(OPTION_SEVERITY_FOR_UNKNOWN_NODETYPES);
            severityForUnknownNodetypes = ValidationMessageSeverity.valueOf(optionValue.toUpperCase());
        } else {
            severityForUnknownNodetypes = DEFAULT_SEVERITY_FOR_UNKNOWN_NODETYPE;
        }

        NodeTypeManagerProvider ntManagerProvider = null;
        for (String cndUrl : resolveJarUrls(cndUrls.split(","))) {
            try (Reader reader = new InputStreamReader(URLFactory.createURL(cndUrl).openStream(), StandardCharsets.US_ASCII)) {
                if (ntManagerProvider == null) {
                    LOGGER.debug("Register node types from {}", cndUrl);
                    ntManagerProvider = new NodeTypeManagerProvider(reader);
                } else {
                    LOGGER.debug("Register additional node types from {}", cndUrl);
                    ntManagerProvider.registerNodeTypes(reader);
                }
            } catch (RepositoryException | IOException | ParseException e) {
                throw new IllegalArgumentException("Error loading node types from CND at " + cndUrl, e);
            }
        }
        if (ntManagerProvider == null) {
            throw new IllegalArgumentException("At least one valid CND must be given!");
        }

        try {
            EffectiveNodeType defaultEffectiveNodeType = ntManagerProvider.getEffectiveNodeTypeProvider()
                    .getEffectiveNodeType(ntManagerProvider.getNameResolver().getQName(defaultNodeType));
            return new NodeTypeValidator(context.getFilter(), ntManagerProvider, defaultEffectiveNodeType, settings.getDefaultSeverity(),
                    severityForUnknownNodetypes);
        } catch (IllegalNameException | NoSuchNodeTypeException | NamespaceException e) {
            throw new IllegalArgumentException("Error loading default node type " + defaultNodeType, e);
        }
    }

    /**
     * Resolve URLs pointing to JARs with META-INF/MANIFEST carrying a {@code Sling-Nodetypes} header
     * @param urls
     * @return
     */
    static List<String> resolveJarUrls(String... urls) {
        List<String> resolvedUrls = new LinkedList<>();
        for (String url : urls) {
            if (url.endsWith(".jar")) {
                // https://docs.oracle.com/javase/7/docs/api/java/net/JarURLConnection.html
                URL jarUrl;
                try {
                    jarUrl = URLFactory.createURL("jar:" + url + "!/");
                    JarURLConnection jarConnection = (JarURLConnection)jarUrl.openConnection();
                    Manifest manifest = jarConnection.getManifest();
                    String slingNodetypes = manifest.getMainAttributes().getValue("Sling-Nodetypes");
                    // split by "," and generate new JAR Urls
                    if (slingNodetypes == null) {
                        LOGGER.warn("No 'Sling-Nodetypes' header found in manifest of '{}'", jarUrl);
                    } else {
                        for (String nodetype : slingNodetypes.split(",")) {
                            resolvedUrls.add(jarUrl.toString() + nodetype);
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalArgumentException("Could not read from JAR " + url, e);
                }
            } else {
                resolvedUrls.add(url);
            }
        }
        return resolvedUrls;
    }

    @Override
    public boolean shouldValidateSubpackages() {
        return false;
    }

    @Override
    public @NotNull String getId() {
        return ValidatorFactory.ID_PREFIX_JACKRABBIT + "nodetypes";
    }

    @Override
    public int getServiceRanking() {
        return 0;
    }

}
