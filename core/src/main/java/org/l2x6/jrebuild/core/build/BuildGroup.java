/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;

public record BuildGroup(
        FqScmRef scmRef,
        Set<Gavtc> artifacts) {
    public static BuildGroup mutable(FqScmRef scmRef, Gavtc artifact) {
        final TreeSet<Gavtc> artifacts = new TreeSet<>(Gavtc.groupFirstComparator());
        artifacts.add(artifact);
        return new BuildGroup(scmRef, artifacts);
    }

    @Override
    public int hashCode() {
        return scmRef.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        BuildGroup other = (BuildGroup) obj;
        return Objects.equals(scmRef, other.scmRef);
    }

    public boolean contains(Gav gav) {
        return artifacts.stream()
                .map(Gavtc::toGav)
                .filter(gav::equals)
                .findAny().isPresent();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(scmRef.isUnknown() ? "❌ " : "✅ ");
        sb.append(scmRef);
        if (artifacts.isEmpty()) {
            sb.append(" []");
        } else if (artifacts.size() == 1) {
            sb.append(" [").append(artifacts.iterator().next()).append("]");
        }
        sb.append(" [");
        sb.append(artifacts.stream().map(a -> a.getGroupId() + ":*:" + a.getVersion()).distinct()
                .collect(Collectors.joining(", ")));
        sb.append("]");
        return sb.toString();
    }

}
