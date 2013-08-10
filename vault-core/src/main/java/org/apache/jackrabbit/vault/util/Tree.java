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

package org.apache.jackrabbit.vault.util;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <code>Tree</code>...
 */
public class Tree<E> {

    private final char separator;

    private Node<E> root = new Node<E>(null, "");

    public Tree() {
        this('/');
    }

    public Tree(char separator) {
        this.separator = separator;
    }

    public void clear() {
        root.elem = null;
        root.children.clear();
    }

    public E put(String path, E elem) {
        E previous;
        Node<E> n = get(path, true);
        previous = n.elem;
        n.elem = elem;
        return previous;
    }

    public E get(String path) {
        Node<E> n = get(path, false);
        return n == null ? null : n.elem;
    }

    public Node<E> getNode(String path) {
        return get(path, false);
    }

    public E remove(String path) {
        Node<E> n = get(path, false);
        if (n == null) {
            return null;
        }
        E previous = n.elem;
        n.elem = null;
        n.prune();
        return previous;
    }

    private Node<E> get(String path, boolean create) {
        String[] segs = Text.explode(path, separator);
        Node<E> n = root;
        for (String name: segs) {
            Node<E> c = n.get(name, create);
            if (c == null) {
                return null;
            }
            n = c;
        }
        return n;
    }

    public void removeChildren(String path) {
        Node<E> n = get(path, false);
        if (n != null) {
            n.removeChildren();
        }
    }

    public Map<String, E> map() {
        Map<String, E> map = new LinkedHashMap<String, E>();
        fill(map, root, "");
        return map;
    }

    public String getRootPath() {
        Node<E> n = root;
        StringBuffer path = new StringBuffer();
        while (n.elem == null && n.children.size() == 1) {
            n = n.children.values().iterator().next();
            path.append(separator).append(n.name);
        }
        if (path.length() == 0) {
            path.append(separator);
        }
        return path.toString();
    }

    public Node<E> getRootNode() {
        Node<E> n = root;
        while (n.elem == null && n.children.size() == 1) {
            n = n.children.values().iterator().next();
        }
        return n;
    }

    private void fill(Map<String, E> map, Node<E> node, String parentPath) {
        String path;
        if (parentPath.length() != 1) {
            // stupid check for root path
            path = parentPath + separator + node.name;
        } else {
            path = parentPath + node.name;
        }
        if (node.elem != null) {
            map.put(path, node.elem);
        }
        for (Node<E> child: node.children.values()) {
            fill(map, child, path);
        }
    }

    public static class Node<E> {

        private String name;

        private E elem;

        private final Node parent;

        private final Map<String, Node<E>> children = new LinkedHashMap<String, Node<E>>();

        private Node(Node parent, String name) {
            this.parent = parent;
            this.name = name;
        }

        private Node<E> get(String name, boolean create) {
            Node<E> child = children.get(name);
            if (child == null && create) {
                child = new Node<E>(this, name);
                children.put(name, child);
            }
            return child;
        }

        private Node<E> remove(String name) {
            return children.remove(name);
        }

        private void prune() {
            if (children.isEmpty() && elem == null && parent != null) {
                parent.remove(this.name);
                parent.prune();
            }
        }

        private void removeChildren() {
            children.clear();
            prune();
        }

        public String getName() {
            return name;
        }

        public E getElem() {
            return elem;
        }

        public Node getParent() {
            return parent;
        }

        public Map<String, Node<E>> getChildren() {
            return children;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("Node");
            sb.append("{name='").append(name).append('\'');
            sb.append(", elem=").append(elem);
            sb.append(", children=").append(children.keySet());
            sb.append('}');
            return sb.toString();
        }
    }

}