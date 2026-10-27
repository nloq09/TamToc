package vn.tamtoc.combat;

import java.util.*;
import vn.tamtoc.combat.CombatCatalog.MonsterDefinition;

public class MonsterState {
    public enum Ai { IDLE,PATROL,CHASE,ATTACK,RETURN,DEAD,RESPAWN }
    public final UUID id=UUID.randomUUID();
    public final MonsterDefinition definition;
    public final String mapId;
    public final double spawnX, y;
    public double x;
    public int hp, phase=1, patrolDirection=1;
    public Ai state=Ai.IDLE;
    public long respawnAt, attackAt, slowedUntil;
    public final Map<UUID,Integer> contributions=new HashMap<>();
    public MonsterState(MonsterDefinition d,String map,double x,double y){definition=d;mapId=map;spawnX=x;this.x=x;this.y=y;hp=d.hp();}
    public record View(UUID id,String type,String name,int level,int hp,int maxHp,double x,double y,String state,int phase) {}
    public View view(){return new View(id,definition.id(),definition.name(),definition.level(),hp,definition.hp(),x,y,state.name(),phase);}
}
