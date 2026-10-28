package vn.tamtoc.instance;

import java.util.*;
import org.springframework.stereotype.Component;

/** Accessed through the world lock; no per-instance threads. */
@Component
public class InstanceRegistry {
    private final Map<UUID,WorldMapInstance> instances=new LinkedHashMap<>();
    public synchronized WorldMapInstance create(String map,String kind,Collection<UUID> players){var instance=new WorldMapInstance(map,kind,players);instances.put(instance.id,instance);return instance;}
    public synchronized WorldMapInstance get(UUID id){return instances.get(id);}
    public synchronized List<WorldMapInstance> all(){return List.copyOf(instances.values());}
    public synchronized void remove(UUID id){instances.remove(id);}
}
