package vn.tamtoc.character;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/characters")
public class CharacterController {
    private final CharacterService service; private final CharacterRepository repo;
    public CharacterController(CharacterService s, CharacterRepository r) { service=s; repo=r; }
    public record Create(@NotNull @Pattern(regexp="[\\p{L}\\p{N}_ ]{3,24}") String name, @NotNull Race race) {}
    public record View(UUID id, String name, Race race, String characterClass, String organization, String cultivationPath,
        int level, long exp, long cultivation, String realm, int realmStage, String mapId) {
        public static View of(GameCharacter c) { return new View(c.id,c.name,c.race,c.characterClass,c.organization,c.cultivationPath,c.level,c.exp,c.cultivation,c.realm,c.realmStage,c.mapId); }
    }
    @GetMapping public List<View> list(@AuthenticationPrincipal Jwt jwt) { return repo.findByUserIdOrderByName(UUID.fromString(jwt.getSubject())).stream().map(View::of).toList(); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public View create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Create c) { return View.of(service.create(UUID.fromString(jwt.getSubject()), c.name(), c.race())); }
    @GetMapping("/{id}") public View get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { return View.of(service.owned(UUID.fromString(jwt.getSubject()),id)); }
}
