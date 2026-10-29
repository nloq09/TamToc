package vn.tamtoc.content;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import vn.tamtoc.world.MapCatalog;

class WorldContentPlanTest {
    final ObjectMapper json=new ObjectMapper();
    JsonNode source()throws Exception{try(var in=new ClassPathResource("world-content-15-v1.json").getInputStream()){return json.readTree(in);}}
    @Test void originalPlanHasExactly15MapsAnd57ArchivedWithAllReferencesValidated()throws Exception{var p=new WorldContentPlan(source());assertEquals(15,p.maps.size());assertEquals(57,p.archived.size());assertEquals(34,p.monsters.size());assertEquals(31,p.spawns.size());assertEquals(12,p.bosses.size());assertEquals(38,p.quests.size());}
    @Test void rejectsDuplicateIdsAndArchivedContentEnteringActiveScope()throws Exception{var a=(ObjectNode)source();((ArrayNode)a.get("maps")).add(a.get("maps").get(0).deepCopy());assertThrows(IllegalArgumentException.class,()->new WorldContentPlan(a));var b=(ObjectNode)source();((ArrayNode)b.get("archivedPlannedMapIds")).set(0,TextNode.valueOf("thanh_van_thanh"));assertThrows(IllegalArgumentException.class,()->new WorldContentPlan(b));}
    @Test void rejectsUnsafeActivationOrMissingGeometryReferences()throws Exception{var a=(ObjectNode)source();((ObjectNode)a.get("monsterSpawnGroups").get(0)).put("enabled",true);assertThrows(IllegalArgumentException.class,()->new WorldContentPlan(a));var b=(ObjectNode)source();((ObjectNode)b.get("bossPlacements").get(0)).put("spawnZoneId","missing");assertThrows(IllegalArgumentException.class,()->new WorldContentPlan(b));}
    @Test void rejectsDanglingQuestAndCombatInTown()throws Exception{var a=(ObjectNode)source();((ObjectNode)a.get("questSeeds").get(4).get("objectives").get(0)).put("targetId","missing");assertThrows(IllegalArgumentException.class,()->new WorldContentPlan(a));var b=(ObjectNode)source();((ObjectNode)b.get("monsterSpawnGroups").get(0)).put("mapId","thanh_van_thanh");assertThrows(IllegalArgumentException.class,()->new WorldContentPlan(b));}
    @Test void aliasesPreserveDeployedMapIdsAndArenasAreOutsideCatalog()throws Exception{var r=new MapContentRegistry(json,new MapCatalog(json));assertEquals("thanh-van",r.runtimeId("thanh_van_thanh"));assertEquals("thanh_van_thanh",r.contentId("thanh-van"));assertEquals(15,r.summaries().size());assertTrue(r.summaries().stream().noneMatch(m->m.get("id").equals("training-arena")));assertTrue(r.archived("linh_tuyen_khe"));}
}
