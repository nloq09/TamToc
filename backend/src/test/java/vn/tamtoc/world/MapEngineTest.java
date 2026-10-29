package vn.tamtoc.world;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import vn.tamtoc.character.*;
import vn.tamtoc.combat.*;
import vn.tamtoc.realtime.ProtocolException;

class MapEngineTest {
    ObjectMapper json=new ObjectMapper();MapCatalog maps;PlayerState p;
    @BeforeEach void setup()throws Exception{maps=new MapCatalog(json);p=new PlayerState(new GameCharacter(UUID.randomUUID(),"MapTester",Race.HUMAN));}
    MapCatalog mutated(java.util.function.Consumer<ArrayNode> change)throws Exception{var tree=(ArrayNode)json.valueToTree(maps.all());change.accept(tree);return new MapCatalog(Arrays.asList(json.treeToValue(tree,MapCatalog.MapDefinition[].class)));}
    @Test void rejectsDuplicateReferencesUnsafeSpawnsAndMalformedGeometry(){
        assertThrows(IllegalArgumentException.class,()->mutated(a->((ObjectNode)a.get(1)).put("id","thanh-van")));
        assertThrows(IllegalArgumentException.class,()->mutated(a->((ObjectNode)a.get(0).get("portals").get(0)).put("target","missing")));
        assertThrows(IllegalArgumentException.class,()->mutated(a->((ObjectNode)a.get(0).get("portals").get(0)).put("targetSpawnId","missing")));
        assertThrows(IllegalArgumentException.class,()->mutated(a->((ObjectNode)a.get(0).get("portals").get(1)).put("portalId","thanh-van-portal-0")));
        assertThrows(IllegalArgumentException.class,()->mutated(a->((ObjectNode)a.get(6).get("spawnPoints").get(0)).put("x",1460)));
        assertThrows(IllegalArgumentException.class,()->mutated(a->((ObjectNode)a.get(6).get("obstacles").get(0)).put("width",-1)));
        assertThrows(IllegalArgumentException.class,()->mutated(a->((ObjectNode)a.get(6).get("navigation").get(0)).put("to","missing")));
        assertThrows(IllegalArgumentException.class,()->mutated(a->((ObjectNode)a.get(6).get("monsterSpawnGroups").get(0)).putNull("id")));
        assertThrows(IllegalArgumentException.class,()->mutated(a->{var g=(ObjectNode)a.get(6).get("monsterSpawnGroups").get(0);g.put("startX",1400);g.put("endX",1544);g.put("maxAlive",3);})); // middle slot intersects the wall
    }
    @Test void validatesPortalPositionLevelQuestAndActivity(){
        p.mapId="thanh-van-ngoai-son";p.x=3030;p.y=540;var portal=maps.portal(p,"ngoai-son-to-center");
        assertEquals("PORTAL_LEVEL_REQUIRED",assertThrows(ProtocolException.class,()->maps.validatePortal(p,portal)).code());p.level=6;assertEquals(650,maps.validatePortal(p,portal).x());
        p.x=1700;assertEquals("PORTAL_TOO_FAR",assertThrows(ProtocolException.class,()->maps.validatePortal(p,portal)).code());p.x=3030;p.instanceId=UUID.randomUUID();assertThrows(ProtocolException.class,()->maps.validatePortal(p,portal));p.instanceId=null;
        var quest=new MapCatalog.Portal(portal.x(),portal.target(),portal.label(),portal.portalId(),portal.sourceMapId(),portal.triggerBounds(),portal.targetSpawnId(),1,"STARTER",true);
        assertEquals("PORTAL_QUEST_REQUIRED",assertThrows(ProtocolException.class,()->maps.validatePortal(p,quest)).code());p.progress.quests.put("STARTER","CLAIMED");assertNotNull(maps.validatePortal(p,quest));
        var disabled=new MapCatalog.Portal(portal.x(),portal.target(),portal.label(),portal.portalId(),portal.sourceMapId(),portal.triggerBounds(),portal.targetSpawnId(),1,null,false);assertEquals("PORTAL_DISABLED",assertThrows(ProtocolException.class,()->maps.validatePortal(p,disabled)).code());
    }
    @Test void wallsBlockWalkingAndDashButCanBeJumped()throws Exception{
        var map=maps.get("thanh-van-ngoai-son");p.mapId=map.id();p.x=1400;p.y=540;p.grounded=true;p.direction=1;
        for(int i=0;i<20;i++)Movement.step(p,map,.05);assertEquals(1434,p.x,.01);
        new CombatEngine(new CombatCatalog(json)).cast(p,"DASH",map,List.of(),1000,(t,e)->{},(a,b)->{});assertEquals(1434,p.x,.01);
        p.jumpQueued=true;for(int i=0;i<40;i++)Movement.step(p,map,.05);assertTrue(p.x>1510);assertEquals(540,p.y,.01);
        assertFalse(MapCollision.lineOfSight(map,1400,520,1550,520));assertTrue(MapCollision.lineOfSight(map,1400,400,1550,400));
    }
    @Test void checkpointsAndMapVersionRepairInvalidSavedLocations()throws Exception{
        p.mapId="thanh-van-ngoai-son";p.progress.checkpoints.put(p.mapId,"camp");p.x=1460;p.y=530;maps.sanitize(p);assertEquals(1970,p.x);assertEquals(540,p.y);assertEquals(maps.get(p.mapId).version(),p.progress.mapVersions.get(p.mapId));
        var data=json.readValue(json.writeValueAsString(p.progress),vn.tamtoc.progression.ProgressData.class);assertEquals("camp",data.checkpoints.get(p.mapId));
        p.mapId="removed-map";maps.sanitize(p);assertEquals("thanh-van",p.mapId);assertEquals(160,p.x);
        p.mapId="duel-arena";maps.sanitize(p);assertEquals("thanh-van",p.mapId);
    }
    @Test void solidCeilingStopsUpwardMotionAndPlatformsStayOneWay(){
        var map=maps.get("thanh-van-ngoai-son");p.x=650;p.y=500;p.vy=-400;p.grounded=false;Movement.step(p,map,.05);assertTrue(p.y<500); // one-way platform permits ascent
        p.x=1434;p.y=540;assertFalse(MapCollision.walkable(map,p.x,p.y,1450,16,44));assertTrue(MapCollision.walkable(map,900,540,910,16,44));
    }
}
