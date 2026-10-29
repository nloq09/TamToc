package vn.tamtoc.combat;

import java.util.*;
import org.springframework.stereotype.Component;
import vn.tamtoc.world.MapCatalog;

/** Fixed slots per data-defined spawn group; death/respawn reuse the same entity. */
@Component
public class MonsterPopulation {
    private final List<MonsterState> monsters=new ArrayList<>();
    private final MapCatalog maps;
    private record Scheduled(MonsterState monster,MapCatalog.SpawnGroup group) {}
    private final List<Scheduled> scheduled=new ArrayList<>();private final vn.tamtoc.content.WorldEventLedger ledger;
    public MonsterPopulation(MapCatalog maps,CombatCatalog catalog){this(maps,catalog,(id,cycle,now)->true);}
    @org.springframework.beans.factory.annotation.Autowired public MonsterPopulation(MapCatalog maps,CombatCatalog catalog,vn.tamtoc.content.WorldEventLedger ledger){
        this.maps=maps;this.ledger=ledger;
        for(var map:maps.all())if(!Set.of("DUNGEON","ARENA").contains(map.mapType()))for(var group:map.monsterSpawnGroups()){
            if(!group.enabled())continue;
            for(String id:group.definitionIds())if(!catalog.monsters.containsKey(id))throw new IllegalArgumentException("Unknown monster definition "+id);
            for(int i=0;i<group.maxAlive();i++){var definition=catalog.monsters.get(group.definitionFor(i));double x=group.maxAlive()==1?group.startX():group.startX()+i*(group.endX()-group.startX())/(group.maxAlive()-1);var m=new MonsterState(definition,map.id(),x,group.y());m.spawnGroupId=group.id();m.placementId=group.placementId();m.respawnDelay=group.respawnMs()>0?group.respawnMs():definition.respawnMs();if(group.spawnPolicy().equals("SCHEDULED_EVENT")){m.scheduled=true;m.hp=0;m.state=MonsterState.Ai.DEAD;m.respawnAt=Long.MAX_VALUE;scheduled.add(new Scheduled(m,group));}monsters.add(m);}
        }
    }
    public List<MonsterState> all(){return List.copyOf(monsters);}
    @FunctionalInterface public interface ScheduleEvent {void accept(MonsterState monster,String type,Object payload);}
    public void schedule(long now,Map<String,Integer> eligibleLevels,ScheduleEvent event){for(var slot:scheduled){var m=slot.monster;var g=slot.group;long cycle=Math.floorDiv(now,g.scheduleIntervalMs()),offset=Math.floorMod(now,g.scheduleIntervalMs());if(offset>=g.activeDurationMs()){if(m.scheduledActive){m.scheduledActive=false;m.hp=0;m.state=MonsterState.Ai.DEAD;m.castingAbility=null;m.navigation=null;m.targetId=null;m.threat.clear();m.contributions.clear();event.accept(m,"WORLD_BOSS_ENDED",Map.of("id",m.id,"placementId",g.placementId(),"x",m.x,"y",m.y));}continue;}if(m.lastEventCycle==cycle||eligibleLevels.getOrDefault(m.mapId,0)<g.requiredLevel()||now<m.schedulerRetryAt)continue;try{boolean claimed=ledger.claim(g.id(),cycle,now);m.lastEventCycle=cycle;if(!claimed)continue;m.hp=m.definition.hp();m.x=m.spawnX;m.y=m.spawnY;m.vy=0;m.grounded=true;m.phase=1;m.state=MonsterState.Ai.IDLE;m.rewardProcessed=false;m.respawnAt=Long.MAX_VALUE;m.scheduledActive=true;m.castingAbility=null;m.strikeAt=0;m.targetId=null;m.strikeTarget=null;m.navigation=null;m.aggroSince=0;m.targetSince=0;m.stunnedUntil=0;m.slowedUntil=0;m.nextThinkAt=now;m.attackAt=now;m.abilityAt=now;m.threat.clear();m.contributions.clear();event.accept(m,"WORLD_BOSS_STARTED",Map.of("id",m.id,"placementId",g.placementId(),"cycle",cycle,"endsAt",now-offset+g.activeDurationMs(),"x",m.x,"y",m.y));}catch(RuntimeException failure){m.schedulerRetryAt=now+5000;org.slf4j.LoggerFactory.getLogger(MonsterPopulation.class).warn("World event ledger unavailable for {}",g.id(),failure);}}}
    public void alertGroup(MonsterState hit,vn.tamtoc.world.PlayerState actor,int damage){
        for(var nearby:monsters)if(nearby!=hit&&nearby.hp>0&&nearby.mapId.equals(hit.mapId)&&Objects.equals(nearby.instanceId,hit.instanceId)&&nearby.spawnGroupId.equals(hit.spawnGroupId)&&Math.hypot(nearby.x-hit.x,nearby.y-hit.y)<=160&&Math.hypot(actor.x-nearby.x,actor.y-nearby.y)<=nearby.definition.detection()&&vn.tamtoc.world.MapCollision.lineOfSight(maps.get(hit.mapId),nearby.x,nearby.y-20,actor.x,actor.y-20)){nearby.threat.merge(actor.id,damage*.2,Double::sum);nearby.nextThinkAt=0;}
    }
}
