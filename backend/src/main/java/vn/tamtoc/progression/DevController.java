package vn.tamtoc.progression;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.tamtoc.world.GameWorld;

/** Absent from the default/production context; requires an independent local admin key. */
@Profile("local") @RestController
public class DevController {
    private final GameWorld world;private final String key;
    public DevController(GameWorld w,@Value("${DEV_ADMIN_KEY:}")String key){world=w;this.key=key;}
    @PostMapping("/api/dev/characters/{id}/level10")
    public Object grant(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@RequestHeader(value="X-Dev-Key",defaultValue="")String supplied,HttpServletRequest request){
        boolean loopback=Set.of("127.0.0.1","0:0:0:0:0:0:0:1","::1").contains(request.getRemoteAddr());
        if(!loopback||key.length()<32||!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ dành cho quản trị viên local.");
        return world.access(UUID.fromString(jwt.getSubject()),id,p->{p.level=Math.max(10,p.level);p.progress.hp=p.maxHp();p.progress.mp=p.maxMp();return Map.of("level",p.level);},true);
    }
}
