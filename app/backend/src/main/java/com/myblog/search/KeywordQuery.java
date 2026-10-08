package com.myblog.search;

import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 검색어 정규화와 LIKE 패턴 (CF-11-1, 3, 4, 9, research §7).
 * 앞뒤 공백 제거, 연속 공백은 하나로, 소문자. 단어마다 %·_·\ 를 이스케이프해 일반 글자로 찾는다.
 */
public record KeywordQuery(String normalized, List<String> patterns) {

    public static KeywordQuery parse(String raw, int min, int max) {
        String q = raw == null ? "" : raw.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        int len = q.codePointCount(0, q.length());
        if (len < min) {
            throw new ApiException(ErrorCode.QUERY_TOO_SHORT);
        }
        if (len > max) {
            q = q.substring(0, q.offsetByCodePoints(0, max)).strip();
        }
        List<String> patterns = Arrays.stream(q.split(" ")).filter(w -> !w.isEmpty())
                .map(w -> "%" + escape(w) + "%").toList();
        return new KeywordQuery(q, patterns);
    }

    static String escape(String word) {
        return word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
