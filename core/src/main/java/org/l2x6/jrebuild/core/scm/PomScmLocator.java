/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.model.Model;
import org.apache.maven.model.Scm;
import org.jboss.logging.Logger;
import org.l2x6.jrebuild.api.scm.FqScmRef;
import org.l2x6.jrebuild.api.scm.RemoteScmLookup;
import org.l2x6.jrebuild.api.scm.ScmRef;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.api.scm.ScmRepository;
import org.l2x6.jrebuild.common.scm.AbstractScmLocator;
import org.l2x6.pom.tuner.model.Gav;

public class PomScmLocator extends AbstractScmLocator {
    private static final Logger log = Logger.getLogger(PomScmLocator.class);
    private static final String SOURCE = "♢";
    private static final String HTTPS_GITHUB_COM = "https://github.com/";
    private static final Pattern SCM_TYPE_PATTERN = Pattern.compile("^scm\\:([^\\|\\:]+)[\\|\\:](.*)$");
    private final Function<Gav, Model> getEffectiveModel;

    public PomScmLocator(Function<Gav, Model> getEffectiveModel, RemoteScmLookup scmLookup) {
        super(scmLookup);
        this.getEffectiveModel = getEffectiveModel;
    }

    @Override
    public List<FqScmRef> locate(Gav gav) {
        final Model effectiveModel = getEffectiveModel.apply(gav);
        final Scm scm = effectiveModel.getScm();
        final List<FqScmRef> result = new ArrayList<>();
        final Set<ScmRepository> visitedRepos = new HashSet<>();
        if (scm != null) {
            List<Supplier<String>> uris = List.of(scm::getConnection, scm::getDeveloperConnection, () -> {
                String url = scm.getUrl();
                return url != null && url.startsWith("https://github.com/") ? url : null;
            });
            for (Supplier<String> supplier : uris) {
                String url = supplier.get();
                if (url != null) {
                    ScmRepository repo = toScmRepository(url);
                    if (visitedRepos.add(repo)) {
                        FqScmRef ref = of(gav, scm.getTag(), repo);
                        if (!ref.isUnknownOrFailed()) {
                            return List.of(ref);
                        }
                        result.add(ref);
                    }
                }
            }
        }
        String url = effectiveModel.getUrl();
        if (url != null && url.startsWith("https://github.com/")) {
            ScmRepository repo = toScmRepository(url);
            if (visitedRepos.add(repo)) {
                FqScmRef ref = of(gav, scm.getTag(), repo);
                if (!ref.isUnknownOrFailed()) {
                    return List.of(ref);
                }
                result.add(ref);
            }
        }
        return Collections.unmodifiableList(result);
    }

    static ScmRepository toScmRepository(String url) {
        Matcher m = SCM_TYPE_PATTERN.matcher(url);
        if (m.matches()) {
            return new ScmRepository(SOURCE, m.group(1), normalizeScmUri(m.group(2)));
        }
        return new ScmRepository(SOURCE, "git", normalizeScmUri(url));
    }

    public FqScmRef of(Gav gav, String tag, ScmRepository uri) {
        Objects.requireNonNull(uri, "repository cannot be null");
        try {
            if (tag == null || "HEAD".equals(tag)) {
                final Map<String, String> tagsToHash = scmLookup.getRefs(uri, Kind.TAG);
                final ScmRef ref = guessTag(uri, gav, tagsToHash);
                if (ref != null) {
                    return new FqScmRef(ref, uri);
                } else {
                    final String msg = "Could not guess SCM ref for generic tag name " + tag + " of " + gav + " in " + uri;
                    return FqScmRef.createFailed(gav, uri, msg);
                }
            }
            final ScmRef ref = validateTag(uri, tag, gav.getVersion());
            if (ref == null) {
                final String msg = "Could not find SCM revision for tag " + tag + " declared in the POM of " + gav + " in "
                        + uri;
                return FqScmRef.createFailed(gav, uri, msg);
            }
            return new FqScmRef(ref, uri);
        } catch (Exception e) {
            final StringWriter sw = new StringWriter();
            final String msg = "Could not find SCM ref for " + gav + " in " + uri;
            sw.append(msg).append("\n");
            try (PrintWriter pw = new PrintWriter(sw)) {
                e.printStackTrace(pw);
            }
            return FqScmRef.createFailed(gav, uri, sw.toString());
        }
    }

    protected static String normalizeScmUri(String s) {
        s = s.replace("scm:", "");
        s = s.replace("git:", "");
        s = s.replace("git@", "");
        s = s.replace("ssh:", "");
        s = s.replace("svn:", "");
        // s = s.replace(".git", "");
        if (s.startsWith("http://")) {
            s = s.replace("http://", "https://");
        } else if (!s.startsWith("https://")) {
            s = s.replace(':', '/');
            if (s.startsWith("github.com:")) {
                s = s.replace(':', '/');
            }
            if (s.startsWith("//")) {
                s = "https:" + s;
            } else {
                s = "https://" + s;
            }
        }
        if (s.startsWith(HTTPS_GITHUB_COM)) {
            var tmp = s.substring(HTTPS_GITHUB_COM.length());
            final String[] parts = tmp.split("/");
            if (parts.length > 2) {
                s = HTTPS_GITHUB_COM + parts[0] + "/" + parts[1];
            }
        }
        return s;
    }

}
