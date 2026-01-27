/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.reproducible.central;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.jrebuild.common.git.GitUtils;
import org.l2x6.jrebuild.common.scm.AbstractScmLocator;
import org.l2x6.jrebuild.reproducible.central.api.BuildspecRepository;
import org.l2x6.pom.tuner.model.Gav;

public class ReproducibleCentralScmLocator extends AbstractScmLocator {
    private static final Logger log = Logger.getLogger(ReproducibleCentralScmLocator.class);
    private static final String SOURCE = "🌏︎";
    private final List<BuildspecRepository> buildspecRepositories;

    public ReproducibleCentralScmLocator(
            Path gitCloneBaseDir,
            Collection<String> reproducibleCentralGitRepositories,
            RemoteScmLookup scmLookup) {
        super(scmLookup);
        final List<BuildspecRepository> result = new ArrayList<>();
        for (String url : reproducibleCentralGitRepositories) {
            final Path workingCopyDir = gitCloneBaseDir.resolve(GitUtils.uriToFileName(url));
            result.add(ReproducibleCentralLayout.cloneOrFetch(url, "master", workingCopyDir));
        }
        this.buildspecRepositories = Collections.unmodifiableList(result);
    }

    public FqScmRef locate(Gav gav) {
        return buildspecRepositories.stream()
                .map(repo -> repo.lookup(gav))
                .filter(r -> r != null)
                .map(recipe -> {
                    ScmRepository url = new ScmRepository(SOURCE, "git", recipe.gitRepo());
                    ScmRef ref = validateTag(url, recipe.gitTag(), gav.getVersion());
                    return new FqScmRef(ref, url);
                })
                .findFirst()
                .orElse(null);
    }

}
