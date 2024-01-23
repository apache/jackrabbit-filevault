package org.apache.jackrabbit.vault.packaging;

public enum UncoveredAncestorHandling {
    /**
     * No enforcement at all
     */
    IGNORE,
    /**
     * Fails with an exception if the node doesn't exist yet otherwise fails only in case the effective node type doesn't contain the given primary and mixin types
     */
    VALIDATE,
    /**
     * Creates the node with the given types if it doesn't exist yet otherwise fails with fails only with an exception in case the effective node type doesn't contain the given primary and mixin types.
     * This is the default handling.
     */
    CREATE,
    /**
     * Creates the node with the given types if it doesn't exist yet, otherwise updates the type information accordingly (addition only for mixin types, i.e. no mixins are removed).
     * May throw an exception in case existing properties or child nodes are not compliant with the modified effective node types.
     */
    CREATEORUPDATE
}
