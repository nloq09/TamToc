package vn.tamtoc.world;

import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/world")
public class WorldMetadataController {
    private final MapCatalog maps;private final vn.tamtoc.combat.CombatCatalog combat;
    public WorldMetadataController(MapCatalog maps,vn.tamtoc.combat.CombatCatalog combat){this.maps=maps;this.combat=combat;}
    @GetMapping("/maps") public Object list(){return maps.all().stream().map(m->Map.of("id",m.id(),"name",m.name(),"regionId",m.regionId(),"mapType",m.mapType(),"recommendedLevelMin",m.recommendedLevelMin(),"recommendedLevelMax",m.recommendedLevelMax(),"version",m.version(),"portals",m.portals())).toList();}
    @GetMapping("/maps/{mapId}") public Object map(@PathVariable String mapId){return maps.get(mapId);}
    @GetMapping("/monsters") public Object monsters(){return combat.monsters.values().stream().map(m->Map.of("id",m.id(),"name",m.name(),"level",m.level(),"tier",m.tier())).toList();}
}
