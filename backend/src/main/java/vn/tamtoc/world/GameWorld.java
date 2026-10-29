package vn.tamtoc.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.PreDestroy;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.*;
import vn.tamtoc.character.*;
import vn.tamtoc.combat.*;
import vn.tamtoc.progression.*;
import vn.tamtoc.realtime.*;
import vn.tamtoc.party.*;
import vn.tamtoc.instance.*;
import vn.tamtoc.dungeon.*;
import vn.tamtoc.duel.*;
import vn.tamtoc.bot.*;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class GameWorld implements InstanceGateway {
    private static final Logger log=LoggerFactory.getLogger(GameWorld.class);
    private final CharacterSessions sessions;
    private final NetworkMetrics metrics;
    private final UUID worldEpoch=UUID.randomUUID();
    private long eventVersion,lastSlowLog;
    private final MapCatalog maps; private final CharacterService characters; private final ObjectMapper json;
    private final ExecutorService saves=Executors.newSingleThreadExecutor();
    private final double dt; private final long inputTimeout;
    private long tick;
    private final ProgressStore progress;
    private final CombatCatalog combat;
    private final CombatEngine engine;
    private final MonsterAi ai;
    private final ProgressionRules rules;
    private final List<LootState> loot=new ArrayList<>();
    private final List<MonsterState> monsters=new ArrayList<>();
    private final PartyRegistry parties;
    private final PartyEventPublisher partyEvents;
    private final PartyRewardPolicy rewards;
    private final CommandRequests requests;
    private long lastPartyBroadcast;
    private final InstanceRegistry instances;
    private final DungeonService dungeons;
    private final ActivityStore history;
    private final DuelService duels;
    private final MonsterDefeats defeats;
    private final LootRoller lootRoller;
    private final BotRegistry bots;
    private final TrainingService training;
    private final WorldInterest interest;
    private final MonsterPopulation population;
    private record Scope(String map,UUID instance) {}
    private record PendingReward(Map<UUID,ProgressStore.Save> saves,Map<UUID,Map<String,Object>> rewards) {}
    private final Map<UUID,PendingReward> pendingRewards=new HashMap<>();
    private boolean rewardPending(UUID id){return pendingRewards.values().stream().anyMatch(r->r.saves.containsKey(id));}
    public GameWorld(MapCatalog m, CharacterService c, ObjectMapper j,
        @Value("${game.tick-ms}") long tickMs, @Value("${game.input-timeout-ms}") long timeout,
        ProgressStore progress,CombatCatalog combat,CombatEngine engine,MonsterAi ai,ProgressionRules rules,CharacterSessions sessions,NetworkMetrics metrics,PartyRegistry parties,PartyEventPublisher partyEvents,PartyRewardPolicy rewards,CommandRequests requests,InstanceRegistry instances,DungeonService dungeons,ActivityStore history,DuelService duels,MonsterPopulation population,MonsterDefeats defeats,LootRoller lootRoller,BotRegistry bots,TrainingService training,WorldInterest interest) {
        maps=m; characters=c; json=j; dt=tickMs/1000.0; inputTimeout=timeout;
        this.progress=progress;this.combat=combat;this.engine=engine;this.ai=ai;
        this.rules=rules;this.sessions=sessions;this.metrics=metrics;
        this.parties=parties;this.partyEvents=partyEvents;this.rewards=rewards;this.requests=requests;
        this.instances=instances;this.dungeons=dungeons;this.history=history;
        this.duels=duels;
        rules.validateWorldData(maps,combat);this.defeats=defeats;this.lootRoller=lootRoller;this.bots=bots;this.training=training;this.interest=interest;this.population=population;monsters.addAll(population.all());
        for(var definition:combat.monsters.values())for(var drop:definition.dropTable())if(!rules.hasItem(drop.itemId()))throw new IllegalArgumentException("Unknown drop item "+drop.itemId());
    }
    public synchronized void join(WebSocketSession socket,GameCharacter character,String resumeSecret,boolean resumable,boolean deltaUpdates) {
        expireSessions();flushSaves();
        var binding=sessions.bind(character.userId,character.id,socket,resumeSecret,resumable,()->{
            var fresh=characters.owned(character.userId,character.id);
            PlayerState p=new PlayerState(fresh);p.progress=progress.load(p.id);p.cooldowns=p.progress.cooldowns;rules.recalculate(p);
            maps.sanitize(p);if(resumeSecret!=null){var spawn=maps.respawn(p);p.x=spawn.x();p.y=spawn.y();p.vy=0;}
            return p;
        },System.currentTimeMillis());
        var lease=binding.lease();lease.deltaUpdates=deltaUpdates;
        rules.discover(lease.player,lease.player.mapId);
        if(binding.replaced()!=null)try{binding.replaced().close(new CloseStatus(4001,"Session replaced"));}catch(Exception ignored){}
        Map<String,Object> auth=new LinkedHashMap<>();
        auth.put("ok",true);auth.put("characterId",character.id);auth.put("connectionId",socket.getId());auth.put("protocolVersion",deltaUpdates?3:resumable?2:1);auth.put("resumed",binding.resumed());auth.put("worldEpoch",worldEpoch);
        if(resumable)auth.put("resumeToken",sessions.resumeSecret(lease));
        send(socket,"AUTH_RESULT",auth);if(binding.resumed())metrics.resumes.increment();
        snapshot(socket,lease.player);send(socket,"SKILLS_UPDATED",combat.unlocked(lease.player));
        broadcast(lease.player,"PLAYER_JOINED",lease.player.view());
        partyState(lease.player.id);
        dungeons.snapshot(lease.player.id,this,System.currentTimeMillis());
        duels.snapshot(lease.player.id,this,System.currentTimeMillis());training.snapshot(lease.player.id,this,System.currentTimeMillis());
    }
    private Map<String,Object> state(PlayerState p){
        Map<String,Object> state=new LinkedHashMap<>();
        state.put("bots",npcViews(p));state.put("players",views(p));var visible=monsterViews(p);visible.forEach(m->p.progress.discoveredMonsters.add(m.speciesId()));state.put("monsters",visible);state.put("loot",lootViews(p));
        state.put("tickDurationMs",metrics.lastTickMs);state.put("tick",tick);state.put("worldTick",tick);state.put("mapId",p.mapId);state.put("instanceId",p.instanceId);state.put("serverTime",System.currentTimeMillis());return state;
    }
    private void snapshot(WebSocketSession socket,PlayerState p){if(socket==null||!socket.isOpen())return;var state=state(p);state.put("selfId",p.id);state.put("map",maps.get(p.mapId));state.put("connectionId",socket.getId());var controller=sessions.byConnection(socket.getId());if(controller!=null&&controller.deltaUpdates)state.put("interestVersion",interest.snapshot(socket.getId(),state));send(socket,"WORLD_SNAPSHOT",state);}
    public synchronized void resumeSnapshot(String sid){var lease=controller(sid);snapshot(lease.session,lease.player);}
    private CharacterSessions.Lease controller(String sid){var lease=sessions.byConnection(sid);if(lease==null)throw new ProtocolException("SESSION_NOT_ACTIVE","Kết nối không còn điều khiển nhân vật.");return lease;}
    private static boolean sameScope(PlayerState p,String map,UUID instance){return p.mapId.equals(map)&&Objects.equals(p.instanceId,instance);}
    private List<PlayerState.View> views(PlayerState observer){return sessions.all().stream().map(l->l.player).filter(p->sameScope(p,observer.mapId,observer.instanceId)&&(p.id.equals(observer.id)||interest.visible(observer,p.x,p.y))).map(PlayerState::view).toList();}
    private List<BotState.View> npcViews(PlayerState p){return bots.views(p).stream().filter(b->interest.visible(p,b.x(),b.y())).toList();}
    public synchronized void input(String sid, int direction, boolean jump, long sequence) {
        PlayerState p=player(sid);
        if (sequence <= p.sequence) return;
        p.sequence=sequence; p.direction=Integer.signum(direction);
        if(p.direction!=0)p.facing=p.direction;
        if (jump && !p.jumpHeld) p.jumpQueued=true;
        p.jumpHeld=jump; p.lastInput=System.currentTimeMillis();
    }
    public synchronized void interact(String sid){interact(sid,null);}
    public synchronized void interact(String sid,UUID targetEntity){
        PlayerState p=player(sid);
        if(targetEntity!=null){sendTo(p.id,"DIALOGUE_STARTED",bots.dialogue(p,targetEntity,System.currentTimeMillis()));return;}
        if(p.instanceId!=null&&instances.get(p.instanceId)!=null&&Set.of("DUEL","TRAINING").contains(instances.get(p.instanceId).kind))throw new ProtocolException("INTERACTION_DISABLED","Không thể tương tác NPC trong đấu trường.");
        if(p.progress.hp<=0)throw new IllegalArgumentException("Đang chờ hồi sinh.");
        var nearby=lootViews(p).stream().filter(l->Math.abs(l.x()-p.x)<80&&Math.abs(l.y()-p.y)<80).findFirst();
        if(nearby.isPresent()) {var item=nearby.get();rules.addItems(p,item.itemId(),item.quantity());loot.remove(item);checkpoint(p);send(controller(sid).session,"INVENTORY_UPDATED",p.progress.inventory);return;}
        if(p.instanceId!=null)throw new ProtocolException("INTERACTION_DISABLED","Trong phó bản chỉ nhặt vật phẩm hoặc rời bằng bảng phó bản.");
        var npc=maps.get(p.mapId).npcSpawns().stream().filter(n->Math.abs(p.x-n.x())<45&&Math.abs(p.y-n.y())<70).findFirst();
        if(npc.isPresent()){
            p.progress.guideMet=true;checkpoint(p);send(controller(sid).session,"NPC_DIALOG",Map.of("name",npc.get().name(),"message",String.join("\n",bots.script(npc.get().dialogueId()))));return;
        }
        if(maps.get(p.mapId).portals().stream().anyMatch(v->v.triggerBounds().contains(p.x,p.y))){usePortal(sid,null);return;}
        var bot=bots.nearest(p);if(bot.isPresent()){sendTo(p.id,"DIALOGUE_STARTED",bots.dialogue(p,bot.get().actor.id,System.currentTimeMillis()));return;}
        usePortal(sid,null);
    }
    public synchronized void usePortal(String sid,String portalId){
        var p=player(sid);long now=System.currentTimeMillis();
        if(now-p.lastPortalAt<1000)throw new ProtocolException("PORTAL_COOLDOWN","Vừa chuyển bản đồ, hãy chờ một giây.");
        var portal=maps.portal(p,portalId);var spawn=maps.validatePortal(p,portal);var target=maps.get(portal.target());
        if(Set.of("DUNGEON","ARENA").contains(target.mapType()))throw new ProtocolException("DUNGEON_ENTRY_REQUIRED","Hãy dùng bảng Phó bản và xác nhận tổ đội để mở instance riêng.");
        if(sessions.all().stream().filter(l->sameScope(l.player,target.id(),null)).count()>=target.maxPlayers())throw new ProtocolException("MAP_FULL","Bản đồ đã đủ người chơi.");
        String previous=p.mapId;p.lastPortalAt=now;moveTo(p.id,target.id(),null,spawn.x(),spawn.y());
        sendTo(p.id,"MAP_ENTERED",Map.of("mapId",p.mapId,"fromMapId",previous,"spawnId",spawn.id(),"version",target.version()));
    }

    public synchronized void chat(String sid, String message) {
        PlayerState p=player(sid); String clean=message.strip();
        if (clean.isEmpty() || clean.length()>200) throw new IllegalArgumentException("Tin nhắn phải có 1–200 ký tự.");
        broadcast(p,"CHAT",Map.of("name",p.name,"message",clean));
    }
    private PlayerState player(String sid) {
        return controller(sid).player;
    }
    private List<MonsterState> runtimeMonsters(PlayerState p){if(p.instanceId!=null){var instance=instances.get(p.instanceId);return instance==null?List.of():List.copyOf(instance.monsters);}return monsters.stream().filter(m->m.mapId.equals(p.mapId)).toList();}
    private List<MonsterState.View> monsterViews(PlayerState p){return runtimeMonsters(p).stream().filter(m->(!m.scheduled||m.scheduledActive)&&interest.visible(p,m.x,m.y)).map(MonsterState::view).toList();}
    private List<MonsterState> allMonsters(){var all=new ArrayList<>(monsters);for(var instance:instances.all())if(instance.active)all.addAll(instance.monsters);return all;}
    private List<LootState> lootViews(PlayerState p){return loot.stream().filter(l->l.mapId().equals(p.mapId)&&Objects.equals(l.instanceId(),p.instanceId)&&l.ownerId().equals(p.id)&&interest.visible(p,l.x(),l.y())).toList();}
    public synchronized void cast(String sid,String skill){
        cast(sid,skill,null);
    }
    public synchronized void cast(String sid,String skill,UUID explicitPlayerTarget){
        PlayerState p=player(sid);
        if(training.forPlayer(p.id)!=null){var run=training.forPlayer(p.id);if(explicitPlayerTarget!=null&&!explicitPlayerTarget.equals(run.bot.actor.id))throw new ProtocolException("INVALID_TARGET","Mục tiêu không thuộc bài đối luyện.");training.cast(p.id,skill,this,System.currentTimeMillis(),(t,v)->broadcast(p,t,v));return;}
        if(explicitPlayerTarget!=null){var duel=duels.forPlayer(p.id);if(duel==null)throw new ProtocolException("PVP_NOT_ALLOWED","Chỉ được tấn công người chơi trong đấu trường tự nguyện.");if(!duel.other(p.id).equals(explicitPlayerTarget))throw new ProtocolException("INVALID_TARGET","Mục tiêu không thuộc trận thách đấu.");}
        if(duels.forPlayer(p.id)!=null){engine.castDuel(p,skill,maps.get(p.mapId),duels.opponents(p.id,this),p.instanceId,System.currentTimeMillis(),(t,v)->broadcast(p,t,v));duels.afterCombat(p.id,this,System.currentTimeMillis());}
        else engine.cast(p,skill,maps.get(p.mapId),runtimeMonsters(p),System.currentTimeMillis(),(t,v)->broadcast(p,t,v),this::reward);
        p.lastActiveAt=System.currentTimeMillis();
    }
    private void reward(PlayerState killer,MonsterState monster){
        var defeated=defeats.once(monster,System.currentTimeMillis());if(defeated.isEmpty())return;
        broadcast(monster.mapId,monster.instanceId,"MONSTER_DEFEATED",defeated.get());
        for(var share:rewards.split(monster,sessions.online().stream().map(l->l.player).toList(),System.currentTimeMillis())){
            var p=share.player();var lease=sessions.byCharacter(p.id);int previousLevel=p.level;
            boolean leveled=ProgressionRules.addExp(p,share.exp());p.cultivation+=share.cultivation();p.progress.totalKills++;
            if(monster.definition.id().equals("WOLF")||monster.definition.speciesId().equals("yeu_lang"))p.progress.wolfKills++;
            Set<String> creditIds=new HashSet<>(List.of(monster.definition.id(),monster.definition.speciesId()));switch(monster.definition.speciesId()){case "yeu_lang"->creditIds.add("WOLF");case "ma_binh"->creditIds.add("SOLDIER");case "linh_thu"->{creditIds.add("SPIRIT");creditIds.add("SPIRIT_VETERAN");}case "huyen_moc_yeu_vuong"->creditIds.add("BOSS");case "bien_canh_loan_thu"->creditIds.add("WOLF_ALPHA");case "thien_mon_tran_linh"->creditIds.add("RUIN_SENTINEL");default->{}}
            for(String key:creditIds){p.progress.killsByDefinition.merge(key,1,Integer::sum);p.progress.killsByDefinition.merge(monster.mapId+":"+key,1,Integer::sum);}rules.killed(p,monster);
            for(var drop:lootRoller.roll(monster,p.id))loot.add(new LootState(UUID.randomUUID(),drop.itemId(),monster.mapId,monster.x,monster.y,System.currentTimeMillis()+60000,p.id,p.instanceId,drop.quantity()));
            if(leveled)send(lease.session,"PLAYER_LEVEL_UP",p.view());
            if(previousLevel<rules.organizationLevel()&&p.level>=rules.organizationLevel())send(lease.session,"ORGANIZATION_UNLOCKED",Map.of("level",p.level));
            send(lease.session,"QUEST_UPDATED",p.progress.quests);checkpoint(p);
        }
    }
    @Scheduled(fixedRateString="${game.tick-ms}") public synchronized void simulate() {
        long started=System.nanoTime();tick++; long now=System.currentTimeMillis();expireSessions();
        loot.removeIf(l->l.expiresAt()<now);
        for(var invite:parties.expire(now)){sendTo(invite.targetId(),"PARTY_INVITATION_EXPIRED",Map.of("invitationId",invite.id()));sendTo(invite.inviterId(),"PARTY_INVITATION_EXPIRED",Map.of("invitationId",invite.id()));}
        for (var c:sessions.all()) {
            PlayerState p=c.player;
            if(c.status!=CharacterSessions.Status.ONLINE||now-p.lastInput>inputTimeout) { p.direction=0; p.jumpHeld=false; p.jumpQueued=false; }
            if(p.progress.hp<=0){
                if(duels.forPlayer(p.id)!=null||training.forPlayer(p.id)!=null)continue;
                if(now>=p.progress.respawnAt){p.progress.hp=p.maxHp();p.progress.mp=p.maxMp();var spawn=maps.respawn(p);p.x=spawn.x();p.y=spawn.y();p.vy=0;}
                else continue;
            }
            p.movementScale=p.slowedUntil>now?.5:1;double previousX=p.x;Movement.step(p,maps.get(p.mapId),dt);if(Math.abs(p.x-previousX)>.1)p.lastActiveAt=now;
            if(p.y>maps.get(p.mapId).height()+96){p.progress.hp=0;p.progress.respawnAt=now+4000;sendTo(p.id,"PLAYER_DIED",Map.of("id",p.id,"respawnMs",4000));continue;}
            if(p.instanceId==null)for(var zone:maps.get(p.mapId).safeZones())if(zone.spawnId()!=null&&zone.bounds().contains(p.x,p.y)&&!zone.spawnId().equals(p.progress.checkpoints.get(p.mapId))){p.progress.checkpoints.put(p.mapId,zone.spawnId());checkpoint(p);sendTo(p.id,"CHECKPOINT_REACHED",Map.of("mapId",p.mapId,"spawnId",zone.spawnId()));}
            if(now>=p.regenAt){p.regenAt=now+1000;p.progress.mp=Math.min(p.maxMp(),p.progress.mp+2);if(p.instanceId==null&&maps.get(p.mapId).heals(p.x,p.y))p.progress.hp=Math.min(p.maxHp(),p.progress.hp+8);}
        }
        Set<String> activeMaps=new HashSet<>();Set<Scope> activeScopes=new HashSet<>();for(var lease:sessions.all()){activeMaps.add(lease.player.mapId);activeScopes.add(new Scope(lease.player.mapId,lease.player.instanceId));}
        Map<String,Integer> eligibleLevels=new HashMap<>();for(var lease:sessions.online())if(lease.player.instanceId==null)eligibleLevels.merge(lease.player.mapId,lease.player.level,Math::max);population.schedule(now,eligibleLevels,(monster,type,payload)->broadcast(monster.mapId,null,type,payload));
        bots.step(allMonsters(),dt,now,(bot,t,payload)->broadcast(bot.actor,t,payload),this::reward,activeMaps);
        Map<Scope,List<PlayerState>> occupants=new HashMap<>();for(var lease:sessions.all())occupants.computeIfAbsent(new Scope(lease.player.mapId,lease.player.instanceId),key->new ArrayList<>()).add(lease.player);
        for(var actor:bots.guards())if(activeScopes.contains(new Scope(actor.mapId,actor.instanceId)))occupants.computeIfAbsent(new Scope(actor.mapId,actor.instanceId),key->new ArrayList<>()).add(actor);
        for(var m:allMonsters()){if(!activeScopes.contains(new Scope(m.mapId,m.instanceId))||(m.instanceId!=null&&m.hp<=0))continue;var local=occupants.getOrDefault(new Scope(m.mapId,m.instanceId),List.of());ai.step(m,local,maps.get(m.mapId),dt,now,(t,v)->broadcast(m.mapId,m.instanceId,t,v));}
        dungeons.step(this,now);
        duels.step(this,now);training.step(this,dt,now);
        long duration=System.nanoTime()-started;metrics.tick(duration,(long)(dt*1000));
        if(duration>dt*1e9&&now-lastSlowLog>5000){lastSlowLog=now;log.warn("World tick {} took {} ms",tick,duration/1e6);}
    }
    @Scheduled(fixedRateString="${game.broadcast-ms}") public synchronized void updates() {
        for(var lease:sessions.online()){var state=state(lease.player);if(!lease.deltaUpdates)send(lease.session,"ENTITY_UPDATE",state);else{var delta=interest.delta(lease.connectionId,state);if(delta==null)snapshot(lease.session,lease.player);else send(lease.session,"ENTITY_DELTA",delta);}}
        if(System.currentTimeMillis()-lastPartyBroadcast>=500){lastPartyBroadcast=System.currentTimeMillis();for(var party:parties.all())publishParty(party.leader);for(var lease:sessions.online())if(lease.player.instanceId!=null){var instance=instances.get(lease.player.instanceId);if(instance!=null&&instance.kind.equals("DUNGEON"))dungeons.snapshot(lease.player.id,this,System.currentTimeMillis());else if(instance!=null&&instance.kind.equals("DUEL"))duels.snapshot(lease.player.id,this,System.currentTimeMillis());else if(instance!=null&&instance.kind.equals("TRAINING"))training.snapshot(lease.player.id,this,System.currentTimeMillis());}}
    }
    @Scheduled(fixedDelay=15000) public synchronized void checkpointAll() { for(var c:sessions.all()) checkpoint(c.player); }
    private void checkpoint(PlayerState p) {
        if(rewardPending(p.id))return;
        var snapshot=progress.freeze(p);
        saves.submit(()-> { try { progress.save(snapshot); } catch(Exception e) { log.error("Progress save failed for {}",snapshot.id(),e); } });
    }
    private void flushSaves(){
        try{saves.submit(()->{}).get(10,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException("Persistence queue unavailable",e);}
    }
    /** All REST gameplay mutations use the same world lock as combat and input. */
    public synchronized <T> T access(UUID owner,UUID id,java.util.function.Function<PlayerState,T> action,boolean write){
        var owned=characters.owned(owner,id);
        var live=sessions.byCharacter(id);
        PlayerState p;
        if(live==null){flushSaves();owned=characters.owned(owner,id);p=new PlayerState(owned);p.progress=progress.load(id);p.cooldowns=p.progress.cooldowns;rules.recalculate(p);maps.sanitize(p);}else p=live.player;
        var before=write?progress.freeze(p):null;
        if(write&&(duels.forPlayer(id)!=null||training.forPlayer(id)!=null))throw new ProtocolException("ACTIVITY_LOCKED","Không thể thay đổi tiến trình hoặc vật phẩm trong trận thách đấu.");
        if(write&&rewardPending(id))throw new ProtocolException("REWARD_PENDING","Đang lưu phần thưởng phó bản. Vui lòng thử lại sau.");
        try{
            T result=action.apply(p);
            if(write){
                var snapshot=progress.freeze(p);saves.submit(()->progress.save(snapshot)).get(10,TimeUnit.SECONDS);
                if(live!=null){
                    send(live.session,"PROGRESSION_UPDATED",p.view());send(live.session,"SKILLS_UPDATED",combat.unlocked(p));send(live.session,"QUEST_UPDATED",p.progress.quests);
                    if(!Objects.equals(before.organization(),p.organization)){send(live.session,"ORGANIZATION_JOINED",Map.of("organization",p.organization));send(live.session,"CULTIVATION_PATH_UNLOCKED",Map.of("path",p.path));}
                    if(before.level()<rules.organizationLevel()&&p.level>=rules.organizationLevel())send(live.session,"ORGANIZATION_UNLOCKED",Map.of("level",p.level));
                }
            }
            return result;
        }catch(Exception e){
            if(write&&before!=null){
                try{p.progress=json.readValue(before.data(),ProgressData.class);p.cooldowns=p.progress.cooldowns;p.level=before.level();p.exp=before.exp();p.cultivation=before.cultivation();p.realm=before.realm();p.realmStage=before.stage();p.organization=before.organization();p.path=before.path();rules.recalculate(p);}catch(Exception corruption){throw new IllegalStateException(corruption);}
            }
            if(e instanceof IllegalArgumentException invalid)throw invalid;
            throw new IllegalStateException("Could not save character progress",e);
        }
    }
    public synchronized void disconnect(String sid,boolean intentional){
        interest.forget(sid);
        var controlling=sessions.byConnection(sid);if(intentional&&controlling!=null&&duels.forPlayer(controlling.player.id)!=null)duels.forfeit(controlling.player.id,"LOGOUT",this,System.currentTimeMillis());
        if(intentional&&controlling!=null&&training.forPlayer(controlling.player.id)!=null)training.cancel(controlling.player.id,"LOGOUT",this,System.currentTimeMillis());
        var lease=sessions.disconnect(sid,intentional,System.currentTimeMillis());if(lease==null)return;
        metrics.disconnects.increment();checkpoint(lease.player);if(intentional)leaveParty(lease.player.id);else publishParty(lease.player.id);
        if(sessions.byCharacter(lease.player.id)==null)broadcast(lease.player,"PLAYER_LEFT",Map.of("id",lease.player.id));
        else broadcast(lease.player,"PLAYER_CONNECTION_STATE",Map.of("id",lease.player.id,"status",lease.status.name(),"expiresAt",lease.expiresAt));
    }
    private boolean online(UUID id){var lease=sessions.byCharacter(id);return lease!=null&&lease.status==CharacterSessions.Status.ONLINE;}
    public synchronized Object inspectContent(){return maps.all().stream().map(m->Map.of("mapId",m.id(),"mapType",m.mapType(),"openMonsterSlots",monsters.stream().filter(v->v.mapId.equals(m.id())).count(),"npcEntities",bots.all().stream().filter(v->v.actor.mapId.equals(m.id())).count(),"instances",instances.all().stream().filter(v->v.mapId.equals(m.id())).map(v->Map.of("instanceId",v.id,"active",v.active,"monsters",v.monsters.size(),"participants",v.participants.size())).toList())).toList();}
    private void sendTo(UUID id,String type,Object payload){var lease=sessions.byCharacter(id);if(lease!=null)send(lease.session,type,payload);}
    private void publishParty(UUID member){partyEvents.publish(member,(id,view)->sendTo(id,"PARTY_UPDATED",view));}
    private void partyState(UUID member){sendTo(member,"PARTY_UPDATED",partyEvents.view(member));for(var i:parties.pending(member,System.currentTimeMillis()))invitation(i);}
    private void invitation(PartyRegistry.Invitation i){var inviter=sessions.byCharacter(i.inviterId());sendTo(i.targetId(),"PARTY_INVITATION_RECEIVED",Map.of("id",i.id(),"inviterId",i.inviterId(),"name",inviter==null?"Đạo hữu":inviter.player.name,"expiresAt",i.expiresAt()));}
    private void leaveParty(UUID member){var before=parties.get(member);if(before==null)return;var remaining=parties.leave(member,this::online);sendTo(member,"PARTY_DISBANDED",Map.of("reason","LEFT_PARTY"));if(remaining!=null)publishParty(remaining.leader);}
    private static UUID id(JsonNode p,String field){try{String raw=p.path(field).asText();if(raw.length()!=36)throw new IllegalArgumentException();return UUID.fromString(raw);}catch(Exception e){throw new ProtocolException("INVALID_ID","ID không hợp lệ: "+field);}}
    public synchronized void partyCommand(String sid,String type,JsonNode payload,String requestId){
        var actor=player(sid);long now=System.currentTimeMillis();
        if(type.equals("NEARBY_PLAYERS_REQUEST")){sendTo(actor.id,"NEARBY_PLAYERS",views(actor).stream().filter(p->!p.id().equals(actor.id)&&p.connectionStatus().equals("ONLINE")).toList());return;}
        if(type.equals("PARTY_STATE_REQUEST")){partyState(actor.id);return;}
        if(type.equals("PARTY_CHAT")){
            var party=parties.get(actor.id);if(party==null)throw new ProtocolException("NOT_IN_PARTY","Chưa có tổ đội.");
            String text=payload.path("text").asText().strip();if(text.isEmpty()||text.length()>200)throw new ProtocolException("INVALID_CHAT","Tin nhắn phải có 1–200 ký tự.");
            for(UUID member:party.members)sendTo(member,"CHAT",Map.of("name",actor.name,"message",text,"channel","PARTY"));return;
        }
        boolean executed=requests.execute(actor.id,requestId,type+":"+payload.toString(),()->{
            var currentParty=parties.get(actor.id);if(currentParty!=null&&dungeons.busy(currentParty.id))throw new ProtocolException("PARTY_BUSY","Không thể đổi tổ đội trong lúc phó bản đang chạy.");
            switch(type){
                case "PARTY_INVITE" -> {var invite=parties.invite(actor.id,id(payload,"targetCharacterId"),this::online,now);invitation(invite);sendTo(actor.id,"PARTY_INVITATION_SENT",Map.of("invitationId",invite.id()));publishParty(actor.id);}
                case "PARTY_ACCEPT" -> {UUID invite=id(payload,"invitationId");var pending=parties.pending(actor.id,now).stream().filter(i->i.id().equals(invite)).findFirst();if(pending.isPresent()&&dungeons.busy(pending.get().partyId()))throw new ProtocolException("PARTY_BUSY","Tổ đội đang tham gia phó bản, không thể nhận lời mời cũ.");parties.accept(actor.id,invite,this::online,now);sendTo(actor.id,"PARTY_INVITATION_RESOLVED",Map.of("invitationId",invite));publishParty(actor.id);}
                case "PARTY_DECLINE" -> {var invite=parties.decline(actor.id,id(payload,"invitationId"),now);sendTo(actor.id,"PARTY_INVITATION_RESOLVED",Map.of("invitationId",invite.id()));sendTo(invite.inviterId(),"PARTY_INVITATION_DECLINED",Map.of("invitationId",invite.id()));}
                case "PARTY_LEAVE" -> leaveParty(actor.id);
                case "PARTY_KICK" -> {UUID target=id(payload,"targetCharacterId");parties.kick(actor.id,target,this::online);sendTo(target,"PARTY_DISBANDED",Map.of("reason","KICKED"));publishParty(actor.id);}
                case "PARTY_TRANSFER_LEADER" -> {parties.transfer(actor.id,id(payload,"targetCharacterId"),this::online);publishParty(actor.id);}
                default -> throw new ProtocolException("UNSUPPORTED_MESSAGE","Thao tác tổ đội không hợp lệ.");
            }
        },now);
        sendTo(actor.id,"COMMAND_RESULT",Map.of("requestId",requestId,"status","OK","replayed",!executed));
    }
    private void expireSessions(){for(var lease:sessions.expire(System.currentTimeMillis())){leaveParty(lease.player.id);checkpoint(lease.player);broadcast(lease.player,"PLAYER_LEFT",Map.of("id",lease.player.id));}}
    @Override public PlayerState getPlayer(UUID id){var lease=sessions.byCharacter(id);return lease==null?null:lease.player;}
    @Override public boolean isOnline(UUID id){return online(id);}
    @Override public long disconnectedSince(UUID id){var lease=sessions.byCharacter(id);return lease==null?0:lease.disconnectedAt;}
    @Override public void event(UUID player,String type,Object payload){sendTo(player,type,payload);}
    @Override public void progressEvent(UUID player,String type,String map){var p=getPlayer(player);if(p!=null){rules.credit(p,type,map,null,null,null);checkpoint(p);sendTo(player,"QUEST_UPDATED",p.progress.quests);}}
    @Override public void moveTo(UUID id,String map,UUID instance,double x){moveTo(id,map,instance,x,maps.get(map).ground());}
    private void moveTo(UUID id,String map,UUID instance,double x,double y){
        var p=getPlayer(id);if(p==null)return;
        if(instance!=null&&p.instanceId==null){p.returnMapId=p.mapId;p.returnX=p.x;p.returnY=p.y;}
        broadcast(p,"PLAYER_LEFT",Map.of("id",p.id));p.mapId=maps.get(map).id();p.instanceId=instance;p.x=x;p.y=y;p.vy=0;p.direction=0;p.jumpHeld=false;p.jumpQueued=false;rules.discover(p,p.mapId);
        var lease=sessions.byCharacter(id);snapshot(lease.session,p);broadcast(p,"PLAYER_JOINED",p.view());checkpoint(p);
    }
    @Override public void returnHome(UUID id){var p=getPlayer(id);if(p==null)return;String map=p.returnMapId==null?"trung-chau":p.returnMapId;double x=p.returnMapId==null?350:p.returnX;p.progress.hp=p.maxHp();p.progress.mp=p.maxMp();p.shieldUntil=0;p.empoweredUntil=0;p.slowedUntil=0;p.movementScale=1;moveTo(id,map,null,x);p.returnMapId=null;checkpoint(p);}
    @Override public Map<UUID,Map<String,Object>> grantDungeon(UUID run,List<UUID> eligible,int exp,int cultivation,int stones){
        var pending=pendingRewards.get(run);
        if(pending==null){
            Map<UUID,ProgressStore.Save> after=new LinkedHashMap<>();Map<UUID,Map<String,Object>> rewards=new LinkedHashMap<>();
            for(UUID id:eligible){var p=getPlayer(id);ProgressionRules.addExp(p,exp);p.cultivation+=cultivation;p.progress.stones+=stones;int amount=p.progress.inventory.getOrDefault("POTION",0)<99?1:0;if(amount==1)rules.addItem(p,"POTION");after.put(id,progress.freeze(p));rewards.put(id,Map.of("exp",exp,"cultivation",cultivation,"stones",stones,"itemId","POTION","itemQuantity",amount));}
            pending=new PendingReward(Map.copyOf(after),Map.copyOf(rewards));pendingRewards.put(run,pending);
        }
        // Keep the same immutable grant on retry, including an unknown commit outcome.
        // Ordinary checkpoints/mutations are blocked until the ledger confirms completion.
        final var prepared=pending;
        try{String detail=json.writeValueAsString(prepared.rewards);saves.submit(()->history.completeDungeon(run,prepared.saves,detail)).get();}
        catch(Exception e){throw new IllegalStateException("Dungeon reward could not commit",e);}
        pendingRewards.remove(run);for(UUID id:prepared.saves.keySet()){var p=getPlayer(id);if(p!=null)checkpoint(p);}
        return prepared.rewards;
    }
    private void restoreProgress(PlayerState p,ProgressStore.Save s){try{p.progress=json.readValue(s.data(),ProgressData.class);p.cooldowns=p.progress.cooldowns;p.level=s.level();p.exp=s.exp();p.cultivation=s.cultivation();p.realm=s.realm();p.realmStage=s.stage();rules.recalculate(p);}catch(Exception e){throw new IllegalStateException(e);}}
    public synchronized void dungeonCommand(String sid,String type,JsonNode p,String requestId){
        var actor=player(sid);long now=System.currentTimeMillis();
        if(duels.forPlayer(actor.id)!=null)throw new ProtocolException("DUEL_BUSY","Không thể thao tác phó bản trong trận thách đấu.");
        switch(type){
            case "DUNGEON_LIST_REQUEST" -> dungeons.snapshot(actor.id,this,now,p.path("dungeonId").asText());
            case "DUNGEON_READY_SET" -> {if(!p.path("ready").isBoolean())throw new ProtocolException("INVALID_READY","ready phải là boolean.");dungeons.ready(actor.id,p.path("ready").asBoolean(),this,p.path("dungeonId").asText());}
            case "DUNGEON_START_REQUEST" -> {boolean executed=requests.execute(actor.id,requestId,type+":"+p,()->dungeons.start(actor.id,p.path("dungeonId").asText(),this,now),now);sendTo(actor.id,"COMMAND_RESULT",Map.of("requestId",requestId,"status","OK","replayed",!executed));}
            case "DUNGEON_LEAVE_REQUEST" -> dungeons.leave(actor.id,this,now);
            default -> throw new ProtocolException("UNSUPPORTED_MESSAGE","Thao tác phó bản không hợp lệ.");
        }
    }
    public synchronized void trainingCommand(String sid,String type,JsonNode p,String requestId){
        var actor=player(sid);long now=System.currentTimeMillis();
        if(type.equals("TRAINING_STATE_REQUEST")){training.snapshot(actor.id,this,now);sendTo(actor.id,"NPC_LIST",npcViews(actor));return;}
        boolean executed=requests.execute(actor.id,requestId,type+":"+p,()->{
            if(type.equals("START_TRAINING"))training.start(actor.id,id(p,"botEntityId"),p.path("difficulty").asText(),this,now);
            else if(type.equals("CANCEL_TRAINING"))training.cancel(actor.id,"CANCELED",this,now);
            else throw new ProtocolException("UNSUPPORTED_MESSAGE","Thao tác đối luyện không hợp lệ.");
        },now);sendTo(actor.id,"COMMAND_RESULT",Map.of("requestId",requestId,"status","OK","replayed",!executed));
    }
    public synchronized void duelCommand(String sid,String type,JsonNode p,String requestId){
        var actor=player(sid);long now=System.currentTimeMillis();
        if(type.equals("DUEL_STATE_REQUEST")){duels.snapshot(actor.id,this,now);sendTo(actor.id,"NEARBY_PLAYERS",views(actor).stream().filter(v->!v.id().equals(actor.id)&&v.connectionStatus().equals("ONLINE")).toList());return;}
        boolean executed=requests.execute(actor.id,requestId,type+":"+p,()->{
            switch(type){
                case "DUEL_CHALLENGE" -> {var invite=duels.challenge(actor.id,id(p,"targetCharacterId"),this,now);sendTo(actor.id,"DUEL_CHALLENGE_SENT",Map.of("id",invite.id()));}
                case "DUEL_ACCEPT" -> duels.accept(actor.id,id(p,"duelInvitationId"),this,now);
                case "DUEL_DECLINE" -> duels.decline(actor.id,id(p,"duelInvitationId"),this,now);
                case "DUEL_FORFEIT" -> duels.forfeit(actor.id,"FORFEIT",this,now);
                default -> throw new ProtocolException("UNSUPPORTED_MESSAGE","Thao tác thách đấu không hợp lệ.");
            }
        },now);
        sendTo(actor.id,"COMMAND_RESULT",Map.of("requestId",requestId,"status","OK","replayed",!executed));
    }
    private void broadcast(PlayerState scope,String type,Object payload){var point=WorldInterest.point(payload);broadcastAt(scope.mapId,scope.instanceId,type,payload,point==null?new WorldInterest.Point(scope.x,scope.y):point);}
    private void broadcast(String map,UUID instance,String type,Object payload){broadcastAt(map,instance,type,payload,WorldInterest.point(payload));}
    private void broadcastAt(String map,UUID instance,String type,Object payload,WorldInterest.Point point){for(var lease:sessions.online())if(sameScope(lease.player,map,instance)&&(type.equals("CHAT")||type.startsWith("WORLD_BOSS_")||point==null||interest.visible(lease.player,point.x(),point.y())))send(lease.session,type,payload);}
    public synchronized void send(WebSocketSession session,String type,Object payload){
        if(session==null||!session.isOpen())return;
        try{var message=new TextMessage(json.writeValueAsString(Map.of("type",type,"payload",payload,"worldTick",tick,"worldEpoch",worldEpoch,"stateVersion",++eventVersion)));session.sendMessage(message);metrics.messages.increment();metrics.bytes.add(message.getPayloadLength());}
        catch(Exception e){metrics.exceptions.increment();log.warn("Socket send failed {}",session.getId(),e);try{session.close();}catch(Exception ignored){}}
    }
    @PreDestroy public void shutdown() throws InterruptedException {
        checkpointAll(); saves.shutdown(); if(!saves.awaitTermination(10,TimeUnit.SECONDS)) log.warn("Pending position checkpoints at shutdown");
    }
}
