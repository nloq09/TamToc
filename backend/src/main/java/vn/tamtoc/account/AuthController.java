package vn.tamtoc.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.tamtoc.security.RateLimits;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AccountRepository accounts; private final PasswordEncoder passwords; private final JwtEncoder encoder; private final RateLimits limits;
    public AuthController(AccountRepository a, PasswordEncoder p, JwtEncoder e, RateLimits l) { accounts=a; passwords=p; encoder=e; limits=l; }
    public record Credentials(@Pattern(regexp="[a-zA-Z0-9_]{3,24}") @NotNull String username,
        @NotNull @Size(min=10, max=64) String password) {}
    @GetMapping("/health") public Map<String, String> health() { return Map.of("status", "up", "phase", "1-4-mvp"); }
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@Valid @RequestBody Credentials c, HttpServletRequest req) {
        limits.check("register:" + req.getRemoteAddr(), 10);
        Account a = accounts.saveAndFlush(new Account(c.username().toLowerCase(Locale.ROOT), passwords.encode(c.password())));
        return token(a);
    }
    @PostMapping("/auth/login")
    public Map<String, Object> login(@Valid @RequestBody Credentials c, HttpServletRequest req) {
        limits.check("login:" + req.getRemoteAddr(), 30);
        Account a = accounts.findByUsername(c.username().toLowerCase(Locale.ROOT)).orElse(null);
        // Always run a password hash even for an unknown account.
        String hash = a == null ? "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy" : a.passwordHash;
        boolean valid = passwords.matches(c.password(), hash);
        if (a == null || !valid) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sai tài khoản hoặc mật khẩu.");
        return token(a);
    }
    @GetMapping("/auth/me") public Map<String, Object> me(@AuthenticationPrincipal Jwt jwt) {
        Account a = accounts.findById(UUID.fromString(jwt.getSubject())).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return Map.of("id", a.id, "username", a.username);
    }
    private Map<String, Object> token(Account a) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("tamtoc").subject(a.id.toString()).issuedAt(now).expiresAt(now.plusSeconds(3600)).build();
        String jwt = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return Map.of("token", jwt, "expiresIn", 3600, "username", a.username);
    }
}
