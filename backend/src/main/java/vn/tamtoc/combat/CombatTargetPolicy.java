package vn.tamtoc.combat;
import java.util.*;
import vn.tamtoc.world.PlayerState;
public final class CombatTargetPolicy {
    private CombatTargetPolicy(){}
    public static boolean pvp(PlayerState actor,PlayerState target,UUID authorizedInstance){return authorizedInstance!=null&&actor.mapId.equals("duel-arena")&&target.mapId.equals(actor.mapId)&&authorizedInstance.equals(actor.instanceId)&&authorizedInstance.equals(target.instanceId)&&!actor.id.equals(target.id)&&target.progress.hp>0;}
}
