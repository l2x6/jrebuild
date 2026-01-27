/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.scm;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.l2x6.jrebuild.api.scm.ScmRef.Kind;

public interface RemoteScmLookup {
    String type();

    String getRevision(ScmRepository url, ScmRef.Kind kind, String name);

    Map<String, String> getRefs(ScmRepository url, ScmRef.Kind kind);

    static class AggregateRemoteScmLookup implements RemoteScmLookup, AutoCloseable {

        private final Map<String, RemoteScmLookup> lookups;

        public AggregateRemoteScmLookup(RemoteScmLookup... lookups) {
            super();
            Map<String, RemoteScmLookup> m = new LinkedHashMap<>();
            for (RemoteScmLookup l : lookups) {
                m.put(l.type(), l);
            }
            this.lookups = Map.copyOf(m);
        }

        @Override
        public void close() {
            for (RemoteScmLookup l : lookups.values()) {
                if (l instanceof AutoCloseable) {
                    try {
                        ((AutoCloseable) l).close();
                    } catch (Exception e) {
                    }
                }
            }
        }

        @Override
        public String getRevision(ScmRepository url, Kind kind, String name) {
            return lookup(url).getRefs(url, kind).get(name);
        }

        private RemoteScmLookup lookup(ScmRepository url) {
            RemoteScmLookup result = lookups.get(url.type());
            if (result == null) {
                throw new IllegalArgumentException("Cannot lookup refs for SCM type '" + url.type() + "' in " + url);
            }
            return result;
        }

        @Override
        public Map<String, String> getRefs(ScmRepository url, Kind kind) {
            return lookup(url).getRefs(url, kind);
        }

        @Override
        public String type() {
            throw new UnsupportedOperationException();
        }

    }

    static class MutableRemoteScmLookup implements RemoteScmLookup {

        private final Map<ScmRepository, Map<String, String>> entries = new LinkedHashMap<>();

        private final String type;

        public MutableRemoteScmLookup(String type) {
            super();
            this.type = type;
        }

        public MutableRemoteScmLookup put(ScmRepository url, Map<String, String> tags) {
            entries.put(url, tags);
            return this;
        }

        @Override
        public String getRevision(ScmRepository url, Kind kind, String name) {
            return getRefs(url, kind).get(name);
        }

        @Override
        public Map<String, String> getRefs(ScmRepository url, Kind kind) {
            return entries.computeIfAbsent(url, k -> Collections.emptyMap());
        }

        @Override
        public String type() {
            return type;
        }

    }

}
