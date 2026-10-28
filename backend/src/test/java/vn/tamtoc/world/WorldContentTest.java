package vn.tamtoc.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import vn.tamtoc.progression.*;
import vn.tamtoc.combat.*;
import vn.tamtoc.character.*;

class WorldContentTest {
    @Test void allRegionsAndLevelOneToTenContentHaveValidReferences()throws Exception{
        var json=new ObjectMapper();var maps=new MapCatalog(json);var combat=new CombatCatalog(json);var progress=new ProgressionCatalog(json);new ProgressionRules(progress,maps).validateWorldData(maps,combat);
        for(String id:List.of("thanh-van","u-minh","van-yeu","trung-chau","thanh-van-ngoai-son","huyet-vu-coc","linh-thu-son-lo","tam-gioi-bien-canh","thien-mon-di-tich","huyen-moc-party"))assertTrue(maps.contains(id));
        assertTrue(maps.get("trung-chau").portals().stream().anyMatch(p->p.target().equals("tam-gioi-bien-canh")));assertTrue(maps.get("tam-gioi-bien-canh").portals().stream().anyMatch(p->p.target().equals("thien-mon-di-tich")&&p.requiredLevel()==10));assertTrue(progress.quests.values().stream().anyMatch(q->q.requiredLevel()==1&&q.mapId()!=null));assertTrue(progress.quests.values().stream().anyMatch(q->q.requiredLevel()==10&&q.mapId()!=null));assertEquals(1800,combat.monsters.get("BOSS").hp());
    }
    @Test void regionQuestStartsAtAcceptanceAndCannotUseKillsFromAnotherMap()throws Exception{
        var json=new ObjectMapper();var maps=new MapCatalog(json);var catalog=new ProgressionCatalog(json);var tree=(ArrayNode)json.valueToTree(maps.all());((ObjectNode)tree.get(0).get("npcSpawns").get(0)).put("x",600);maps=new MapCatalog(Arrays.asList(json.treeToValue(tree,MapCatalog.MapDefinition[].class)));var rules=new ProgressionRules(catalog,maps);var p=new PlayerState(new GameCharacter(UUID.randomUUID(),"QuestTester",Race.HUMAN));p.x=600;p.y=540;String key="thanh-van-ngoai-son:WOLF";p.progress.killsByDefinition.put(key,20);rules.accept(p,"OUTER_WOLVES");assertEquals(0,rules.questProgress(p,"OUTER_WOLVES"));p.progress.killsByDefinition.put("u-minh:WOLF",100);assertEquals(0,rules.questProgress(p,"OUTER_WOLVES"));p.progress.killsByDefinition.put(key,21);assertEquals(1,rules.questProgress(p,"OUTER_WOLVES"));assertThrows(IllegalArgumentException.class,()->rules.claim(p,"OUTER_WOLVES"));p.progress.killsByDefinition.put(key,24);rules.claim(p,"OUTER_WOLVES");long exp=p.exp,cult=p.cultivation;int stones=p.progress.stones;assertThrows(IllegalArgumentException.class,()->rules.claim(p,"OUTER_WOLVES"));assertEquals(exp,p.exp);assertEquals(cult,p.cultivation);assertEquals(stones,p.progress.stones);
    }
}
