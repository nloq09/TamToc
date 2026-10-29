package vn.tamtoc.dungeon;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import vn.tamtoc.world.*;
import vn.tamtoc.combat.*;
import vn.tamtoc.character.*;
import vn.tamtoc.party.*;
import vn.tamtoc.instance.*;
import vn.tamtoc.realtime.ProtocolException;
import vn.tamtoc.progression.ProgressData;

class World15DungeonTest {
    ObjectMapper json=new ObjectMapper();DungeonCatalog catalog;DungeonService service;DungeonTest.World world;PartyRegistry parties;long now;
    @BeforeEach void setup()throws Exception{var combat=new CombatCatalog(json);catalog=new DungeonCatalog(json,new MapCatalog(json),combat);world=new DungeonTest.World();parties=new PartyRegistry(4,60000);service=new DungeonService(new InstanceRegistry(),parties,combat,new DungeonTest.Store(),10,900000,60000,catalog);now=System.currentTimeMillis();}
    UUID player(String map,int level){var p=new PlayerState(new GameCharacter(UUID.randomUUID(),"Dungeon15",Race.HUMAN));p.level=level;p.mapId=map;p.x=map.equals("trung-chau")?350:3450;p.y=540;p.lastActiveAt=now;world.players.put(p.id,p);return p.id;}
    UUID[] pair(String map,int level){UUID a=player(map,level),b=player(map,level);var i=parties.invite(a,b,id->true,now);parties.accept(b,i.id(),id->true,now);return new UUID[]{a,b};}
    DungeonRun start(String id,UUID[] pair){for(UUID p:pair)service.ready(p,true,world,id);return service.start(pair[0],id,world,now);}
    @Test void realCatalogRequiresTenAndEighteenAndOnlyEncounterSpawnsBosses(){assertEquals(10,catalog.get("HUYEN_MOC").minLevel());assertEquals(18,catalog.get("CUU_U").minLevel());var pair=pair("thien-mon-di-tich",17);assertEquals("DUNGEON_LEVEL_REQUIRED",assertThrows(ProtocolException.class,()->service.ready(pair[0],true,world,"CUU_U")).code());}
    @Test void bothDefinitionsUseFourFourBossWavesAndIndependentPlacements(){var h=start("HUYEN_MOC",pair("trung-chau",25));var c=start("CUU_U",pair("thien-mon-di-tich",25));assertEquals(4,h.instance.monsters.size());assertEquals(4,c.instance.monsters.size());assertNotEquals(h.instance.id,c.instance.id);assertTrue(c.instance.monsters.stream().allMatch(m->m.definition.speciesId().equals("u_hon_thach_ve")));for(int i=1;i<=2;i++){h.instance.monsters.forEach(m->m.hp=0);c.instance.monsters.forEach(m->m.hp=0);service.step(world,now+i*100);}assertEquals(1,h.instance.monsters.size());assertEquals(15,h.instance.monsters.get(0).definition.level());assertEquals("huyen_moc_bi_canh_boss_dungeon_boss",h.instance.monsters.get(0).placementId);assertEquals(25,c.instance.monsters.get(0).definition.level());assertEquals("cuu_u_thach_phu_boss_dungeon_boss",c.instance.monsters.get(0).placementId);assertEquals(2,h.instance.monsters.get(0).definition.phases().size());}
    @Test void completionGrantIsOnceAndCuuResetCooldownSurvivesProgressSerialization()throws Exception{var pair=pair("thien-mon-di-tich",25);var run=start("CUU_U",pair);for(int i=1;i<=3;i++){run.instance.monsters.forEach(m->m.hp=0);service.step(world,now+i*100);}service.step(world,now+450);assertEquals(DungeonRun.State.COMPLETED,run.state);assertEquals(1,world.grants.get(run.id));for(UUID id:pair){service.leave(id,world,now+500);var p=world.getPlayer(id);p.mapId="thien-mon-di-tich";p.x=3450;p.progress=json.readValue(json.writeValueAsString(p.progress),ProgressData.class);assertEquals(now+900000,p.progress.nextDungeonAt.get("CUU_U"));assertEquals("DUNGEON_RESET_COOLDOWN",assertThrows(ProtocolException.class,()->service.ready(id,true,world,"CUU_U")).code());}}
}
