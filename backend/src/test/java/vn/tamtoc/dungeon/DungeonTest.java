package vn.tamtoc.dungeon;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.tamtoc.instance.*;
import vn.tamtoc.party.*;
import vn.tamtoc.world.*;
import vn.tamtoc.character.*;
import vn.tamtoc.combat.*;
import vn.tamtoc.progression.ProgressStore;
import vn.tamtoc.realtime.ProtocolException;

class DungeonTest {
    InstanceRegistry instances;PartyRegistry parties;DungeonService service;World world;Store store;UUID a,b;
    static class Store implements ActivityStore {
        Set<UUID> started=new HashSet<>();
        public void startDungeon(UUID run,UUID instance,UUID party,Collection<UUID> members,long now){started.add(run);}
        public boolean completeDungeon(UUID run,Map<UUID,ProgressStore.Save> saves,String rewards){return true;}
        public void finishDungeon(UUID run,String status,String reason){}
        public void finishDuel(UUID duel,UUID a,UUID b,UUID winner,String reason,long start,long end){}
    }
    static class World implements InstanceGateway {
        Map<UUID,PlayerState> players=new HashMap<>();Set<UUID> offline=new HashSet<>();Map<UUID,Integer> grants=new HashMap<>();int returns;boolean failGrant;List<UUID> eligibleSnapshot;
        public PlayerState getPlayer(UUID id){return players.get(id);}public boolean isOnline(UUID id){return players.containsKey(id)&&!offline.contains(id);}
        public void moveTo(UUID id,String map,UUID instance,double x){var p=players.get(id);p.mapId=map;p.instanceId=instance;p.x=x;}
        public void returnHome(UUID id){var p=players.get(id);if(p!=null){p.instanceId=null;p.mapId="trung-chau";}returns++;}
        public void event(UUID id,String type,Object payload){}
        public Map<UUID,Map<String,Object>> grantDungeon(UUID run,List<UUID> eligible,int exp,int cultivation,int stones){grants.merge(run,1,Integer::sum);eligibleSnapshot=List.copyOf(eligible);if(failGrant){failGrant=false;throw new IllegalStateException("Database unavailable");}var result=new HashMap<UUID,Map<String,Object>>();eligible.forEach(id->result.put(id,Map.of("exp",exp)));return result;}
    }
    @BeforeEach void setup()throws Exception{
        instances=new InstanceRegistry();parties=new PartyRegistry(4,60000);world=new World();store=new Store();service=new DungeonService(instances,parties,new CombatCatalog(new ObjectMapper()),store,5,900000,60000);
        a=player();b=player();join(a,b);
    }
    UUID player(){var p=new PlayerState(new GameCharacter(UUID.randomUUID(),"Tester",Race.HUMAN));p.level=10;p.mapId="trung-chau";p.x=350;p.y=540;p.lastActiveAt=1000;world.players.put(p.id,p);return p.id;}
    void join(UUID leader,UUID other){var i=parties.invite(leader,other,id->true,1);parties.accept(other,i.id(),id->true,2);}
    DungeonRun start(UUID leader,UUID member){service.ready(leader,true,world);service.ready(member,true,world);return service.start(leader,"HUYEN_MOC",world,1000);}
    @Test void requiresLevelEntryLeaderAndEveryReady(){
        assertEquals("DUNGEON_NOT_READY",assertThrows(ProtocolException.class,()->service.start(a,"HUYEN_MOC",world,1000)).code());
        world.players.get(b).level=1;assertThrows(ProtocolException.class,()->service.ready(b,true,world));world.players.get(b).level=10;
        world.players.get(b).x=1000;assertThrows(ProtocolException.class,()->service.ready(b,true,world));world.players.get(b).x=350;
        var run=start(a,b);assertEquals(DungeonRun.State.ACTIVE,run.state);assertThrows(ProtocolException.class,()->service.start(a,"HUYEN_MOC",world,1100));assertEquals(1,instances.all().size());assertEquals(1,store.started.size());
    }
    @Test void twoPartiesHaveSeparateEntitiesAndAllThreeWavesGrantOnce(){
        var first=start(a,b);UUID c=player(),d=player();join(c,d);var second=start(c,d);
        assertNotEquals(first.instance.id,second.instance.id);assertNotEquals(first.instance.monsters.get(0).id,second.instance.monsters.get(0).id);
        first.instance.monsters.forEach(m->m.hp=0);service.step(world,1100);assertEquals(2,first.wave);assertEquals(1,second.wave);assertTrue(second.instance.monsters.stream().allMatch(m->m.hp>0));
        first.instance.monsters.forEach(m->m.hp=0);service.step(world,1200);assertEquals(3,first.wave);assertEquals("BOSS",first.instance.monsters.get(0).definition.id());
        first.instance.monsters.forEach(m->m.hp=0);service.step(world,1300);service.step(world,1400);assertEquals(DungeonRun.State.COMPLETED,first.state);assertEquals(1,world.grants.get(first.id));
        service.step(world,62000);assertEquals(DungeonRun.State.CLEANED,first.state);assertEquals(1,instances.all().size());
    }
    @Test void wipeResetsCurrentWaveOnlyOnceThenFails(){
        var run=start(a,b);world.players.get(a).progress.hp=0;world.players.get(b).progress.hp=0;service.step(world,1100);assertEquals(1,run.wipes);assertEquals(DungeonRun.State.ACTIVE,run.state);
        service.step(world,1200);assertEquals(DungeonRun.State.ACTIVE,run.state);
        world.players.get(a).progress.hp=100;world.players.get(b).progress.hp=100;service.step(world,5200);
        world.players.get(a).progress.hp=0;world.players.get(b).progress.hp=0;service.step(world,5300);assertEquals(DungeonRun.State.FAILED,run.state);assertEquals("WIPE_LIMIT",run.reason);
    }
    @Test void reservesDisconnectedParticipantUntilGraceRemovalAndTimesOut(){
        var run=start(a,b);world.offline.add(b);service.step(world,2000);assertEquals(DungeonRun.State.ACTIVE,run.state);assertSame(run,service.forPlayer(b));
        world.players.remove(b);service.step(world,3000);assertEquals(DungeonRun.State.FAILED,run.state);
        UUID c=player(),d=player();join(c,d);var other=start(c,d);service.step(world,901001);assertEquals(DungeonRun.State.EXPIRED,other.state);assertTrue(world.grants.isEmpty());
    }
    @Test void completionRetryFreezesEligibilityAndSurvivesDepartureAndTimeout(){
        var run=start(a,b);run.wave=3;run.instance.monsters.forEach(m->m.hp=0);world.failGrant=true;service.step(world,1100);
        assertTrue(run.completionPending);assertFalse(run.instance.active);assertEquals(Set.of(a,b),Set.copyOf(world.eligibleSnapshot));
        service.leave(a,world,1200);world.offline.add(b);service.step(world,1500);assertEquals(1,world.grants.get(run.id));
        service.step(world,901001);assertEquals(DungeonRun.State.COMPLETED,run.state);assertEquals(Set.of(a,b),Set.copyOf(world.eligibleSnapshot));assertEquals(2,world.grants.get(run.id));service.step(world,901002);assertEquals(2,world.grants.get(run.id));
    }

}
