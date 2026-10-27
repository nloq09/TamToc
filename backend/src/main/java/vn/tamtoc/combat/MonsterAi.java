package vn.tamtoc.combat;

import java.util.*;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Component;
import vn.tamtoc.world.PlayerState;
import static vn.tamtoc.combat.MonsterState.Ai.*;

@Component
public class MonsterAi {
    public void step(MonsterState m,List<PlayerState> players,double dt,long now,BiConsumer<String,Object> event){
        if(m.hp<=0){
            if(now<m.respawnAt)return;
            m.state=RESPAWN;m.hp=m.definition.hp();m.x=m.spawnX;m.phase=1;m.contributions.clear();
            event.accept("MONSTER_SPAWN",m.view());return;
        }
        m.phase=m.definition.id().equals("BOSS")&&m.hp<m.definition.hp()/2?2:1;
        var target=players.stream().filter(p->p.progress.hp>0&&p.x>420&&Math.abs(p.x-m.spawnX)<430&&Math.abs(p.y-m.y)<100)
            .min(Comparator.comparingDouble(p->Math.abs(p.x-m.x))).orElse(null);
        double speed=m.definition.speed()*(m.phase==2?1.5:1)*(m.slowedUntil>now?.5:1);
        if(target!=null&&Math.abs(target.x-m.x)<m.definition.detection()){
            if(Math.abs(target.x-m.x)>m.definition.range()) {m.state=CHASE;m.x+=Math.signum(target.x-m.x)*speed*dt;}
            else {
                m.state=ATTACK;
                if(now>=m.attackAt){
                    m.attackAt=now+(m.phase==2?750:1200);
                    int damage=Math.max(1,m.definition.atk()*m.phase-target.defense()-(target.shieldUntil>now?18:0));
                    target.progress.hp=Math.max(0,target.progress.hp-damage);
                    event.accept("COMBAT_EVENT",Map.of("source",m.id,"target",target.id,"damage",damage,"crit",false,"skill","MONSTER","x",target.x,"y",target.y));
                    if(target.progress.hp==0){target.progress.respawnAt=now+4000;target.direction=0;event.accept("PLAYER_DIED",Map.of("id",target.id,"respawnMs",4000));}
                }
            }
        }else if(Math.abs(m.x-m.spawnX)>90){m.state=RETURN;m.x+=Math.signum(m.spawnX-m.x)*speed*dt;}
        else{m.state=PATROL;m.x+=m.patrolDirection*speed*.3*dt;if(Math.abs(m.x-m.spawnX)>85)m.patrolDirection*=-1;}
    }
}
