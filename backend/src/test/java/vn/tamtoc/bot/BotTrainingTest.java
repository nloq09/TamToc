package vn.tamtoc.bot;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import vn.tamtoc.world.*;
import vn.tamtoc.combat.*;
import vn.tamtoc.character.*;
import vn.tamtoc.instance.*;
import vn.tamtoc.realtime.ProtocolException;

class BotTrainingTest {
    static class World implements InstanceGateway {
        Map<UUID,PlayerState> players=new HashMap<>();Map<UUID,Long> disconnects=new HashMap<>();List<String> events=new ArrayList<>();
        public PlayerState getPlayer(UUID id){return players.get(id);}public boolean isOnline(UUID id){return players.containsKey(id)&&!disconnects.containsKey(id);}public long disconnectedSince(UUID id){return disconnects.getOrDefault(id,0L);}
        public void moveTo(UUID id,String map,UUID instance,double x){var p=players.get(id);p.mapId=map;p.instanceId=instance;p.x=x;p.y=540;}
        public void returnHome(UUID id){var p=players.get(id);if(p!=null){p.mapId="thanh-van";p.instanceId=null;p.x=160;p.y=540;p.progress.hp=p.maxHp();p.progress.mp=p.maxMp();}}
        public void event(UUID id,String type,Object payload){events.add(type);}
        public Map<UUID,Map<String,Object>> grantDungeon(UUID run,List<UUID> members,int exp,int cult,int stones){throw new AssertionError("NPC practice cannot grant rewards");}
    }
    MapCatalog maps;CombatCatalog combat;BotCatalog catalog;BotRegistry bots;BotBrain brain;TrainingService training;InstanceRegistry instances;World world;PlayerState hero;BotState source;int baseline;
    @BeforeEach void setup()throws Exception{var json=new ObjectMapper();maps=new MapCatalog(json);combat=new CombatCatalog(json);catalog=new BotCatalog(json,maps,combat);var engine=new CombatEngine(combat);brain=new BotBrain(engine,combat);bots=new BotRegistry(maps,catalog,brain);instances=new InstanceRegistry();training=new TrainingService(instances,maps,catalog,bots,brain,engine);world=new World();hero=new PlayerState(new GameCharacter(UUID.randomUUID(),"PracticeTester",Race.HUMAN));hero.x=160;hero.y=540;world.players.put(hero.id,hero);source=bots.all().stream().filter(b->b.definition.id().equals("human-challenger")&&b.actor.mapId.equals("thanh-van")).findFirst().orElseThrow();baseline=bots.all().size();}
    @Test void aiRegistryNeverCreatesRealPlayersAndGuardTargetsOnlyHostileMonsters(){
        assertEquals(1,world.players.size());assertTrue(bots.all().stream().allMatch(b->b.actor.isAi()&&b.view().isAI()));assertEquals(baseline,bots.all().stream().map(b->b.actor.id).distinct().count());
        var guard=bots.all().stream().filter(b->b.actor.mapId.equals("thanh-van")&&b.definition.botType().equals("GUARD")).findFirst().orElseThrow();var wolf=new MonsterState(combat.monsters.get("WOLF"),"thanh-van",525,540);int hp=hero.progress.hp;
        bots.step(List.of(wolf),.05,1000,(b,t,p)->{},(p,m)->{});assertTrue(wolf.hp<80);assertEquals(hp,hero.progress.hp);int remaining=wolf.hp;bots.step(List.of(wolf),.05,1100,(b,t,p)->{},(p,m)->{});assertEquals(remaining,wolf.hp);assertTrue(guard.actor.cooldowns.containsKey("QI"));
        UUID id=guard.actor.id;guard.actor.progress.hp=0;bots.step(List.of(),.05,2000,(b,t,p)->{},(p,m)->{});assertEquals(BotState.State.DEAD,guard.state);bots.step(List.of(),.05,6999,(b,t,p)->{},(p,m)->{});assertEquals(0,guard.actor.progress.hp);bots.step(List.of(),.05,7000,(b,t,p)->{},(p,m)->{});assertEquals(id,guard.actor.id);assertEquals(guard.actor.maxHp(),guard.actor.progress.hp);assertEquals(baseline,bots.all().size());
    }
    @Test void wandererFollowsGroundRouteTalksAndRejectsRemoteInteractions(){
        var npc=bots.all().stream().filter(b->b.actor.mapId.equals("thanh-van")&&b.definition.botType().equals("WANDERER")).findFirst().orElseThrow();double start=npc.actor.x;
        for(int i=0;i<90;i++)bots.step(List.of(),.05,1000+i*50,(b,t,p)->{},(p,m)->{});assertTrue(Math.abs(npc.actor.x-start)>15);assertTrue(npc.actor.x>=449&&npc.actor.x<=561);assertEquals(540,npc.actor.y,.01);
        hero.x=npc.actor.x;var dialogue=(Map<?,?>)bots.dialogue(hero,npc.actor.id,6000);assertEquals(true,dialogue.get("isAI"));assertFalse(((List<?>)dialogue.get("lines")).isEmpty());double talking=npc.actor.x;bots.step(List.of(),.05,6100,(b,t,p)->{},(p,m)->{});assertEquals(talking,npc.actor.x,.001);assertEquals(BotState.State.TALK,npc.state);
        hero.mapId="u-minh";assertEquals("NPC_TOO_FAR",assertThrows(ProtocolException.class,()->bots.dialogue(hero,npc.actor.id,6200)).code());
    }
    @Test void requiresProactiveStartAndCountsDownBeforeSharedCombatAndCancel(){
        assertNull(training.forPlayer(hero.id));assertTrue(instances.all().isEmpty());assertThrows(ProtocolException.class,()->training.start(hero.id,source.actor.id,"IMPOSSIBLE",world,1000));hero.x=1000;assertThrows(ProtocolException.class,()->training.start(hero.id,source.actor.id,"EASY",world,1000));hero.x=160;
        var inventory=Map.copyOf(hero.progress.inventory);long exp=hero.exp,cult=hero.cultivation;var run=training.start(hero.id,source.actor.id,"EASY",world,1000);assertEquals(1,instances.all().size());assertEquals(Set.of(hero.id),run.instance.participants);assertEquals(baseline+1,bots.all().size());assertThrows(ProtocolException.class,()->training.cast(hero.id,"ATTACK",world,2000,(t,p)->{}));training.step(world,.05,4000);assertEquals("ACTIVE",run.state);
        hero.x=run.bot.actor.x-45;hero.facing=1;int hp=run.bot.actor.progress.hp,mp=hero.progress.mp;training.cast(hero.id,"QI",world,4100,(t,p)->{});assertTrue(run.bot.actor.progress.hp<hp);assertEquals(mp-8,hero.progress.mp);assertEquals("SKILL_COOLDOWN",assertThrows(ProtocolException.class,()->training.cast(hero.id,"QI",world,4200,(t,p)->{})).code());
        training.cancel(hero.id,"CANCELED",world,4300);assertNull(hero.instanceId);assertEquals(exp,hero.exp);assertEquals(cult,hero.cultivation);assertEquals(inventory,hero.progress.inventory);assertEquals(baseline,bots.all().size());assertTrue(instances.all().isEmpty());assertEquals(1,world.events.stream().filter(e->e.equals("TRAINING_RESULT")).count());assertThrows(ProtocolException.class,()->training.cancel(hero.id,"CANCELED",world,4400));
    }
    @Test void difficultiesChangeDeclaredStatsAndReactionWithoutBypassingTargetPolicy(){
        var easy=training.start(hero.id,source.actor.id,"EASY",world,1000);int hp=easy.bot.actor.maxHp();long think=easy.bot.thinkInterval();training.cancel(hero.id,"CANCELED",world,1100);var hard=training.start(hero.id,source.actor.id,"HARD",world,1200);assertTrue(hard.bot.actor.maxHp()>hp);assertTrue(hard.bot.thinkInterval()<think);assertTrue(hard.bot.actor.attack()>easy.bot.actor.attack());assertTrue(CombatTargetPolicy.training(hero,hard.bot.actor,hard.instance.id));assertFalse(CombatTargetPolicy.pvp(hero,hard.bot.actor,hard.instance.id));
        var outsider=new PlayerState(new GameCharacter(UUID.randomUUID(),"Other",Race.HUMAN));outsider.mapId=TrainingService.MAP;outsider.instanceId=hard.instance.id;assertFalse(CombatTargetPolicy.training(hero,outsider,hard.instance.id));outsider.instanceId=UUID.randomUUID();assertFalse(CombatTargetPolicy.training(outsider,hard.bot.actor,hard.instance.id));
    }
    @Test void knockoutDefeatTimeoutAndDisconnectCleanEveryTemporaryEntity(){
        var win=training.start(hero.id,source.actor.id,"EASY",world,1000);training.step(world,.05,4000);win.bot.actor.progress.hp=0;training.afterCombat(hero.id,world,4100);training.afterCombat(hero.id,world,4200);assertEquals("VICTORY",win.reason);assertEquals(baseline,bots.all().size());
        var lose=training.start(hero.id,source.actor.id,"HARD",world,5000);hero.progress.hp=0;training.afterCombat(hero.id,world,5100);assertEquals("DEFEAT",lose.reason);assertEquals(hero.maxHp(),hero.progress.hp);
        var timeout=training.start(hero.id,source.actor.id,"EASY",world,6000);training.step(world,.05,timeout.activeAt+180000);assertEquals("TIMEOUT",timeout.reason);
        var disconnected=training.start(hero.id,source.actor.id,"EASY",world,200000);world.disconnects.put(hero.id,201000L);training.step(world,.05,220999);assertNotEquals("FINISHED",disconnected.state);training.step(world,.05,221000);assertEquals("ABANDONED",disconnected.reason);assertEquals(baseline,bots.all().size());assertTrue(instances.all().isEmpty());assertEquals(0,hero.exp);assertEquals(0,hero.cultivation);
    }
}
