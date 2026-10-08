package com.myblog.support;

import com.myblog.mail.MailSender;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 테스트용: 보낸 메일을 모아 두고 인증번호를 꺼낸다. */
public class CapturingMailSender implements MailSender {
    public record Sent(String to, String subject, String body) {}

    private static final Pattern CODE = Pattern.compile("인증번호: ([A-Z0-9]{6})");
    public final List<Sent> sent = new CopyOnWriteArrayList<>();
    public volatile boolean available = true;

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void send(String to, String subject, String body) {
        sent.add(new Sent(to, subject, body));
    }

    public String lastCode(String to) {
        for (int i = sent.size() - 1; i >= 0; i--) {
            Sent s = sent.get(i);
            Matcher m = CODE.matcher(s.body());
            if (s.to().equals(to) && m.find()) {
                return m.group(1);
            }
        }
        throw new AssertionError("no code mail for " + to);
    }

    public long countTo(String to) {
        return sent.stream().filter(s -> s.to().equals(to)).count();
    }
}
