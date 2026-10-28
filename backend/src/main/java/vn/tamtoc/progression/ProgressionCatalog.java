package vn.tamtoc.progression;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ProgressionCatalog {
    public record Organization(String id,String name,String race,String path,String archetype,String description,String skill) {}
    public record Quest(String id,String name,String description,int requiredLevel,String type,String target,int count,int exp,int cultivation,int stones,String mapId) {}
    public record Item(String id,String name,String slot,int level,String realm,String path,int attack,int defense,int stack,String category,boolean questItem) {}
    public record Data(int organizationLevel,int trialKills,List<Organization> organizations,List<Quest> quests,List<Item> items) {}
    public final Data data;
    public final Map<String,Organization> organizations=new LinkedHashMap<>();
    public final Map<String,Quest> quests=new LinkedHashMap<>();
    public final Map<String,Item> items=new LinkedHashMap<>();
    public ProgressionCatalog(ObjectMapper json) throws IOException {
        try(var in=new ClassPathResource("progression.json").getInputStream()){data=json.readValue(in,Data.class);}
        data.organizations.forEach(o->organizations.put(o.id,o));for(var q:data.quests)if(quests.putIfAbsent(q.id,q)!=null||q.count<1||q.exp<0||q.cultivation<0||q.stones<0||q.requiredLevel<1)throw new IllegalArgumentException("Invalid/duplicate quest "+q.id);for(var i:data.items)if(items.putIfAbsent(i.id,i)!=null||i.stack<1)throw new IllegalArgumentException("Invalid/duplicate item "+i.id);
    }
}
