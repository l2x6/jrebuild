/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.pnc;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Set;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.response.ArtifactInfo;
import org.jboss.pnc.dto.response.Page;
import org.jboss.pnc.enums.ArtifactQuality;
import org.jboss.pnc.enums.BuildCategory;
import org.jboss.pnc.enums.RepositoryType;

public record CachingArtifactEndpointClient(
        Path cacheDir,
        ObjectMapper mapper,
        ArtifactEndpointClient delegate) implements ArtifactEndpointClient {

    @Override
    public Page<ArtifactInfo> getAllFiltered(
            int pageIndex,
            int pageSize,
            String identifier,
            Set<ArtifactQuality> qualities, RepositoryType repoType,
            Set<BuildCategory> buildCategories) {
        return delegate.getAllFiltered(pageIndex, pageSize, identifier, qualities, repoType, buildCategories);
    }

    @Override
    public Artifact getSpecific(String id) {
        return Clients.readCached(cacheDir, id, mapper, Artifact.class)
                .orElse(delegate.getSpecific(id));
    }
}
