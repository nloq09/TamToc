package vn.tamtoc.instance;

import java.util.*;
import vn.tamtoc.combat.MonsterState;

public class WorldMapInstance {
    public final UUID id=UUID.randomUUID();
    public final String mapId,kind;
    public final Set<UUID> participants;
    public final List<MonsterState> monsters=new ArrayList<>();
    public volatile boolean active=true;
    public WorldMapInstance(String mapId,String kind,Collection<UUID> players){this.mapId=mapId;this.kind=kind;participants=new LinkedHashSet<>(players);}
}
