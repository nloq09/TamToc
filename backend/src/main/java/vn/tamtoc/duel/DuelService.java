package vn.tamtoc.duel;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.tamtoc.instance.*;
import vn.tamtoc.realtime.ProtocolException;
import vn.tamtoc.world.PlayerState;

@Service
public class DuelService {
    public static final String MAP="duel-arena";
    public record Invitation(UUID id,UUID challenger,UUID opponent,long expiresAt) {}
    private final Map<UUID,Invitation> invitations=new LinkedHashMap<>();
    private final Map<UUID,DuelRun> runs=new LinkedHashMap<>();
    private final Map<UUID,UUID> active=new HashMap<>(),lastFinished=new HashMap<>();
    private final InstanceRegistry instances;private final ActivityStore store;
    private final long inviteMs,countdownMs,durationMs,disconnectMs,cleanupMs;
    public DuelService(InstanceRegistry i,ActivityStore s,@Value("${game.duel.invitation-ms:30000}")long invite,@Value("${game.duel.countdown-ms:5000}")long countdown,@Value("${game.duel.duration-ms:180000}")long duration,@Value("${game.duel.disconnect-ms:15000}")long disconnect,@Value("${game.instance-cleanup-ms:60000}")long cleanup){instances=i;store=s;inviteMs=invite;countdownMs=countdown;durationMs=duration;disconnectMs=disconnect;cleanupMs=cleanup;}
    private static void require(boolean ok,String code,String message){if(!ok)throw new ProtocolException(code,message);}
    public DuelRun forPlayer(UUID id){return runs.get(active.get(id));}
    private void eligible(UUID a,UUID b,InstanceGateway world){
        var one=world.getPlayer(a);var two=world.getPlayer(b);
        require(!a.equals(b)&&one!=null&&two!=null&&world.isOnline(a)&&world.isOnline(b),"DUEL_TARGET_UNAVAILABLE","Đối thủ không trực tuyến.");
        require(!active.containsKey(a)&&!active.containsKey(b)&&one.instanceId==null&&two.instanceId==null,"DUEL_BUSY","Một người chơi đang tham gia hoạt động riêng.");
        require(one.progress.hp>0&&two.progress.hp>0,"PLAYER_DEAD","Cả hai cần còn sống.");
        require(one.mapId.equals(two.mapId)&&Math.abs(one.x-two.x)<=800,"DUEL_TARGET_TOO_FAR","Đối thủ phải ở gần trong cùng bản đồ.");
    }
    public Invitation challenge(UUID a,UUID b,InstanceGateway world,long now){eligible(a,b,world);require(invitations.values().stream().noneMatch(i->i.challenger.equals(a)&&i.opponent.equals(b)&&i.expiresAt>now),"DUEL_INVITE_PENDING","Đã có lời thách đấu đang chờ.");var invite=new Invitation(UUID.randomUUID(),a,b,now+inviteMs);invitations.put(invite.id,invite);world.event(b,"DUEL_CHALLENGE_RECEIVED",Map.of("id",invite.id,"challengerId",a,"name",world.getPlayer(a).name,"expiresAt",invite.expiresAt));return invite;}
    private Invitation invitation(UUID id,UUID target,long now){var invite=invitations.get(id);require(invite!=null&&invite.opponent.equals(target),"DUEL_INVITE_NOT_FOUND","Không có lời thách đấu phù hợp.");require(invite.expiresAt>now,"DUEL_INVITE_EXPIRED","Thách đấu đã hết hạn.");return invite;}
    public void decline(UUID target,UUID inviteId,InstanceGateway world,long now){var i=invitation(inviteId,target,now);invitations.remove(i.id);world.event(i.challenger,"DUEL_DECLINED",Map.of("invitationId",i.id));world.event(target,"DUEL_INVITATION_RESOLVED",Map.of("invitationId",i.id));}
    public DuelRun accept(UUID target,UUID invitationId,InstanceGateway world,long now){
        var invite=invitation(invitationId,target,now);eligible(invite.challenger,target,world);invitations.remove(invite.id);
        var instance=instances.create(MAP,"DUEL",List.of(invite.challenger,target));var run=new DuelRun(invite.challenger,target,instance,now+countdownMs);runs.put(run.id,run);
        for(UUID id:instance.participants){active.put(id,run.id);lastFinished.remove(id);var p=world.getPlayer(id);p.progress.hp=p.maxHp();p.progress.mp=p.maxMp();p.shieldUntil=0;p.empoweredUntil=0;world.moveTo(id,MAP,instance.id,id.equals(run.challenger)?300:900);world.event(id,"DUEL_PREPARE",view(run,now));}
        world.event(target,"DUEL_INVITATION_RESOLVED",Map.of("invitationId",invite.id));return run;
    }
    public List<PlayerState> opponents(UUID player,InstanceGateway world){var run=forPlayer(player);require(run!=null&&run.state==DuelRun.State.ACTIVE,"DUEL_NOT_ACTIVE","Chưa hết đếm ngược hoặc trận đấu đang kết thúc.");var other=world.getPlayer(run.other(player));return other==null?List.of():List.of(other);}
    public void forfeit(UUID player,String reason,InstanceGateway world,long now){var run=forPlayer(player);require(run!=null,"NOT_IN_DUEL","Không ở trong trận thách đấu.");finish(run,run.other(player),reason,world,now);}
    public void afterCombat(UUID player,InstanceGateway world,long now){var run=forPlayer(player);if(run!=null&&run.state==DuelRun.State.ACTIVE)checkDeath(run,world,now);}
    private boolean checkDeath(DuelRun run,InstanceGateway world,long now){var a=world.getPlayer(run.challenger);var b=world.getPlayer(run.opponent);if(a!=null&&b!=null&&(a.progress.hp<=0||b.progress.hp<=0)){finish(run,a.progress.hp<=0&&b.progress.hp<=0?null:a.progress.hp<=0?b.id:a.id,"KNOCKOUT",world,now);return true;}return false;}
    public void step(InstanceGateway world,long now){
        for(var i:List.copyOf(invitations.values()))if(i.expiresAt<=now){invitations.remove(i.id);world.event(i.opponent,"DUEL_INVITATION_EXPIRED",Map.of("invitationId",i.id));}
        for(var run:List.copyOf(runs.values())){
            if(run.state==DuelRun.State.FINISHING){if(now>=run.retryAt)commit(run,world,now);continue;}
            if(run.state==DuelRun.State.FINISHED){if(now-run.endedAt>=cleanupMs){lastFinished.values().removeIf(id->id.equals(run.id));runs.remove(run.id);run.state=DuelRun.State.CLEANED;}continue;}
            boolean aGone=world.getPlayer(run.challenger)==null||(world.disconnectedSince(run.challenger)>0&&now-world.disconnectedSince(run.challenger)>=disconnectMs);
            boolean bGone=world.getPlayer(run.opponent)==null||(world.disconnectedSince(run.opponent)>0&&now-world.disconnectedSince(run.opponent)>=disconnectMs);
            if(aGone||bGone){finish(run,aGone&&bGone?null:aGone?run.opponent:run.challenger,"DISCONNECT",world,now);continue;}
            if(run.state==DuelRun.State.PREPARING&&now>=run.activeAt){run.state=DuelRun.State.ACTIVE;for(UUID id:run.instance.participants)world.event(id,"DUEL_STARTED",view(run,now));}
            if(run.state==DuelRun.State.ACTIVE){if(checkDeath(run,world,now))continue;if(now-run.activeAt>=durationMs)finish(run,null,"TIMEOUT",world,now);}
        }
    }
    private void finish(DuelRun run,UUID winner,String reason,InstanceGateway world,long now){if(run.state==DuelRun.State.FINISHING||run.state==DuelRun.State.FINISHED)return;run.winner=winner;run.reason=reason;run.endedAt=now;run.state=DuelRun.State.FINISHING;commit(run,world,now);}
    private void commit(DuelRun run,InstanceGateway world,long now){
        try{store.finishDuel(run.id,run.challenger,run.opponent,run.winner,run.reason,Math.min(run.activeAt,run.endedAt),run.endedAt);}
        catch(RuntimeException e){run.retryAt=now+5000;org.slf4j.LoggerFactory.getLogger(DuelService.class).error("Duel result persistence failed for {}",run.id,e);return;}
        run.state=DuelRun.State.FINISHED;run.instance.active=false;
        for(UUID id:run.instance.participants){active.remove(id);lastFinished.put(id,run.id);world.returnHome(id);world.event(id,"DUEL_FINISHED",view(run,now));}instances.remove(run.instance.id);
    }
    public void snapshot(UUID player,InstanceGateway world,long now){var run=forPlayer(player);if(run==null)run=runs.get(lastFinished.get(player));if(run!=null)world.event(player,run.state==DuelRun.State.FINISHED?"DUEL_FINISHED":"DUEL_STATE",view(run,now));for(var invite:invitations.values())if(invite.opponent.equals(player)&&invite.expiresAt>now)world.event(player,"DUEL_CHALLENGE_RECEIVED",Map.of("id",invite.id,"challengerId",invite.challenger,"name",world.getPlayer(invite.challenger)==null?"Đạo hữu":world.getPlayer(invite.challenger).name,"expiresAt",invite.expiresAt));}
    public Map<String,Object> view(DuelRun run,long now){var view=new LinkedHashMap<String,Object>();view.put("duelId",run.id);view.put("instanceId",run.instance.id);view.put("state",run.state.name());view.put("challengerId",run.challenger);view.put("opponentId",run.opponent);view.put("winnerCharacterId",run.winner);view.put("reason",run.reason);view.put("secondsRemaining",run.state==DuelRun.State.PREPARING?Math.max(0,(run.activeAt-now+999)/1000):Math.max(0,(durationMs-(now-run.activeAt)+999)/1000));view.put("durationMs",Math.max(0,(run.endedAt==0?now:run.endedAt)-run.activeAt));return view;}
}
