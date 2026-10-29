package vn.tamtoc.combat;

import java.util.*;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Component;
import vn.tamtoc.world.PlayerState;
import vn.tamtoc.world.*;
import static vn.tamtoc.combat.MonsterState.Ai.*;

@Component
public class MonsterAi {
    public void step(MonsterState m,List<PlayerState> players,double dt,long now,BiConsumer<String,Object> event){
        step(m,players,null,dt,now,event);
    }
    public void step(MonsterState m,List<PlayerState> players,vn.tamtoc.world.MapCatalog.MapDefinition map,double dt,long now,BiConsumer<String,Object> event){
        final var geometry=map==null?new MapCatalog.MapDefinition("legacy","Legacy",10000,1000,m.spawnY,"#000",List.of(),List.of()):map;
        final var b=m.definition.behavior();
        if(m.hp<=0){
            m.castingAbility=null;m.strikeAt=0;m.navigation=null;m.direction=0;m.targetId=null;
            if(m.scheduled||m.instanceId!=null||now<m.respawnAt)return;
            m.state=RESPAWN;m.hp=m.definition.hp();m.x=m.spawnX;m.y=m.spawnY;m.vy=0;m.grounded=true;m.phase=1;m.contributions.clear();m.threat.clear();m.rewardProcessed=false;m.nextThinkAt=now;m.aggroSince=0;m.attackAt=now;m.abilityAt=now;
            event.accept("MONSTER_SPAWN",m.view());return;
        }
        var phase=phase(m);if(m.phase!=phase.phase()){m.phase=phase.phase();event.accept("BOSS_PHASE_CHANGED",Map.of("id",m.id,"phase",m.phase,"hp",m.hp,"maxHp",m.definition.hp(),"x",m.x,"y",m.y));}
        var cap=new Navigation.Capabilities(m.definition.speed()*phase.speedMultiplier(),b.jumpVelocity(),b.canJump(),b.maxDrop(),b.halfWidth(),b.height());m.movementScale=m.slowedUntil>now?.5:1;
        if(now<m.stunnedUntil){m.state=STUNNED;m.navigation=null;Navigation.step(m,geometry,cap,dt);return;}
        if(m.strikeAt>0){
            if(now<m.strikeAt){m.navigation=null;Navigation.step(m,geometry,cap,dt);return;}
            if(m.castingAbility!=null){var ability=m.castingAbility;for(var p:players)if(valid(m,p,map)&&Math.abs(p.x-m.castX)<=ability.radius()&&Math.abs(p.y-m.castY)<=65&&MapCollision.lineOfSight(geometry,m.x,m.y-20,p.x,p.y-20))hit(m,p,phase,ability.bonusDamage(),now,event);event.accept("BOSS_ABILITY_RESOLVED",Map.of("id",m.id,"castId",m.castId,"abilityId",ability.id(),"x",m.castX,"y",m.castY));m.castingAbility=null;}
            else players.stream().filter(p->p.id.equals(m.strikeTarget)&&valid(m,p,map)&&inRange(m,p)&&MapCollision.lineOfSight(geometry,m.x,m.y-20,p.x,p.y-20)).findFirst().ifPresent(p->hit(m,p,phase,0,now,event));
            m.strikeAt=0;
        }
        if(m.state==RETURN){
            if(Math.hypot(m.x-m.spawnX,m.y-m.spawnY)<4){m.state=IDLE;m.targetId=null;m.navigation=null;m.hp=m.definition.hp();m.threat.clear();m.contributions.clear();m.aggroSince=0;m.phase=1;event.accept("MONSTER_RESET",m.view());}
            else if(now>=m.nextThinkAt){m.nextThinkAt=now+b.thinkIntervalMs();m.navigation=Navigation.plan(m,geometry,m.spawnX,m.spawnY,cap);}
            Navigation.step(m,geometry,cap,dt);return;
        }
        if(now>=m.nextThinkAt){
            m.nextThinkAt=now+b.thinkIntervalMs();
            var current=players.stream().filter(p->Objects.equals(p.id,m.targetId)&&valid(m,p,map)&&Math.hypot(p.x-m.x,p.y-m.y)<=m.definition.detection()*1.5&&MapCollision.lineOfSight(geometry,m.x,m.y-20,p.x,p.y-20)).findFirst().orElse(null);
            var best=players.stream().filter(p->valid(m,p,map)&&Math.hypot(p.x-m.x,p.y-m.y)<=m.definition.detection()&&MapCollision.lineOfSight(geometry,m.x,m.y-20,p.x,p.y-20)).max(Comparator.comparingDouble(p->score(m,p))).orElse(null);
            var target=current;if(target==null||(best!=null&&now-m.targetSince>=b.targetStickMs()&&score(m,best)>score(m,current)*(1+b.threatSwitchRatio())))target=best;
            if(target==null){if(m.targetId!=null){beginReturn(m);}else{m.state=PATROL;double goal=m.spawnX+m.patrolDirection*80;if(Math.abs(m.x-goal)<5||!MapCollision.walkLine(geometry,m.x,m.y,goal,b.halfWidth(),b.height())){m.patrolDirection*=-1;goal=m.spawnX+m.patrolDirection*80;}m.navigation=Navigation.plan(m,geometry,goal,m.spawnY,new Navigation.Capabilities(cap.speed()*.3,cap.jumpVelocity(),false,cap.maxDrop(),cap.halfWidth(),cap.height()));}}
            else if(Math.hypot(target.x-m.spawnX,target.y-m.spawnY)>b.leashRadius()||(m.state==CHASE&&m.aggroSince>0&&now-m.aggroSince>=b.chaseTimeoutMs()))beginReturn(m);
            else{
                if(!target.id.equals(m.targetId)){m.targetId=target.id;m.targetSince=now;if(m.aggroSince==0)m.aggroSince=now;m.state=ALERT;m.navigation=null;event.accept("MONSTER_STATE_CHANGED",m.view());}
                else if(!m.grounded&&m.navigation!=null){m.state=CHASE;}
                else if(inRange(m,target)){
                    m.state=ATTACK;m.navigation=null;m.aggroSince=now;
                    if(now>=m.attackAt){
                        if(!phase.abilities().isEmpty()&&now>=m.abilityAt){var ability=phase.abilities().get((int)((now/1000)%phase.abilities().size()));m.state=CASTING;m.castId=UUID.randomUUID();m.castX=target.x;m.castY=target.y;m.castingAbility=ability;m.strikeAt=now+ability.telegraphMs();m.abilityAt=now+ability.cooldownMs();m.attackAt=m.strikeAt+phase.attackCooldownMs();event.accept("BOSS_TELEGRAPH",Map.of("id",m.id,"castId",m.castId,"abilityId",ability.id(),"x",m.castX,"y",m.castY,"radius",ability.radius(),"resolvesAt",m.strikeAt,"phase",m.phase));}
                        else{m.strikeTarget=target.id;m.strikeAt=now+b.attackWindupMs();m.attackAt=m.strikeAt+phase.attackCooldownMs();}
                    }
                }else{m.state=CHASE;m.navigation=Navigation.plan(m,geometry,target.x,target.y,cap);if(m.navigation==null)beginReturn(m);}
            }
        }
        double before=m.x;Navigation.step(m,geometry,cap,dt);
        if(m.navigation!=null&&Math.abs(m.x-before)<.01){if(m.stuckSince==0)m.stuckSince=now;else if(now-m.stuckSince>3000)beginReturn(m);}else m.stuckSince=0;
    }
    private static CombatCatalog.BossPhase phase(MonsterState m){return m.definition.phases().stream().filter(p->m.hp<=m.definition.hp()*p.hpBelow()).max(Comparator.comparingInt(CombatCatalog.BossPhase::phase)).orElse(m.definition.phases().get(0));}
    private static double score(MonsterState m,PlayerState p){return m.threat.getOrDefault(p.id,0d)+20/(1+Math.abs(p.x-m.x)/50);}
    private static boolean valid(MonsterState m,PlayerState p,MapCatalog.MapDefinition map){return p.progress.hp>0&&m.mapId.equals(p.mapId)&&Objects.equals(m.instanceId,p.instanceId)&&(map==null?p.x>420:!map.safe(p.x,p.y));}
    private static boolean inRange(MonsterState m,PlayerState p){return Math.abs(p.x-m.x)<=m.definition.range()&&Math.abs(p.y-m.y)<=85;}
    private static void beginReturn(MonsterState m){m.state=RETURN;m.targetId=null;m.navigation=null;m.castingAbility=null;m.strikeAt=0;m.nextThinkAt=0;}
    private static void hit(MonsterState m,PlayerState p,CombatCatalog.BossPhase phase,int bonus,long now,BiConsumer<String,Object> event){
        int damage=Math.max(1,(int)(m.definition.atk()*phase.damageMultiplier())+bonus-p.defense()-(p.shieldUntil>now?18:0));damage=Math.min(damage,p.progress.hp);p.progress.hp-=damage;
        event.accept("COMBAT_EVENT",Map.of("source",m.id,"target",p.id,"damage",damage,"crit",false,"skill",m.castingAbility==null?"MONSTER":m.castingAbility.id(),"x",p.x,"y",p.y));
        if(p.progress.hp==0){p.progress.respawnAt=now+4000;p.direction=0;event.accept("PLAYER_DIED",Map.of("id",p.id,"respawnMs",4000));}
    }
}
