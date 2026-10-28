package vn.tamtoc.dungeon;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.tamtoc.combat.*;
import vn.tamtoc.instance.*;
import vn.tamtoc.party.PartyRegistry;
import vn.tamtoc.realtime.ProtocolException;

@Service
public class DungeonService {
    public static final String MAP="huyen-moc-party";
    private record Readiness(long revision,Set<UUID> ready) {}
    private final Map<UUID,Readiness> readiness=new HashMap<>();
    private final Map<UUID,DungeonRun> runs=new LinkedHashMap<>();
    private final InstanceRegistry instances;private final PartyRegistry parties;private final CombatCatalog combat;private final ActivityStore store;
    private final int minLevel;private final long duration,cleanupMs;
    public DungeonService(InstanceRegistry i,PartyRegistry p,CombatCatalog c,ActivityStore s,@Value("${game.dungeon.min-level:5}")int level,@Value("${game.dungeon.duration-ms:900000}")long duration,@Value("${game.instance-cleanup-ms:60000}")long cleanupMs){instances=i;parties=p;combat=c;store=s;minLevel=level;this.duration=duration;this.cleanupMs=cleanupMs;}
    private static void require(boolean ok,String code,String message){if(!ok)throw new ProtocolException(code,message);}
    public boolean busy(UUID party){return runs.values().stream().anyMatch(r->r.partyId.equals(party)&&r.state==DungeonRun.State.ACTIVE);}
    public DungeonRun forPlayer(UUID id){return runs.values().stream().filter(r->r.instance.participants.contains(id)&&!r.departed.contains(id)).findFirst().orElse(null);}
    private Readiness readyState(PartyRegistry.Party party){var state=readiness.get(party.id);if(state==null||state.revision!=party.revision){state=new Readiness(party.revision,new HashSet<>());readiness.put(party.id,state);}return state;}
    public Object lobby(UUID player,InstanceGateway world){var p=parties.get(player);return Map.of("dungeonId","HUYEN_MOC","name","Huyền Mộc Bí Cảnh – Tổ Đội","minLevel",minLevel,"minPlayers",2,"durationMs",duration,"entryMap","trung-chau","entryX",350,"ready",p==null?Set.of():Set.copyOf(readyState(p).ready),"inParty",p!=null);}
    public void ready(UUID player,boolean ready,InstanceGateway world){var p=parties.get(player);require(p!=null,"NOT_IN_PARTY","Cần tổ đội trước khi chuẩn bị phó bản.");require(!busy(p.id),"DUNGEON_ALREADY_ACTIVE","Tổ đội đang có lượt phó bản.");validateEntry(player,world);if(ready)readyState(p).ready.add(player);else readyState(p).ready.remove(player);publishLobby(p,world);}
    private void validateEntry(UUID id,InstanceGateway world){var p=world.getPlayer(id);require(p!=null&&world.isOnline(id)&&p.progress.hp>0,"PLAYER_NOT_READY","Tất cả thành viên cần trực tuyến và còn sống.");require(p.level>=minLevel,"DUNGEON_LEVEL_REQUIRED","Chưa đủ cấp vào phó bản.");require(p.instanceId==null&&p.mapId.equals("trung-chau")&&Math.abs(p.x-350)<=150,"DUNGEON_ENTRY_REQUIRED","Hãy đến Tiếp Dẫn Sứ ở Trung Châu (x=350) trước khi sẵn sàng.");}
    public DungeonRun start(UUID leader,String dungeon,InstanceGateway world,long now){
        var party=parties.get(leader);require(party!=null&&party.leader.equals(leader),"LEADER_REQUIRED","Chỉ trưởng đội được mở phó bản.");require(dungeon.equals("HUYEN_MOC"),"DUNGEON_NOT_FOUND","Phó bản không tồn tại.");
        require(!busy(party.id),"DUNGEON_ALREADY_ACTIVE","Tổ đội đã có phó bản đang chạy.");require(party.members.size()>=2&&party.members.size()<=parties.maxSize(),"DUNGEON_PARTY_SIZE","Cần 2–4 thành viên.");
        for(UUID id:party.members)validateEntry(id,world);
        require(readyState(party).ready.containsAll(party.members),"DUNGEON_NOT_READY","Chưa đủ xác nhận sẵn sàng.");
        var instance=instances.create(MAP,"DUNGEON",party.members);var run=new DungeonRun(party.id,instance,now);run.state=DungeonRun.State.WAITING;
        try{store.startDungeon(run.id,instance.id,party.id,party.members,now);}catch(RuntimeException e){instances.remove(instance.id);throw e;}
        runs.put(run.id,run);readiness.remove(party.id);run.state=DungeonRun.State.ACTIVE;
        for(UUID id:instance.participants){world.moveTo(id,MAP,instance.id,180);world.event(id,"DUNGEON_INSTANCE_CREATED",view(run,now));}
        spawnWave(run,world,now);return run;
    }
    private void spawnWave(DungeonRun run,InstanceGateway world,long now){
        run.instance.monsters.clear();String[] types=run.wave==1?new String[]{"WOLF","WOLF","WOLF"}:run.wave==2?new String[]{"SOLDIER","SPIRIT","SOLDIER"}:new String[]{"BOSS"};
        for(int i=0;i<types.length;i++){var m=new MonsterState(combat.monsters.get(types[i]),MAP,650+i*180,540);m.instanceId=run.instance.id;run.instance.monsters.add(m);}
        for(UUID id:run.instance.participants)world.event(id,"DUNGEON_WAVE_STARTED",view(run,now));
    }
    public void step(InstanceGateway world,long now){
        readiness.keySet().removeIf(id->parties.all().stream().noneMatch(p->p.id.equals(id)));
        for(var run:List.copyOf(runs.values())){
            if(run.state==DungeonRun.State.ACTIVE){
                if(run.completionPending){complete(run,world,now);continue;}
                long reserved=run.instance.participants.stream().filter(id->!run.departed.contains(id)&&world.getPlayer(id)!=null).count();
                if(reserved<2){finish(run,"FAILED","NOT_ENOUGH_PLAYERS",world,now);continue;}
                if(now-run.startedAt>=duration){finish(run,"EXPIRED","TIMEOUT",world,now);continue;}
                if(now<run.reviveAt)continue;
                boolean wiped=run.instance.participants.stream().filter(id->!run.departed.contains(id)).map(world::getPlayer).filter(Objects::nonNull).noneMatch(p->p.progress.hp>0);
                if(wiped){if(run.wipes++==0){run.reviveAt=now+4000;spawnWave(run,world,now);for(UUID id:run.instance.participants){var p=world.getPlayer(id);if(p!=null){p.x=180;p.y=540;p.vy=0;p.progress.respawnAt=run.reviveAt;}}}else finish(run,"FAILED","WIPE_LIMIT",world,now);continue;}
                if(!run.instance.monsters.isEmpty()&&run.instance.monsters.stream().allMatch(m->m.hp<=0)){
                    if(run.wave<3){run.wave++;spawnWave(run,world,now); }else {
                        run.eligible=run.instance.participants.stream().filter(id->!run.departed.contains(id)&&world.isOnline(id)).filter(id->{var p=world.getPlayer(id);return p!=null&&Objects.equals(p.instanceId,run.instance.id)&&p.lastActiveAt>0&&now-p.lastActiveAt<=10000;}).toList();
                        run.completionPending=true;run.instance.active=false;complete(run,world,now);
                    }
                }
            }else if(now-run.endedAt>=cleanupMs){for(UUID id:run.instance.participants)if(!run.departed.contains(id))world.returnHome(id);instances.remove(run.instance.id);run.state=DungeonRun.State.CLEANED;runs.remove(run.id);}
        }
    }
    private void complete(DungeonRun run,InstanceGateway world,long now){
        if(now<run.retryAt)return;
        try{run.grants=world.grantDungeon(run.id,run.eligible,500,150,100);run.rewarded=Set.copyOf(run.grants.keySet());run.rewardsProcessed=true;run.completionPending=false;run.state=DungeonRun.State.COMPLETED;run.endedAt=now;run.reason="VICTORY";publishFinished(run,world,now);}
        catch(RuntimeException e){run.retryAt=now+5000;org.slf4j.LoggerFactory.getLogger(DungeonService.class).error("Dungeon completion persistence failed for {}",run.id,e);}
    }
    private void finish(DungeonRun run,String state,String reason,InstanceGateway world,long now){if(run.state!=DungeonRun.State.ACTIVE)return;store.finishDungeon(run.id,state,reason);run.state=DungeonRun.State.valueOf(state);run.instance.active=false;run.reason=reason;run.endedAt=now;publishFinished(run,world,now);}
    private void publishFinished(DungeonRun run,InstanceGateway world,long now){for(UUID id:run.instance.participants)world.event(id,"DUNGEON_FINISHED",Map.of("run",view(run,now),"rewards",run.grants.getOrDefault(id,Map.of())));}
    public void leave(UUID player,InstanceGateway world,long now){var run=forPlayer(player);require(run!=null,"NOT_IN_DUNGEON","Không ở trong phó bản.");run.departed.add(player);world.returnHome(player);world.event(player,"DUNGEON_LEFT",Map.of("runId",run.id));if(run.state==DungeonRun.State.ACTIVE)step(world,now);}
    public void snapshot(UUID player,InstanceGateway world,long now){var run=forPlayer(player);if(run!=null){world.event(player,"DUNGEON_PROGRESS_UPDATED",view(run,now));if(run.state!=DungeonRun.State.ACTIVE)world.event(player,"DUNGEON_FINISHED",Map.of("run",view(run,now),"rewards",run.grants.getOrDefault(player,Map.of())));}else world.event(player,"DUNGEON_LIST",lobby(player,world));}
    public Map<String,Object> view(DungeonRun r,long now){return Map.of("runId",r.id,"instanceId",r.instance.id,"state",r.state.name(),"wave",r.wave,"totalWaves",3,"remainingMs",Math.max(0,duration-(now-r.startedAt)),"wipes",r.wipes,"reason",r.reason,"participants",r.instance.participants);}
    private void publishLobby(PartyRegistry.Party p,InstanceGateway world){for(UUID id:p.members)world.event(id,"DUNGEON_LIST",lobby(id,world));}
}
