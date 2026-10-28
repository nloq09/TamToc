package vn.tamtoc.party;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.tamtoc.world.PlayerState;
import vn.tamtoc.combat.MonsterState;

@Component
public class PartyRewardPolicy {
    public record Share(PlayerState player,int exp,int cultivation) {}
    private final PartyRegistry parties;
    private final int radius;
    private final long activityMs;
    public PartyRewardPolicy(PartyRegistry p,@Value("${game.party.reward-radius:800}")int radius,@Value("${game.party.activity-ms:10000}")long activityMs){parties=p;this.radius=radius;this.activityMs=activityMs;}
    public List<Share> split(MonsterState monster,List<PlayerState> online,long now){
        Set<UUID> contributingParties=new HashSet<>();
        for(UUID id:monster.contributions.keySet()){var party=parties.get(id);if(party!=null)contributingParties.add(party.id);}
        var eligible=online.stream().filter(p->p.progress.hp>0&&p.mapId.equals(monster.mapId)&&Objects.equals(p.instanceId,monster.instanceId)&&Math.hypot(p.x-monster.x,p.y-monster.y)<=radius)
            .filter(p->p.lastActiveAt>0&&now-p.lastActiveAt<=activityMs)
            .filter(p->monster.contributions.containsKey(p.id)||(parties.get(p.id)!=null&&contributingParties.contains(parties.get(p.id).id)))
            .sorted(Comparator.comparing(p->p.id.toString())).toList();
        var shares=new ArrayList<Share>();int size=eligible.size();
        for(int i=0;i<size;i++)shares.add(new Share(eligible.get(i),monster.definition.exp()/size+(i<monster.definition.exp()%size?1:0),monster.definition.cultivation()/size+(i<monster.definition.cultivation()%size?1:0)));
        return shares;
    }
}
