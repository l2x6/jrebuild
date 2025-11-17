/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import org.l2x6.pom.tuner.model.Gav;

public record ScmRepository(
        String type,
        String uri) {
    public static String UNKNOWN = "unknown";

    public static ScmRepository createUnknown(Gav gav) {
        return new ScmRepository(UNKNOWN, gav.getGroupId());
    }

    public boolean isUnknown() {
        return UNKNOWN.equals(type);
    }

    public boolean isKnown() {
        return !UNKNOWN.equals(type);
    }

    @Override
    public String toString() {
        return uri;
    }
}
