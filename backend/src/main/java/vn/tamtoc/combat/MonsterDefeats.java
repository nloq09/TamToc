package vn.tamtoc.combat;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class MonsterDefeats {
    public record MonsterDefeated(UUID eventId,UUID entityId,String monsterDefinitionId,String mapId,UUID instanceId,long ordinal,long timestamp,double x,double y) {}
    public Optional<MonsterDefeated> once(MonsterState m,long now){
        if(m.hp>0||m.rewardProcessed)return Optional.empty();m.rewardProcessed=true;m.deathOrdinal++;return Optional.of(new MonsterDefeated(UUID.randomUUID(),m.id,m.definition.id(),m.mapId,m.instanceId,m.deathOrdinal,now,m.x,m.y));
    }
}
