/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import org.l2x6.pom.tuner.model.Gav;

public record FqScmRef(ScmRef scmRef, ScmRepository repository, String sourceOfInfo) {

    public static FqScmRef createUnknown(Gav gav) {
        return new FqScmRef(ScmRef.createUnknown(gav.getVersion()), ScmRepository.createUnknown(gav), null);
    }

    public boolean isUnknown() {
        return scmRef.isUnknown() || repository.isUnknown();
    }

    @Override
    public String toString() {
        return (sourceOfInfo == null ? "" : (sourceOfInfo + " ")) + repository + "#" + scmRef;
    }

}
