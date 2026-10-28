package vn.tamtoc.dungeon;

import java.util.*;
import vn.tamtoc.instance.WorldMapInstance;

public class DungeonRun {
    public enum State { CREATED, WAITING, ACTIVE, COMPLETED, FAILED, EXPIRED, CLEANED }
    public final UUID id=UUID.randomUUID(),partyId;
    public final WorldMapInstance instance;
    public final long startedAt;
    public final Set<UUID> departed=new HashSet<>();
    public State state=State.CREATED;
    public int wave=1,wipes;
    public long endedAt,retryAt,reviveAt;
    public boolean rewardsProcessed,completionPending;
    public List<UUID> eligible=List.of();
    public String reason="";
    public Set<UUID> rewarded=Set.of();
    public Map<UUID,Map<String,Object>> grants=Map.of();
    public DungeonRun(UUID party,WorldMapInstance instance,long now){partyId=party;this.instance=instance;startedAt=now;}
}
