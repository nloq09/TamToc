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
    private record Readiness(long revision,String dungeon,Set<UUID> ready) {}
    private final Map<UUID,Readiness> readiness=new HashMap<>();
    private final Map<UUID,DungeonRun> runs=new LinkedHashMap<>();
    private final InstanceRegistry instances;private final PartyRegistry parties;private final CombatCatalog combat;private final ActivityStore store;
    private final DungeonCatalog catalog;private final long duration,cleanupMs;
    public DungeonService(InstanceRegistry i,PartyRegistry p,CombatCatalog c,ActivityStore s,int level,long duration,long cleanupMs){this(i,p,c,s,level,duration,cleanupMs,DungeonCatalog.legacy(level));}
    @org.springframework.beans.factory.annotation.Autowired public DungeonService(InstanceRegistry i,PartyRegistry p,CombatCatalog c,ActivityStore s,@Value("${game.dungeon.min-level:10}")int level,@Value("${game.dungeon.duration-ms:900000}")long duration,@Value("${game.instance-cleanup-ms:60000}")long cleanupMs,DungeonCatalog catalog){instances=i;parties=p;combat=c;store=s;this.catalog=catalog;this.duration=duration;this.cleanupMs=cleanupMs;}
    private static void require(boolean ok,String code,String message){if(!ok)throw new ProtocolException(code,message);}
    public boolean busy(UUID party){return runs.values().stream().anyMatch(r->r.partyId.equals(party)&&r.state==DungeonRun.State.ACTIVE);}
    public DungeonRun forPlayer(UUID id){return runs.values().stream().filter(r->r.instance.participants.contains(id)&&!r.departed.contains(id)).findFirst().orElse(null);}
    private Readiness readyState(PartyRegistry.Party party,String dungeon){var state=readiness.get(party.id);if(state==null||state.revision!=party.revision||!state.dungeon.equals(dungeon)){state=new Readiness(party.revision,dungeon,new HashSet<>());readiness.put(party.id,state);}return state;}
    private DungeonCatalog.Definition selected(UUID player,InstanceGateway world,String requested){if(requested!=null&&!requested.isBlank())return catalog.get(requested);var party=parties.get(player);var state=party==null?null:readiness.get(party.id);if(state!=null&&state.revision==party.revision)return catalog.get(state.dungeon);var p=world.getPlayer(player);return catalog.all().stream().filter(d->p!=null&&d.entries().stream().anyMatch(e->e.mapId().equals(p.mapId)&&Math.abs(e.x()-p.x)<=e.radius())).findFirst().orElse(catalog.get("HUYEN_MOC"));}
    public Object lobby(UUID player,InstanceGateway world){return lobby(player,world,null);}
    public Object lobby(UUID player,InstanceGateway world,String requested){var party=parties.get(player);var d=selected(player,world,requested);var actor=world.getPlayer(player);var entry=d.entries().stream().filter(e->actor!=null&&e.mapId().equals(actor.mapId)).findFirst().orElse(d.entries().get(0));var state=party==null?null:readiness.get(party.id);return Map.ofEntries(Map.entry("dungeonId",d.id()),Map.entry("name",d.name()),Map.entry("minLevel",d.minLevel()),Map.entry("minPlayers",2),Map.entry("durationMs",duration),Map.entry("entryMap",entry.mapId()),Map.entry("entryMapName",catalog.maps==null?"Trung Châu":catalog.maps.get(entry.mapId()).name()),Map.entry("entryX",entry.x()),Map.entry("cooldownMs",d.cooldownMs()),Map.entry("ready",state!=null&&state.revision==party.revision&&state.dungeon.equals(d.id())?Set.copyOf(state.ready):Set.of()),Map.entry("inParty",party!=null),Map.entry("dungeons",catalog.all().stream().map(v->Map.of("id",v.id(),"name",v.name(),"minLevel",v.minLevel())).toList()));}
    public void ready(UUID player,boolean ready,InstanceGateway world){ready(player,ready,world,null);}
    public void ready(UUID player,boolean ready,InstanceGateway world,String requested){var p=parties.get(player);require(p!=null,"NOT_IN_PARTY","Cần tổ đội trước khi chuẩn bị phó bản.");require(!busy(p.id),"DUNGEON_ALREADY_ACTIVE","Tổ đội đang có lượt phó bản.");var d=selected(player,world,requested);validateEntry(player,d,world,System.currentTimeMillis());if(ready)readyState(p,d.id()).ready.add(player);else readyState(p,d.id()).ready.remove(player);publishLobby(p,world);}
    private void validateEntry(UUID id,DungeonCatalog.Definition d,InstanceGateway world,long now){var p=world.getPlayer(id);require(p!=null&&world.isOnline(id)&&p.progress.hp>0,"PLAYER_NOT_READY","Tất cả thành viên cần trực tuyến và còn sống.");require(p.level>=d.minLevel(),"DUNGEON_LEVEL_REQUIRED","Cần cấp "+d.minLevel()+" vào bí cảnh này.");require(p.instanceId==null&&d.entries().stream().anyMatch(e->e.mapId().equals(p.mapId)&&Math.abs(p.x-e.x())<=e.radius()),"DUNGEON_ENTRY_REQUIRED","Hãy đến điểm vào "+d.name()+" trước khi sẵn sàng.");require(p.progress.nextDungeonAt.getOrDefault(d.id(),0L)<=now,"DUNGEON_RESET_COOLDOWN","Bí cảnh đang trong thời gian hồi lượt.");}
    public DungeonRun start(UUID leader,String dungeon,InstanceGateway world,long now){
        var party=parties.get(leader);require(party!=null&&party.leader.equals(leader),"LEADER_REQUIRED","Chỉ trưởng đội được mở phó bản.");var d=catalog.get(dungeon);
        require(!busy(party.id),"DUNGEON_ALREADY_ACTIVE","Tổ đội đã có phó bản đang chạy.");require(party.members.size()>=2&&party.members.size()<=parties.maxSize(),"DUNGEON_PARTY_SIZE","Cần 2–4 thành viên.");
        for(UUID id:party.members)validateEntry(id,d,world,now);
        require(readyState(party,d.id()).ready.containsAll(party.members),"DUNGEON_NOT_READY","Chưa đủ xác nhận sẵn sàng.");
        var instance=instances.create(d.mapId(),"DUNGEON",party.members);var run=new DungeonRun(party.id,instance,now);run.dungeonId=d.id();run.state=DungeonRun.State.WAITING;
        try{store.startDungeon(run.id,instance.id,party.id,party.members,now,d.id());}catch(RuntimeException e){instances.remove(instance.id);throw e;}
        runs.put(run.id,run);readiness.remove(party.id);run.state=DungeonRun.State.ACTIVE;
        for(UUID id:instance.participants){if(d.cooldownMs()>0)world.getPlayer(id).progress.nextDungeonAt.put(d.id(),now+d.cooldownMs());world.moveTo(id,d.mapId(),instance.id,180);world.event(id,"DUNGEON_INSTANCE_CREATED",view(run,now));}
        spawnWave(run,world,now);return run;
    }
    private void spawnWave(DungeonRun run,InstanceGateway world,long now){
        run.instance.monsters.clear();var d=catalog.get(run.dungeonId);var wave=d.waves().get(run.wave-1);
        for(int j=0;j<wave.size();j++){String id=wave.get(j);if(catalog.maps==null){var m=new MonsterState(combat.monsters.get(id),d.mapId(),650+j*180,540);m.instanceId=run.instance.id;run.instance.monsters.add(m);}else{var g=catalog.group(d,id);for(int i=0;i<g.maxAlive();i++){double x=g.maxAlive()==1?g.startX():g.startX()+i*(g.endX()-g.startX())/(g.maxAlive()-1);var m=new MonsterState(combat.monsters.get(g.definitionFor(i)),d.mapId(),x,g.y());m.spawnGroupId=g.id();m.placementId=g.placementId();m.instanceId=run.instance.id;run.instance.monsters.add(m);}}}
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
                    for(UUID id:run.instance.participants)if(!run.departed.contains(id)&&world.isOnline(id))world.progressEvent(id,"CLEAR_DUNGEON_WAVE",run.instance.mapId);
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
        try{var d=catalog.get(run.dungeonId);run.grants=world.grantDungeon(run.id,run.eligible,d.rewardExp(),d.rewardCultivation(),d.rewardStones());run.rewarded=Set.copyOf(run.grants.keySet());run.rewardsProcessed=true;run.completionPending=false;run.state=DungeonRun.State.COMPLETED;run.endedAt=now;run.reason="VICTORY";publishFinished(run,world,now);}
        catch(RuntimeException e){run.retryAt=now+5000;org.slf4j.LoggerFactory.getLogger(DungeonService.class).error("Dungeon completion persistence failed for {}",run.id,e);}
    }
    private void finish(DungeonRun run,String state,String reason,InstanceGateway world,long now){if(run.state!=DungeonRun.State.ACTIVE)return;store.finishDungeon(run.id,state,reason);run.state=DungeonRun.State.valueOf(state);run.instance.active=false;run.reason=reason;run.endedAt=now;publishFinished(run,world,now);}
    private void publishFinished(DungeonRun run,InstanceGateway world,long now){for(UUID id:run.instance.participants)world.event(id,"DUNGEON_FINISHED",Map.of("run",view(run,now),"rewards",run.grants.getOrDefault(id,Map.of())));}
    public void leave(UUID player,InstanceGateway world,long now){var run=forPlayer(player);require(run!=null,"NOT_IN_DUNGEON","Không ở trong phó bản.");run.departed.add(player);world.returnHome(player);world.event(player,"DUNGEON_LEFT",Map.of("runId",run.id));if(run.state==DungeonRun.State.ACTIVE)step(world,now);}
    public void snapshot(UUID player,InstanceGateway world,long now){var run=forPlayer(player);if(run!=null){world.event(player,"DUNGEON_PROGRESS_UPDATED",view(run,now));if(run.state!=DungeonRun.State.ACTIVE)world.event(player,"DUNGEON_FINISHED",Map.of("run",view(run,now),"rewards",run.grants.getOrDefault(player,Map.of())));}else world.event(player,"DUNGEON_LIST",lobby(player,world));}
    public void snapshot(UUID player,InstanceGateway world,long now,String requested){if(forPlayer(player)!=null)snapshot(player,world,now);else world.event(player,"DUNGEON_LIST",lobby(player,world,requested));}
    public Map<String,Object> view(DungeonRun r,long now){return Map.ofEntries(Map.entry("runId",r.id),Map.entry("instanceId",r.instance.id),Map.entry("dungeonId",r.dungeonId),Map.entry("name",catalog.get(r.dungeonId).name()),Map.entry("mapId",r.instance.mapId),Map.entry("state",r.state.name()),Map.entry("wave",r.wave),Map.entry("totalWaves",3),Map.entry("remainingMs",Math.max(0,duration-(now-r.startedAt))),Map.entry("wipes",r.wipes),Map.entry("reason",r.reason),Map.entry("participants",r.instance.participants));}
    private void publishLobby(PartyRegistry.Party p,InstanceGateway world){for(UUID id:p.members)world.event(id,"DUNGEON_LIST",lobby(id,world));}
}
