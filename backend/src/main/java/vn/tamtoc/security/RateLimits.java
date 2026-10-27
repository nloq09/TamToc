package vn.tamtoc.security;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Component
public class RateLimits {
    private record Window(long expires, int count) {}
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    public void check(String key, int limit) {
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, old) -> old == null || old.expires < now ? new Window(now + 60_000, 1) : new Window(old.expires, old.count + 1));
        if (w.count > limit) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Thao tác quá nhanh. Thử lại sau một phút.");
    }
    @Scheduled(fixedDelay = 60000) void clean() { long now = System.currentTimeMillis(); windows.entrySet().removeIf(e -> e.getValue().expires < now); }
}
