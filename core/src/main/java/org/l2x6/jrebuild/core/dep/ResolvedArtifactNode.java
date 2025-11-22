/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.dep;

import java.util.Collections;
import java.util.List;
import org.eclipse.aether.repository.RemoteRepository;
import org.l2x6.jrebuild.core.tree.Node;
import org.l2x6.pom.tuner.model.Gavtc;

public class ResolvedArtifactNode implements Comparable<ResolvedArtifactNode>, Node<ResolvedArtifactNode> {
    private final Gavtc gavtc;
    private List<ResolvedArtifactNode> children;
    private final List<RemoteRepository> repositories;

    ResolvedArtifactNode(Gavtc rootGavtc, List<ResolvedArtifactNode> children, List<RemoteRepository> repositories) {
        super();
        this.gavtc = rootGavtc;
        this.children = children;
        this.repositories = repositories;
    }

    public Gavtc gavtc() {
        return gavtc;
    }

    @Override
    public List<ResolvedArtifactNode> children() {
        return children;
    }

    public List<RemoteRepository> repositories() {
        return repositories;
    }

    @Override
    public int compareTo(ResolvedArtifactNode o) {
        return Gavtc.groupFirstComparator().compare(gavtc, o.gavtc);
    }

    public String toString() {
        return gavtc.toString();
    }

    @Override
    public int hashCode() {
        return gavtc.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ResolvedArtifactNode other = (ResolvedArtifactNode) obj;
        return gavtc.equals(other.gavtc);
    }

    void makeImmutable() {
        this.children = Collections.unmodifiableList(children);
    }

    StringBuilder append(StringBuilder sb) {
        sb.append("\n    -> ").append(gavtc);
        for (RemoteRepository repo : repositories) {
            final String url = repo.getUrl();
            sb.append("\n        ").append(url);
            if (!url.endsWith("/")) {
                sb.append('/');
            }
            sb.append(gavtc.getGroupId().replace('.', '/'))
                    .append('/').append(gavtc.getArtifactId())
                    .append('/').append(gavtc.getVersion())
                    .append('/').append(gavtc.getArtifactId()).append('-').append(gavtc.getVersion()).append(".pom");
        }
        return sb;
    }
}
