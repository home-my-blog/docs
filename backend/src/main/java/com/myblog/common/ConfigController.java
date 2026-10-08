package com.myblog.common;

import com.myblog.blog.TopicService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 화면이 쓸 기본값을 내려 준다 (FR-039, NF-08). */
@RestController
public class ConfigController {
    private final MyBlogProperties props;
    private final TopicService topics;

    public ConfigController(MyBlogProperties props, TopicService topics) {
        this.props = props;
        this.topics = topics;
    }

    @GetMapping("/api/config")
    public Map<String, Object> config() {
        Map<String, Object> m = new LinkedHashMap<>();
        var member = props.member();
        m.put("nickname", Map.of("min", member.nicknameMin(), "max", member.nicknameMax()));
        m.put("password", Map.of("min", member.passwordMin(), "max", member.passwordMax(),
                "specials", member.passwordSpecials()));
        m.put("bioMax", member.bioMax());
        m.put("verification", Map.of(
                "codeTtlSeconds", props.verification().codeTtl().toSeconds(),
                "resendIntervalSeconds", props.verification().resendInterval().toSeconds(),
                "dailyLimit", props.verification().dailyLimit()));
        m.put("blog", Map.of("nameMax", props.blog().nameMax(), "descriptionMax", props.blog().descriptionMax(),
                "aboutMax", props.blog().aboutMax(), "categoryNameMax", props.blog().categoryNameMax()));
        m.put("post", Map.of("titleMax", props.post().titleMax(), "bodyMax", props.post().bodyMax(),
                "pageSize", props.post().pageSize()));
        m.put("comment", Map.of("bodyMax", props.comment().bodyMax(),
                "cooldownSeconds", props.comment().cooldown().toSeconds()));
        m.put("tag", Map.of("maxPerPost", props.tag().maxPerPost(), "maxLength", props.tag().maxLength()));
        m.put("report", Map.of("detailMax", props.report().detailMax()));
        m.put("image", Map.of("maxBytes", props.image().maxBytes(), "maxPerPost", props.image().maxPerPost(),
                "types", new String[] {"image/jpeg", "image/png", "image/gif", "image/webp"}));
        m.put("search", Map.of("minLength", props.search().minLength(), "maxLength", props.search().maxLength()));
        m.put("trending", Map.of("size", props.trending().size(),
                "intervalSeconds", props.trending().interval().toSeconds()));
        m.put("topics", topics.list());
        return m;
    }
}
