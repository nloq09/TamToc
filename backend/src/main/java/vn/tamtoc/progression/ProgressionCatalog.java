package vn.tamtoc.progression;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ProgressionCatalog {
    public record Organization(String id,String name,String race,String path,String archetype,String description,String skill) {}
    public record Objective(String type,String mapId,String monsterId,String spawnGroupId,String placementId,int count) {}
    public record Quest(String id,String name,String description,int requiredLevel,String type,String target,int count,int exp,int cultivation,int stones,String mapId,String requiredRace,List<String> prerequisiteQuestIds,List<String> anyPrerequisiteQuestIds,String followUpQuestId,Boolean enabled,String category,List<Objective> objectives) {
        public Quest {prerequisiteQuestIds=prerequisiteQuestIds==null?List.of():List.copyOf(prerequisiteQuestIds);anyPrerequisiteQuestIds=anyPrerequisiteQuestIds==null?List.of():List.copyOf(anyPrerequisiteQuestIds);objectives=objectives==null?List.of():List.copyOf(objectives);enabled=enabled==null?true:enabled;category=category==null?"SIDE":category;}
    }
    public record Item(String id,String name,String slot,int level,String realm,String path,int attack,int defense,int stack,String category,boolean questItem) {}
    public record Data(int organizationLevel,int trialKills,List<Organization> organizations,List<Quest> quests,List<Item> items) {}
    public final Data data;
    public final Map<String,Organization> organizations=new LinkedHashMap<>();
    public final Map<String,Quest> quests=new LinkedHashMap<>();
    public final Map<String,Item> items=new LinkedHashMap<>();
    public ProgressionCatalog(ObjectMapper json) throws IOException {
        try(var in=new ClassPathResource("progression.json").getInputStream()){data=json.readValue(in,Data.class);}
        data.organizations.forEach(o->organizations.put(o.id,o));for(var q:data.quests)if(quests.putIfAbsent(q.id,q)!=null||q.count<1||q.exp<0||q.cultivation<0||q.stones<0||q.requiredLevel<1)throw new IllegalArgumentException("Invalid/duplicate quest "+q.id);for(var i:data.items)if(items.putIfAbsent(i.id,i)!=null||i.stack<1)throw new IllegalArgumentException("Invalid/duplicate item "+i.id);
        for(var q:quests.values()){for(String ref:java.util.stream.Stream.concat(q.prerequisiteQuestIds.stream(),q.anyPrerequisiteQuestIds.stream()).toList())if(!quests.containsKey(ref))throw new IllegalArgumentException("Missing prerequisite "+ref);if(q.followUpQuestId!=null&&!quests.containsKey(q.followUpQuestId))throw new IllegalArgumentException("Missing follow-up "+q.id);for(var o:q.objectives)if(o.count<1||!Set.of("DISCOVER_MAP","KILL_MONSTER","DEFEAT_BOSS_PLACEMENT","CLEAR_DUNGEON_WAVE").contains(o.type))throw new IllegalArgumentException("Invalid objective "+q.id);}
        for(String id:quests.keySet())validateChain(id,new HashSet<>(),new HashSet<>());
    }
    private void validateChain(String id,Set<String> path,Set<String> done){if(done.contains(id))return;if(!path.add(id))throw new IllegalArgumentException("Quest cycle "+id);var q=quests.get(id);for(String ref:java.util.stream.Stream.concat(q.prerequisiteQuestIds.stream(),q.anyPrerequisiteQuestIds.stream()).toList())validateChain(ref,path,done);path.remove(id);done.add(id);}
}
