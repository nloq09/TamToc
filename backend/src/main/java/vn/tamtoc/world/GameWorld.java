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

@Service
public class GameWorld {
    private static final Logger log=LoggerFactory.getLogger(GameWorld.class);
    private record Connection(WebSocketSession session, PlayerState player) {}
    private final Map<String,Connection> connections=new HashMap<>();
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
    public GameWorld(MapCatalog m, CharacterService c, ObjectMapper j,
        @Value("${game.tick-ms}") long tickMs, @Value("${game.input-timeout-ms}") long timeout,
        ProgressStore progress,CombatCatalog combat,CombatEngine engine,MonsterAi ai,ProgressionRules rules) {
        maps=m; characters=c; json=j; dt=tickMs/1000.0; inputTimeout=timeout;
        this.progress=progress;this.combat=combat;this.engine=engine;this.ai=ai;
        this.rules=rules;
        for(String map:List.of("thanh-van","u-minh","van-yeu","trung-chau")) {
            for(int i=0;i<5;i++)monsters.add(new MonsterState(combat.monsters.get("WOLF"),map,800+i*180,maps.get(map).ground()));
            monsters.add(new MonsterState(combat.monsters.get("SOLDIER"),map,1800,maps.get(map).ground()));
            monsters.add(new MonsterState(combat.monsters.get("SPIRIT"),map,2000,maps.get(map).ground()));
            if(map.equals("trung-chau"))monsters.add(new MonsterState(combat.monsters.get("BOSS"),map,2750,maps.get(map).ground()));
        }
    }
    public synchronized void join(WebSocketSession session, GameCharacter c) {
        if (connections.containsKey(session.getId())) throw new IllegalArgumentException("Đã vào thế giới.");
        if (connections.values().stream().anyMatch(v -> v.player.id.equals(c.id))) throw new IllegalArgumentException("Nhân vật đang được điều khiển ở kết nối khác.");
        flushSaves();
        var fresh=characters.owned(c.userId,c.id);
        PlayerState player=new PlayerState(fresh);
        player.progress=progress.load(c.id);
        player.cooldowns=player.progress.cooldowns;rules.recalculate(player);
        connections.put(session.getId(),new Connection(session,player));
        send(session,"AUTH_RESULT",Map.of("ok",true,"characterId",c.id));
        snapshot(session,player);
        send(session,"SKILLS_UPDATED",combat.unlocked(player));
        broadcast(player.mapId,"PLAYER_JOINED",player.view());
    }
    private void snapshot(WebSocketSession s, PlayerState p) {
        send(s,"WORLD_SNAPSHOT",Map.of("selfId",p.id,"map",maps.get(p.mapId),"players",views(p.mapId),"monsters",monsterViews(p.mapId),"loot",lootViews(p.mapId),"tick",tick,"serverTime",System.currentTimeMillis()));
    }
    private List<PlayerState.View> views(String map) { return connections.values().stream().map(Connection::player).filter(p -> p.mapId.equals(map)).map(PlayerState::view).toList(); }
    public synchronized void input(String sid, int direction, boolean jump, long sequence) {
        PlayerState p=player(sid);
        if (sequence <= p.sequence) return;
        p.sequence=sequence; p.direction=Integer.signum(direction);
        if(p.direction!=0)p.facing=p.direction;
        if (jump && !p.jumpHeld) p.jumpQueued=true;
        p.jumpHeld=jump; p.lastInput=System.currentTimeMillis();
    }
    public synchronized void interact(String sid) {
        PlayerState p=player(sid);
        if(p.progress.hp<=0)throw new IllegalArgumentException("Đang chờ hồi sinh.");
        var nearby=loot.stream().filter(l->l.mapId().equals(p.mapId)&&Math.abs(l.x()-p.x)<80&&Math.abs(l.y()-p.y)<80).findFirst();
        if(nearby.isPresent()) {var item=nearby.get();rules.addItem(p,item.itemId());loot.remove(item);checkpoint(p);send(connections.get(sid).session,"INVENTORY_UPDATED",p.progress.inventory);return;}
        if(Math.abs(p.x-300)<35) {
            p.progress.guideMet=true;checkpoint(p);send(connections.get(sid).session,"NPC_DIALOG",Map.of("name","Tiếp Dẫn Sứ","message","Mở Nhiệm vụ để nhận thử thách Yêu Lang. Từ cấp 10, nhận Con Đường Tu Luyện rồi chọn một thế lực."));return;
        }
        var portal=maps.get(p.mapId).portals().stream().filter(v -> Math.abs(v.x()-p.x)<70 && p.y > maps.get(p.mapId).ground()-80).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Hãy đến gần cổng dịch chuyển rồi nhấn E."));
        String old=p.mapId; broadcast(old,"PLAYER_LEFT",Map.of("id",p.id));
        p.mapId=portal.target(); p.x=p.mapId.equals("trung-chau") ? 650 : 2000; p.y=maps.get(p.mapId).ground(); p.vy=0; p.direction=0;
        checkpoint(p); snapshot(connections.get(sid).session,p); broadcast(p.mapId,"PLAYER_JOINED",p.view());
    }
    public synchronized void chat(String sid, String message) {
        PlayerState p=player(sid); String clean=message.strip();
        if (clean.isEmpty() || clean.length()>200) throw new IllegalArgumentException("Tin nhắn phải có 1–200 ký tự.");
        broadcast(p.mapId,"CHAT",Map.of("name",p.name,"message",clean));
    }
    private PlayerState player(String sid) {
        Connection c=connections.get(sid); if(c==null) throw new IllegalArgumentException("Chưa vào thế giới."); return c.player;
    }
    private List<MonsterState.View> monsterViews(String map){return monsters.stream().filter(m->m.mapId.equals(map)).map(MonsterState::view).toList();}
    private List<LootState> lootViews(String map){return loot.stream().filter(l->l.mapId().equals(map)).toList();}
    public synchronized void cast(String sid,String skill){
        PlayerState p=player(sid);
        engine.cast(p,skill,maps.get(p.mapId),monsters,System.currentTimeMillis(),(t,v)->broadcast(p.mapId,t,v),this::reward);
    }
    private void reward(PlayerState killer,MonsterState monster){
        loot.add(new LootState(UUID.randomUUID(),monster.definition.id().equals("WOLF")?"WOLF_FANG":"POTION",monster.mapId,monster.x,monster.y,System.currentTimeMillis()+60000));
        for(var c:connections.values()){
            PlayerState p=c.player;
            if(!p.mapId.equals(monster.mapId)||p.progress.hp<=0||!monster.contributions.containsKey(p.id)||Math.abs(p.x-monster.x)>600)continue;
            // Shared credit requires actual damage contribution, no client-supplied rewards.
            int previousLevel=p.level;
            boolean leveled=ProgressionRules.addExp(p,monster.definition.exp());p.cultivation+=monster.definition.cultivation();p.progress.totalKills++;
            if(monster.definition.id().equals("WOLF"))p.progress.wolfKills++;
            if(leveled)send(c.session,"PLAYER_LEVEL_UP",p.view());
            if(previousLevel<rules.organizationLevel()&&p.level>=rules.organizationLevel())send(c.session,"ORGANIZATION_UNLOCKED",Map.of("level",p.level));
            send(c.session,"QUEST_UPDATED",p.progress.quests);
            checkpoint(p);
        }
    }
    @Scheduled(fixedRateString="${game.tick-ms}") public synchronized void simulate() {
        tick++; long now=System.currentTimeMillis();
        loot.removeIf(l->l.expiresAt()<now);
        for (Connection c:connections.values()) {
            PlayerState p=c.player;
            if(now-p.lastInput>inputTimeout) { p.direction=0; p.jumpHeld=false; p.jumpQueued=false; }
            if(p.progress.hp<=0){
                if(now>=p.progress.respawnAt){p.progress.hp=p.maxHp();p.progress.mp=p.maxMp();p.x=180;p.y=maps.get(p.mapId).ground();p.vy=0;}
                else continue;
            }
            Movement.step(p,maps.get(p.mapId),dt);
            if(now>=p.regenAt){p.regenAt=now+1000;p.progress.mp=Math.min(p.maxMp(),p.progress.mp+2);if(p.x<420)p.progress.hp=Math.min(p.maxHp(),p.progress.hp+8);}
        }
        for(var m:monsters){var local=connections.values().stream().map(Connection::player).filter(p->p.mapId.equals(m.mapId)).toList();if(!local.isEmpty())ai.step(m,local,dt,now,(t,v)->broadcast(m.mapId,t,v));}
    }
    @Scheduled(fixedRateString="${game.broadcast-ms}") public synchronized void updates() {
        for(String map:connections.values().stream().map(c->c.player.mapId).distinct().toList())
            broadcast(map,"ENTITY_UPDATE",Map.of("players",views(map),"monsters",monsterViews(map),"loot",lootViews(map),"tick",tick,"serverTime",System.currentTimeMillis()));
    }
    @Scheduled(fixedDelay=15000) public synchronized void checkpointAll() { for(var c:connections.values()) checkpoint(c.player); }
    private void checkpoint(PlayerState p) {
        var snapshot=progress.freeze(p);
        saves.submit(()-> { try { progress.save(snapshot); } catch(Exception e) { log.error("Progress save failed for {}",snapshot.id(),e); } });
    }
    private void flushSaves(){
        try{saves.submit(()->{}).get(10,TimeUnit.SECONDS);}catch(Exception e){throw new IllegalStateException("Persistence queue unavailable",e);}
    }
    /** All REST gameplay mutations use the same world lock as combat and input. */
    public synchronized <T> T access(UUID owner,UUID id,java.util.function.Function<PlayerState,T> action,boolean write){
        var owned=characters.owned(owner,id);
        var live=connections.values().stream().filter(c->c.player.id.equals(id)).findFirst().orElse(null);
        PlayerState p;
        if(live==null){flushSaves();owned=characters.owned(owner,id);p=new PlayerState(owned);p.progress=progress.load(id);p.cooldowns=p.progress.cooldowns;rules.recalculate(p);}else p=live.player;
        var before=write?progress.freeze(p):null;
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
    public synchronized void leave(String sid) {
        Connection c=connections.remove(sid);
        if(c!=null) { checkpoint(c.player); broadcast(c.player.mapId,"PLAYER_LEFT",Map.of("id",c.player.id)); }
    }
    private void broadcast(String map,String type,Object payload) {
        for(Connection c:connections.values()) if(c.player.mapId.equals(map)) send(c.session,type,payload);
    }
    public void send(WebSocketSession session,String type,Object payload) {
        if(!session.isOpen()) return;
        try { session.sendMessage(new TextMessage(json.writeValueAsString(Map.of("type",type,"payload",payload)))); }
        catch(Exception e) { log.debug("Socket send failed {}",session.getId()); try { session.close(); } catch(Exception ignored) {} }
    }
    @PreDestroy public void shutdown() throws InterruptedException {
        checkpointAll(); saves.shutdown(); if(!saves.awaitTermination(10,TimeUnit.SECONDS)) log.warn("Pending position checkpoints at shutdown");
    }
}
