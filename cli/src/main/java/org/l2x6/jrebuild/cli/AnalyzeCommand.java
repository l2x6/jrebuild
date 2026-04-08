/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli;

import eu.maveniverse.maven.mima.context.Context;
import eu.maveniverse.maven.mima.context.ContextOverrides;
import eu.maveniverse.maven.mima.context.Runtime;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.core.build.BuildGroup;
import org.l2x6.jrebuild.core.dep.DependencyCollector;
import org.l2x6.jrebuild.core.dep.DependencyCollectorRequest;
import org.l2x6.jrebuild.core.dep.DependencyCollectorRequest.Builder;
import org.l2x6.jrebuild.core.dep.ManagedGavsSelector;
import org.l2x6.jrebuild.core.dep.ResolvedArtifactNode;
import org.l2x6.jrebuild.core.mima.JRebuildRuntime;
import org.l2x6.jrebuild.core.mima.internal.CachingMavenModelReader;
import org.l2x6.jrebuild.core.scm.GitRemoteScmLookup;
import org.l2x6.jrebuild.core.scm.ScmRepositoryService;
import org.l2x6.jrebuild.core.scm.ScmRepositoryService.ScmInfoNode;
import org.l2x6.jrebuild.core.tree.CutStemVisitor;
import org.l2x6.jrebuild.core.tree.PrintVisitor;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.GavSet;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.GavtcsPattern;
import org.l2x6.pom.tuner.model.GavtcsSet;
import picocli.CommandLine;
import picocli.CommandLine.ITypeConverter;

@CommandLine.Command(name = "analyze")
public class AnalyzeCommand extends AbstractRootArtifactsCommand implements Runnable {
    private static final Logger log = Logger.getLogger(AnalyzeCommand.class);

    @CommandLine.Option(names = {
            "--buildspec-clone-dir" },
            description = "A directory where to clone remote Domino and Reproducible Central recipes",
            defaultValue = "~/.m2/buildspec")
    Path dominoCloneDir;

    @CommandLine.Option(names = {
            "--domino-recipes-urls" }, description = "A list of Git URLs hosting Domino build recipes", split = ",")
    Set<String> dominoRecipeUrls = Set.of();

    @CommandLine.Option(names = {
            "--reproducible-central-urls" },
            description = "A list of Git URLs hosting Reproducible Central buildspecs, such as https://github.com/jvm-repo-rebuild/reproducible-central.git",
            split = ",")
    Set<String> reproducibleCentralUrls = Set.of();

    @CommandLine.Option(names = { "--pnc-base-url" }, description = "The base URL of PNC build service")
    String pncBaseUri;

    @CommandLine.Option(names = {
            "--ls-remotes-older-than" }, description = """
                    A timestamp in 2025-12-01T10:15:30Z format determining how fresh the entries in ls-remotes-cache must be.
                    You should typically set this to the release date of the root artifacts you are analyzing.
                    E.g. if you are analyzing artigfacts from a project that was released on 2025-12-01T10:15:30Z,
                    then it is fine to set --ls-remotes-older-than=2025-12-01T10:15:30Z
                    because it should be fine to assume that all its dependencies were tagged before that date.
                    If not specified, then it is set to first the execution time on the given day.
                    Use --ls-remotes-older-than=now to force refreshing the entries in ls-remotes-cache.
                    """)
    String rawMinRetievalTime;
    Instant minRetievalTime;

    public AnalyzeCommand() {
    }

