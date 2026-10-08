package com.myblog.common;

import java.util.List;
import java.util.function.BiFunction;

/** 페이지 번호를 1부터 세고, 범위를 넘으면 마지막 페이지로 맞춘다 (CF-10-4). */
public final class PageRequests {
    private PageRequests() {}

    public record Slice(int page, int size, int offset, int totalPages) {}

    public static Slice clamp(Integer requestedPage, int size, long totalItems) {
        int totalPages = (int) Math.max(1, (totalItems + size - 1) / size);
        int page = requestedPage == null || requestedPage < 1 ? 1 : Math.min(requestedPage, totalPages);
        return new Slice(page, size, (page - 1) * size, totalPages);
    }

    /** total을 센 뒤 잘린 범위의 행을 읽는다. fetch는 (limit, offset)을 받는다. */
    public static <T> PageResponse<T> page(Integer requestedPage, int size, long totalItems,
                                           BiFunction<Integer, Integer, List<T>> fetch) {
        Slice s = clamp(requestedPage, size, totalItems);
        List<T> items = totalItems == 0 ? List.of() : fetch.apply(s.size(), s.offset());
        return new PageResponse<>(items, s.page(), s.size(), totalItems, totalItems == 0 ? 0 : s.totalPages());
    }
}
