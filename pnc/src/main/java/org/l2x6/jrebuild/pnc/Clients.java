/*
 * SPDX-FileCopyrightText: Copyright (c) 2025 jrebuild project contributors as indicated by the @author tags
 * SPDX-License-Identifier: Apache-2.0
 */
package org.l2x6.jrebuild.pnc;

import java.util.function.Function;
import java.util.stream.Stream;
import org.jboss.pnc.dto.response.Page;
import org.jboss.pnc.rest.api.parameters.PaginationParameters;

public class Clients {

    public static PaginationParameters pagination(int i) {
        PaginationParameters pp = new PaginationParameters();
        pp.setPageIndex(i);
        pp.setPageSize(128);
        return pp;
    }

    public static <T> Stream<T> stream(Function<Integer, Page<T>> getPage) {
        return pages(getPage).flatMap(page -> page.getContent().stream());
    }

    public static <T> Stream<Page<T>> pages(Function<Integer, Page<T>> getPage) {
        Page<T> seed = getPage.apply(0);
        return Stream.iterate(
                seed,
                page -> page == seed || page.getPageIndex() + 1 < page.getTotalPages(),
                p -> getPage.apply(p.getPageIndex() + 1));
    }

}
