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
package org.apache.jackrabbit.vault.validation.spi.impl;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.ValueFormatException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.felix.cm.json.ConfigurationReader;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.util.Text;
import org.apache.jackrabbit.value.ValueFactoryImpl;
import org.apache.jackrabbit.vault.util.DocViewNode2;
import org.apache.jackrabbit.vault.util.DocViewProperty2;
import org.apache.jackrabbit.vault.util.UncheckedRepositoryException;
import org.apache.jackrabbit.vault.validation.ValidationExecutor;
import org.apache.jackrabbit.vault.validation.impl.util.ValidatorException;
import org.apache.jackrabbit.vault.validation.spi.DocumentViewXmlValidator;
import org.apache.jackrabbit.vault.validation.spi.GenericJcrDataValidator;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.OsgiConfigurationValidator;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OsgiConfigurationParserValidator implements DocumentViewXmlValidator, GenericJcrDataValidator {

    private static final String SLING_OSGI_CONFIG_NODETYPE = "sling:OsgiConfig";

    private static final String OSGI_CONFIG_NODE_PATH = "/(?:[^/]*/){0,4}?config(?:\\.[^/]*)?/.*";

    private static final Pattern OSGI_CONFIG_NODE_PATH_PATTERN = Pattern.compile(OSGI_CONFIG_NODE_PATH);

    /**
     * Binary formats described at <a href="https://sling.apache.org/documentation/bundles/configuration-installer-factory.html#configuration-serialization-formats">Configuration Installer Serialization Formats</a>
     */
    private static final Pattern OSGI_CONFIG_BINARY_NODE_PATH_PATTERN =
            Pattern.compile(OSGI_CONFIG_NODE_PATH + "\\.(config|cfg\\.json|cfg)");

    private final Map<String, OsgiConfigurationValidator> osgiConfigurationValidators;
    private static final ValueFactory VALUE_FACTORY = ValueFactoryImpl.getInstance();

    /**
     * Formats described at <a href="https://sling.apache.org/documentation/bundles/configuration-installer-factory.html#configuration-serialization-formats">Configuration Installer Serialization Formats</a>
     */
    enum OsgiConfigurationSerializationFormat {
        CFG_JSON,
        CONFIG,
        CFG,
        NT_OSGI_CONFIG
    }

    public OsgiConfigurationParserValidator() {
        osgiConfigurationValidators = new HashMap<>();
    }

    @Override
    public @Nullable Collection<ValidationMessage> done() {
        return null;
    }

    @Override
    public @Nullable Collection<ValidationMessage> validate(
            @NotNull DocViewNode2 node, @NotNull NodeContext nodeContext, boolean isRoot) {
        if (SLING_OSGI_CONFIG_NODETYPE.equals(node.getPrimaryType().orElse(""))
                && OSGI_CONFIG_NODE_PATH_PATTERN
                        .matcher(nodeContext.getNodePath())
                        .matches()) {
            Map<String, Object> configuration = deserializeOsgiConfiguration(node);
            Map.Entry<String, String> pidAndSubname = extractPidAndSubnameFromName(
                    Text.getName(nodeContext.getNodePath()), OsgiConfigurationSerializationFormat.NT_OSGI_CONFIG);
            return validateConfig(
                    configuration,
                    OsgiConfigurationSerializationFormat.NT_OSGI_CONFIG,
                    pidAndSubname.getKey(),
                    pidAndSubname.getValue(),
                    nodeContext.getNodePath());
        }
        return null;
    }

    static Optional<Object> convertValue(String v, int type) {
        Value value;
        if (type == PropertyType.UNDEFINED) {
            type = PropertyType.STRING;
        }
        try {
            value = VALUE_FACTORY.createValue(v, type);
        } catch (ValueFormatException e) {
            throw new UncheckedRepositoryException(e);
        }
        return convertValue(value);
    }

    /**
     * Convert v according to its type.
     */
    private static Optional<Object> convertValue(Value v) {
        final Object object;
        try {
            switch (v.getType()) {
                case PropertyType.STRING:
                    object = v.getString();
                    break;
                case PropertyType.DATE:
                    object = v.getDate();
                    break;
                case PropertyType.DOUBLE:
                    object = v.getDouble();
                    break;
                case PropertyType.LONG:
                    object = v.getLong();
                    break;
                case PropertyType.BOOLEAN:
                    object = v.getBoolean();
                    break;
                default:
                    // TODO: log
                    object = null;
            }
        } catch (RepositoryException e) {
            throw new UncheckedRepositoryException(e);
        }
        return Optional.ofNullable(object);
    }

    static Map.Entry<String, String> extractPidAndSubnameFromName(
            String name, OsgiConfigurationSerializationFormat format) {
        // strip potential extension

        switch (format) {
            case CFG:
                name = name.substring(0, name.length() - ".cfg".length());
                break;
            case CFG_JSON:
                name = name.substring(0, name.length() - ".cfg.json".length());
                break;
            case CONFIG:
                name = name.substring(0, name.length() - ".config".length());
                break;
            default:
                break;
        }
        int separatorPos = name.lastIndexOf('~');
        if (separatorPos == -1) {
            separatorPos = name.lastIndexOf('-');
        }
        final String pid;
        final String subname;
        if (separatorPos == -1) {
            pid = name;
            subname = null;
        } else {
            pid = name.substring(0, separatorPos);
            subname = name.substring(separatorPos + 1);
        }
        return new SimpleEntry<>(pid, subname);
    }

    @Override
    public boolean shouldValidateJcrData(@NotNull Path filePath, @NotNull Path basePath) {
        return isBinaryOsgiConfig(ValidationExecutor.filePathToNodePath(filePath));
    }

    @Override
    @Nullable
    public Collection<ValidationMessage> validateJcrData(
            @NotNull InputStream input,
            @NotNull Path filePath,
            @NotNull Path basePath,
            @NotNull Map<String, Integer> nodePathsAndLineNumbers)
            throws IOException {
        String nodePath = ValidationExecutor.filePathToNodePath(filePath);
        OsgiConfigurationSerializationFormat type = getType(Text.getName(nodePath));
        Map<String, Object> config = deserializeOsgiConfiguration(type, input);
        Map.Entry<String, String> pidAndSubname =
                extractPidAndSubnameFromName(filePath.getFileName().toString(), type);
        return validateConfig(config, type, pidAndSubname.getKey(), pidAndSubname.getValue(), nodePath);
    }

    private @NotNull Collection<ValidationMessage> validateConfig(
            Map<String, Object> config,
            OsgiConfigurationSerializationFormat type,
            String pid,
            String subname,
            String nodePath) {
        @NotNull List<ValidationMessage> allMessages = new LinkedList<>();
        for (Map.Entry<String, OsgiConfigurationValidator> entry : osgiConfigurationValidators.entrySet()) {
            try {
                final Collection<ValidationMessage> messages =
                        entry.getValue().validateConfig(config, pid, subname, nodePath);
                if (messages != null && !messages.isEmpty()) {
                    allMessages.addAll(messages);
                }
            } catch (RuntimeException e) {
                throw new ValidatorException(entry.getKey(), e);
            }
        }
        return allMessages;
    }

    static @NotNull OsgiConfigurationSerializationFormat getType(String nodeName) {
        if (nodeName.endsWith(".cfg.json")) {
            return OsgiConfigurationSerializationFormat.CFG_JSON;
        } else if (nodeName.endsWith(".config")) {
            return OsgiConfigurationSerializationFormat.CONFIG;
        } else if (nodeName.endsWith(".cfg")) {
            return OsgiConfigurationSerializationFormat.CFG;
        } else {
            throw new IllegalArgumentException(
                    "Given file name " + nodeName + " does not represent a known OSGi configuration serialization");
        }
    }

    private boolean isBinaryOsgiConfig(@NotNull String nodePath) {
        return OSGI_CONFIG_BINARY_NODE_PATH_PATTERN.matcher(nodePath).matches();
    }

    Map<String, Object> deserializeOsgiConfiguration(
            @NotNull OsgiConfigurationSerializationFormat serializationType, @NotNull InputStream input)
            throws IOException {
        try {
            switch (serializationType) {
                case CONFIG:
                    return convertToMap(org.apache.felix.cm.file.ConfigurationHandler.read(input));
                case CFG:
                    Properties properties = new Properties();
                    properties.load(input);
                    return convertToMap(properties);
                case CFG_JSON:
                    Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
                    ConfigurationReader configReader = org.apache.felix.cm.json.Configurations.buildReader()
                            .build(reader);
                    return convertToMap(configReader.readConfiguration());
                default:
                    throw new IllegalArgumentException("Only .cfg, .cfg.json or .config binary formats supported");
            }
        } catch (NoClassDefFoundError e) {
            throw new IllegalStateException("Cannot deserialize OSGi configuration due to missing dependencies", e);
        }
    }

    /**
     * Similar to <a href="https://github.com/apache/sling-org-apache-sling-installer-provider-jcr/blob/fcb77de40973672548e79f3a42c19f5decd95651/src/main/java/org/apache/sling/installer/provider/jcr/impl/ConfigNodeConverter.java#L82">ConfigNodeConverter</a>.
     * @param node the node with type {@code sling:OsgiConfig}
     * @return the deserialized OSGi configuration object
     */
    Map<String, Object> deserializeOsgiConfiguration(DocViewNode2 node) {
        Map<String, Object> configurationMap = new HashMap<>();
        for (DocViewProperty2 property : node.getProperties()) {
            // ignore every namespaced property
            if (!Name.NS_DEFAULT_URI.equals(property.getName().getNamespaceURI())) {
                continue;
            }
            if (property.isMultiValue()) {
                Object[] data = property.getStringValues().stream()
                        .map(v -> OsgiConfigurationParserValidator.convertValue(v, property.getType()))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toArray(Object[]::new);
                configurationMap.put(property.getName().getLocalName(), data);
            } else {
                Object data = property.getStringValue()
                        .map(v -> OsgiConfigurationParserValidator.convertValue(v, property.getType()))
                        .orElse(Optional.empty())
                        .orElse(null);
                if (data != null) {
                    configurationMap.put(property.getName().getLocalName(), data);
                }
            }
        }
        return configurationMap;
    }

    static Map<String, Object> convertToMap(Dictionary<String, ?> dictionary) {
        List<String> keys = Collections.list(dictionary.keys());
        return keys.stream().collect(Collectors.toMap(Function.identity(), dictionary::get));
    }

    static Map<String, Object> convertToMap(Properties properties) {
        return properties.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> String.valueOf(e.getKey()),
                        e -> String.valueOf(e.getValue()),
                        (prev, next) -> next,
                        HashMap::new));
    }

    public void setOsgiConfigurationValidators(Map<String, OsgiConfigurationValidator> osgiConfigurationValidators) {
        this.osgiConfigurationValidators.putAll(osgiConfigurationValidators);
    }
}
