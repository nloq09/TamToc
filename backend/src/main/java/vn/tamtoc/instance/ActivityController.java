package vn.tamtoc.instance;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import vn.tamtoc.character.CharacterService;

@RestController
public class ActivityController {
    private final JdbcTemplate jdbc;private final CharacterService characters;
    public ActivityController(JdbcTemplate j,CharacterService c){jdbc=j;characters=c;}
    @GetMapping("/api/characters/{id}/activities") public Object history(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){
        characters.owned(UUID.fromString(jwt.getSubject()),id);
        return Map.of("dungeons",jdbc.queryForList("SELECT r.id,r.instance_id,r.dungeon_id,r.status,r.result,p.eligible,(SELECT count(*) FROM dungeon_reward_grants g WHERE g.run_id=r.id AND g.character_id=p.character_id) AS reward_count FROM dungeon_runs r JOIN dungeon_run_participants p ON p.run_id=r.id WHERE p.character_id=? ORDER BY r.started_at DESC LIMIT 20",id),
            "duels",jdbc.queryForList("SELECT id,challenger_id,opponent_id,winner_id,reason,started_at,ended_at FROM duel_results WHERE challenger_id=? OR opponent_id=? ORDER BY ended_at DESC LIMIT 20",id,id));
    }
}
