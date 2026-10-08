package com.myblog.search;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {
    private final SearchService search;
    private final TrendingService trending;

    public SearchController(SearchService search, TrendingService trending) {
        this.search = search;
        this.trending = trending;
    }

    @GetMapping("/api/search")
    public Map<String, Object> search(@RequestParam(required = false) String q,
                                      @RequestParam(required = false) String type,
                                      @RequestParam(required = false) Integer page) {
        return search.search(q, type, page);
    }

    @GetMapping("/api/search/trending")
    public TrendingService.Snapshot trending() {
        return trending.current();
    }
}
