package vn.tamtoc.content;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import vn.tamtoc.world.GameWorld;

@Profile("local") @RestController
public class DevContentInspector {
    private final MapContentRegistry registry;private final GameWorld world;private final String key;
    public DevContentInspector(MapContentRegistry registry,GameWorld world,@Value("${DEV_ADMIN_KEY:}")String key){this.registry=registry;this.world=world;this.key=key;}
    @GetMapping("/api/dev/content") public Object inspect(@RequestHeader(value="X-Dev-Key",defaultValue="")String supplied,HttpServletRequest request){boolean local=Set.of("127.0.0.1","::1","0:0:0:0:0:0:0:1").contains(request.getRemoteAddr());if(!local||key.length()<32||!MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Chỉ dành cho quản trị viên local.");return Map.of("audit",registry.audit(),"maps",registry.summaries(),"runtime",world.inspectContent(),"invalidReferences",List.of(),"unconfiguredZones",registry.plan.spawns.keySet().stream().filter(id->!registry.bindings().path("spawnBindings").path(id).path("geometryVerified").asBoolean()).toList(),"disabledBosses",registry.plan.bosses.keySet().stream().filter(id->!registry.bindings().path("bossBindings").path(id).path("enabled").asBoolean()).toList());}
}
