package vn.tamtoc.combat;

import java.util.*;
import vn.tamtoc.combat.CombatCatalog.MonsterDefinition;

public class MonsterState extends vn.tamtoc.world.MotionBody {
    public enum Ai { IDLE,PATROL,ALERT,CHASE,ATTACK,RETURN,STUNNED,CASTING,DEAD,RESPAWN }
    public final UUID id=UUID.randomUUID();
    public final MonsterDefinition definition;
    public final String mapId;
    public UUID instanceId;
    public boolean rewardProcessed;
    public final double spawnX,spawnY;
    public String spawnGroupId="instance";
    public long respawnDelay;
    public int hp, phase=1, patrolDirection=1;
    public Ai state=Ai.IDLE;
    public long respawnAt, attackAt, slowedUntil;
    public long nextThinkAt,aggroSince,targetSince,strikeAt,abilityAt,stuckSince,stunnedUntil,deathOrdinal;
    public UUID targetId,strikeTarget,castId;
    public double castX,castY;
    public CombatCatalog.Ability castingAbility;
    public final Map<UUID,Double> threat=new HashMap<>();
    public final Map<UUID,Integer> contributions=new HashMap<>();
    public MonsterState(MonsterDefinition d,String map,double x,double y){definition=d;mapId=map;spawnX=x;spawnY=y;this.x=x;this.y=y;hp=d.hp();respawnDelay=d.respawnMs();grounded=true;}
    public record Telegraph(UUID castId,String abilityId,double x,double y,double radius,long resolvesAt) {}
    public record View(UUID id,String type,String name,int level,int hp,int maxHp,double x,double y,String state,int phase,String definitionId,String spawnGroupId,UUID instanceId,String tier,UUID targetId,double detection,double leashRadius,double spawnX,double spawnY,Telegraph telegraph) {}
    public View view(){return new View(id,definition.id(),definition.name(),definition.level(),hp,definition.hp(),x,y,state.name(),phase,definition.id(),spawnGroupId,instanceId,instanceId!=null&&definition.tier().equals("WORLD_BOSS")?"DUNGEON_BOSS":definition.tier(),targetId,definition.detection(),definition.behavior().leashRadius(),spawnX,spawnY,hp>0&&castingAbility!=null?new Telegraph(castId,castingAbility.id(),castX,castY,castingAbility.radius(),strikeAt):null);}
}
