package vn.tamtoc.realtime;

import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import vn.tamtoc.security.RateLimits;

@RestController
public class TicketController {
    private final TicketService tickets; private final RateLimits limits;
    public TicketController(TicketService t, RateLimits l) { tickets=t; limits=l; }
    @PostMapping("/api/characters/{id}/connection-ticket")
    public Map<String, Object> issue(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        limits.check("ticket:" + jwt.getSubject(), 30);
        return Map.of("ticket",tickets.issue(UUID.fromString(jwt.getSubject()),id),"expiresIn",30);
    }
}
