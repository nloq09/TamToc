package vn.tamtoc.progression;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ProgressionCatalog {
    public record Organization(String id,String name,String race,String path,String archetype,String description,String skill) {}
    public record Quest(String id,String name,String description,int requiredLevel,String type,String target,int count,int exp,int cultivation,int stones) {}
    public record Item(String id,String name,String slot,int level,String realm,String path,int attack,int defense,int stack) {}
    public record Data(int organizationLevel,int trialKills,List<Organization> organizations,List<Quest> quests,List<Item> items) {}
    public final Data data;
    public final Map<String,Organization> organizations=new LinkedHashMap<>();
    public final Map<String,Quest> quests=new LinkedHashMap<>();
    public final Map<String,Item> items=new LinkedHashMap<>();
    public ProgressionCatalog(ObjectMapper json) throws IOException {
        try(var in=new ClassPathResource("progression.json").getInputStream()){data=json.readValue(in,Data.class);}
        data.organizations.forEach(o->organizations.put(o.id,o));data.quests.forEach(q->quests.put(q.id,q));data.items.forEach(i->items.put(i.id,i));
    }
}
