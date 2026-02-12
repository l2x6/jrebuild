/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.pnc;

import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.jboss.logging.Logger;
import org.jboss.pnc.dto.Artifact;
import org.jboss.pnc.dto.Build;
import org.jboss.pnc.dto.response.ArtifactInfo;
import org.jboss.pnc.dto.response.Page;
import org.jboss.pnc.enums.BuildCategory;
import org.jboss.pnc.enums.RepositoryType;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.jrebuild.api.util.ComparableVersion;
import org.l2x6.jrebuild.common.scm.AbstractScmLocator;
import org.l2x6.pom.tuner.model.Gav;

public class PncScmLocator extends AbstractScmLocator {
    private static final Logger log = Logger.getLogger(PncScmLocator.class);
    private static final String SOURCE = "♖";
    private final String pncBaseUri;
    private final ArtifactEndpointClient artifactEndpoint;

    public PncScmLocator(
            Path cacheDir,
            String pncBaseUri,
            RemoteScmLookup scmLookup) {
        super(scmLookup);
        this.pncBaseUri = pncBaseUri;
        this.artifactEndpoint = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(pncBaseUri))
                .build(ArtifactEndpointClient.class);
    }

    static String toJarGatv(Gav gav) {
        return gav.toGa().toString() + ":pom:" + gav.getVersion() + "*";
    }

    public List<FqScmRef> locate(Gav gav) {
        List<FqScmRef> result = new ArrayList<>();
        Optional<ComparableArtifactInfo> latestBuiltArtifact = latestBuiltArtifact(gav);
        log.debugf("Latest build artifact is %s", latestBuiltArtifact);
        if (latestBuiltArtifact.isPresent()) {
            Artifact artifact = artifactEndpoint.getSpecific(latestBuiltArtifact.get().artifact.getId());
            Build build = artifact.getBuild();
            if (build != null) {
                ScmRepository repo = new ScmRepository(SOURCE, "git", build.getScmRepository().getExternalUrl());
                String tag = build.getBuildConfigRevision().getScmRevision();
                FqScmRef ref = validateTag(repo, tag, gav.getVersion());
                result.add(ref);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private Optional<ComparableArtifactInfo> latestBuiltArtifact(Gav gav) {

        Function<Integer, Page<ArtifactInfo>> getArtifactPages = i -> {
            log.debugf("Looging up %s in PNC", gav);
            Page<ArtifactInfo> result = artifactEndpoint.getAllFiltered(
                    Clients.pagination(i),
                    toJarGatv(gav),
                    Set.of(org.jboss.pnc.enums.ArtifactQuality.NEW),
                    RepositoryType.MAVEN, Set.of(BuildCategory.values()));
            log.debugf("Got %d results on page %d/%d for %s: %s", result.getTotalHits(), result.getPageIndex(),
                    result.getTotalPages(), gav, result.getContent());
            return result;
        };

        return Clients.stream(getArtifactPages).map(ComparableArtifactInfo::of)
                .peek(ai -> log.debugf("PNC artifact %s", ai.artifact.getIdentifier()))
                .max(Comparator.comparing(ComparableArtifactInfo::version));
    }

    static record ComparableArtifactInfo(ArtifactInfo artifact, ComparableVersion version) {
        static ComparableArtifactInfo of(ArtifactInfo artifact) {
            Gatv gatv = Gatv.of(artifact.getIdentifier());
            ComparableVersion v = new ComparableVersion(gatv.gav.getVersion());
            return new ComparableArtifactInfo(artifact, v);
        }
    }

    static class Gatv {
        public static Gatv of(String gatv) {
            String[] segments = gatv.split(":");
            if (segments.length != 4) {
                throw new IllegalArgumentException("Expected 4 segments, found " + gatv);
            }
            return new Gatv(new Gav(segments[0], segments[1], segments[3]), segments[2]);
        }

        private final Gav gav;
        private final String type;

        public Gatv(Gav gav, String type) {
            super();
            this.gav = gav;
            this.type = type;
        }

        public String toString() {
            return gav.toGa().toString() + ":" + type + ":" + gav.getVersion();
        }
    }

}
