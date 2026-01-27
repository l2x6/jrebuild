/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.tree;

import java.util.Collection;

public record Forest<T extends Node<T>>(Collection<T> trees) {
    //
    //    public Forest<T> mergeOpverlaps() {
    //
    //    }
}
