package vn.tamtoc.content;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** The supplied proposal is immutable input; runtime activation is reviewed separately. */
public final class WorldContentPlan {
    public static final String VERSION="world_catalog_15_v1";
    public static final Set<String> MAP_IDS=Set.of("thanh_van_thanh","u_minh_thanh","van_yeu_lam","thanh_van_ngoai_son","huyet_vu_coc","linh_thu_son_lo","truc_anh_lam","u_minh_ngoai_dao","thanh_khau_truc_lam","trung_chau","tam_gioi_bien_canh","vo_chu_hoang_nguyen","thien_mon_di_tich","huyen_moc_bi_canh","cuu_u_thach_phu");
    public final Map<String,JsonNode> maps,monsters,spawns,bosses,connections,quests,stories;
    public final Set<String> archived;
    public WorldContentPlan(JsonNode root){
        require(VERSION.equals(root.path("metadata").path("contentVersion").asText()),"contentVersion");
        maps=index(root,"maps","mapId");monsters=index(root,"monsterDefinitions","monsterId");spawns=index(root,"monsterSpawnGroups","spawnGroupId");bosses=index(root,"bossPlacements","placementId");connections=index(root,"plannedConnections","connectionId");quests=index(root,"questSeeds","questId");stories=index(root,"storyBeats","storyBeatId");
        require(maps.keySet().equals(MAP_IDS)&&root.path("metadata").path("intendedMapCount").asInt()==15,"exactly the 15 scoped map IDs required");
        Set<String> archivedIds=new LinkedHashSet<>();for(var id:root.path("archivedPlannedMapIds")){require(validId(id.asText())&&archivedIds.add(id.asText())&&!maps.containsKey(id.asText()),"duplicate/active archived map "+id);}
        require(archivedIds.size()==57,"57 archived plans required");archived=Set.copyOf(archivedIds);
        for(var m:maps.values()){
            String id=m.path("mapId").asText();int min=m.path("recommendedLevelMin").asInt(),max=m.path("recommendedLevelMax").asInt();require(min>=1&&max>=min&&(m.path("safe").asBoolean()?max<=100:max<=25),"map level range "+id);
            Set<String> expected=new HashSet<>();spawns.values().stream().filter(s->id.equals(s.path("mapId").asText())).forEach(s->expected.add(s.path("spawnGroupId").asText()));require(strings(m.path("spawnGroupIds")).equals(expected),"map spawn references "+id);
            Set<String> bossIds=new HashSet<>();bosses.values().stream().filter(b->id.equals(b.path("mapId").asText())).forEach(b->bossIds.add(b.path("monsterId").asText()));require(strings(m.path("bossIds")).equals(bossIds),"map boss references "+id);
            if(m.path("safe").asBoolean())require(expected.isEmpty()&&bossIds.isEmpty(),"safe town combat spawn "+id);
        }
        for(var s:spawns.values()){references(s);require(s.path("maxAlive").asInt()>0&&s.path("maxAlive").asInt()<=100&&s.path("levelMin").asInt()>=1&&s.path("levelMax").asInt()>=s.path("levelMin").asInt()&&s.path("levelMax").asInt()<=25,"spawn range/count "+s);require(!s.path("enabled").asBoolean(),"proposal spawn must remain disabled pending runtime verification");require(s.path("respawn").path("seconds").asInt()>0||s.path("respawn").path("policy").asText().equals("ENCOUNTER_WAVE"),"respawn policy/seconds");zoneReference(s);}
        for(var b:bosses.values()){references(b);require(b.path("maxAlive").asInt()==1&&b.path("level").asInt()>=1&&b.path("level").asInt()<=25,"boss level/count");require(monsters.get(b.path("monsterId").asText()).path("tier").asText().equals(b.path("tier").asText()),"boss tier reference");require(!b.path("enabled").asBoolean(),"proposal boss must remain disabled");zoneReference(b);String tier=b.path("tier").asText(),policy=b.path("spawnPolicy").path("policy").asText();require(!tier.equals("WORLD_BOSS")||policy.equals("SCHEDULED_EVENT"),"world boss needs scheduler");require(!tier.equals("DUNGEON_BOSS")||policy.equals("DUNGEON_ENCOUNTER"),"dungeon boss needs encounter");}
        for(var c:connections.values()){require(maps.containsKey(c.path("fromMapId").asText())&&maps.containsKey(c.path("toMapId").asText())&&!c.path("fromMapId").equals(c.path("toMapId")),"connection map reference");require(!c.path("enabled").asBoolean(),"proposal portal requires geometry review");}
        for(var q:quests.values()){require(maps.containsKey(q.path("mapId").asText())&&!q.path("enabled").asBoolean(),"quest map/enabled "+q.path("questId"));require(q.path("objectives").isArray()&&!q.path("objectives").isEmpty(),"quest objectives");for(var o:q.path("objectives")){require(o.path("count").asInt()>0,"objective count");switch(o.path("type").asText()){case "KILL_MONSTER"->require(monsters.containsKey(o.path("targetId").asText()),"quest monster reference");case "DEFEAT_BOSS_PLACEMENT"->require(bosses.containsKey(o.path("targetId").asText())&&bosses.get(o.path("targetId").asText()).path("monsterId").asText().equals(o.path("monsterId").asText()),"quest placement reference");case "CLEAR_DUNGEON_WAVE"->require(maps.containsKey(o.path("targetId").asText())&&maps.get(o.path("targetId").asText()).path("type").asText().equals("DUNGEON"),"quest dungeon reference");case "DISCOVER_MAP"->{}default->throw new IllegalArgumentException("Invalid world content: objective type");}}}
        for(var s:stories.values())require(maps.containsKey(s.path("mapId").asText())&&!s.path("enabled").asBoolean(),"story draft reference/enabled");
    }
    private void references(JsonNode n){require(maps.containsKey(n.path("mapId").asText())&&monsters.containsKey(n.path("monsterId").asText()),"map/monster reference "+n);require(!maps.get(n.path("mapId").asText()).path("safe").asBoolean(),"combat in safe town");}
    private void zoneReference(JsonNode n){require(strings(maps.get(n.path("mapId").asText()).path("spawnZoneRefs")).contains(n.path("spawnZoneId").asText()),"spawn zone reference");}
    private static Set<String> strings(JsonNode rows){Set<String> result=new HashSet<>();for(var row:rows)require(result.add(row.asText()),"duplicate reference "+row);return result;}
    private static Map<String,JsonNode> index(JsonNode root,String field,String key){require(root.path(field).isArray(),"missing list "+field);Map<String,JsonNode> result=new LinkedHashMap<>();for(var row:root.path(field)){String id=row.path(key).asText();require(validId(id)&&result.putIfAbsent(id,row.deepCopy())==null,"duplicate/invalid "+key+" "+id);}return Collections.unmodifiableMap(result);}
    private static boolean validId(String id){return id!=null&&id.matches("[a-z0-9_]{1,100}");}
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalArgumentException("Invalid world content: "+reason);}
}
