package vn.tamtoc.world;

import java.util.UUID;
import vn.tamtoc.character.GameCharacter;

/** Realtime state is deliberately separate from the persistence entity. */
public class PlayerState {
    public final UUID id;
    public final String name, race;
    public int level;
    public long exp, cultivation;
    public String realm, organization, path;
    public int realmStage;
    public vn.tamtoc.progression.ProgressData progress=new vn.tamtoc.progression.ProgressData();
    public java.util.Map<String,Long> cooldowns=progress.cooldowns;
    public int facing=1;
    public long shieldUntil,empoweredUntil,regenAt;
    public int equipmentAttack=4,equipmentDefense=3;
    public String mapId;
    public UUID instanceId;
    public String connectionStatus="ONLINE";
    public String returnMapId;
    public double returnX,returnY;
    public double x, y, vy;
    public boolean grounded, jumpHeld, jumpQueued;
    public int direction;
    public long lastInput = System.currentTimeMillis();
    public long sequence = -1;
    public long lastActiveAt;
    public long slowedUntil;
    public double movementScale=1;
    public PlayerState(GameCharacter c) {
        id=c.id; name=c.name; race=c.race.name(); level=c.level; mapId=c.mapId; x=c.x; y=c.y;
        exp=c.exp;cultivation=c.cultivation;realm=c.realm;realmStage=c.realmStage;organization=c.organization;path=c.cultivationPath;
    }
    public int maxHp(){return 100+(level-1)*12+realmStage*15+(realm.equals("TRUC_CO")?100:0);}
    public int maxMp(){return 60+(level-1)*5;}
    public int attack(){return (level-1)*2+equipmentAttack;}
    public int defense(){return (level-1)+equipmentDefense;}
    public record View(UUID id, String name, String race, int level, double x, double y, int hp, int maxHp, int mp, int maxMp, long sequence,int facing,long exp,long cultivation,String realm,int realmStage,String organization,String path,java.util.Map<String,Long> cooldowns,String mapId,UUID instanceId,String connectionStatus) {}
    public View view() { return new View(id,name,race,level,x,y,progress.hp,maxHp(),progress.mp,maxMp(),sequence,facing,exp,cultivation,realm,realmStage,organization,path,java.util.Map.copyOf(cooldowns),mapId,instanceId,connectionStatus); }
}
