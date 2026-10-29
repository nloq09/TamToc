package vn.tamtoc.dungeon;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.util.*;
import java.io.IOException;
import vn.tamtoc.world.MapCatalog;
import vn.tamtoc.combat.CombatCatalog;

@Component
public class DungeonCatalog {
    public record Entry(String mapId,double x,double radius) {}
    public record Definition(String id,String name,String mapId,int minLevel,long cooldownMs,List<Entry> entries,List<List<String>> waves,int rewardExp,int rewardCultivation,int rewardStones) {}
    private final Map<String,Definition> definitions=new LinkedHashMap<>();
    public final MapCatalog maps;
    @org.springframework.beans.factory.annotation.Autowired public DungeonCatalog(ObjectMapper json,MapCatalog maps,CombatCatalog combat)throws IOException{this.maps=maps;try(var in=new ClassPathResource("dungeons.json").getInputStream()){for(var d:json.readValue(in,Definition[].class)){if(definitions.putIfAbsent(d.id,d)!=null||d.minLevel<1||d.minLevel>25||d.cooldownMs<0||d.waves.size()!=3||!maps.get(d.mapId).mapType().equals("DUNGEON"))throw new IllegalArgumentException("Invalid dungeon "+d.id);for(var entry:d.entries)if(!MapCatalog.validPosition(maps.get(entry.mapId),entry.x,maps.get(entry.mapId).ground())||entry.radius<=0)throw new IllegalArgumentException("Invalid dungeon entry "+d.id);for(var wave:d.waves)for(String id:wave){var group=group(d,id);if(!group.enabled()||!combat.monsters.containsKey(group.definitionId()))throw new IllegalArgumentException("Disabled/missing dungeon group "+id);}}}}
    private DungeonCatalog(int min){maps=null;definitions.put("HUYEN_MOC",new Definition("HUYEN_MOC","Huyền Mộc Bí Cảnh","huyen-moc-party",min,0,List.of(new Entry("trung-chau",350,150)),List.of(List.of("WOLF","WOLF","WOLF"),List.of("SOLDIER","SPIRIT","SOLDIER"),List.of("BOSS")),500,150,100));}
    public static DungeonCatalog legacy(int min){return new DungeonCatalog(min);}
    public Definition get(String id){var d=definitions.get(id);if(d==null)throw new vn.tamtoc.realtime.ProtocolException("DUNGEON_NOT_FOUND","Phó bản không tồn tại.");return d;}
    public Collection<Definition> all(){return List.copyOf(definitions.values());}
    public MapCatalog.SpawnGroup group(Definition d,String id){return maps.get(d.mapId).monsterSpawnGroups().stream().filter(g->g.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Missing dungeon group "+id));}
}
