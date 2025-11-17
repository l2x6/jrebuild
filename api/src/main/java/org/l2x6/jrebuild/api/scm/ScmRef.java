/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import java.util.Objects;
import org.l2x6.pom.tuner.model.Gav;

public record ScmRef(
        String name,
        Kind kind,
        ScmRepository repository) {

    public static ScmRef of(String tag, ScmRepository repository) {
        Objects.requireNonNull(repository, "repository cannot be null");
        if (tag == null || "HEAD".equals(tag)) {
            return new ScmRef(null, Kind.UNKNOWN, repository);
        }
        return new ScmRef(tag, Kind.TAG, repository);
    }

    public static ScmRef createUnknown(Gav gav) {
        return new ScmRef(null, Kind.UNKNOWN, ScmRepository.createUnknown(gav));
    }

    @Override
    public String toString() {
        return repository + "#" + name;
    }

    public static enum Kind {
        TAG(false), BRANCH(false), COMMIT(false), UNKNOWN(true);

        private Kind(boolean unknown) {
            this.unknown = unknown;
        }

        private final boolean unknown;

        public boolean isUnknown() {
            return unknown;
        }
    }

}
