package vn.tamtoc.party;

import java.util.*;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Component;
import vn.tamtoc.realtime.CharacterSessions;

@Component
public class PartyEventPublisher {
    private final CharacterSessions sessions;private final PartyRegistry registry;
    public PartyEventPublisher(CharacterSessions s,PartyRegistry r){sessions=s;registry=r;}
    public Object view(UUID member){
        var p=registry.get(member);if(p==null)return Map.of("inParty",false,"maxSize",registry.maxSize());
        var members=p.members.stream().map(id->{var l=sessions.byCharacter(id);var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("status",l==null?"OFFLINE":l.status.name());if(l!=null){var actor=l.player;row.put("name",actor.name);row.put("race",actor.race);row.put("level",actor.level);row.put("hp",actor.progress.hp);row.put("maxHp",actor.maxHp());row.put("mp",actor.progress.mp);row.put("maxMp",actor.maxMp());row.put("mapId",actor.mapId);row.put("instanceId",actor.instanceId);row.put("x",actor.x);row.put("y",actor.y);}return row;}).toList();
        return Map.of("inParty",true,"id",p.id,"leaderId",p.leader,"maxSize",registry.maxSize(),"revision",p.revision,"members",members);
    }
    public void publish(UUID member,BiConsumer<UUID,Object> send){var party=registry.get(member);if(party!=null){var view=view(member);for(UUID id:List.copyOf(party.members))send.accept(id,view);}else send.accept(member,view(member));}
}
