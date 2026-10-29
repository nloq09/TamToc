package vn.tamtoc.combat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.tamtoc.world.*;
import vn.tamtoc.character.*;

class EcologyTest {
    ObjectMapper json=new ObjectMapper();MapCatalog maps;CombatCatalog catalog;MonsterAi ai=new MonsterAi();
    @BeforeEach void setup()throws Exception{maps=new MapCatalog(json);catalog=new CombatCatalog(json);}
    PlayerState player(String map,double x){var p=new PlayerState(new GameCharacter(UUID.randomUUID(),"EcologyTester",Race.HUMAN));p.mapId=map;p.x=x;p.y=540;return p;}
    @Test void dataGroupsHaveBoundedUniqueSlotsAndStableRespawnIdentity(){
        var population=new MonsterPopulation(maps,catalog);int expected=maps.all().stream().filter(m->!Set.of("DUNGEON","ARENA").contains(m.mapType())).flatMap(m->m.monsterSpawnGroups().stream()).filter(MapCatalog.SpawnGroup::enabled).mapToInt(MapCatalog.SpawnGroup::maxAlive).sum();assertEquals(expected,population.all().size());assertEquals(expected,population.all().stream().map(m->m.id).distinct().count());
        var m=population.all().get(0);UUID id=m.id;var defeats=new MonsterDefeats();m.hp=0;m.state=MonsterState.Ai.DEAD;m.respawnAt=2000;assertTrue(defeats.once(m,1000).isPresent());assertTrue(defeats.once(m,1001).isEmpty());ai.step(m,List.of(),maps.get(m.mapId),.05,1999,(t,p)->{});assertEquals(0,m.hp);ai.step(m,List.of(),maps.get(m.mapId),.05,2000,(t,p)->{});assertEquals(id,m.id);assertEquals(m.definition.hp(),m.hp);assertFalse(m.rewardProcessed);m.hp=0;assertEquals(2,defeats.once(m,3000).orElseThrow().ordinal());
    }
    @Test void threatKeepsTargetUntilHoldExpiresAndHigherThreatWins(){
        var m=new MonsterState(catalog.monsters.get("WOLF"),"thanh-van-ngoai-son",800,540);var a=player(m.mapId,835);var b=player(m.mapId,830);m.threat.put(a.id,50d);m.threat.put(b.id,1d);
        ai.step(m,List.of(a,b),maps.get(m.mapId),.05,1000,(t,p)->{});assertEquals(a.id,m.targetId);m.threat.put(b.id,300d);m.nextThinkAt=0;ai.step(m,List.of(a,b),maps.get(m.mapId),.05,1100,(t,p)->{});assertEquals(a.id,m.targetId);ai.step(m,List.of(a,b),maps.get(m.mapId),.05,2700,(t,p)->{});assertEquals(b.id,m.targetId);
    }
    @Test void leashTimeoutReturnsAndFullyResetsInsteadOfChasingForever(){
        var map=maps.get("thanh-van-ngoai-son");var m=new MonsterState(catalog.monsters.get("WOLF"),map.id(),800,540);var p=player(map.id(),900);m.hp=20;m.contributions.put(p.id,60);m.threat.put(p.id,60d);
        ai.step(m,List.of(p),map,.05,1000,(t,v)->{});m.x=880;p.x=1000;ai.step(m,List.of(p),map,.05,1200,(t,v)->{});ai.step(m,List.of(p),map,.05,17000,(t,v)->{});assertEquals(MonsterState.Ai.RETURN,m.state);for(int i=1;i<=100;i++)ai.step(m,List.of(),map,.05,17000+i*50,(t,v)->{});assertEquals(m.definition.hp(),m.hp);assertTrue(m.contributions.isEmpty());assertTrue(m.threat.isEmpty());assertNull(m.targetId);assertTrue(Math.abs(m.x-m.spawnX)<=85);
    }
    @Test void perceptionAndMovementCannotCrossSolidWallOrAttackSafeZone(){
        var map=maps.get("thanh-van-ngoai-son");var m=new MonsterState(catalog.monsters.get("WOLF"),map.id(),1400,540);var target=player(map.id(),1540);int hp=target.progress.hp;for(int i=0;i<100;i++)ai.step(m,List.of(target),map,.05,1000+i*50,(t,p)->{});assertNull(m.targetId);assertTrue(m.x<=1434);assertEquals(hp,target.progress.hp);
        var nearSafe=new MonsterState(catalog.monsters.get("WOLF"),map.id(),450,540);target.x=350;for(int i=0;i<80;i++)ai.step(nearSafe,List.of(target),map,.05,1000+i*50,(t,p)->{});assertEquals(hp,target.progress.hp);assertNull(nearSafe.targetId);
    }
    @Test void bossTelegraphComesBeforeDamageAndPhaseUsesData(){
        var map=maps.get("huyen-moc-party");var m=new MonsterState(catalog.monsters.get("BOSS"),map.id(),650,540);var p=player(map.id(),700);m.instanceId=UUID.randomUUID();p.instanceId=m.instanceId;m.hp=500;List<String> events=new ArrayList<>();List<Map<?,?>> telegraphs=new ArrayList<>();java.util.function.BiConsumer<String,Object> event=(t,v)->{events.add(t);if(t.equals("BOSS_TELEGRAPH"))telegraphs.add((Map<?,?>)v);};
        ai.step(m,List.of(p),map,.05,1000,event);assertEquals(2,m.phase);ai.step(m,List.of(p),map,.05,1200,event);assertEquals(1,telegraphs.size());long resolves=((Number)telegraphs.get(0).get("resolvesAt")).longValue();int hp=p.progress.hp;ai.step(m,List.of(p),map,.05,resolves-1,event);assertEquals(hp,p.progress.hp);ai.step(m,List.of(p),map,.05,resolves,event);assertTrue(p.progress.hp<hp);assertTrue(events.indexOf("BOSS_TELEGRAPH")<events.indexOf("COMBAT_EVENT"));assertTrue(events.contains("BOSS_ABILITY_RESOLVED"));
    }
    @Test void navigationExecutesJumpDropAndRejectsUnsupportedCapabilities(){
        var map=maps.get("thanh-van-ngoai-son");var body=new MotionBody();body.x=850;body.y=540;body.grounded=true;var cap=new Navigation.Capabilities(95,570,true,240,16,35);body.navigation=Navigation.plan(body,map,805,450,cap);assertNotNull(body.navigation);for(int i=0;i<50;i++)Navigation.step(body,map,cap,.05);assertEquals(450,body.y,.01);assertEquals(805,body.x,3);
        body.navigation=Navigation.plan(body,map,850,540,cap);assertNotNull(body.navigation);for(int i=0;i<70;i++)Navigation.step(body,map,cap,.05);assertEquals(540,body.y,.01);assertEquals(850,body.x,3);
        assertNull(Navigation.plan(body,map,805,450,new Navigation.Capabilities(95,570,false,240,16,35)));body.x=1400;assertNull(Navigation.plan(body,map,1600,540,cap));
    }
    @Test void lootRollsFromConfiguredTableWithDeterministicPrivateSeed(){
        var m=new MonsterState(catalog.monsters.get("WOLF_ALPHA"),"thanh-van-ngoai-son",2830,540);m.deathOrdinal=1;UUID owner=UUID.randomUUID();var a=new LootRoller(12345);var b=new LootRoller(12345);assertEquals(a.roll(m,owner),b.roll(m,owner));var fang=a.roll(m,owner).stream().filter(d->d.itemId().equals("WOLF_FANG")).findFirst().orElseThrow();assertTrue(fang.quantity()>=2&&fang.quantity()<=3);assertEquals("ELITE",m.view().tier());
    }
    @Test void aJumpingMonsterCompletesItsGraphEdgeBeforeStoppingToAttack(){
        var map=maps.get("thanh-van-ngoai-son");var m=new MonsterState(catalog.monsters.get("SPIRIT"),map.id(),850,540);var target=player(map.id(),805);target.y=450;
        for(int i=0;i<80;i++)ai.step(m,List.of(target),map,.05,1000+i*50,(t,p)->{});
        assertEquals(450,m.y,.01);assertTrue(Math.abs(m.x-805)<60);assertTrue(target.progress.hp<100);
    }
}
