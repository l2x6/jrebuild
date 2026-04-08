/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.cli;

import java.nio.file.Path;
import java.util.List;
import org.l2x6.jrebuild.cli.AnalyzeCommand.GavConverter;
import org.l2x6.jrebuild.cli.AnalyzeCommand.GavSetConverter;
import org.l2x6.jrebuild.cli.AnalyzeCommand.GavtcConverter;
import org.l2x6.jrebuild.cli.AnalyzeCommand.GavtcsPatternConverter;
import org.l2x6.pom.tuner.model.Gav;
import org.l2x6.pom.tuner.model.GavSet;
import org.l2x6.pom.tuner.model.Gavtc;
import org.l2x6.pom.tuner.model.GavtcsPattern;
import picocli.CommandLine;

public class AbstractRootArtifactsCommand {
    @CommandLine.Option(names = { "--project-dir" }, description = "A directory containing a source tree to analyze")
    private Path projectDir;
    private volatile Path projectDirResolved;

    @CommandLine.Option(names = {
            "--bom" },
            description = "BOM in format groupId:artifactId:version whose constraints should be used as top level artifacts to be built",
            converter = GavConverter.class)
    protected Gav bom;

    @CommandLine.Option(names = {
            "--bom-includes" },
            description = """
                    A list of patterns in format groupId[:artifactId[:version[:type[:classifier]]]] where each segment may contain one or more * wildcards.
                    These patterns are used for filtering the entries of the BOM (specified through --bom) and are added to the set of root artifacts.
                    """,
            converter = GavtcsPatternConverter.class, split = ",")
    protected List<GavtcsPattern> bomIncludes = List.of(GavtcsPattern.matchAll());

    @CommandLine.Option(names = {
            "--excludes" },
            description = """
                    A list of patterns in format groupId[:artifactId[:version[:type[:classifier]]]] where each segment may contain one or more * wildcards.
                    Artifacts matching any of these patterns are excluded from the set of root artifacts and if any of those artifacts is hit during
                    the analysis then the artifact is ignored and the analysis won't descend to its dependencies.
                    """,
            converter = GavtcsPatternConverter.class, split = ",")
    protected List<GavtcsPattern> excludes = List.of();

    @CommandLine.Option(names = {
            "--root-artifacts" },
            description = """
                    Root artifacts whose dependencies should be analyzed in format groupId:artifactId:version[:type[:classifier]].
                    Note that root artifacts can also be specified via --bom, --bom-includes (and --excludes if needed).
                    """,
            converter = GavtcConverter.class, split = ",")
    protected List<Gavtc> rootArtifacts = List.of();

    @CommandLine.Option(names = {
            "--include-optional-deps" }, description = """
                    If true, all optional dependencies (both first level and transitive) of root artifacts will be processed;
                    otherwise only the first level optionals will be processed
                    """, defaultValue = "true", fallbackValue = "true")
    protected boolean includeOptionalDeps;

    @CommandLine.Option(names = {
            "--include-parents-and-imports" },
            description = "If true, process also parents and dependencyManagement imports as if they were dependencies; otherwise process only dependencies",
            defaultValue = "true", fallbackValue = "true")
    protected boolean includeParentsAndImports;

    @CommandLine.Option(names = {
            "--additional-boms" },
            description = """
                    A list of groupId:artifactId:version whose constraints should be enforced in addition to the main BOM specified through --bom.
                    BOMs specified via --additional-boms do not extend the universe for --bom-includes.
                    """,
            converter = GavConverter.class, split = ",")
    protected List<Gav> additionalBoms = List.of();

    @CommandLine.Option(names = {
            "--cut-stem" },
            description = """
                    A list of patterns in format groupId[:artifactId[:version[:type[:classifier]]]] where each segment may contain one or more * wildcards.
                    After creating the initial dependency trees of all root artiafcts, these patterns are used for removing some
                    (possibly empty) rooted part (i.e. stem) of those dependecy trees before searching for build metadata.
                    This is typically useful when you want to analyze only dependencies of some project, but not the project itself.
                    In such a situation, you would use --exclude-stem to exclude the artifacts belonging to that project.
                    """,
            converter = GavSetConverter.class)
    protected GavSet stem = GavSet.excludeAll();

    @CommandLine.Option(names = {
            "--cache-dir" },
            description = """
                    A directory under which various cache and index files are stored, such as ls-remotes-cache.txt - the tag name -> sha1 mappings are cached for remote SCM repositories.
                    """,
            defaultValue = "~/.cache/jrebuild")
    private Path cacheDir;
    private volatile Path cacheDirResolved;
    private volatile Path userHome;

    protected Path projectDir() {
        Path result;
        if ((result = projectDirResolved) == null) {
            result = projectDirResolved = resolveHome(projectDir);
        }
        return result;
    }

    protected Path cacheDir() {
        Path result;
        if ((result = cacheDirResolved) == null) {
            result = cacheDirResolved = resolveHome(cacheDir);
        }
        return result;
    }

    protected Path userHome() {
        Path result;
        if ((result = userHome) == null) {
            result = userHome = Path.of(System.getProperty("user.home"));
        }
        return result;
    }

    protected Path resolveHome(Path path) {
        if (path != null && path.startsWith("~")) {
            return userHome().resolve(path.subpath(1, path.getNameCount()));
        }
        return path;
    }

}
