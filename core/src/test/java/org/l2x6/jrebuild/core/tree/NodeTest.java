/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.core.tree;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class NodeTest {

    @Test
    void mergeSame() {
        N forest = N.empty("root");
        N t1 = N.path("1.1", "2.1");

        forest.adopt(t1);
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1)
                           `- 2.1 (2.1)
                        """);
        forest.adopt(t1);
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1)
                           `- 2.1 (2.1)
                        """);

        N t1_ = N.path("1.1", "2.1");
        forest.adopt(t1_);
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1,1.1)
                           `- 2.1 (2.1,2.1)
                        """);

    }

    @Test
    void mergeSibling() {
        N forest = N.empty("root");
        N t1 = N.path("1.1", "2.1");

        forest.adopt(t1);
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1)
                           `- 2.1 (2.1)
                        """);
        N t2 = N.path("1.2", "2.2");
        forest.adopt(t2);
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        +- 1.1 (1.1)
                        |  `- 2.1 (2.1)
                        `- 1.2 (1.2)
                           `- 2.2 (2.2)
                        """);

        N t2_ = N.path("1.2", "2.2");
        forest.adopt(t2_);
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        +- 1.1 (1.1)
                        |  `- 2.1 (2.1)
                        `- 1.2 (1.2,1.2)
                           `- 2.2 (2.2,2.2)
                        """);

    }

    @Test
    void mergeReplace() {
        N forest = N.empty("root");
        N t1 = N.path("1.1", "2.1");

        forest.adopt(t1);
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1)
                           `- 2.1 (2.1)
                        """);

        N t2 = N.path("0.1", "1.1");
        forest.adopt(t2);
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 0.1 (0.1)
                           `- 1.1 (1.1,1.1)
                              `- 2.1 (2.1)
                        """);

        N t2_ = N.path("0.1", "1.1");
        forest.adopt(t2_);
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 0.1 (0.1,0.1)
                           `- 1.1 (1.1,1.1,1.1)
                              `- 2.1 (2.1)
                        """);

    }

    @Test
    void mergeReplaceEnd() {
        N forest = N.empty("root");
        N t1 = N.path("1.1", "2.1");
        forest.adopt(t1);
        forest.appendPath("1.2", "2.2");
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        +- 1.1 (1.1)
                        |  `- 2.1 (2.1)
                        `- 1.2 (1.2)
                           `- 2.2 (2.2)
                        """);

        N t2 = N.path("0.1", "1.2", "2.3");
        t2.appendPath("1.3");
        forest.adopt(t2);
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        +- 1.1 (1.1)
                        |  `- 2.1 (2.1)
                        `- 0.1 (0.1)
                           +- 1.2 (1.2,1.2)
                           |  +- 2.3 (2.3)
                           |  `- 2.2 (2.2)
                           `- 1.3 (1.3)
                        """);

    }

    @Test
    void mergeKeepOrder() {
        N forest = N.empty("root");
        forest.appendPath("1.1");
        forest.appendPath("1.2");
        forest.appendPath("1.3");

        N t2 = N.path("0.1", "1.1");
        forest.adopt(t2);
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        +- 0.1 (0.1)
                        |  `- 1.1 (1.1,1.1)
                        +- 1.2 (1.2)
                        `- 1.3 (1.3)
                        """);
    }

    @Test
    void mergeGrandChild() {
        N forest = N.empty("root");
        N t1 = N.path("1.1", "2.1");

        forest.adopt(t1);
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1)
                           `- 2.1 (2.1)
                        """);

        N t2 = N.path("1.1", "2.2");
        forest.adopt(t2);
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1,1.1)
                           +- 2.1 (2.1)
                           `- 2.2 (2.2)
                        """);

        N t2_ = N.path("1.1", "2.2");
        forest.adopt(t2_);
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1,1.1,1.1)
                           +- 2.1 (2.1)
                           `- 2.2 (2.2,2.2)
                        """);

    }

    @Test
    void mergeGrandChildren() {
        N forest = N.empty("root");
        N t1 = N.path("1.1", "2.1");

        forest.adopt(t1);
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1)
                           `- 2.1 (2.1)
                        """);

        N t2 = N.path("1.1", "2.2");
        t2.appendPath("2.3");
        forest.adopt(t2);
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1,1.1)
                           +- 2.1 (2.1)
                           +- 2.2 (2.2)
                           `- 2.3 (2.3)
                        """);

    }

    @Test
    void n() {
        N forest = N.empty("root");
        forest.appendPath("1.1", "2.1", "3.1");
        forest.appendPath("1.1", "2.2", "3.1");

        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        `- 1.1 (1.1)
                           +- 2.1 (2.1)
                           |  `- 3.1 (3.1)
                           `- 2.2 (2.2)
                              `- 3.1 (3.1)
                        """);

        forest.appendPath("1.2", "2.2", "3.1");
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        +- 1.1 (1.1)
                        |  +- 2.1 (2.1)
                        |  |  `- 3.1 (3.1)
                        |  `- 2.2 (2.2)
                        |     `- 3.1 (3.1)
                        `- 1.2 (1.2)
                           `- 2.2 (2.2)
                              `- 3.1 (3.1)
                        """);

        forest.appendPath("1.2", "2.2", "3.1");
        Assertions.assertThat(PrintVisitor.<N> stringBuilderPrintVisitor().walk(forest).toString())
                .isEqualTo("""
                        root (root)
                        +- 1.1 (1.1)
                        |  +- 2.1 (2.1)
                        |  |  `- 3.1 (3.1)
                        |  `- 2.2 (2.2)
                        |     `- 3.1 (3.1)
                        `- 1.2 (1.2)
                           `- 2.2 (2.2)
                              `- 3.1 (3.1)
                        """);

    }

    static record N(String label, StringBuilder data, Collection<N> children) implements Node<N> {
        static N empty(String label) {
            return new N(label, new StringBuilder(label), new LinkedHashSet<>());
        }

        static N path(String... labels) {
            N result = empty(labels[0]);
            result.appendPath(tail(labels));
            return result;
        }

        @Override
        public N merge(N other) {
            this.data.append(",").append(other.data.toString());
            return this;
        }

        N appendPath(String... nodes) {
            if (nodes.length == 0) {
                return null;
            } else {
                N newNode = N.empty(nodes[0]);
                N result = children.stream().filter(newNode::equals).findFirst().orElse(null);
                if (result == null) {
                    children.add(newNode);
                    result = newNode;
                }
                if (nodes.length > 1) {
                    String[] rest = tail(nodes);
                    return result.appendPath(rest);
                } else {
                    return result;
                }
            }
        }

        static String[] tail(String... nodes) {
            String[] rest = new String[nodes.length - 1];
            System.arraycopy(nodes, 1, rest, 0, nodes.length - 1);
            return rest;
        }

        @Override
        public int hashCode() {
            return label.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            N other = (N) obj;
            return Objects.equals(label, other.label);
        }

        @Override
        public String toString() {
            return label + " (" + data + ")";
        }

    }
}
