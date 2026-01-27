/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.build;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.common.JrebuildCommonUtils;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;

public class BuildGroup {

    private final FqScmRef scmRef;
    private final Set<Gavtc> artifacts;
    private final int hashCode;

    private BuildGroup(FqScmRef scmRef, Set<Gavtc> artifacts) {
        super();
        this.scmRef = Objects.requireNonNull(scmRef);
        this.artifacts = JrebuildCommonUtils.assertImmutable(Objects.requireNonNull(artifacts));
        this.hashCode = 31 * scmRef.hashCode() + artifacts.hashCode();
    }

    public FqScmRef scmRef() {
        return scmRef;
    }

    public Set<Gavtc> artifacts() {
        return artifacts;
    }

    public static Builder builder(FqScmRef scmRef) {
        return new Builder(scmRef);
    }

    public Builder builder() {
        return new Builder(scmRef).artifacts(artifacts);
    }

    @Override
    public int hashCode() {
        return hashCode;
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
        return scmRef.equals(other.scmRef) && artifacts.equals(other.artifacts);
    }

    public boolean contains(Gav gav) {
        return artifacts.stream()
                .map(Gavtc::toGav)
                .filter(gav::equals)
                .findAny().isPresent();
    }

    public BuildGroup assertImmutable() {
        JrebuildCommonUtils.assertImmutable(artifacts);
        return this;
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
        } else {
            sb.append(" [");
            sb.append(artifacts.stream().map(a -> a.getGroupId() + ":*:" + a.getVersion()).distinct()
                    .collect(Collectors.joining(", ")));
            sb.append("]");
        }
        return sb.toString();
    }

    public static class Builder {
        private final FqScmRef scmRef;
        private final SortedSet<Gavtc> artifacts;

        public Builder(FqScmRef scmRef) {
            this.scmRef = scmRef;
            this.artifacts = new TreeSet<>(Gavtc.groupFirstComparator());
        }

        public FqScmRef scmRef() {
            return scmRef;
        }

        public Builder artifact(Gavtc artifact) {
            this.artifacts.add(artifact);
            return this;
        }

        public Builder artifacts(Collection<Gavtc> artifacts) {
            this.artifacts.addAll(artifacts);
            return this;
        }

        public Builder merge(BuildGroup other) {
            if (!this.scmRef.equals(other.scmRef)) {
                throw new IllegalStateException("Cannot merge BuildGroup with scmRef " + other.scmRef
                        + " into BuildGroup with scmRef " + this.scmRef + "; they must be equal");
            }
            this.artifacts.addAll(other.artifacts);
            return this;
        }

        public BuildGroup build() {
            return new BuildGroup(scmRef, Collections.unmodifiableSet(new TreeSet<>(this.artifacts)));
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
    }

}
