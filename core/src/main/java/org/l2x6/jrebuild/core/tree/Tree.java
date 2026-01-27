/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.tree;

import java.util.Objects;

public record Tree<T extends Node<T>>(T rootNode) {

    public Tree(T rootNode) {
        this.rootNode = Objects.requireNonNull(rootNode);
    }

    @Override
    public int hashCode() {
        return rootNode.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Tree<T> other = (Tree<T>) obj;
        return rootNode.equals(other.rootNode);
    }

}
