package vn.tamtoc.world;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.tamtoc.bot.BotState;
import vn.tamtoc.combat.*;

/** Per-connection interest state; immutable entity records make comparisons deterministic. */
@Component
public class WorldInterest {
    private final double radius,vertical;
    private static final List<String> KINDS=List.of("players","monsters","bots","loot");
    private record Frame(String map,Object instance,long version,Map<String,Map<String,Object>> entities) {}
    private final Map<String,Frame> frames=new java.util.concurrent.ConcurrentHashMap<>();
    public record Point(double x,double y) {}
    public WorldInterest(@Value("${game.interest-radius:900}")double radius,@Value("${game.interest-vertical:550}")double vertical){if(radius<400||vertical<100)throw new IllegalArgumentException("Invalid interest radius");this.radius=radius;this.vertical=vertical;}
    public boolean visible(PlayerState observer,double x,double y){return Math.abs(observer.x-x)<=radius&&Math.abs(observer.y-y)<=vertical;}
    public void forget(String connection){frames.remove(connection);}
    public int cachedConnections(){return frames.size();}
    private static String id(Object value){return switch(value){case PlayerState.View p->p.id().toString();case MonsterState.View m->m.id().toString();case BotState.View b->b.id().toString();case LootState l->l.id().toString();default->throw new IllegalArgumentException("Unsupported interest entity");};}
    private static Frame frame(Map<String,Object> state,long version){Map<String,Map<String,Object>> entities=new LinkedHashMap<>();for(String kind:KINDS){Map<String,Object> rows=new LinkedHashMap<>();for(Object value:(Collection<?>)state.get(kind))rows.put(id(value),value);entities.put(kind,Map.copyOf(rows));}return new Frame((String)state.get("mapId"),state.get("instanceId"),version,entities);}
    public long snapshot(String connection,Map<String,Object> state){var old=frames.get(connection);long version=old==null?1:old.version+1;frames.put(connection,frame(state,version));return version;}
    public Map<String,Object> delta(String connection,Map<String,Object> state){
        var previous=frames.get(connection);if(previous==null||!previous.map.equals(state.get("mapId"))||!Objects.equals(previous.instance,state.get("instanceId")))return null;
        var current=frame(state,previous.version+1);var payload=new LinkedHashMap<>(state);payload.put("baseInterestVersion",previous.version);payload.put("interestVersion",current.version);
        for(String kind:KINDS){var before=previous.entities.get(kind);var after=current.entities.get(kind);var upsert=after.entrySet().stream().filter(e->!e.getValue().equals(before.get(e.getKey()))).map(Map.Entry::getValue).toList();var remove=before.keySet().stream().filter(id->!after.containsKey(id)).toList();payload.put(kind,Map.of("upsert",upsert,"remove",remove));}
        frames.put(connection,current);return payload;
    }
    public static Point point(Object payload){return switch(payload){case PlayerState.View p->new Point(p.x(),p.y());case MonsterState.View m->new Point(m.x(),m.y());case BotState.View b->new Point(b.x(),b.y());case MonsterDefeats.MonsterDefeated d->new Point(d.x(),d.y());case Map<?,?> map->map.get("x") instanceof Number x&&map.get("y") instanceof Number y?new Point(x.doubleValue(),y.doubleValue()):null;default->null;};}
}
