package vn.tamtoc.bot;

import java.util.*;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import vn.tamtoc.world.MapCatalog;
import vn.tamtoc.combat.CombatCatalog;

@Component
public class BotCatalog {
    public record Stats(int level,int hp,int mp,int attack,int defense,double speed) {}
    public record BehaviorProfile(String id,long thinkIntervalMs,double perception,double leashRadius,double retreatHpRatio,boolean canJump,double jumpVelocity,double maxDrop) {}
    public record BotDefinition(String id,String botType,String race,String factionId,String name,Stats stats,List<String> skills,String behaviorProfileId,String dialogueId,boolean interactable,String combatPolicy,long respawnMs) {}
    public record Dialogue(String id,List<String> lines) {}
    public record Difficulty(String id,long thinkIntervalMs,int levelBonus,double hpMultiplier,double mpMultiplier,int attackBonus,int defenseBonus,double speedMultiplier) {}
    public record Data(List<BotDefinition> definitions,List<BehaviorProfile> profiles,List<Dialogue> dialogues,List<Difficulty> difficulties) {}
    public final Map<String,BotDefinition> definitions=new LinkedHashMap<>();public final Map<String,BehaviorProfile> profiles=new LinkedHashMap<>();public final Map<String,Dialogue> dialogues=new LinkedHashMap<>();public final Map<String,Difficulty> difficulties=new LinkedHashMap<>();
    public BotCatalog(ObjectMapper json,MapCatalog maps,CombatCatalog combat)throws IOException{
        final Data data;try(var in=new ClassPathResource("bots.json").getInputStream()){data=json.readValue(in,Data.class);}
        for(var p:data.profiles){require(profiles.putIfAbsent(p.id,p)==null&&p.thinkIntervalMs>=100&&p.perception>0&&p.leashRadius>0&&p.retreatHpRatio>=0&&p.retreatHpRatio<=1,"behavior "+p.id);}
        for(var d:data.dialogues)require(dialogues.putIfAbsent(d.id,d)==null&&!d.lines.isEmpty()&&d.lines.stream().allMatch(s->s.length()<=500),"dialogue "+d.id);
        for(var d:data.difficulties)require(difficulties.putIfAbsent(d.id,d)==null&&d.thinkIntervalMs>=100&&d.hpMultiplier>0&&d.mpMultiplier>0&&d.speedMultiplier>0&&d.levelBonus>=0,"difficulty "+d.id);
        for(var d:data.definitions){require(definitions.putIfAbsent(d.id,d)==null&&Set.of("GUARD","WANDERER","CHALLENGER").contains(d.botType)&&Set.of("HUMAN","DEMON","BEAST").contains(d.race),"bot definition "+d.id);require(profiles.containsKey(d.behaviorProfileId)&&dialogues.containsKey(d.dialogueId)&&Set.of("HOSTILE_MONSTERS_ONLY","NEVER","CONSENT_TRAINING").contains(d.combatPolicy),"bot reference/policy "+d.id);require(d.stats.hp>0&&d.stats.mp>=0&&d.stats.level>=1&&d.stats.speed>=0&&d.respawnMs>=1000,"bot stats "+d.id);for(String skill:d.skills)require(combat.skills.containsKey(skill)&&(combat.skills.get(skill).requiredRace()==null||combat.skills.get(skill).requiredRace().equals(d.race))&&combat.skills.get(skill).requiredLevel()<=d.stats.level,"bot skill "+skill);}
        for(var map:maps.all())for(var spawn:map.botSpawnGroups())require(definitions.containsKey(spawn.definitionId()),"map bot spawn "+spawn.id());
        for(var map:maps.all())for(var npc:map.npcSpawns())require(dialogues.containsKey(npc.dialogueId()),"NPC dialogue "+npc.id());
        require(difficulties.keySet().containsAll(List.of("EASY","HARD")),"two training difficulties");
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalArgumentException("Invalid bot data: "+message);}
}
