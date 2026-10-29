package vn.tamtoc.content;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import vn.tamtoc.combat.*;
import vn.tamtoc.world.MapCatalog;

class WorldEventTest {
    @Test void serverClockEligibilityAndDurableCycleClaimPreventDuplicateBossAfterRestart()throws Exception{
        var json=new ObjectMapper();var maps=new MapCatalog(json);var combat=new CombatCatalog(json);Set<String> claimed=new HashSet<>();WorldEventLedger ledger=(id,cycle,now)->claimed.add(id+":"+cycle);var pop=new MonsterPopulation(maps,combat,ledger);var boss=pop.all().stream().filter(m->m.scheduled).findFirst().orElseThrow();long interval=14400000,now=interval*10+1000;List<String> events=new ArrayList<>();
        pop.schedule(now,Map.of(),(m,t,p)->events.add(t));assertFalse(boss.scheduledActive);pop.schedule(now,Map.of(boss.mapId,7),(m,t,p)->events.add(t));assertFalse(boss.scheduledActive);pop.schedule(now,Map.of(boss.mapId,8),(m,t,p)->events.add(t));assertTrue(boss.scheduledActive);assertEquals(1,events.size());UUID id=boss.id;
        boss.hp=0;pop.schedule(now+100,Map.of(boss.mapId,25),(m,t,p)->events.add(t));new MonsterAi().step(boss,List.of(),maps.get(boss.mapId),.05,now+100000,(t,p)->{});assertEquals(0,boss.hp);assertEquals(1,events.size());
        var restarted=new MonsterPopulation(maps,combat,ledger);restarted.schedule(now+5000,Map.of(boss.mapId,25),(m,t,p)->events.add(t));assertFalse(restarted.all().stream().filter(m->m.scheduled).findFirst().orElseThrow().scheduledActive);
        pop.schedule(interval*11+1000,Map.of(boss.mapId,25),(m,t,p)->events.add(t));assertEquals(id,boss.id);assertEquals(boss.definition.hp(),boss.hp);pop.schedule(interval*11+1200001,Map.of(boss.mapId,25),(m,t,p)->events.add(t));assertFalse(boss.scheduledActive);assertEquals(0,boss.hp);
    }
    @Test void encounterDefinitionsSpanTheirDeclaredLevelsAndNeverSpawnInOpenWorld()throws Exception{var json=new ObjectMapper();var maps=new MapCatalog(json);var combat=new CombatCatalog(json);for(var map:maps.all())for(var g:map.monsterSpawnGroups())for(String id:g.definitionIds())assertTrue(combat.monsters.containsKey(id));assertEquals(Set.of(10,11),maps.get("huyen-moc-party").monsterSpawnGroups().get(0).definitionIds().stream().map(id->combat.monsters.get(id).level()).collect(java.util.stream.Collectors.toSet()));assertTrue(new MonsterPopulation(maps,combat).all().stream().noneMatch(m->m.instanceId!=null||maps.get(m.mapId).mapType().equals("DUNGEON")));}
}
