package vn.tamtoc.instance;

import java.util.*;
import vn.tamtoc.progression.ProgressStore;

public interface ActivityStore {
    void startDungeon(UUID run,UUID instance,UUID party,Collection<UUID> participants,long started);
    default void startDungeon(UUID run,UUID instance,UUID party,Collection<UUID> participants,long started,String dungeon){startDungeon(run,instance,party,participants,started);}
    boolean completeDungeon(UUID run,Map<UUID,ProgressStore.Save> snapshots,String rewards);
    void finishDungeon(UUID run,String status,String reason);
    void finishDuel(UUID duel,UUID challenger,UUID opponent,UUID winner,String reason,long started,long ended);
}
