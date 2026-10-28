package vn.tamtoc.duel;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.tamtoc.instance.*;
import vn.tamtoc.world.*;
import vn.tamtoc.character.*;
import vn.tamtoc.combat.*;
import vn.tamtoc.progression.ProgressStore;
import vn.tamtoc.realtime.ProtocolException;

class DuelTest {
    static class Store implements ActivityStore {
        Set<UUID> results=new HashSet<>();int calls;boolean fail;
        public void startDungeon(UUID a,UUID b,UUID c,Collection<UUID>d,long n){}
        public boolean completeDungeon(UUID r,Map<UUID,ProgressStore.Save>s,String d){return true;}
        public void finishDungeon(UUID r,String s,String d){}
        public void finishDuel(UUID r,UUID a,UUID b,UUID w,String why,long start,long end){calls++;if(fail)throw new IllegalStateException("Database unavailable");results.add(r);}
    }
    static class World implements InstanceGateway {
        Map<UUID,PlayerState> players=new HashMap<>();Map<UUID,Long> disconnected=new HashMap<>();
        public PlayerState getPlayer(UUID id){return players.get(id);}public boolean isOnline(UUID id){return players.containsKey(id)&&!disconnected.containsKey(id);}
        public long disconnectedSince(UUID id){return disconnected.getOrDefault(id,0L);}
        public void moveTo(UUID id,String map,UUID instance,double x){var p=players.get(id);p.mapId=map;p.instanceId=instance;p.x=x;p.y=540;}
        public void returnHome(UUID id){var p=players.get(id);if(p!=null){p.mapId="trung-chau";p.instanceId=null;p.progress.hp=p.maxHp();p.progress.mp=p.maxMp();}}
        public void event(UUID id,String type,Object payload){}
        public Map<UUID,Map<String,Object>> grantDungeon(UUID r,List<UUID> e,int x,int c,int s){throw new AssertionError("Duel must never grant rewards");}
    }
    World world;Store store;InstanceRegistry instances;DuelService service;UUID a,b,c;
    @BeforeEach void setup(){world=new World();store=new Store();instances=new InstanceRegistry();service=new DuelService(instances,store,30000,5000,180000,15000,60000);a=player();b=player();c=player();}
    UUID player(){var p=new PlayerState(new GameCharacter(UUID.randomUUID(),"Tester",Race.HUMAN));p.mapId="trung-chau";p.x=350;p.y=540;world.players.put(p.id,p);return p.id;}
    DuelRun start(){var i=service.challenge(a,b,world,1000);return service.accept(b,i.id(),world,1100);}
    @Test void requiresConsentAndTargetBoundUnexpiredInvitation(){
        var i=service.challenge(a,b,world,1000);assertTrue(instances.all().isEmpty());assertThrows(ProtocolException.class,()->service.accept(c,i.id(),world,1100));service.decline(b,i.id(),world,1200);assertTrue(instances.all().isEmpty());assertThrows(ProtocolException.class,()->service.accept(b,i.id(),world,1300));
        var expired=service.challenge(a,b,world,2000);assertThrows(ProtocolException.class,()->service.accept(b,expired.id(),world,32000));service.step(world,32001);assertTrue(instances.all().isEmpty());assertTrue(store.results.isEmpty());
    }
    @Test void countdownGatesCombatAndKnockoutCommitsOnceWithoutRewards(){
        var run=start();assertThrows(ProtocolException.class,()->service.opponents(a,world));service.step(world,6099);assertEquals(DuelRun.State.PREPARING,run.state);service.step(world,6100);assertEquals(DuelRun.State.ACTIVE,run.state);assertEquals(b,service.opponents(a,world).get(0).id);
        long exp=world.getPlayer(a).exp,cult=world.getPlayer(a).cultivation;world.getPlayer(b).progress.hp=0;service.afterCombat(a,world,6200);service.afterCombat(a,world,6300);service.step(world,6400);assertEquals(a,run.winner);assertEquals("KNOCKOUT",run.reason);assertEquals(1,store.calls);assertEquals(exp,world.getPlayer(a).exp);assertEquals(cult,world.getPlayer(a).cultivation);assertNull(world.getPlayer(a).instanceId);assertTrue(instances.all().isEmpty());
    }
    @Test void disconnectReservesSeatThenLosesAtFifteenSecondsAndTimeoutDraws(){
        var run=start();world.disconnected.put(a,2000L);service.step(world,16999);assertNotEquals(DuelRun.State.FINISHED,run.state);service.step(world,17000);assertEquals(b,run.winner);assertEquals("DISCONNECT",run.reason);
        world.disconnected.clear();var next=start();service.step(world,next.activeAt+180000);assertEquals(DuelRun.State.FINISHED,next.state);assertNull(next.winner);assertEquals("TIMEOUT",next.reason);assertEquals(2,store.results.size());
    }
    @Test void failedPersistenceFreezesMatchAndRetriesSameResult(){
        var run=start();store.fail=true;service.forfeit(b,"FORFEIT",world,2000);assertEquals(DuelRun.State.FINISHING,run.state);assertThrows(ProtocolException.class,()->service.opponents(a,world));service.step(world,6999);assertEquals(1,store.calls);store.fail=false;service.step(world,7000);assertEquals(DuelRun.State.FINISHED,run.state);assertEquals(a,run.winner);assertEquals(Set.of(run.id),store.results);
    }
    @Test void sharedCombatRespectsInstanceRangeCooldownManaAndDefense()throws Exception{
        var run=start();service.step(world,6100);var actor=world.getPlayer(a);var target=world.getPlayer(b);actor.x=500;target.x=550;actor.facing=1;var catalog=new CombatCatalog(new ObjectMapper());var engine=new CombatEngine(catalog);var map=new MapCatalog(new ObjectMapper()).get(DuelService.MAP);int hp=target.progress.hp,mp=actor.progress.mp;
        engine.castDuel(actor,"QI",map,service.opponents(a,world),run.instance.id,6200,(t,p)->{});assertTrue(target.progress.hp<hp);assertEquals(mp-8,actor.progress.mp);assertThrows(ProtocolException.class,()->engine.castDuel(actor,"QI",map,List.of(target),run.instance.id,6300,(t,p)->{}));
        hp=target.progress.hp;target.instanceId=UUID.randomUUID();engine.castDuel(actor,"ATTACK",map,List.of(target),run.instance.id,7000,(t,p)->{});assertEquals(hp,target.progress.hp);target.instanceId=run.instance.id;target.x=100;engine.castDuel(actor,"ATTACK",map,List.of(target),run.instance.id,8000,(t,p)->{});assertEquals(hp,target.progress.hp);assertFalse(CombatTargetPolicy.pvp(actor,world.getPlayer(c),run.instance.id));actor.progress.mp=0;assertThrows(ProtocolException.class,()->engine.castDuel(actor,"QI",map,List.of(target),run.instance.id,10000,(t,p)->{}));
    }
}
