package vn.tamtoc.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.tamtoc.character.*;
import vn.tamtoc.combat.*;

class WorldInterestTest {
    WorldInterest interest=new WorldInterest(900,550);PlayerState hero;MonsterState wolf;
    @BeforeEach void setup()throws Exception{hero=new PlayerState(new GameCharacter(UUID.randomUUID(),"Observer",Race.HUMAN));hero.x=160;hero.y=540;wolf=new MonsterState(new CombatCatalog(new ObjectMapper()).monsters.get("WOLF"),hero.mapId,800,540);}
    Map<String,Object> state(List<?> monsters){var s=new LinkedHashMap<String,Object>();s.put("mapId",hero.mapId);s.put("instanceId",hero.instanceId);s.put("players",List.of(hero.view()));s.put("monsters",monsters);s.put("bots",List.of());s.put("loot",List.of());s.put("serverTime",1000);s.put("tick",1);return s;}
    @Test void boundaryIsDeterministicAndOnlyChangedEntitiesAreSent(){
        assertTrue(interest.visible(hero,1060,540));assertFalse(interest.visible(hero,1060.1,540));assertEquals(1,interest.snapshot("socket",state(List.of(wolf.view()))));
        var unchanged=interest.delta("socket",state(List.of(wolf.view())));assertEquals(1L,unchanged.get("baseInterestVersion"));assertTrue(((List<?>)((Map<?,?>)unchanged.get("monsters")).get("upsert")).isEmpty());
        wolf.hp-=10;hero.x+=12;var changed=interest.delta("socket",state(List.of(wolf.view())));assertEquals(3L,changed.get("interestVersion"));assertEquals(1,((List<?>)((Map<?,?>)changed.get("players")).get("upsert")).size());assertEquals(1,((List<?>)((Map<?,?>)changed.get("monsters")).get("upsert")).size());
        var left=interest.delta("socket",state(List.of()));assertEquals(List.of(wolf.id.toString()),((Map<?,?>)left.get("monsters")).get("remove"));
    }
    @Test void scopeChangesRequireSnapshotAndOldSocketCleanupCannotRemoveNewCache(){
        interest.snapshot("old",state(List.of(wolf.view())));interest.snapshot("new",state(List.of(wolf.view())));interest.forget("old");assertEquals(1,interest.cachedConnections());assertNotNull(interest.delta("new",state(List.of(wolf.view()))));hero.instanceId=UUID.randomUUID();assertNull(interest.delta("new",state(List.of())));interest.snapshot("new",state(List.of()));assertNotNull(interest.delta("new",state(List.of())));interest.forget("new");assertEquals(0,interest.cachedConnections());
    }
    @Test void eventPositionUsesActualTargetCoordinates(){
        var point=WorldInterest.point(Map.of("source",hero.id,"target",wolf.id,"x",900,"y",540));assertEquals(900,point.x());assertEquals(540,point.y());assertEquals(800,WorldInterest.point(wolf.view()).x());assertNull(WorldInterest.point(Map.of("message","map chat")));
    }
}
