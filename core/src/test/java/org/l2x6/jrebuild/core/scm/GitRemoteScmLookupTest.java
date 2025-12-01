/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.scm;

import java.lang.Thread.State;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;
import org.l2x6.jrebuild.core.scm.GitRemoteScmLookup.UrlEntry;

import static org.assertj.core.api.Assertions.assertThat;

public class GitRemoteScmLookupTest {

    @Test
    void loadStore() {
        final Instant minRetrievalTime = Instant.now();
        final Path file = Paths.get("target/GitRemoteScmLookupTest/loadStore-" + UUID.randomUUID() + ".txt");
        assertThat(file).doesNotExist();

        {
            final Map<String, UrlEntry> map = GitRemoteScmLookup.load(file);
            assertThat(map).isEmpty();
        }

        /* store some non-empty items */
        final Instant outdatedRetrievalTime = minRetrievalTime.minus(2, ChronoUnit.SECONDS);
        final Instant futureRetrievalTime = minRetrievalTime.plus(2, ChronoUnit.SECONDS);
        GitRemoteScmLookup.store(file, new UrlEntry("empty", futureRetrievalTime, Collections.emptyMap()));
        final Map<String, String> fooMap = Map.of("k1", "v1", "k2", "v2");
        GitRemoteScmLookup.store(file, new UrlEntry("foo", futureRetrievalTime, fooMap));
        final Map<String, String> fooBarMap = Map.of("fb1", "fbv1", "fb2", "fbv2");
        GitRemoteScmLookup.store(file, new UrlEntry("bar", minRetrievalTime, fooBarMap));

        final Map<String, String> outdatedMap = Map.of("old", "outdated");
        GitRemoteScmLookup.store(file, new UrlEntry("outdated", outdatedRetrievalTime, outdatedMap));

        Runnable check = () -> {
            final Map<String, UrlEntry> map = GitRemoteScmLookup.load(file);
            assertThat(map).hasSize(4);
            assertThat(map.get("empty").refs()).isEmpty();
            assertThat(map.get("empty").retrievalTime()).isEqualTo(futureRetrievalTime);
            assertThat(map.get("foo").refs()).isEqualTo(fooMap);
            assertThat(map.get("foo").retrievalTime()).isEqualTo(futureRetrievalTime);
            assertThat(map.get("bar").refs()).isEqualTo(fooBarMap);
            assertThat(map.get("bar").retrievalTime()).isEqualTo(minRetrievalTime);

            assertThat(map.get("outdated").refs()).isEqualTo(outdatedMap);
            assertThat(map.get("outdated").retrievalTime()).isEqualTo(outdatedRetrievalTime);
        };
        check.run();

        /* Just load and close, the entries should not change */
        {
            GitRemoteScmLookup remoteScm = null;
            try {
                remoteScm = new GitRemoteScmLookup(file, minRetrievalTime);
            } finally {
                if (remoteScm != null) {
                    remoteScm.close();
                }
            }
            while (remoteScm.runner.getState() != State.TERMINATED) {
                /* await termination */
            }
            check.run();
        }

        {
            final Map<String, String> updatedMap = Map.of("new", "updated");
            GitRemoteScmLookup remoteScm = null;
            try {
                remoteScm = new GitRemoteScmLookup(file, minRetrievalTime) {

                    @Override
                    UrlEntry lsRemote(String url) {
                        return new UrlEntry(url, futureRetrievalTime, updatedMap);
                    }

                };
                Map<String, String> refs = remoteScm.getRefs("outdated", Kind.TAG);
                Assertions.assertThat(refs).isEqualTo(updatedMap);
            } finally {
                if (remoteScm != null) {
                    remoteScm.close();
                }
            }
            while (remoteScm.runner.getState() != State.TERMINATED) {
                /* await termination */
            }
            final Map<String, UrlEntry> map = GitRemoteScmLookup.load(file);
            assertThat(map).hasSize(4);
            assertThat(map.get("empty").refs()).isEmpty();
            assertThat(map.get("empty").retrievalTime()).isEqualTo(futureRetrievalTime);
            assertThat(map.get("foo").refs()).isEqualTo(fooMap);
            assertThat(map.get("foo").retrievalTime()).isEqualTo(futureRetrievalTime);
            assertThat(map.get("bar").refs()).isEqualTo(fooBarMap);
            assertThat(map.get("bar").retrievalTime()).isEqualTo(minRetrievalTime);

            assertThat(map.get("outdated").refs()).isEqualTo(updatedMap);
            assertThat(map.get("outdated").retrievalTime()).isEqualTo(futureRetrievalTime);
        }
    }

}
