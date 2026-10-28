package vn.tamtoc.bot;

import java.util.*;
import vn.tamtoc.world.*;
import vn.tamtoc.character.*;

public class BotState {
    public enum State { IDLE,PATROL,TALK,FOLLOW,CHASE,ATTACK,FLEE,RETURN,DEAD }
    public final BotCatalog.BotDefinition definition;public final BotCatalog.BehaviorProfile profile;public final PlayerState actor;public final String spawnId;
    public final List<MapCatalog.Waypoint> route;public final double homeX,homeY,interactionRadius;
    public BotCatalog.Difficulty difficulty;public boolean training;public State state=State.IDLE;public UUID targetId;public int routeIndex,dialogueIndex;
    public long nextThinkAt,respawnAt,talkUntil;public String currentGoal="home";
    public BotState(BotCatalog.BotDefinition def,BotCatalog.BehaviorProfile profile,MapCatalog.MapDefinition map,MapCatalog.BotSpawn spawn){
        definition=def;this.profile=profile;spawnId=spawn.id();var point=map.spawn(spawn.spawnPoint());homeX=point.x();homeY=point.y();interactionRadius=spawn.interactionRadius();route=spawn.patrolRoute().stream().map(id->map.waypoints().stream().filter(w->w.id().equals(id)).findFirst().orElseThrow()).toList();
        actor=PlayerState.ai(UUID.randomUUID(),def.name(),def.race(),def.stats().level(),map.id());actor.x=homeX;actor.y=homeY;actor.grounded=true;apply(null);
    }
    public void apply(BotCatalog.Difficulty difficulty){this.difficulty=difficulty;var s=definition.stats();actor.level=s.level()+(difficulty==null?0:difficulty.levelBonus());actor.aiStats=new PlayerState.AiStats((int)(s.hp()*(difficulty==null?1:difficulty.hpMultiplier())),(int)(s.mp()*(difficulty==null?1:difficulty.mpMultiplier())),s.attack()+(difficulty==null?0:difficulty.attackBonus()),s.defense()+(difficulty==null?0:difficulty.defenseBonus()));actor.progress.hp=actor.maxHp();actor.progress.mp=actor.maxMp();actor.cooldowns.clear();}
    public double speed(){return definition.stats().speed()*(difficulty==null?1:difficulty.speedMultiplier());}
    public long thinkInterval(){return difficulty==null?profile.thinkIntervalMs():difficulty.thinkIntervalMs();}
    public record View(UUID id,boolean isAI,String definitionId,String name,String botType,String race,String factionId,String attitude,String mapId,UUID instanceId,double x,double y,int hp,int maxHp,int mp,int maxMp,int level,int facing,String state,boolean interactable,double interactionRadius,double perception,double leashRadius,double homeX,double homeY) {}
    public View view(){return new View(actor.id,true,definition.id(),definition.name(),definition.botType(),definition.race(),definition.factionId(),training?"HOSTILE":definition.botType().equals("WANDERER")?"NEUTRAL":"FRIENDLY",actor.mapId,actor.instanceId,actor.x,actor.y,actor.progress.hp,actor.maxHp(),actor.progress.mp,actor.maxMp(),actor.level,actor.facing,state.name(),!training&&definition.interactable(),interactionRadius,profile.perception(),profile.leashRadius(),homeX,homeY);}
}
