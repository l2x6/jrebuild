/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import java.util.Comparator;
import org.l2x6.pom.tuner.model.Gav;

public record ScmRepository(
        String source,
        String type,
        String uri) implements Comparable<ScmRepository> {
    public static String UNKNOWN = "unknown";
    private static final Comparator<ScmRepository> COMPARATOR = Comparator.comparing(ScmRepository::uri)
            .thenComparing(ScmRepository::type).thenComparing(ScmRepository::source);

    public static ScmRepository of(String asString) {
        String[] parts = asString.split(" ");
        if (parts.length != 3) {
            throw new IllegalStateException("Two spaces expected in '" + asString + "'");
        }
        return new ScmRepository(parts[0], parts[1], parts[2]);
    }

    public static ScmRepository createUnknown(Gav gav) {
        return new ScmRepository("?", UNKNOWN, gav.getGroupId());
    }

    public boolean isUnknown() {
        return UNKNOWN.equals(type);
    }

    public boolean isKnown() {
        return !UNKNOWN.equals(type);
    }

    @Override
    public String toString() {
        return source + " " + type + ":" + uri;
    }

    @Override
    public int compareTo(ScmRepository o) {
        return COMPARATOR.compare(this, o);
    }
}
