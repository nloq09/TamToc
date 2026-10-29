package vn.tamtoc.content;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import vn.tamtoc.world.MapCatalog;
import vn.tamtoc.combat.CombatCatalog;
import vn.tamtoc.progression.ProgressionCatalog;

@Component
public class MapContentRegistry {
    public final WorldContentPlan plan;
    private final MapCatalog maps;
    private final JsonNode bindings;
    public MapContentRegistry(ObjectMapper json,MapCatalog maps)throws IOException{this(json,maps,new CombatCatalog(json),new ProgressionCatalog(json));}
    @org.springframework.beans.factory.annotation.Autowired public MapContentRegistry(ObjectMapper json,MapCatalog maps,CombatCatalog combat,ProgressionCatalog quests)throws IOException{
        this.maps=maps;try(var in=new ClassPathResource("world-content-15-v1.json").getInputStream()){plan=new WorldContentPlan(json.readTree(in));}
        try(var in=new ClassPathResource("world15-bindings.json").getInputStream()){bindings=json.readTree(in);}
        Set<String> ids=new HashSet<>();bindings.path("mapIds").fieldNames().forEachRemaining(ids::add);if(!ids.equals(WorldContentPlan.MAP_IDS))throw new IllegalArgumentException("Map bindings must cover exactly 15 content IDs");
        Set<String> runtime=new HashSet<>();bindings.path("mapIds").elements().forEachRemaining(n->{if(!runtime.add(n.asText()))throw new IllegalArgumentException("Duplicate runtime map binding");});
        if(!bindings.path("verifiedMaps").isEmpty())validateActivation(combat,quests);
    }
    private void validateActivation(CombatCatalog combat,ProgressionCatalog quests){
        for(var row:plan.maps.values()){String id=row.path("mapId").asText();if(!maps.contains(runtimeId(id)))throw new IllegalArgumentException("Verified map missing geometry "+id);if(row.path("safe").asBoolean()&&!maps.get(runtimeId(id)).monsterSpawnGroups().isEmpty())throw new IllegalArgumentException("Safe town contains combat "+id);}
        for(var entries:List.of(plan.spawns,plan.bosses))for(var row:entries.values()){String key=row.path(row.has("placementId")?"placementId":"spawnGroupId").asText();var b=bindings.path(row.has("placementId")?"bossBindings":"spawnBindings").path(key);var map=maps.get(runtimeId(row.path("mapId").asText()));var g=map.monsterSpawnGroups().stream().filter(s->s.id().equals(key)).findFirst().orElseThrow(()->new IllegalArgumentException("Missing runtime group "+key));var m=combat.monsters.get(g.definitionId());if(!b.path("geometryVerified").asBoolean()||m==null||!m.speciesId().equals(row.path("monsterId").asText())||g.enabled()!=b.path("enabled").asBoolean()||g.maxAlive()>row.path("maxAlive").asInt())throw new IllegalArgumentException("Invalid activation "+key);if(row.has("placementId")&&!key.equals(g.placementId()))throw new IllegalArgumentException("Placement ID mismatch "+key);}
        for(var c:plan.connections.values()){var b=bindings.path("connectionBindings").path(c.path("connectionId").asText());var map=maps.get(runtimeId(c.path("fromMapId").asText()));if(!b.path("geometryVerified").asBoolean()||map.portals().stream().noneMatch(p->p.portalId().equals(b.path("sourcePortalId").asText())&&p.target().equals(runtimeId(c.path("toMapId").asText()))))throw new IllegalArgumentException("Unverified connection "+c.path("connectionId"));}
        for(var q:plan.quests.values()){var b=bindings.path("questBindings").path(q.path("questId").asText());if(!quests.quests.containsKey(b.path("questId").asText()))throw new IllegalArgumentException("Missing reviewed quest binding "+q.path("questId"));}
    }
    public String runtimeId(String id){return bindings.path("mapIds").path(id).asText(id);}
    public String contentId(String runtime){for(var entry:plan.maps.keySet())if(runtimeId(entry).equals(runtime))return entry;return runtime;}
    public boolean archived(String id){return plan.archived.contains(id);}
    public JsonNode bindings(){return bindings.deepCopy();}
    public List<Map<String,Object>> monsters(String mapId){String id=contentId(runtimeId(mapId));if(!plan.maps.containsKey(id))throw new IllegalArgumentException("Map ngoài catalog 15 vùng.");var result=new ArrayList<Map<String,Object>>();for(var rows:List.of(plan.spawns,plan.bosses))for(var row:rows.values())if(id.equals(row.path("mapId").asText())){String key=row.path(row.has("placementId")?"placementId":"spawnGroupId").asText();var bind=bindings.path(row.has("placementId")?"bossBindings":"spawnBindings").path(key);var out=new LinkedHashMap<String,Object>();out.put("monsterId",row.path("monsterId").asText());out.put("name",plan.monsters.get(row.path("monsterId").asText()).path("name").asText());out.put("tier",row.path("tier").asText());out.put("spawnGroupId",key);out.put("placementId",row.path("placementId").isMissingNode()?null:key);out.put("levelMin",row.path("levelMin").asInt(row.path("level").asInt()));out.put("levelMax",row.path("levelMax").asInt(row.path("level").asInt()));out.put("maxAlive",row.path("maxAlive").asInt());out.put("enabled",bind.path("enabled").asBoolean());out.put("status",bind.path("enabled").asBoolean()?"ENABLED":"DISABLED_REQUIRES_SCHEDULER");result.add(out);}return result;}
    public List<Map<String,Object>> locations(String monsterId){return plan.maps.keySet().stream().flatMap(id->monsters(id).stream().filter(m->m.get("monsterId").equals(monsterId)).map(m->{var out=new LinkedHashMap<>(m);out.put("mapId",id);out.put("runtimeMapId",runtimeId(id));out.put("mapName",plan.maps.get(id).path("name").asText());return (Map<String,Object>)out;})).toList();}
    public List<Map<String,Object>> summaries(){return plan.maps.values().stream().map(row->{String canonical=row.path("mapId").asText(),id=runtimeId(canonical);Map<String,Object> out=new LinkedHashMap<>();out.put("id",id);out.put("mapId",canonical);out.put("runtimeMapId",id);out.put("name",row.path("name").asText());out.put("contentVersion",WorldContentPlan.VERSION);out.put("recommendedLevelMin",row.path("recommendedLevelMin").asInt());out.put("recommendedLevelMax",row.path("recommendedLevelMax").asInt());out.put("regionId",row.path("region").asText());out.put("progressionRole",row.path("progressionRole").asText());out.put("enabled",maps.contains(id));out.put("contentStatus",maps.contains(id)?"RUNTIME_AVAILABLE":"PLANNED_DISABLED");out.put("mapType",row.path("safe").asBoolean()?"TOWN":row.path("type").asText());out.put("portals",maps.contains(id)?maps.get(id).portals():List.of());out.put("version",maps.contains(id)?maps.get(id).version():0);return out;}).toList();}
    public Map<String,Object> audit(){var out=new LinkedHashMap<String,Object>();out.put("contentVersion",WorldContentPlan.VERSION);out.put("catalogMaps",plan.maps.size());out.put("runtimeMapped",plan.maps.keySet().stream().filter(id->maps.contains(runtimeId(id))).count());out.put("archivedPlans",plan.archived.size());out.put("monsterDefinitions",plan.monsters.size());out.put("spawnGroups",plan.spawns.size());out.put("bossPlacements",plan.bosses.size());out.put("questSeeds",plan.quests.size());out.put("plannedConnections",plan.connections.size());out.put("unconfiguredMaps",plan.maps.keySet().stream().filter(id->!maps.contains(runtimeId(id))).toList());return out;}
}
