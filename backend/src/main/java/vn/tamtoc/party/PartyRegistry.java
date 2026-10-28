package vn.tamtoc.party;

import java.util.*;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.tamtoc.realtime.ProtocolException;

@Component
public class PartyRegistry {
    public static final class Party {
        public final UUID id=UUID.randomUUID();
        public UUID leader;
        public final LinkedHashSet<UUID> members=new LinkedHashSet<>();
        public long revision=1;
        Party(UUID leader){this.leader=leader;members.add(leader);}
    }
    public record Invitation(UUID id,UUID partyId,UUID inviterId,UUID targetId,long expiresAt) {}
    private final Map<UUID,Party> parties=new LinkedHashMap<>();
    private final Map<UUID,UUID> membership=new HashMap<>();
    private final Map<UUID,Invitation> invitations=new LinkedHashMap<>();
    private final int maxSize;
    private final long invitationMs;
    public PartyRegistry(@Value("${game.party.max-size:4}")int maxSize,@Value("${game.party.invitation-ms:60000}")long invitationMs){this.maxSize=maxSize;this.invitationMs=invitationMs;}
    private static void require(boolean ok,String code,String message){if(!ok)throw new ProtocolException(code,message);}
    public synchronized Party get(UUID member){return parties.get(membership.get(member));}
    public synchronized List<Party> all(){return List.copyOf(parties.values());}
    public int maxSize(){return maxSize;}
    public synchronized List<Invitation> pending(UUID target,long now){return invitations.values().stream().filter(i->i.targetId.equals(target)&&i.expiresAt>now).toList();}
    public synchronized Invitation invite(UUID actor,UUID target,Predicate<UUID> online,long now){
        require(!actor.equals(target),"INVALID_TARGET","Không thể tự mời chính mình.");
        require(online.test(actor)&&online.test(target),"PLAYER_NOT_ONLINE","Người chơi không trực tuyến.");
        require(get(target)==null,"ALREADY_IN_PARTY","Người chơi đã có tổ đội.");
        Party party=get(actor);
        require(party==null||party.leader.equals(actor),"LEADER_REQUIRED","Chỉ trưởng đội được mời.");
        require(party==null||party.members.size()<maxSize,"PARTY_FULL","Tổ đội đã đủ người.");
        require(invitations.values().stream().noneMatch(i->i.inviterId.equals(actor)&&i.targetId.equals(target)&&i.expiresAt>now),"INVITE_PENDING","Lời mời đang chờ trả lời.");
        if(party==null){party=new Party(actor);parties.put(party.id,party);membership.put(actor,party.id);}
        Invitation invite=new Invitation(UUID.randomUUID(),party.id,actor,target,now+invitationMs);invitations.put(invite.id,invite);return invite;
    }
    private Invitation invitation(UUID target,UUID id,long now){var i=invitations.get(id);require(i!=null&&i.targetId.equals(target),"INVITE_NOT_PENDING","Không có lời mời đang chờ.");require(i.expiresAt>now,"INVITE_EXPIRED","Lời mời đã hết hạn.");return i;}
    public synchronized Party accept(UUID target,UUID id,Predicate<UUID> online,long now){
        var i=invitation(target,id,now);var p=parties.get(i.partyId);
        require(p!=null,"PARTY_NOT_FOUND","Tổ đội đã giải tán.");require(get(target)==null,"ALREADY_IN_PARTY","Mỗi nhân vật chỉ có một tổ đội.");
        require(online.test(target)&&online.test(p.leader),"PLAYER_NOT_ONLINE","Người chơi hoặc trưởng đội không trực tuyến.");require(p.members.size()<maxSize,"PARTY_FULL","Tổ đội đã đủ 4 người.");
        p.members.add(target);membership.put(target,p.id);p.revision++;invitations.remove(id);return p;
    }
    public synchronized Invitation decline(UUID target,UUID id,long now){var i=invitation(target,id,now);invitations.remove(id);return i;}
    public synchronized void transfer(UUID actor,UUID target,Predicate<UUID> online){var p=leader(actor);require(p.members.contains(target)&&online.test(target),"INVALID_LEADER","Trưởng đội mới phải là thành viên trực tuyến.");p.leader=target;p.revision++;}
    public synchronized void kick(UUID actor,UUID target,Predicate<UUID> online){var p=leader(actor);require(!actor.equals(target)&&p.members.contains(target),"INVALID_TARGET","Không thể đuổi người chơi này.");leave(target,online);}
    private Party leader(UUID actor){var p=get(actor);require(p!=null,"NOT_IN_PARTY","Chưa có tổ đội.");require(p.leader.equals(actor),"LEADER_REQUIRED","Chỉ trưởng đội có quyền thao tác.");return p;}
    public synchronized Party leave(UUID actor,Predicate<UUID> online){
        var p=get(actor);if(p==null)return null;membership.remove(actor);p.members.remove(actor);p.revision++;
        if(p.members.isEmpty()){parties.remove(p.id);invitations.values().removeIf(i->i.partyId.equals(p.id));return null;}
        if(p.leader.equals(actor))p.leader=p.members.stream().sorted(Comparator.comparing(UUID::toString)).filter(online).findFirst().orElseGet(()->p.members.stream().min(Comparator.comparing(UUID::toString)).orElseThrow());
        return p;
    }
    public synchronized List<Invitation> expire(long now){var expired=invitations.values().stream().filter(i->i.expiresAt<=now||!parties.containsKey(i.partyId)).toList();expired.forEach(i->invitations.remove(i.id));return expired;}
}
