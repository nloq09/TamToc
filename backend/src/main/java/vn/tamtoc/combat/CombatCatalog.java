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
    public record Behavior(double leashRadius,long chaseTimeoutMs,long thinkIntervalMs,long targetStickMs,long attackWindupMs,long attackCooldownMs,double threatSwitchRatio,boolean canJump,double jumpVelocity,double maxDrop,double halfWidth,double height) {}
    public record Ability(String id,long cooldownMs,long telegraphMs,double radius,int bonusDamage) {}
    public record BossPhase(int phase,double hpBelow,double speedMultiplier,double damageMultiplier,long attackCooldownMs,List<Ability> abilities) {public BossPhase{abilities=abilities==null?List.of():List.copyOf(abilities);}}
    public record Drop(String itemId,double chance,int minQuantity,int maxQuantity) {}
    public record MonsterDefinition(String id,String name,int level,int hp,int atk,int def,int speed,int detection,int range,long respawnMs,int exp,int cultivation,String tier,Behavior behavior,List<BossPhase> phases,List<Drop> dropTable) {
        public MonsterDefinition {
            tier=tier==null?"NORMAL":tier;
            behavior=behavior==null?new Behavior(430,15000,200,1500,150,1200,.35,false,570,240,16,35):behavior;
            phases=phases==null?List.of(new BossPhase(1,1,1,1,behavior.attackCooldownMs,List.of())):List.copyOf(phases);dropTable=dropTable==null?List.of():List.copyOf(dropTable);
        }
        public MonsterDefinition(String id,String name,int level,int hp,int atk,int def,int speed,int detection,int range,long respawnMs,int exp,int cultivation){this(id,name,level,hp,atk,def,speed,detection,range,respawnMs,exp,cultivation,"NORMAL",null,null,null);}
    }
    public record Data(List<SkillDefinition> skills,List<MonsterDefinition> monsters) {}
    public final Map<String,SkillDefinition> skills=new LinkedHashMap<>();
    public final Map<String,MonsterDefinition> monsters=new LinkedHashMap<>();
    public CombatCatalog(ObjectMapper json) throws IOException {
        try(var in=new ClassPathResource("combat.json").getInputStream()){
            var data=json.readValue(in,Data.class);
            for(var s:data.skills){if(skills.putIfAbsent(s.id,s)!=null||s.cooldown<0||s.mpCost<0||s.range<0)throw new IllegalArgumentException("Invalid/duplicate skill "+s.id);}
            for(var m:data.monsters){if(monsters.putIfAbsent(m.id,m)!=null||m.hp<=0||m.level<1||m.speed<0||m.exp<0||m.cultivation<0||!Set.of("NORMAL","ELITE","MINIBOSS","WORLD_BOSS","DUNGEON_BOSS").contains(m.tier))throw new IllegalArgumentException("Invalid/duplicate monster "+m.id);
                var b=m.behavior;if(b.leashRadius<=0||b.chaseTimeoutMs<1000||b.thinkIntervalMs<50||b.attackCooldownMs<=0||b.attackWindupMs<0||b.halfWidth<=0||b.height<=0||b.threatSwitchRatio<0)throw new IllegalArgumentException("Invalid monster behavior "+m.id);
                if(m.phases.isEmpty()||m.phases.get(0).phase!=1)throw new IllegalArgumentException("Missing phase one "+m.id);Set<Integer> phaseIds=new HashSet<>();
                for(var phase:m.phases){if(!phaseIds.add(phase.phase)||phase.hpBelow<=0||phase.hpBelow>1||phase.speedMultiplier<=0||phase.damageMultiplier<=0||phase.attackCooldownMs<=0)throw new IllegalArgumentException("Invalid phase "+m.id);for(var a:phase.abilities)if(a.telegraphMs<100||a.cooldownMs<a.telegraphMs||a.radius<=0)throw new IllegalArgumentException("Invalid ability "+a.id);}
                for(var drop:m.dropTable)if(drop.itemId==null||drop.chance<0||drop.chance>1||drop.minQuantity<1||drop.maxQuantity<drop.minQuantity||drop.maxQuantity>99)throw new IllegalArgumentException("Invalid drop table "+m.id);
            }
        }
    }
    public static boolean unlocked(PlayerState p,SkillDefinition s) {
        return p.level>=s.requiredLevel && (s.requiredRace==null||s.requiredRace.equals(p.race)) && (s.requiredPath==null||s.requiredPath.equals(p.path));
    }
    public List<SkillDefinition> unlocked(PlayerState p) {return skills.values().stream().filter(s->unlocked(p,s)).toList();}
}
