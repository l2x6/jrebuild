/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.api.os;

import java.util.Locale;

public enum Os {
    LINUX(Shell.BASH),
    MACOS(Shell.BASH),
    WINDOWS(Shell.CMD_EXE);

    private final Shell defaultShell;

    private Os(Shell defaultShell) {
        this.defaultShell = defaultShell;
    }

    public static Os getDefault() {
        return LINUX;
    }

    public static Os current() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("linux")) {
            return LINUX;
        } else if (osName.contains("win")) {
            return WINDOWS;
        } else if (osName.contains("mac")) {
            return MACOS;
        }
        throw new IllegalStateException("os.name " + osName + " is neither Linux, Windows or Mac");
    }

    public Shell defaultShell() {
        return defaultShell;
    }
}
