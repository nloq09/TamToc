package vn.tamtoc.combat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import vn.tamtoc.world.PlayerState;

@Component
public class CombatCatalog {
    public record SkillDefinition(String id,String name,String description,String requiredPath,String requiredRace,int requiredLevel,int mpCost,long cooldown,int castTime,int damage,int range,String targetType,String effectType) {}
    public record MonsterDefinition(String id,String name,int level,int hp,int atk,int def,int speed,int detection,int range,long respawnMs,int exp,int cultivation) {}
    public record Data(List<SkillDefinition> skills,List<MonsterDefinition> monsters) {}
    public final Map<String,SkillDefinition> skills=new LinkedHashMap<>();
    public final Map<String,MonsterDefinition> monsters=new LinkedHashMap<>();
    public CombatCatalog(ObjectMapper json) throws IOException {
        try(var in=new ClassPathResource("combat.json").getInputStream()){
            var data=json.readValue(in,Data.class);data.skills.forEach(s->skills.put(s.id,s));data.monsters.forEach(m->monsters.put(m.id,m));
        }
    }
    public static boolean unlocked(PlayerState p,SkillDefinition s) {
        return p.level>=s.requiredLevel && (s.requiredRace==null||s.requiredRace.equals(p.race)) && (s.requiredPath==null||s.requiredPath.equals(p.path));
    }
    public List<SkillDefinition> unlocked(PlayerState p) {return skills.values().stream().filter(s->unlocked(p,s)).toList();}
}
