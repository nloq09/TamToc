package vn.tamtoc.bot;

import java.util.*;
import java.util.function.BiConsumer;
import org.springframework.stereotype.Service;
import vn.tamtoc.instance.*;
import vn.tamtoc.world.*;
import vn.tamtoc.combat.*;
import vn.tamtoc.realtime.ProtocolException;

/** Solo practice is ephemeral and never grants XP, loot, currency or quest credit. */
@Service
public class TrainingService {
    public static final String MAP="training-arena";
    public static class Run {
        public final UUID id=UUID.randomUUID(),player;public final WorldMapInstance instance;public final BotState bot;public final long activeAt;public String state="PREPARING",reason="";public long endedAt;
        Run(UUID player,WorldMapInstance instance,BotState bot,long now){this.player=player;this.instance=instance;this.bot=bot;activeAt=now+3000;}
    }
    private final Map<UUID,Run> active=new LinkedHashMap<>(),finished=new HashMap<>();
    private final InstanceRegistry instances;private final MapCatalog maps;private final BotCatalog catalog;private final BotRegistry bots;private final BotBrain brain;private final CombatEngine combat;
    public TrainingService(InstanceRegistry instances,MapCatalog maps,BotCatalog catalog,BotRegistry bots,BotBrain brain,CombatEngine combat){this.instances=instances;this.maps=maps;this.catalog=catalog;this.bots=bots;this.brain=brain;this.combat=combat;}
    public Run forPlayer(UUID player){return active.get(player);}
    public Run start(UUID player,UUID botId,String difficulty,InstanceGateway world,long now){
        var p=world.getPlayer(player);require(p!=null&&world.isOnline(player)&&p.progress.hp>0&&p.instanceId==null&&!active.containsKey(player),"TRAINING_BUSY","Cần còn sống, trực tuyến và ở bản đồ chung.");
        require(catalog.difficulties.containsKey(difficulty),"TRAINING_DIFFICULTY_INVALID","Chọn mức EASY hoặc HARD.");var source=bots.interactable(p,botId);require(source.definition.botType().equals("CHALLENGER")&&source.definition.combatPolicy().equals("CONSENT_TRAINING"),"TRAINING_NPC_REQUIRED","Hãy gặp NPC đối luyện.");
        var instance=instances.create(MAP,"TRAINING",List.of(player));var bot=bots.training(source,instance.id,difficulty);var run=new Run(player,instance,bot,now);active.put(player,run);finished.remove(player);p.progress.hp=p.maxHp();p.progress.mp=p.maxMp();p.shieldUntil=0;p.empoweredUntil=0;p.slowedUntil=0;
        world.moveTo(player,MAP,instance.id,300);world.event(player,"TRAINING_STARTED",view(run,now));return run;
    }
    public void cast(UUID player,String skill,InstanceGateway world,long now,BiConsumer<String,Object> event){var run=active.get(player);require(run!=null&&run.state.equals("ACTIVE"),"TRAINING_NOT_ACTIVE","Chưa hết thời gian chuẩn bị đối luyện.");combat.castTraining(world.getPlayer(player),skill,maps.get(MAP),run.bot.actor,run.instance.id,now,event);afterCombat(player,world,now);}
    public void afterCombat(UUID player,InstanceGateway world,long now){var run=active.get(player);if(run==null)return;var p=world.getPlayer(player);if(run.bot.actor.progress.hp<=0)finish(run,"VICTORY",world,now);else if(p!=null&&p.progress.hp<=0)finish(run,"DEFEAT",world,now);}
    public void cancel(UUID player,String reason,InstanceGateway world,long now){var run=active.get(player);require(run!=null,"NOT_IN_TRAINING","Không ở trong bài đối luyện.");finish(run,reason,world,now);}
    public void step(InstanceGateway world,double dt,long now){
        finished.values().removeIf(r->now-r.endedAt>=60000);
        for(var run:List.copyOf(active.values())){
            var p=world.getPlayer(run.player);if(p==null||(world.disconnectedSince(run.player)>0&&now-world.disconnectedSince(run.player)>=20000)){finish(run,"ABANDONED",world,now);continue;}
            if(now-run.activeAt>=180000){finish(run,"TIMEOUT",world,now);continue;}
            if(now<run.activeAt)continue;
            if(run.state.equals("PREPARING")){run.state="ACTIVE";world.event(run.player,"TRAINING_STATE",view(run,now));}
            afterCombat(run.player,world,now);if(!active.containsKey(run.player))continue;
            brain.training(run.bot,p,maps.get(MAP),dt,now,(t,payload)->world.event(run.player,t,payload));afterCombat(run.player,world,now);
        }
    }
    private void finish(Run run,String reason,InstanceGateway world,long now){if(!active.remove(run.player,run))return;run.state="FINISHED";run.reason=reason;run.endedAt=now;run.instance.active=false;bots.remove(run.bot.actor.id);instances.remove(run.instance.id);finished.put(run.player,run);world.returnHome(run.player);world.event(run.player,"TRAINING_RESULT",view(run,now));}
    public void snapshot(UUID player,InstanceGateway world,long now){var run=active.get(player);if(run!=null)world.event(player,"TRAINING_STATE",view(run,now));else if((run=finished.get(player))!=null)world.event(player,"TRAINING_RESULT",view(run,now));}
    public Map<String,Object> view(Run run,long now){return Map.of("runId",run.id,"instanceId",run.instance.id,"botId",run.bot.actor.id,"name",run.bot.definition.name(),"difficulty",run.bot.difficulty.id(),"state",run.state,"reason",run.reason,"secondsRemaining",Math.max(0,(run.state.equals("PREPARING")?run.activeAt-now:180000-(now-run.activeAt)+999)/1000),"rewardGranted",false);}
    private static void require(boolean ok,String code,String text){if(!ok)throw new ProtocolException(code,text);}
}
