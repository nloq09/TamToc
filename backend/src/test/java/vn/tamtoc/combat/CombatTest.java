package vn.tamtoc.combat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import vn.tamtoc.character.*;
import vn.tamtoc.world.*;

class CombatTest {
    CombatCatalog catalog; CombatEngine engine; PlayerState p; MapCatalog.MapDefinition map;
    @BeforeEach void setup() throws Exception {
        catalog=new CombatCatalog(new ObjectMapper());engine=new CombatEngine(catalog);
        p=new PlayerState(new GameCharacter(UUID.randomUUID(),"Tester",Race.HUMAN));p.x=800;p.y=540;
        map=new MapCatalog(new ObjectMapper()).get(p.mapId);
    }
    @Test void rejectsLockedSkillAndWrongRaceWithoutSpendingMana(){
        assertThrows(IllegalArgumentException.class,()->engine.cast(p,"KIEM_TU",map,List.of(),1000,(t,e)->{},(a,b)->{}));
        assertThrows(IllegalArgumentException.class,()->engine.cast(p,"DEMON_POWER",map,List.of(),1000,(t,e)->{},(a,b)->{}));
        assertEquals(60,p.progress.mp);
    }
    @Test void validatesCooldownManaFacingAndRange(){
        var near=new MonsterState(catalog.monsters.get("WOLF"),p.mapId,850,540);
        var behind=new MonsterState(catalog.monsters.get("WOLF"),p.mapId,740,540);
        var far=new MonsterState(catalog.monsters.get("WOLF"),p.mapId,1500,540);
        engine.cast(p,"QI",map,List.of(near,behind,far),1000,(t,e)->{},(a,b)->{});
        assertTrue(near.hp<80);assertEquals(80,behind.hp);assertEquals(80,far.hp);assertEquals(52,p.progress.mp);
        assertThrows(IllegalArgumentException.class,()->engine.cast(p,"QI",map,List.of(near),1100,(t,e)->{},(a,b)->{}));
        p.progress.mp=0;
        assertThrows(IllegalArgumentException.class,()->engine.cast(p,"QI",map,List.of(near),5000,(t,e)->{},(a,b)->{}));
    }
    @Test void deadMonsterCannotGrantRewardsAgain(){
        var m=new MonsterState(catalog.monsters.get("WOLF"),p.mapId,820,540);m.hp=1;
        int[] killed={0};
        engine.cast(p,"ATTACK",map,List.of(m),1000,(t,e)->{},(a,b)->killed[0]++);
        engine.cast(p,"ATTACK",map,List.of(m),2000,(t,e)->{},(a,b)->killed[0]++);
        assertEquals(1,killed[0]);assertEquals(MonsterState.Ai.DEAD,m.state);
    }
    @Test void bossEnragesAndRespawnsOnServer(){
        var m=new MonsterState(catalog.monsters.get("BOSS"),p.mapId,820,540);m.hp=500;
        new MonsterAi().step(m,List.of(p),.05,1000,(t,e)->{});assertEquals(2,m.phase);
        m.hp=0;m.respawnAt=2000;new MonsterAi().step(m,List.of(p),.05,2001,(t,e)->{});
        assertEquals(1800,m.hp);assertEquals(1,m.phase);
    }
}
