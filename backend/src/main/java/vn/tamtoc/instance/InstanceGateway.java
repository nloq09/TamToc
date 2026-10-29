package vn.tamtoc.instance;

import java.util.*;
import vn.tamtoc.world.PlayerState;

public interface InstanceGateway {
    PlayerState getPlayer(UUID id);
    boolean isOnline(UUID id);
    default long disconnectedSince(UUID id){return 0;}
    void moveTo(UUID player,String map,UUID instance,double x);
    void returnHome(UUID player);
    void event(UUID player,String type,Object payload);
    default void progressEvent(UUID player,String type,String map){}
    Map<UUID,Map<String,Object>> grantDungeon(UUID run,List<UUID> eligible,int exp,int cultivation,int stones);
}
