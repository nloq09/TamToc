package vn.tamtoc.world;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/world")
public class WorldMetadataController {
    private final MapCatalog maps;private final vn.tamtoc.combat.CombatCatalog combat;private final vn.tamtoc.content.MapContentRegistry content;private final com.fasterxml.jackson.databind.ObjectMapper json;
    public WorldMetadataController(MapCatalog maps,vn.tamtoc.combat.CombatCatalog combat,vn.tamtoc.content.MapContentRegistry content,com.fasterxml.jackson.databind.ObjectMapper json){this.maps=maps;this.combat=combat;this.content=content;this.json=json;}
    @GetMapping("/maps") public Object list(){return content.summaries();}
    @GetMapping("/maps/{mapId}") public Object map(@PathVariable String mapId){var data=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(maps.get(content.runtimeId(mapId)));data.remove(List.of("navigation","waypoints","botSpawnGroups"));return data;}
    @GetMapping("/maps/{mapId}/monsters") public Object mapMonsters(@PathVariable String mapId){return content.monsters(mapId);}
    @GetMapping("/monsters/{monsterId}/locations") public Object locations(@PathVariable String monsterId){return content.locations(monsterId);}
    @GetMapping("/monsters") public Object monsters(){return combat.monsters.values().stream().map(m->Map.of("id",m.id(),"name",m.name(),"level",m.level(),"tier",m.tier())).toList();}
}
