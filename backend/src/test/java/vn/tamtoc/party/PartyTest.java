package vn.tamtoc.party;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.concurrent.*;
import vn.tamtoc.character.*;
import vn.tamtoc.world.*;
import vn.tamtoc.combat.*;
import vn.tamtoc.realtime.*;
import com.fasterxml.jackson.databind.ObjectMapper;

class PartyTest {
    PartyRegistry parties;UUID a,b,c,d,e;
    @BeforeEach void setup(){parties=new PartyRegistry(4,60000);a=UUID.randomUUID();b=UUID.randomUUID();c=UUID.randomUUID();d=UUID.randomUUID();e=UUID.randomUUID();}
    PartyRegistry.Invitation invite(UUID target){return parties.invite(a,target,id->true,1000);}
    void add(UUID target){var i=invite(target);parties.accept(target,i.id(),id->true,2000);}
    @Test void invitationsAreTargetBoundConsumedOnceAndExpire(){
        var i=invite(b);assertThrows(ProtocolException.class,()->parties.accept(c,i.id(),id->true,2000));
        parties.accept(b,i.id(),id->true,2000);assertEquals(2,parties.get(a).members.size());assertThrows(ProtocolException.class,()->parties.accept(b,i.id(),id->true,2001));
        var declined=invite(c);parties.decline(c,declined.id(),2000);assertThrows(ProtocolException.class,()->parties.accept(c,declined.id(),id->true,2001));
        var expired=invite(d);assertEquals("INVITE_EXPIRED",assertThrows(ProtocolException.class,()->parties.accept(d,expired.id(),id->true,61000)).code());assertEquals(1,parties.expire(61000).size());
    }
    @Test void concurrentAcceptCannotExceedFourMembers()throws Exception{
        add(b);add(c);var one=invite(d);var two=invite(e);var pool=Executors.newFixedThreadPool(2);
        try{var results=pool.invokeAll(List.<Callable<Boolean>>of(()->accept(d,one.id()),()->accept(e,two.id())));assertEquals(1,results.stream().filter(f->{try{return f.get();}catch(Exception x){throw new RuntimeException(x);}}).count());assertEquals(4,parties.get(a).members.size());}
        finally{pool.shutdownNow();}
    }
    boolean accept(UUID target,UUID invite){try{parties.accept(target,invite,id->true,2000);return true;}catch(ProtocolException x){return false;}}
    @Test void cannotJoinTwoParties(){
        var one=invite(b);var two=parties.invite(c,b,id->true,1000);parties.accept(b,one.id(),id->true,2000);assertEquals("ALREADY_IN_PARTY",assertThrows(ProtocolException.class,()->parties.accept(b,two.id(),id->true,2000)).code());
    }
    @Test void leaderPermissionsAndDeterministicSuccession(){
        add(b);add(c);assertEquals("LEADER_REQUIRED",assertThrows(ProtocolException.class,()->parties.kick(b,c,id->true)).code());
        parties.transfer(a,b,id->true);assertEquals(b,parties.get(a).leader);parties.leave(b,id->true);
        assertEquals(List.of(a,c).stream().min(Comparator.comparing(UUID::toString)).orElseThrow(),parties.get(a).leader);
        UUID leader=parties.get(a).leader;UUID other=leader.equals(a)?c:a;parties.kick(leader,other,id->true);assertNull(parties.get(other));parties.leave(leader,id->true);assertTrue(parties.all().isEmpty());
    }
    @Test void successfulRequestIsNotExecutedTwiceAndCannotChangeMeaning(){
        var requests=new CommandRequests();var request=UUID.randomUUID().toString();int[] calls={0};
        assertTrue(requests.execute(a,request,"invite:b",()->calls[0]++,1000));assertFalse(requests.execute(a,request,"invite:b",()->calls[0]++,2000));
        assertThrows(ProtocolException.class,()->requests.execute(a,request,"kick:b",()->calls[0]++,2000));assertEquals(1,calls[0]);
    }
    @Test void rewardBudgetIsSharedDeterministicallyWithoutAfkOrForeignInstances()throws Exception{
        var catalog=new CombatCatalog(new ObjectMapper());var monster=new MonsterState(catalog.monsters.get("WOLF"),"thanh-van",800,540);
        var players=new ArrayList<PlayerState>();
        for(int i=0;i<4;i++){var p=new PlayerState(new GameCharacter(UUID.randomUUID(),"Tester"+i,Race.values()[i%3]));p.mapId=monster.mapId;p.x=800;p.y=540;players.add(p);}
        UUID leader=players.get(0).id;
        for(int i=1;i<4;i++){var inv=parties.invite(leader,players.get(i).id,id->true,1000);parties.accept(players.get(i).id,inv.id(),id->true,2000);}
        monster.contributions.put(leader,80);players.get(0).lastActiveAt=10000;players.get(1).lastActiveAt=10000;players.get(2).lastActiveAt=0;players.get(3).lastActiveAt=10000;players.get(3).instanceId=UUID.randomUUID();
        var policy=new PartyRewardPolicy(parties,800,10000);var result=policy.split(monster,players,11000);
        assertEquals(2,result.size());assertEquals(45,result.stream().mapToInt(PartyRewardPolicy.Share::exp).sum());assertEquals(15,result.stream().mapToInt(PartyRewardPolicy.Share::cultivation).sum());
        Collections.reverse(players);assertEquals(result,policy.split(monster,players,11000));
        players.stream().filter(p->p.id.equals(leader)).findFirst().orElseThrow().lastActiveAt=1;
        var withoutAfkContributor=policy.split(monster,players,11000);assertEquals(1,withoutAfkContributor.size());assertNotEquals(leader,withoutAfkContributor.get(0).player().id);
        players.stream().filter(p->p.id.equals(leader)).findFirst().orElseThrow().x=2000;assertTrue(policy.split(monster,players,30000).isEmpty());
    }
}
