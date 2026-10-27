package vn.tamtoc.combat;

import java.util.*;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Component;
import vn.tamtoc.world.*;

/** Shared engine for every race and cultivation path. World owns synchronization. */
@Component
public class CombatEngine {
    private final CombatCatalog catalog;
    public CombatEngine(CombatCatalog c){catalog=c;}
    public void cast(PlayerState p,String skillId,MapCatalog.MapDefinition map,Collection<MonsterState> mobs,long now,
        BiConsumer<String,Object> event,BiConsumer<PlayerState,MonsterState> killed){
        var skill=catalog.skills.get(skillId);
        if(skill==null||!CombatCatalog.unlocked(p,skill))throw new IllegalArgumentException("Kỹ năng chưa mở khóa.");
        if(p.progress.hp<=0)throw new IllegalArgumentException("Nhân vật đang chờ hồi sinh.");
        if(p.cooldowns.getOrDefault(skillId,0L)>now)throw new IllegalArgumentException("Kỹ năng đang hồi chiêu.");
        if(p.progress.mp<skill.mpCost())throw new IllegalArgumentException("Không đủ MP.");
        p.progress.mp-=skill.mpCost();p.cooldowns.put(skillId,now+skill.cooldown());
        switch(skill.effectType()){
            case "DASH" -> p.x=Math.clamp(p.x+p.facing*skill.range(),Movement.HALF_WIDTH,map.width()-Movement.HALF_WIDTH);
            case "HEAL" -> {p.progress.hp=Math.min(p.maxHp(),p.progress.hp+skill.damage());p.progress.mp=Math.min(p.maxMp(),p.progress.mp+20);}
            case "SHIELD" -> p.shieldUntil=now+5000;
            case "EMPOWER" -> p.empoweredUntil=now+5000;
            default -> {
                for(var m:mobs){
                    if(m.hp<=0||!m.mapId.equals(p.mapId)||Math.abs(m.y-p.y)>85||Math.abs(m.x-p.x)>skill.range())continue;
                    if(!skill.targetType().equals("AREA")&&(m.x-p.x)*p.facing < -20)continue;
                    boolean crit=java.util.concurrent.ThreadLocalRandom.current().nextDouble()<.12;
                    int damage=Math.max(1,skill.damage()+p.attack()+(p.empoweredUntil>now?15:0)-m.definition.def());
                    if(crit)damage=(int)(damage*1.5);
                    damage=Math.min(m.hp,damage);m.hp-=damage;m.contributions.merge(p.id,damage,Integer::sum);
                    if(skill.effectType().equals("SLOW"))m.slowedUntil=now+3000;
                    event.accept("COMBAT_EVENT",Map.of("source",p.id,"target",m.id,"damage",damage,"crit",crit,"skill",skill.id(),"x",m.x,"y",m.y));
                    if(m.hp==0){m.state=MonsterState.Ai.DEAD;m.respawnAt=now+m.definition.respawnMs();killed.accept(p,m);}
                }
            }
        }
        event.accept("SKILL_CAST",Map.of("id",p.id,"skill",skill.id(),"x",p.x,"y",p.y,"facing",p.facing));
    }
}
