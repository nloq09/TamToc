package vn.tamtoc.bot;

import java.util.*;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Component;
import vn.tamtoc.world.*;
import vn.tamtoc.combat.*;

@Component
public class BotBrain {
    private final CombatEngine engine;private final CombatCatalog combat;
    public BotBrain(CombatEngine engine,CombatCatalog combat){this.engine=engine;this.combat=combat;}
    private static Navigation.Capabilities cap(BotState bot){return new Navigation.Capabilities(bot.speed(),bot.profile.jumpVelocity(),bot.profile.canJump(),bot.profile.maxDrop(),16,44);}
    private static void plan(BotState b,MapCatalog.MapDefinition map,double x,double y){if(b.actor.navigation!=null&&!b.actor.grounded)return;b.actor.navigation=Navigation.plan(b.actor,map,x,y,cap(b));b.currentGoal=String.format(java.util.Locale.ROOT,"%.0f,%.0f",x,y);if(b.actor.navigation==null){b.state=BotState.State.RETURN;b.currentGoal="blocked";}}
    private static void regen(BotState b,long now){if(now>=b.actor.regenAt){b.actor.regenAt=now+1000;b.actor.progress.mp=Math.min(b.actor.maxMp(),b.actor.progress.mp+2);}b.actor.movementScale=b.actor.slowedUntil>now?.5:1;}
    private static boolean ready(BotState b,String skill,CombatCatalog c,long now){var def=c.skills.get(skill);return b.definition.skills().contains(skill)&&def!=null&&CombatCatalog.unlocked(b.actor,def)&&b.actor.progress.mp>=def.mpCost()&&b.actor.cooldowns.getOrDefault(skill,0L)<=now;}
    private String utility(BotState b,long now){for(String id:b.definition.skills()){var skill=combat.skills.get(id);if(!ready(b,id,combat,now))continue;if(skill.effectType().equals("HEAL")&&b.actor.progress.hp<b.actor.maxHp()*.7)return id;if((skill.effectType().equals("SHIELD")||skill.effectType().equals("EMPOWER"))&&b.targetId!=null)return id;}return null;}
    private String offensive(BotState b,double distance,long now){return b.definition.skills().stream().filter(id->ready(b,id,combat,now)&&Set.of("DAMAGE","SLOW").contains(combat.skills.get(id).effectType())&&distance<=combat.skills.get(id).range()).max(Comparator.comparingInt(id->combat.skills.get(id).damage())).orElse(null);}
    private static void patrol(BotState b,MapCatalog.MapDefinition map){
        if(b.route.isEmpty()){b.state=BotState.State.IDLE;b.actor.navigation=null;return;}var target=b.route.get(b.routeIndex%b.route.size());if(Math.hypot(b.actor.x-target.x(),b.actor.y-target.y())<4){b.routeIndex=(b.routeIndex+1)%b.route.size();target=b.route.get(b.routeIndex);}b.state=BotState.State.PATROL;plan(b,map,target.x(),target.y());
    }
    private static void move(BotState b,MapCatalog.MapDefinition map,double dt){double before=b.actor.x;Navigation.step(b.actor,map,cap(b),dt);if(Math.abs(b.actor.x-before)>.01)b.actor.facing=b.actor.x>before?1:-1;}
    public void step(BotState b,MapCatalog.MapDefinition map,List<MonsterState> perceived,double dt,long now,BiConsumer<String,Object> event,BiConsumer<PlayerState,MonsterState> defeated){
        var old=b.state;
        if(b.actor.progress.hp<=0){b.state=BotState.State.DEAD;b.actor.navigation=null;if(b.respawnAt==0)b.respawnAt=now+b.definition.respawnMs();if(now>=b.respawnAt){b.actor.x=b.homeX;b.actor.y=b.homeY;b.actor.vy=0;b.actor.grounded=true;b.apply(null);b.respawnAt=0;b.targetId=null;b.nextThinkAt=0;b.state=BotState.State.IDLE;}if(old!=b.state)event.accept("BOT_STATE_CHANGED",b.view());return;}
        regen(b,now);
        if(now>=b.nextThinkAt){
            b.nextThinkAt=now+b.thinkInterval();
            if(now<b.talkUntil){b.state=BotState.State.TALK;b.actor.navigation=null;}
            else if(b.definition.botType().equals("GUARD")&&b.definition.combatPolicy().equals("HOSTILE_MONSTERS_ONLY")){
                var target=perceived.stream().filter(m->m.hp>0&&m.mapId.equals(b.actor.mapId)&&Objects.equals(m.instanceId,b.actor.instanceId)&&Math.hypot(m.x-b.actor.x,m.y-b.actor.y)<=b.profile.perception()&&Math.hypot(m.x-b.homeX,m.y-b.homeY)<=b.profile.leashRadius()&&MapCollision.lineOfSight(map,b.actor.x,b.actor.y-20,m.x,m.y-20)).min(Comparator.comparingDouble(m->Math.hypot(m.x-b.actor.x,m.y-b.actor.y))).orElse(null);
                b.targetId=target==null?null:target.id;
                if(target==null){String utility=utility(b,now);if(utility!=null){b.actor.navigation=null;engine.cast(b.actor,utility,map,List.of(),now,event,defeated);}else patrol(b,map);}else if(b.actor.progress.hp<b.actor.maxHp()*b.profile.retreatHpRatio()){b.state=BotState.State.FLEE;b.targetId=null;plan(b,map,b.homeX,b.homeY);}else{
                    b.actor.facing=target.x>=b.actor.x?1:-1;String skill=utility(b,now);if(skill==null)skill=offensive(b,Math.abs(target.x-b.actor.x),now);
                    if(skill!=null&&Math.abs(target.y-b.actor.y)<=85){b.state=BotState.State.ATTACK;b.actor.navigation=null;engine.cast(b.actor,skill,map,List.of(target),now,event,defeated);}
                    else{b.state=BotState.State.CHASE;plan(b,map,target.x,target.y);}
                }
            }else if(b.definition.botType().equals("WANDERER"))patrol(b,map);else{b.state=BotState.State.IDLE;b.actor.navigation=null;}
        }
        move(b,map,dt);if(old!=b.state)event.accept("BOT_STATE_CHANGED",b.view());
    }
    public void training(BotState b,PlayerState target,MapCatalog.MapDefinition map,double dt,long now,BiConsumer<String,Object> event){
        regen(b,now);var old=b.state;
        if(now>=b.nextThinkAt){
            b.nextThinkAt=now+b.thinkInterval();
            boolean seen=target.progress.hp>0&&Objects.equals(target.instanceId,b.actor.instanceId)&&target.mapId.equals(b.actor.mapId)&&Math.hypot(target.x-b.actor.x,target.y-b.actor.y)<=b.profile.perception()&&MapCollision.lineOfSight(map,b.actor.x,b.actor.y-20,target.x,target.y-20);
            if(!seen){b.targetId=null;b.state=BotState.State.RETURN;plan(b,map,b.homeX,b.homeY);}
            else{
                b.targetId=target.id;b.actor.facing=target.x>=b.actor.x?1:-1;String skill=utility(b,now);double range=Math.abs(target.x-b.actor.x);if(skill==null)skill=offensive(b,range,now);
                if(skill!=null&&Math.abs(target.y-b.actor.y)<=85){b.state=BotState.State.ATTACK;b.actor.navigation=null;engine.castTraining(b.actor,skill,map,target,b.actor.instanceId,now,event);}
                else if(b.actor.progress.hp<b.actor.maxHp()*b.profile.retreatHpRatio()&&range<150){b.state=BotState.State.FLEE;plan(b,map,Math.clamp(b.actor.x-Math.signum(target.x-b.actor.x)*140,40,map.width()-40),map.ground());}
                else{b.state=BotState.State.CHASE;plan(b,map,target.x-Math.signum(target.x-b.actor.x)*55,target.y);}
            }
        }
        move(b,map,dt);if(old!=b.state)event.accept("BOT_STATE_CHANGED",b.view());
    }
}
