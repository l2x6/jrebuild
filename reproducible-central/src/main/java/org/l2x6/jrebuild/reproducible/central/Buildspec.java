/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.reproducible.central;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public record Buildspec(
        /* 1. what does this rebuild? */
        /** Central Repository coordinates for the Reference release (for multi-module, pick an artitrary module) */
        String groupId,
        /** Central Repository coordinates for the Reference release (for multi-module, pick an artitrary module) */
        String artifactId,
        /** Central Repository coordinates for the Reference release (for multi-module, pick an artitrary module) */
        String version,
        /** Where are reference binaries? */
        String referenceRepo,
        /**
         * (= gav to path in referenceRepo) is Maven repository: https://maven.apache.org/repositories/layout.html
         * (future options could be PyPI, npm, Brew, Dockerhub, ...)
         */
        String layout,

        /* 2. where is source code? */
        String gitRepo,
        String gitTag,
        /** or use source zip archive */
        String sourceDistribution,
        /** ${artifactId}-${version} */
        String sourcePath,
        String sourceRmFiles,

        /* 3. rebuild environment prerequisites */
        /** mvn, mvn-3.8.5, gradle, sbt */
        String tool,
        /** 8 */
        String jdk,
        String toolchains,
        /** crlf for Windows, lf for Unix */
        Newline newline,
        /** optional */
        String umask,
        /** Etc/GMT */
        String timezone,
        /** en_US */
        String locale,

        /** if rebuild output depends on OS and/or processor architecture */
        String os,
        String arch,

        /**
         * if rebuild output depends on Azul JDK even when default OpenJDK is available, like with JDK 8 and
         * cyclonedx-maven-plugin
         */
        boolean jdkForceAzul,
        /** if rebuild output depends on working directory where source code is stored: /var/<tool>/app */
        String workdir,

        /* 4. rebuild command */
        /** mvn -Papache-release clean package -DskipTests -Dmaven.javadoc.skip -Dgpg.skip */
        String command,
        /** optional */
        String execBefore,
        /** optional */
        String execAfter,

        /* 5. location of the buildinfo file generated during rebuild to record output fingerprints */
        /** target/${artifactId}-${version}.buildinfo */
        String buildinfo,

        /* 6. if the release is finally not reproducible, link to an issue tracker entry if one was created */
        /** ${artifactId}-${version}.diffoscope */
        String diffoscope,
        /** https://github.com/project_org/${artifactId}/issues/xx */
        String issue) {

    public static Buildspec of(Path file) {
        try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
            Builder b = new Builder();
            lines.forEach(b::line);
            return b.build();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + file, e);
        } catch (Exception e) {
            throw new RuntimeException("Could not parse " + file + ": " + e.getMessage(), e);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static enum Newline {
        lf("lf"), crlf("crlf"), crlf_nogit("crlf-nogit");

        private final String literal;

        private Newline(String literal) {
            this.literal = literal;
        }

        public String toString() {
            return literal;
        }

        public static Newline of(String literal) {
            return switch (literal) {
            case "lf" -> lf;
            case "crlf" -> crlf;
            case "crlf,no-auto.crlf" -> crlf; // TODO https://github.com/jvm-repo-rebuild/reproducible-central/issues/4556
            case "crlf-nogit" -> crlf_nogit; // TODO https://github.com/jvm-repo-rebuild/reproducible-central/issues/4556
            default -> throw new IllegalArgumentException("Unexpected " + Newline.class.getName() + " value: " + literal);
            };
        }
    }

    public static class Builder {

        private Map<String, String> values = new LinkedHashMap<>();

        public Builder() {
            values.put("locale", "en_US");
            values.put("timezone", "UTC");
            values.put("umask", "0002");
            values.put("referenceRepo", "https://repo.maven.apache.org/maven2/");

        }

        public Builder line(String line) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                return this;
            }
            KeyVal kv = new KeyValParser(line).parse();
            values.put(kv.key, kv.val);
            return this;
        }

        static class KeyValParser {
            int pos = 0;
            final String src;

            public KeyValParser(String src) {
                super();
                this.src = src;
            }

            KeyVal parse() {
                String key = identifier();
                consume('=');
                String val = value();
                ws();
                comment();
                if (pos != src.length()) {
                    throw new IllegalStateException("Unexpected characters at the end of input '" + src + "' at index " + pos);
                }
                return new KeyVal(key, val);
            }

            private void ws() {
                char ch;
                while (pos < src.length()) {
                    ch = src.charAt(pos);
                    if (!(ch == ' '
                            || ch == '\t')) {
                        break;
                    }
                    pos++;
                }
            }

            private void comment() {
                if (pos < src.length() && src.charAt(pos) == '#') {
                    pos = src.length();
                }
            }

            private void consume(char c) {
                if (pos >= src.length()) {
                    throw new IllegalStateException("Unexpected end of input in " + src + "; expected " + c);
                }
                char ch;
                if ((ch = src.charAt(pos)) != c) {
                    throw new IllegalStateException("Unexpected character " + ch + " at index " + pos + "; expected " + c);
                }
                pos++;
            }

            private String identifier() {
                if (pos >= src.length()) {
                    throw new IllegalStateException(
                            "Unexpected end of input in " + src + "; expected identifier start [A-Za-z_]");
                }
                int start = pos;
                char ch = src.charAt(pos);
                if (!(ch == '_'
                        || (ch >= 'a' && ch <= 'z')
                        || (ch >= 'A' && ch <= 'Z'))) {
                    throw new IllegalStateException(
                            "Unexpected character " + ch + " at index " + pos + "; expected identifier start [A-Za-z_]");
                }
                pos++;
                while (pos < src.length()) {
                    ch = src.charAt(pos);
                    if (!(ch == '_'
                            || (ch >= '0' && ch <= '9')
                            || (ch >= 'a' && ch <= 'z')
                            || (ch >= 'A' && ch <= 'Z'))) {
                        break;
                    }
                    pos++;
                }
                return src.substring(start, pos);
            }

            private String doubleQuotedExpression() {
                consume('"');
                String escapables = " \t\"\\";
                StringBuilder result = new StringBuilder();
                while (pos < src.length()) {
                    char ch = src.charAt(pos);
                    if (ch == '\\') {
                        if ((pos + 1) < src.length()) {
                            char ch2 = src.charAt(pos + 1);
                            if (escapables.indexOf(ch2) >= 0) {
                                /* Escaped escapable */
                                result.append(ch2);
                                pos += 2;
                                continue;
                            }
                        }
                    }
                    if (ch == '$') {
                        if ((pos + 1) < src.length()) {
                            char ch2 = src.charAt(pos + 1);
                            if (ch2 == '(') {
                                subshell(result);
                                continue;
                            }
                            if ("(".indexOf(ch2) >= 0) {
                                /* Escaped escapable */
                                result.append(ch2);
                                pos += 2;
                            }
                        }
                    }
                    if (ch == '"') {
                        break;
                    }
                    result.append(ch);
                    pos++;
                }
                consume('"');
                return result.toString();
            }
            private String span(char delimiter, String escapables) {
                StringBuilder result = new StringBuilder();
                while (pos < src.length()) {
                    char ch = src.charAt(pos);
                    if (ch == '\\') {
                        if ((pos + 1) < src.length()) {
                            char ch2 = src.charAt(pos + 1);
                            if (escapables.indexOf(ch2) >= 0) {
                                /* Escaped escapable */
                                result.append(ch2);
                                pos += 2;
                                continue;
                            }
                        }
                    }
                    if (ch == delimiter) {
                        break;
                    }
                    result.append(ch);
                    pos++;
                }
                return result.toString();
            }

            private String value() {
                if (pos >= src.length()) {
                    return null;
                }
                char ch = src.charAt(pos);
                if (ch == '"') {
                    String result = doubleQuotedExpression();
                    return result;
                } else if (ch == '\'') {
                    consume('\'');
                    String result = span('\'', " \t'\\");
                    consume('\'');
                    return result;
                }
                /* Unqouted value */
                return unquotedLiteral();
            }

            private String unquotedLiteral() {
                if (pos >= src.length()) {
                    throw new IllegalStateException(
                            "Unexpected end of input in " + src + "; expected identifier start [A-Za-z_]");
                }
                StringBuilder result = new StringBuilder();
                char ch;
                while (pos < src.length()) {
                    ch = src.charAt(pos);
                    if (ch == '\\') {
                        if ((pos + 1) < src.length()) {
                            char ch2 = src.charAt(pos + 1);
                            if (" \t'\"\\".indexOf(ch2) >= 0) {
                                /* Escaped escapable */
                                result.append(ch2);
                                pos += 2;
                                continue;
                            }
                        }
                    }

                    if (!("_+-./$*?[]~`{}#:".indexOf(ch) >= 0
                            || (ch >= '0' && ch <= '9')
                            || (ch >= 'a' && ch <= 'z')
                            || (ch >= 'A' && ch <= 'Z'))) {
                        break;
                    }
                    result.append(ch);
                    pos++;
                }
                return result.toString();
            }

        }

        private record KeyVal(String key, String val) {
        }

        public Buildspec build() {

            final String groupId = resolveMandatory("groupId");
            final String artifactId = resolveMandatory("artifactId");
            final String version = resolveMandatory("version");
            final String referenceRepo = resolve("referenceRepo");
            final String layout = resolve("layout");
            final String sourceDistribution = resolve("sourceDistribution");
            final String gitRepo;
            final String gitTag;
            if (sourceDistribution == null) {
                gitRepo = resolveMandatory("gitRepo");
                gitTag = resolveMandatory("gitTag");
            } else {
                gitRepo = null;
                gitTag = null;
            }
            final String sourcePath = resolve("sourcePath");
            final String sourceRmFiles = resolve("sourceRmFiles");
            final String tool = resolveMandatory("tool");
            final String jdk = resolveMandatory("jdk");
            final String toolchains = resolve("toolchains");
            final Newline newline = Newline.of(resolveMandatory("newline"));
            final String umask = resolve("umask");
            final String timezone = resolve("timezone");
            final String locale = resolve("locale");
            final String os = resolve("os");
            final String arch = resolve("arch");
            final boolean jdkForceAzul = Boolean.parseBoolean(resolve("jdkForceAzul"));
            final String workdir = resolve("workdir");
            final String command = resolveMandatory("command");
            final String execBefore = resolve("execBefore");
            final String execAfter = resolve("execAfter");
            final String buildinfo = resolveMandatory("buildinfo");
            final String diffoscope = resolve("diffoscope");
            final String issue = resolve("issue");

            return new Buildspec(groupId, artifactId, version, referenceRepo, layout, gitRepo, gitTag, sourceDistribution,
                    sourcePath, sourceRmFiles, tool, jdk, toolchains, newline, umask, timezone, locale, os, arch, jdkForceAzul,
                    workdir, command, execBefore, execAfter, buildinfo, diffoscope, issue);
        }

        private static final Pattern BRACED_EXPRESSION_PATTERN = Pattern.compile("\\$\\{([^\\}]+)\\}");
        private static final Pattern SIMPLE_EXPRESSION_PATTERN = Pattern.compile("\\$([a-zA-Z_][0-9a-zA-Z_]*)");

        private String resolveMandatory(String key) {
            String result = resolve(key);
            if (result == null) {
                throw new IllegalStateException("Missing required key " + key);
            }
            return result;
        }

        String resolve(String key) {
            final String val = values.get(key);
            if (val == null) {
                return null;
            }
            CharSequence src = replace(BRACED_EXPRESSION_PATTERN, val);
            src = replace(SIMPLE_EXPRESSION_PATTERN, src);
            final String newVal = src.toString();
            values.put(key, newVal);
            return newVal;
        }

        private StringBuilder replace(Pattern pattern, CharSequence val) {
            StringBuilder sb = new StringBuilder();
            {
                Matcher m = pattern.matcher(val);
                while (m.find()) {
                    String k = m.group(1);
                    final String v = values.get(k);
                    if (v == null) {
                        throw new IllegalStateException("Unresolved expression ${" + k + "} in " + val);
                    }
                    m.appendReplacement(sb, v.replace("\\", "\\\\").replace("$", "\\$"));
                }
                m.appendTail(sb);
            }
            return sb;
        }

    }
}
