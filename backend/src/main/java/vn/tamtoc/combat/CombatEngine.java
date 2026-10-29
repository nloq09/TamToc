package vn.tamtoc.combat;

import java.util.*;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import vn.tamtoc.world.*;
import vn.tamtoc.realtime.ProtocolException;

/** One validation/cost/hitbox/damage pipeline for PvE and consented arena PvP. */
@Component
public class CombatEngine {
    private final CombatCatalog catalog;private final PvPDamageModifier pvp;
    @Autowired(required=false) private MonsterPopulation population;
    public CombatEngine(CombatCatalog c){this(c,new PvPDamageModifier(1));}
    @Autowired public CombatEngine(CombatCatalog c,PvPDamageModifier modifier){catalog=c;pvp=modifier;}
    public void cast(PlayerState player,String skill,MapCatalog.MapDefinition map,Collection<MonsterState> monsters,long now,BiConsumer<String,Object> event,BiConsumer<PlayerState,MonsterState> killed){cast(player,skill,map,monsters,List.of(),null,false,now,event,killed);}
    public void castDuel(PlayerState player,String skill,MapCatalog.MapDefinition map,Collection<PlayerState> targets,UUID instance,long now,BiConsumer<String,Object> event){cast(player,skill,map,List.of(),targets,instance,false,now,event,(p,m)->{});}
    public void castTraining(PlayerState player,String skill,MapCatalog.MapDefinition map,PlayerState target,UUID instance,long now,BiConsumer<String,Object> event){cast(player,skill,map,List.of(),List.of(target),instance,true,now,event,(p,m)->{});}
    private void cast(PlayerState p,String id,MapCatalog.MapDefinition map,Collection<MonsterState> monsters,Collection<PlayerState> players,UUID duel,boolean training,long now,BiConsumer<String,Object> event,BiConsumer<PlayerState,MonsterState> killed){
        var skill=catalog.skills.get(id);
        if(skill==null||!CombatCatalog.unlocked(p,skill))throw new ProtocolException("SKILL_LOCKED","Kỹ năng chưa mở khóa.");
        if(p.progress.hp<=0)throw new ProtocolException("PLAYER_DEAD","Nhân vật đang chờ hồi sinh.");
        if(p.aiStats==null&&map.safe(p.x,p.y)&&Set.of("DAMAGE","SLOW").contains(skill.effectType()))throw new ProtocolException("SAFE_ZONE_COMBAT","Hãy rời vùng an toàn trước khi tấn công.");
        if(p.cooldowns.getOrDefault(id,0L)>now)throw new ProtocolException("SKILL_COOLDOWN","Kỹ năng đang hồi chiêu.");
        if(p.progress.mp<skill.mpCost())throw new ProtocolException("INSUFFICIENT_MP","Không đủ MP.");
        p.progress.mp-=skill.mpCost();p.cooldowns.put(id,now+skill.cooldown());p.lastActiveAt=now;
        switch(skill.effectType()){
            case "DASH" -> p.x=MapCollision.horizontal(map,p.x,p.y,p.facing*skill.range(),Movement.HALF_WIDTH,Movement.HEIGHT);
            case "HEAL" -> {p.progress.hp=Math.min(p.maxHp(),p.progress.hp+skill.damage());p.progress.mp=Math.min(p.maxMp(),p.progress.mp+20);}
            case "SHIELD" -> p.shieldUntil=now+5000;
            case "EMPOWER" -> p.empoweredUntil=now+5000;
            default -> {
                for(var m:monsters){
                    if(m.hp<=0||!m.mapId.equals(p.mapId)||!Objects.equals(m.instanceId,p.instanceId)||!inRange(p,m.x,m.y,skill)||!MapCollision.lineOfSight(map,p.x,p.y-20,m.x,m.y-20))continue;
                    boolean critical=critical();int damage=Math.min(m.hp,damage(p,skill,m.definition.def(),critical,now));m.hp-=damage;m.contributions.merge(p.id,damage,Integer::sum);m.threat.merge(p.id,(double)damage,Double::sum);
                    if(population!=null)population.alertGroup(m,p,damage);
                    if(skill.effectType().equals("SLOW"))m.slowedUntil=now+3000;
                    event.accept("COMBAT_EVENT",Map.of("source",p.id,"target",m.id,"damage",damage,"crit",critical,"skill",id,"x",m.x,"y",m.y));
                    if(m.hp==0){m.state=MonsterState.Ai.DEAD;m.respawnAt=now+m.respawnDelay;killed.accept(p,m);}
                }
                for(var target:players){
                    if(!(training?CombatTargetPolicy.training(p,target,duel):CombatTargetPolicy.pvp(p,target,duel))||!inRange(p,target.x,target.y,skill)||!MapCollision.lineOfSight(map,p.x,p.y-20,target.x,target.y-20))continue;
                    boolean critical=critical();int baseDamage=damage(p,skill,target.defense()+(target.shieldUntil>now?18:0),critical,now);int damage=Math.min(target.progress.hp,training?baseDamage:pvp.apply(baseDamage));target.progress.hp-=damage;
                    if(skill.effectType().equals("SLOW"))target.slowedUntil=now+3000;
                    event.accept("COMBAT_EVENT",Map.of("source",p.id,"target",target.id,"damage",damage,"crit",critical,"skill",id,"x",target.x,"y",target.y));
                }
            }
        }
        event.accept("SKILL_CAST",Map.of("id",p.id,"skill",id,"x",p.x,"y",p.y,"facing",p.facing));
    }
    private static boolean inRange(PlayerState p,double x,double y,CombatCatalog.SkillDefinition s){return Math.abs(y-p.y)<=85&&Math.abs(x-p.x)<=s.range()&&(s.targetType().equals("AREA")||(x-p.x)*p.facing>=-20);}
    private static boolean critical(){return java.util.concurrent.ThreadLocalRandom.current().nextDouble()<.12;}
    private static int damage(PlayerState p,CombatCatalog.SkillDefinition skill,int defense,boolean critical,long now){int amount=Math.max(1,skill.damage()+p.attack()+(p.empoweredUntil>now?15:0)-defense);return critical?(int)(amount*1.5):amount;}
}
