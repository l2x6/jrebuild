package org.l2x6.jrebuild.core.build.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.l2x6.jrebuild.api.os.Arch;
import org.l2x6.jrebuild.api.os.Os;
import org.l2x6.jrebuild.api.os.Shell;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.jrebuild.core.build.BuildRequest;
import org.l2x6.jrebuild.core.build.Reproducibility;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.Gavtc;

/**
 * Layout:
 *
 * <pre>{@code
 * builds
 * + org/group1/project/version/<sha1>
 * | + 2026-03-04T11-22-33 // attempt timestamp
 * | | +- artifacts.txt
 * | | +- buildinfo.properties
 * | + 2026-03-05T22-33-44 // attempt timestamp
 * | | +- artifacts.txt
 * | | +- buildinfo.properties
 * }</pre>
 *
 */
public record GitHubRebuildService(
        Path cloneDirectory,
        Path buildServiceRootDirectory,
        CompletableFuture<BuildMetadataLayout> lazyBuildMetadataLayout) {

    private static final DateTimeFormatter DIR_FORMAT = null;

    static GitHubRebuildService of(
            Path cloneDirectory,
            Path buildServiceRootDirectory, Executor executor) {
        return new GitHubRebuildService(
                cloneDirectory,
                buildServiceRootDirectory,
                CompletableFuture.supplyAsync(() -> BuildMetadataLayout.of(buildServiceRootDirectory.resolve("builds")),
                        executor));
    }

    public BuildReport ensureBuilt(BuildRequest buildRequest) {
        return ensureBuilt(buildRequest, Clock.systemUTC());
    }

    public BuildReport ensureBuilt(BuildRequest buildRequest, Clock clock) {
        /* Clone the build repo */

        /* Create or find the build directory */
        Path buildDir = getLayout().findBuildDirectory(buildRequest.buildGroup());

        Reproducibility requestedRepro = buildRequest.requiredReproducibility();
        Optional<BuildReport> availableBuild;
        try (Stream<BuildReport> reports = listReports(buildDir)) {
            availableBuild = reports.filter(report -> report.reproducibility.isBetterOrSame(requestedRepro))
                    .sorted(BuildReport.byTimestamp().reversed())
                    .findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load reports from " + buildDir, e);
        }

        if (availableBuild.isPresent()) {
            return availableBuild.get();
        }

        return build(buildDir, buildRequest, clock);

    }

    static BuildReport build(Path buildDir, BuildRequest buildRequest, Clock clock) {
        ZonedDateTime ts = ZonedDateTime.now(clock.withZone(ZoneId.of("UTC")));
        String formattedTs = ts.format(DIR_FORMAT);
        Path dir = buildDir.resolve(formattedTs);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create " + dir, e);
        }

        ScmRepository repo = buildRequest.scmRef().repository();

        throw new RuntimeException("unimplemented");
    }

    static Stream<BuildReport> listReports(Path buildDir) throws IOException {
        Stream<BuildReport> result;
        result = Files.list(buildDir)
                .map(f -> buildDir.resolve(f))
                .map(dir -> dir.resolve("build-report.yaml"))
                .filter(Files::isRegularFile)
                .map(buildReportYaml -> load(buildReportYaml, BuildReport.class));
        return result;
    }

    private static <T> T load(Path buildReportYaml, Class<T> cl) {
        return null;
    }

    private BuildMetadataLayout getLayout() {
        try {
            return lazyBuildMetadataLayout.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Could not get lazyBuildMetadataLayout", e);
        }
    }

    static class Workflow {

    }

    static record BuildReport(
            /** When the build was started */
            ZonedDateTime timeStamp,
            /**
             * Overall reproducibility aggregated over all
             */
            Reproducibility reproducibility,
            Map<Gavtc, ArtifactInfo> artifacts,
            Os os,
            Arch arch,
            String shell,
            String cd,
            String buildScript,
            List<Tool> tools

    ) {

        private static Comparator<BuildReport> BY_TIMESTAMP_COMPARATOR = Comparator.comparing(BuildReport::timeStamp);

        public static Comparator<? super BuildReport> byTimestamp() {
            return BY_TIMESTAMP_COMPARATOR;
        }

    }

    static record Tool(
            /** The command name, such as {@code mvn}, {@code java} without directory. Should be installed in PATH */
            String executable,
            String version,
            /** Used esp. for Java; e.g. {@code temurin} or {@code corretto} */
            String distribution,
            Packager packager) {

    }

    public static interface Packager {
        String name();

        void installSelf(Os os, Arch arch, Shell shell, Consumer<Entry<String, String>> env);

        void install(Tool tool, Consumer<Entry<String, String>> env);
    }

    public static enum WellKnownPackager implements Packager {
        sdkman() {

            @Override
            public void installSelf(Os os, Arch arch, Shell shell, Consumer<Entry<String, String>> env) {
                if (shell != Shell.BASH) {
                    throw new IllegalArgumentException(
                            "Cannot install SDKMAN on " + shell + " shell. Only " + Shell.BASH + " is supported");
                }

            }

            @Override
            public void install(Tool tool, Consumer<Entry<String, String>> env) {

            }

        };
    }

    static record ArtifactInfo(
            Reproducibility reproducibility) {

    }

    static record BuildMetadataLayout(
            Path buildsDirectory,
            Map<GavtcRevision, Path> buildsDirectoriesByGavtcRevision) {

        static BuildMetadataLayout of(Path buildsDirectory) {
            Map<GavtcRevision, Path> buildsDirectoriesByGavtcRevision = new ConcurrentHashMap<>();
            try (Stream<Path> files = Files.walk(buildsDirectory)) {
                files
                        .filter(f -> Files.isRegularFile(f) && f.getFileName().toString().equals("artifacts.txt"))
                        .forEach(artifactsTxt -> {
                            final Set<Gavtc> artifacts = readArtifacts(artifactsTxt);
                            Path buildDir = artifactsTxt.getParent();
                            final String revision = buildDir.getFileName().toString();
                            for (Gavtc gavtc : artifacts) {
                                final GavtcRevision gavtcRev = new GavtcRevision(gavtc, revision);
                                final Path existing = buildsDirectoriesByGavtcRevision.put(
                                        gavtcRev,
                                        buildDir);
                                if (existing != null) {
                                    throw new IllegalStateException(gavtcRev + " cannot be associated with multiple paths: "
                                            + existing + ", " + buildDir);
                                }
                            }
                        });
            } catch (IOException e) {
                throw new UncheckedIOException("Could not walk " + buildsDirectory, e);
            }
            return new BuildMetadataLayout(buildsDirectory, buildsDirectoriesByGavtcRevision);
        }

        public Path findBuildDirectory(BuildGroup buildGroup) {
            final String revision = buildGroup.scmRef().scmRef().revisionOrUnknownRevision();
            Set<Path> paths = buildGroup.artifacts().stream()
                    .map(a -> buildsDirectoriesByGavtcRevision.get(new GavtcRevision(a, revision)))
                    .collect(Collectors.toCollection(TreeSet::new));
            return switch (paths.size()) {
            case 0 -> createBuildDirectory(buildGroup);
            case 1 -> paths.iterator().next();
            default -> throw new IllegalArgumentException("Multiple build directories found for " + buildGroup + ": " + paths);
            };
        }

        Path createBuildDirectory(BuildGroup buildGroup) {
            Gav mainArtifact = buildGroup.findMainArtifact();
            FqScmRef scmRef = buildGroup.scmRef();
            Optional<String> lastPathSegment = scmRef.repository().lastPathSegment();
            if (lastPathSegment.isPresent()) {
                mainArtifact = new Gav(mainArtifact.getGroupId(), lastPathSegment.get(), mainArtifact.getVersion());
            }
            final String rev = scmRef.scmRef().revisionOrUnknownRevision();
            final Path result = buildsDirectory.resolve(mainArtifact.getRepositoryPath()).resolve(rev);
            if (!Files.exists(result)) {
                try {
                    Files.createDirectories(result);
                } catch (IOException e) {
                    throw new RuntimeException("Could not create " + result);
                }
            }
            return result;
        }

        static Set<Gavtc> readArtifacts(Path artifactsTxt) {
            try (Stream<String> lines = Files.lines(artifactsTxt, StandardCharsets.UTF_8)) {
                return Collections.unmodifiableSet(lines
                        .map(Gavtc::of)
                        .collect(Collectors.<Gavtc, Set<Gavtc>> toCollection(TreeSet::new)));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + artifactsTxt, e);
            }
        }

        static record GavtcRevision(Gavtc gavtc, String revision) {

            @Override
            public String toString() {
                return gavtc + "@" + revision;
            }

        }
    }

}
