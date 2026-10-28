package vn.tamtoc.realtime;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.web.socket.WebSocketSession;
import vn.tamtoc.character.*;
import vn.tamtoc.world.PlayerState;

class CharacterSessionsTest {
    CharacterSessions sessions;UUID owner;GameCharacter character;
    @BeforeEach void setup(){sessions=new CharacterSessions(20000);owner=UUID.randomUUID();character=new GameCharacter(owner,"Tester",Race.HUMAN);}
    WebSocketSession socket(String id){return (WebSocketSession)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{WebSocketSession.class},(proxy,method,args)->method.getName().equals("getId")?id:method.getName().equals("isOpen")?true:null);}
    CharacterSessions.Binding join(String sid){return sessions.bind(owner,character.id,socket(sid),null,true,()->new PlayerState(character),1000);}
    @Test void duplicateControllerNeedsPrivateResumeProof(){
        var first=join("a");
        var e=assertThrows(ProtocolException.class,()->join("b"));assertEquals("CHARACTER_ALREADY_CONNECTED",e.code());
        assertThrows(ProtocolException.class,()->sessions.bind(UUID.randomUUID(),character.id,socket("b"),sessions.resumeSecret(first.lease()),true,()->new PlayerState(character),1100));
        assertEquals(1,sessions.online().size());
    }
    @Test void graceKeepsTheActualRuntimeStateAndClearsInput(){
        var first=join("a");var p=first.lease().player;p.x=900;p.progress.hp=42;p.direction=1;p.jumpHeld=true;p.jumpQueued=true;p.sequence=55;
        sessions.disconnect("a",false,2000);
        assertEquals(1,sessions.all().size());assertTrue(sessions.online().isEmpty());assertEquals(0,p.direction);assertFalse(p.jumpQueued);
        var resumed=sessions.bind(owner,character.id,socket("b"),sessions.resumeSecret(first.lease()),true,()->{throw new AssertionError("Must not reload DB state");},9000);
        assertTrue(resumed.resumed());assertSame(p,resumed.lease().player);assertEquals(42,p.progress.hp);assertEquals(900,p.x);assertEquals(-1,p.sequence);
    }
    @Test void staleCloseAfterTakeoverCannotEvictNewConnection(){
        var first=join("a");
        var second=sessions.bind(owner,character.id,socket("b"),sessions.resumeSecret(first.lease()),true,()->new PlayerState(character),1200);
        assertNotNull(second.replaced());assertNull(sessions.disconnect("a",false,1300));
        assertNotNull(sessions.byConnection("b"));assertEquals(CharacterSessions.Status.ONLINE,second.lease().status);
    }
    @Test void expirationIsExactlyOnceAndLaterJoinUsesFreshState(){
        var first=join("a");String secret=sessions.resumeSecret(first.lease());sessions.disconnect("a",false,2000);
        assertTrue(sessions.expire(21999).isEmpty());assertEquals(1,sessions.expire(22000).size());assertTrue(sessions.expire(23000).isEmpty());
        var fresh=sessions.bind(owner,character.id,socket("b"),secret,true,()->new PlayerState(character),23000);
        assertFalse(fresh.resumed());assertNotSame(first.lease().player,fresh.lease().player);assertNotEquals(secret,sessions.resumeSecret(fresh.lease()));
    }
    @Test void intentionalLeaveAndLegacyClientsReleaseImmediately(){
        join("a");sessions.disconnect("a",true,2000);assertTrue(sessions.all().isEmpty());
        sessions.bind(owner,character.id,socket("legacy"),null,false,()->new PlayerState(character),3000);sessions.disconnect("legacy",false,4000);assertTrue(sessions.all().isEmpty());
    }
    @Test void concurrentFreshJoinsHaveOneController()throws Exception{
        var pool=Executors.newFixedThreadPool(4);
        try{
            var tasks=new ArrayList<Callable<Boolean>>();for(int i=0;i<4;i++){String id="s"+i;tasks.add(()->{try{join(id);return true;}catch(ProtocolException e){return false;}});}
            int accepted=0;for(var result:pool.invokeAll(tasks))if(result.get())accepted++;
            assertEquals(1,accepted);assertEquals(1,sessions.online().size());assertEquals(1,sessions.all().size());
        }finally{pool.shutdownNow();}
    }
}
