package com.myblog.common;

import java.util.List;
import java.util.function.Function;

/** 목록 응답 모양 (research §10). */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public <R> PageResponse<R> map(Function<T, R> mapper) {
        return new PageResponse<>(items.stream().map(mapper).toList(), page, size, totalItems, totalPages);
    }
}