    @Override
    public void run() {

        final Path cacheDir = cacheDir();

        dominoCloneDir = resolveHome(dominoCloneDir);
        final Path lsRemotesCache = cacheDir.resolve("ls-remotes-cache.txt");

        if (rawMinRetievalTime == null) {
            minRetievalTime = defaultMinRetrievalTime(cacheDir);
        } else if ("now".equals(rawMinRetievalTime)) {
            minRetievalTime = Instant.now();
        } else {
            minRetievalTime = Instant.parse(rawMinRetievalTime);
        }

        Runtime runtime = JRebuildRuntime.getInstance();
        ContextOverrides.Builder overrides = ContextOverrides.create();
        try (Context context = runtime.create(overrides.build())) {

            final Builder builder = DependencyCollectorRequest.builder()
                    .projectDirectory(projectDir())
                    .includeOptionalDependencies(includeOptionalDeps)
                    .includeParentsAndImports(includeParentsAndImports)
                    .additionalBoms(additionalBoms)
                    .rootArtifacts(rootArtifacts);
            if (bom != null) {
                final GavtcsSet gavtcsSet = GavtcsSet.builder()
                        .includePatterns(bomIncludes)
                        .excludePatterns(excludes)
                        .build();
                final Set<Gavtc> bomRootArtifacts = new ManagedGavsSelector(
                        context.lookup().lookup(CachingMavenModelReader.class).get()::readEffectiveModel)
                        .select(bom, gavtcsSet);
                builder
                        .rootBom(bom)
                        .rootArtifacts(bomRootArtifacts)
                        .excludes(excludes);
            }

            final DependencyCollectorRequest re = builder.build();

            if (re.rootArtifacts().isEmpty()) {
                throw new IllegalStateException(
                        "Specify some root artifacts using (a) --root-artifacts groupId[:artifactId[:version[:type[:classifier]]]][,groupId[:artifactId[:version[:type[:classifier]]]],...] or (b) using --bom groupId:artifactId:version and --bom-includes and --bom-excludes or by combining (a) and (b)");
            }

            try (RemoteScmLookup.AggregateRemoteScmLookup remoteScm = new RemoteScmLookup.AggregateRemoteScmLookup(
                    new GitRemoteScmLookup(lsRemotesCache, minRetievalTime))) {
                final ScmRepositoryService locator = ScmRepositoryService.create(
                        context.lookup().lookup(CachingMavenModelReader.class).get()::readEffectiveModel,
                        remoteScm,
                        dominoCloneDir,
                        cacheDir,
                        reproducibleCentralUrls,
                        dominoRecipeUrls,
                        pncBaseUri);

                //final Collection<ScmInfoNode> dependencyTrees =
                List<ScmInfoNode> roots = DependencyCollector.collect(context, re)

                        .onItem()
                        .transformToMulti(
                                resolvedArtifact -> cutStemVisitor(stem).walk(resolvedArtifact).result())
                        .merge()

                        // .onItem().invoke(resolvedArtifact -> log.infof("Resolved:\n%s", PrintVisitor.toString(resolvedArtifact)))

                        .select().distinct()

                        .onItem()
                        .transformToUniAndMerge(resolvedArtifact -> {

                            return Uni.createFrom().item(() -> locator.newVisitor().walk(resolvedArtifact).rootNode())
                                    .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());

                        })

                        .select().distinct()

                        .onItem()
                        .invoke(p -> log.infof("Scm Repos:\n%s", PrintVisitor.toString(p)))
                        .onFailure().invoke(e -> log.error(e.getMessage(), e))

                        .collect().asList()
                        .await().indefinitely();
                ;

                final ScmInfoNode.Builder forest = ScmInfoNode
                        .builder(new BuildGroup.Builder(FqScmRef.createUnknown(Gav.of("root:root:0.0.0"))));
                for (ScmInfoNode root : roots) {
                    log.infof("Merging " + root);
                    forest.adopt(root.builder());
                }
                ScmInfoNode result = forest.build();
                log.infof("Final tree:\n\n %s", PrintVisitor.<ScmInfoNode> stringBuilderPrintVisitor().walk(result).toString());
            }
        }
    }

    static Instant defaultMinRetrievalTime(Path cacheDir) {
        final Path lastRunProperties = cacheDir.resolve("last-run.properties");
        final Properties props = new Properties();
        boolean exists = Files.exists(lastRunProperties);
        if (exists) {
            try (InputStream in = Files.newInputStream(lastRunProperties)) {
                props.load(in);
            } catch (IOException e) {
                throw new RuntimeException("Could not read " + lastRunProperties);
            }
        }
        final String rawMinimalRetievalTime = (String) props.getProperty("refresh-remotes-older-than");
        if (rawMinimalRetievalTime != null) {
            final Instant result = Instant.parse(rawMinimalRetievalTime);
            Instant startOfTheDay = LocalDate.now()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant();
            if (result.isBefore(startOfTheDay)) {
                return storeLastRun(lastRunProperties, props);
            } else {
                log.infof(
                        "Loaded default refresh-remotes-older-than = %s from %s; if you may want to override the value using the option --refresh-remotes-older-than",
                        result, lastRunProperties);
                return result;
            }
        }
        return storeLastRun(lastRunProperties, props);
    }

    static Instant storeLastRun(Path lastRunProperties, Properties props) {
        final Instant result = Instant.now();
        props.setProperty("refresh-remotes-older-than", result.toString());
        if (!Files.exists(lastRunProperties.getParent())) {
            try {
                Files.createDirectories(lastRunProperties.getParent());
            } catch (IOException e) {
                throw new RuntimeException("Could not create " + lastRunProperties.getParent());
            }
        }
        try (OutputStream out = Files.newOutputStream(lastRunProperties)) {
            props.store(out, "");
        } catch (IOException e) {
            throw new RuntimeException("Could not write " + lastRunProperties);
        }
        log.infof(
                "Setting refresh-remotes-older-than = %s for today and storing it in %s; if you may want to override the value using the option --refresh-remotes-older-than",
                result, lastRunProperties);
        return result;
    }

    public static <THIS extends CutStemVisitor<ResolvedArtifactNode, THIS>> CutStemVisitor<ResolvedArtifactNode, THIS> cutStemVisitor(
            GavSet stem) {
        return new CutStemVisitor<>(node -> stem.contains(node.gavtc().toGav()));
    }

    static class GavConverter implements ITypeConverter<Gav> {
        public Gav convert(String value) throws Exception {
            return Gav.of(value);
        }
    }

    static class GavtcConverter implements ITypeConverter<Gavtc> {
        public Gavtc convert(String value) throws Exception {
            return Gavtc.of(value);
        }
    }

    static class GavtcsPatternConverter implements ITypeConverter<GavtcsPattern> {
        public GavtcsPattern convert(String value) throws Exception {
            return GavtcsPattern.of(value);
        }
    }

    static class GavSetConverter implements ITypeConverter<GavSet> {
        public GavSet convert(String value) throws Exception {
            return GavSet.builder().defaultResult(GavSet.excludeAll()).includes(value).build();
        }
    }

}
