/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.pnc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.rest.client.reactive.QuarkusRestClientBuilder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private final ArtifactEndpointClient artifactEndpoint;
    private final Map<Gav, List<FqScmRef>> cache = new ConcurrentHashMap<>();

    public PncScmLocator(
            Path cacheDir,
            String pncBaseUri,
            RemoteScmLookup scmLookup) {
        super(scmLookup);
        final String artifactsUri = pncBaseUri.endsWith("/") ? (pncBaseUri + "artifacts/") : (pncBaseUri + "/artifacts/");
        final Path artifactsCacheDir = cacheDir.resolve("pnc/artifacts");
        try {
            Files.createDirectories(artifactsCacheDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + artifactsCacheDir, e);
        }

        final CachingResponseFilter filter = new CachingResponseFilter(
                uri -> {
                    String uriStr = uri.toString();
                    return uriStr.startsWith(artifactsUri) && !uriStr.contains("artifacts/filter");
                },
                uri -> artifactsCacheDir.resolve(uri.toString().substring(artifactsUri.length())));

        ArtifactEndpointClient client = QuarkusRestClientBuilder.newBuilder()
                .baseUri(URI.create(pncBaseUri))
                .register(filter)
                //.register(PaginationParameters.class)
                .build(ArtifactEndpointClient.class);

        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.artifactEndpoint = new CachingArtifactEndpointClient(
                artifactsCacheDir,
                mapper,
                client);
    }

    static String toJarGatv(Gav gav) {
        return gav.toGa().toString() + ":pom:" + gav.getVersion() + "*";
    }

    public List<FqScmRef> locate(Gav gav) {
        return cache.computeIfAbsent(gav, k -> {
            Optional<ComparableArtifactInfo> latestBuiltArtifact = latestBuiltArtifact(k);
            if (latestBuiltArtifact.isPresent()) {
                ArtifactInfo latestArtifactInfo = latestBuiltArtifact.get().artifact();
                log.debugf("Latest build %s", latestArtifactInfo.getIdentifier());
                Artifact artifact = artifactEndpoint.getSpecific(latestArtifactInfo.getId());
                Build build = artifact.getBuild();
                String externalUrl;
                if (build != null && (externalUrl = build.getScmRepository().getExternalUrl()) != null) {
                    externalUrl = normalizeScmUri(externalUrl);
                    ScmRepository repo = new ScmRepository(SOURCE, "git", externalUrl);
                    String tag = build.getBuildConfigRevision().getScmRevision();
                    log.debugf("Validating tag from PNC for %s: %s#%s", latestArtifactInfo.getIdentifier(), repo, tag);
                    FqScmRef ref = validateTag(repo, tag, k.getVersion());
                    return List.of(ref);
                }
            }
            return List.of();
        });
    }

    private Optional<ComparableArtifactInfo> latestBuiltArtifact(Gav gav) {

        Function<Integer, Page<ArtifactInfo>> getArtifactPages = i -> {
            //log.debugf("Getting page %d", i);
            Page<ArtifactInfo> result = artifactEndpoint.getAllFiltered(
                    i,
                    org.jboss.pnc.rest.configuration.Constants.MAX_PAGE_SIZE,
                    toJarGatv(gav),
                    Set.of(org.jboss.pnc.enums.ArtifactQuality.NEW),
                    RepositoryType.MAVEN, Set.of(BuildCategory.values()));
            //log.debugf("Got %d results on page %d/%d for %s", result.getTotalHits(), result.getPageIndex(), result.getTotalPages(), gav);
            return result;
        };

        return Clients.stream(getArtifactPages).map(ComparableArtifactInfo::of)
                //.peek(ai -> log.debugf("PNC artifact %s", ai.artifact.getIdentifier()))
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
