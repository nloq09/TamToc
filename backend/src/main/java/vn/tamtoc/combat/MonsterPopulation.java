package vn.tamtoc.combat;

import java.util.*;
import org.springframework.stereotype.Component;
import vn.tamtoc.world.MapCatalog;

/** Fixed slots per data-defined spawn group; death/respawn reuse the same entity. */
@Component
public class MonsterPopulation {
    private final List<MonsterState> monsters=new ArrayList<>();
    private final MapCatalog maps;
    public MonsterPopulation(MapCatalog maps,CombatCatalog catalog){
        this.maps=maps;
        for(var map:maps.all())for(var group:map.monsterSpawnGroups()){
            var definition=catalog.monsters.get(group.definitionId());if(definition==null)throw new IllegalArgumentException("Unknown monster definition "+group.definitionId());
            for(int i=0;i<group.maxAlive();i++){double x=group.maxAlive()==1?group.startX():group.startX()+i*(group.endX()-group.startX())/(group.maxAlive()-1);var m=new MonsterState(definition,map.id(),x,group.y());m.spawnGroupId=group.id();m.respawnDelay=group.respawnMs()>0?group.respawnMs():definition.respawnMs();monsters.add(m);}
        }
    }
    public List<MonsterState> all(){return List.copyOf(monsters);}
    public void alertGroup(MonsterState hit,vn.tamtoc.world.PlayerState actor,int damage){
        for(var nearby:monsters)if(nearby!=hit&&nearby.hp>0&&nearby.mapId.equals(hit.mapId)&&Objects.equals(nearby.instanceId,hit.instanceId)&&nearby.spawnGroupId.equals(hit.spawnGroupId)&&Math.hypot(nearby.x-hit.x,nearby.y-hit.y)<=160&&Math.hypot(actor.x-nearby.x,actor.y-nearby.y)<=nearby.definition.detection()&&vn.tamtoc.world.MapCollision.lineOfSight(maps.get(hit.mapId),nearby.x,nearby.y-20,actor.x,actor.y-20)){nearby.threat.merge(actor.id,damage*.2,Double::sum);nearby.nextThinkAt=0;}
    }
}
