package com.vetspace.web;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** List pagination policy: page size is clamped to [1, 50] so no endpoint can be asked to dump unbounded rows. */
public final class Paging {

    public static final int MAX_PAGE_SIZE = 50;

    private Paging() {
    }

    public static Pageable of(int page, int size, Sort sort) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), sort);
    }
}
