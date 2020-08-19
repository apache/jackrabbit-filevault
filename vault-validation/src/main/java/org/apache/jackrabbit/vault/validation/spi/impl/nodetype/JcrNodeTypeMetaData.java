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

import java.util.Collection;
import java.util.Optional;

import javax.jcr.ItemNotFoundException;
import javax.jcr.NamespaceException;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;

import org.apache.jackrabbit.jcr2spi.nodetype.EffectiveNodeTypeProvider;
import org.apache.jackrabbit.jcr2spi.nodetype.ItemDefinitionProvider;
import org.apache.jackrabbit.jcr2spi.nodetype.NodeTypeDefinitionProvider;
import org.apache.jackrabbit.spi.Name;
import org.apache.jackrabbit.spi.commons.conversion.IllegalNameException;
import org.apache.jackrabbit.spi.commons.conversion.NamePathResolver;
import org.apache.jackrabbit.spi.commons.conversion.NameResolver;
import org.apache.jackrabbit.vault.fs.api.WorkspaceFilter;
import org.apache.jackrabbit.vault.validation.spi.NodeContext;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessage;
import org.apache.jackrabbit.vault.validation.spi.ValidationMessageSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface JcrNodeTypeMetaData {

    void addProperty(@NotNull NodeContext nodeContext, @NotNull NamePathResolver namePathResolver, @NotNull EffectiveNodeTypeProvider effectiveNodeTypeProvider,
            @NotNull NodeTypeDefinitionProvider nodeTypeDefinitionProvider, @NotNull ItemDefinitionProvider itemDefinitionProvider,
            @NotNull ValidationMessageSeverity severity, String name, boolean isMultiValue, Value... values) throws RepositoryException;
    @NotNull JcrNodeTypeMetaData addImplicitChildNode(@NotNull NameResolver nameResolver,
            @NotNull EffectiveNodeTypeProvider effectiveNodeTypeProvider, @NotNull NodeTypeDefinitionProvider nodeTypeDefinitionProvider,
            @NotNull ItemDefinitionProvider itemDefinitionProvider, @NotNull NodeContext nodeContext, @NotNull Name implicitNodeType)
            throws RepositoryException;
    @NotNull JcrNodeTypeMetaData addChildNode(@NotNull NameResolver nameResolver, @NotNull EffectiveNodeTypeProvider effectiveNodeTypeProvider,
            @NotNull NodeTypeDefinitionProvider nodeTypeDefinitionProvider, @NotNull ItemDefinitionProvider itemDefinitionProvider,
            @NotNull ValidationMessageSeverity severity, @NotNull NodeContext nodeContext, @NotNull String primaryType, String... mixinTypes)
                    throws RepositoryException, NamespaceExceptionInNodeName;
    @NotNull JcrNodeTypeMetaData addUnknownChildNode(@NotNull NameResolver nameResolver, @NotNull String name) throws IllegalNameException, NamespaceException;
    
    // navigate
    @NotNull Collection<@NotNull ? extends JcrNodeTypeMetaData> getChildren();
    Optional<JcrNodeTypeMetaData> getNode(NamePathResolver nameResolver, String path)
            throws ItemNotFoundException, RepositoryException;
    @NotNull JcrNodeTypeMetaData getOrCreateNode(NamePathResolver nameResolver, String path) throws RepositoryException;
    
    @NotNull Collection<ValidationMessage> finalizeValidation(@NotNull NamePathResolver nameResolver,
            @NotNull ValidationMessageSeverity severity, @NotNull WorkspaceFilter filter) throws NamespaceException;
    void fetchAndClearValidationMessages(Collection<ValidationMessage> messages);
    @NotNull Name getPrimaryNodeType();
    String getQualifiedPath(NamePathResolver resolver) throws NamespaceException;
    void setNodeTypes(@NotNull NameResolver nameResolver, @NotNull EffectiveNodeTypeProvider effectiveNodeTypeProvider,
           @Nullable String primaryType, String... mixinTypes)
            throws IllegalNameException, ConstraintViolationException, NoSuchNodeTypeException, NamespaceException;
    void setUnknownNodeTypes();
    

}
