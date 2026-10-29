package vn.tamtoc.content;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import vn.tamtoc.world.*;
import vn.tamtoc.combat.*;
import vn.tamtoc.character.*;
import vn.tamtoc.progression.*;

class World15ProgressionTest {
    ObjectMapper json=new ObjectMapper();MapCatalog maps;CombatCatalog combat;ProgressionCatalog quests;ProgressionRules rules;
    @BeforeEach void setup()throws Exception{maps=new MapCatalog(json);combat=new CombatCatalog(json);quests=new ProgressionCatalog(json);rules=new ProgressionRules(quests,maps);rules.validateWorldData(maps,combat);}
    PlayerState hero(){var p=new PlayerState(new GameCharacter(UUID.randomUUID(),"World15Tester",Race.HUMAN));p.x=300;p.y=540;return p;}
    @Test void reviewed15MapsHaveSafeTownsValidSlotsAndReachableRaceRoutes()throws Exception{
        var registry=new MapContentRegistry(json,maps);assertEquals(15,registry.summaries().size());assertEquals(15L,registry.audit().get("runtimeMapped"));
        for(String id:List.of("thanh-van","u-minh","van-yeu","trung-chau")){assertTrue(maps.get(id).monsterSpawnGroups().isEmpty());assertTrue(maps.get(id).safe(1400,540));}
        for(String start:List.of("thanh-van","u-minh","van-yeu")){Set<String> seen=new HashSet<>();Deque<String> pending=new ArrayDeque<>();pending.add(start);while(!pending.isEmpty()){String id=pending.remove();if(!seen.add(id))continue;maps.get(id).portals().stream().filter(p->p.enabled()&&p.requiredLevel()<=25).forEach(p->pending.add(p.target()));}assertTrue(seen.containsAll(List.of("trung-chau","tam-gioi-bien-canh","vo_chu_hoang_nguyen","thien-mon-di-tich")));}
        var population=new MonsterPopulation(maps,combat);assertTrue(population.all().stream().noneMatch(m->Set.of("TOWN","DUNGEON","ARENA").contains(maps.get(m.mapId).mapType())));assertTrue(population.all().stream().allMatch(m->MapCatalog.validPosition(maps.get(m.mapId),m.x,m.y)));
    }
    @Test void disabledSpawnCannotCreateEntities()throws Exception{var tree=(ArrayNode)json.valueToTree(maps.all());var row=(ObjectNode)tree.get(6).get("monsterSpawnGroups").get(0);String id=row.get("id").asText();row.put("enabled",false);var changed=new MapCatalog(Arrays.asList(json.treeToValue(tree,MapCatalog.MapDefinition[].class)));assertTrue(new MonsterPopulation(changed,combat).all().stream().noneMatch(m->m.spawnGroupId.equals(id)));}
    @Test void raceAndPrerequisitesGateNewQuestsAndDiscoveryRewardIsOnce(){var p=hero();assertThrows(IllegalArgumentException.class,()->rules.accept(p,"hunt_thanh_van_ngoai_son"));assertThrows(IllegalArgumentException.class,()->rules.accept(p,"guide_u_minh_thanh"));rules.discover(p,p.mapId);rules.accept(p,"guide_thanh_van_thanh");assertEquals(1,rules.questProgress(p,"guide_thanh_van_thanh"));rules.claim(p,"guide_thanh_van_thanh");int stones=p.progress.stones;assertThrows(IllegalArgumentException.class,()->rules.claim(p,"guide_thanh_van_thanh"));assertEquals(stones,p.progress.stones);rules.accept(p,"explore_thanh_van_ngoai_son");rules.discover(p,"thanh-van-ngoai-son");assertEquals(1,rules.questProgress(p,"explore_thanh_van_ngoai_son"));}
    @Test void placementQuestNeverUsesSameSpeciesAtWrongPlacement(){var p=hero();p.level=25;p.progress.quests.put("explore_huyen_moc_bi_canh","CLAIMED");String q="bounty_huyen_moc_bi_canh_boss_dungeon_boss";rules.accept(p,q);var group=maps.get("huyen-moc-party").monsterSpawnGroups().stream().filter(g->g.placementId()!=null).findFirst().orElseThrow();var boss=new MonsterState(combat.monsters.get(group.definitionId()),"huyen-moc-party",900,540);boss.placementId="different-placement";rules.killed(p,boss);assertEquals(0,rules.questProgress(p,q));boss.placementId=group.placementId();rules.killed(p,boss);assertEquals(1,rules.questProgress(p,q));}
    @Test void oldSavesAndArchivedMapFallbackKeepOrganizationsItemsAndClaimedRewards()throws Exception{var p=hero();p.mapId="linh_tuyen_khe";p.exp=72;p.cultivation=400;p.organization="THANH_VAN";p.path="KIEM_TU";p.progress.quests.put("WOLVES","CLAIMED");p.progress.inventory.put("QI_SWORD",1);maps.sanitize(p);assertEquals("thanh-van",p.mapId);assertEquals(72,p.exp);assertEquals("KIEM_TU",p.path);assertEquals(1,p.progress.inventory.get("QI_SWORD"));var restored=json.readValue(json.writeValueAsString(p.progress),ProgressData.class);assertEquals("CLAIMED",restored.quests.get("WOLVES"));p.mapId="thanh_van_thanh";maps.sanitize(p);assertEquals("thanh-van",p.mapId);p.level=25;ProgressionRules.addExp(p,100000);assertEquals(25,p.level);}
}
