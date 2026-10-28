package vn.tamtoc.duel;

import java.util.*;
import vn.tamtoc.instance.WorldMapInstance;

public class DuelRun {
    public enum State { PREPARING, ACTIVE, FINISHING, FINISHED, CANCELED, CLEANED }
    public final UUID id=UUID.randomUUID(),challenger,opponent;
    public final WorldMapInstance instance;
    public final long activeAt;
    public State state=State.PREPARING;
    public long endedAt,retryAt;
    public UUID winner;
    public String reason="";
    public DuelRun(UUID a,UUID b,WorldMapInstance instance,long activeAt){challenger=a;opponent=b;this.instance=instance;this.activeAt=activeAt;}
    public UUID other(UUID id){return challenger.equals(id)?opponent:challenger;}
}
