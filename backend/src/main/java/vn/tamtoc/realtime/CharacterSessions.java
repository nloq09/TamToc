package vn.tamtoc.realtime;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import vn.tamtoc.world.PlayerState;

/** World owns gameplay locking. This registry additionally serializes lease changes. */
@Component
public class CharacterSessions {
    public enum Status { ONLINE, DISCONNECTED_GRACE }
    public static final class Lease {
        public final UUID owner;
        public final PlayerState player;
        public final boolean resumable;
        private final String secret;
        public WebSocketSession session;
        public String connectionId;
        public Status status=Status.ONLINE;
        public long expiresAt;
        public long disconnectedAt;
        public boolean deltaUpdates;
        private Lease(UUID owner,PlayerState player,boolean resumable,String secret){this.owner=owner;this.player=player;this.resumable=resumable;this.secret=secret;}
    }
    public record Binding(Lease lease,boolean resumed,WebSocketSession replaced) {}
    private final Map<UUID,Lease> characters=new LinkedHashMap<>();
    private final Map<String,Lease> connections=new HashMap<>();
    private final SecureRandom random=new SecureRandom();
    private final long graceMs;
    public CharacterSessions(@Value("${game.reconnect-grace-ms:20000}")long graceMs){this.graceMs=graceMs;}

    public synchronized Binding bind(UUID owner,UUID character,WebSocketSession socket,String resumeSecret,boolean resumable,Supplier<PlayerState> load,long now){
        if(connections.containsKey(socket.getId()))throw new ProtocolException("ALREADY_AUTHENTICATED","Đã vào thế giới.");
        Lease lease=characters.get(character);
        WebSocketSession replaced=null;
        boolean resumed=lease!=null;
        if(lease!=null){
            if(lease.status==Status.DISCONNECTED_GRACE&&lease.expiresAt<=now)throw new ProtocolException("RESUME_EXPIRED","Phiên phục hồi đã hết hạn.");
            boolean authorized=lease.resumable&&resumable&&lease.owner.equals(owner)&&resumeSecret!=null
                &&MessageDigest.isEqual(lease.secret.getBytes(StandardCharsets.UTF_8),resumeSecret.getBytes(StandardCharsets.UTF_8));
            if(!authorized)throw new ProtocolException("CHARACTER_ALREADY_CONNECTED","Nhân vật đang được điều khiển ở kết nối khác hoặc đang chờ phục hồi.");
            replaced=lease.session;
            if(lease.connectionId!=null)connections.remove(lease.connectionId);
        }else{
            byte[] bytes=new byte[32];random.nextBytes(bytes);
            lease=new Lease(owner,load.get(),resumable,Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            characters.put(character,lease);
        }
        lease.session=socket;lease.connectionId=socket.getId();lease.status=Status.ONLINE;lease.expiresAt=0;lease.disconnectedAt=0;
        lease.player.direction=0;lease.player.jumpHeld=false;lease.player.jumpQueued=false;lease.player.sequence=-1;
        lease.player.connectionStatus=Status.ONLINE.name();
        connections.put(socket.getId(),lease);
        return new Binding(lease,resumed,replaced);
    }
    public synchronized String resumeSecret(Lease lease){return lease.secret;}
    public synchronized Lease byConnection(String id){return connections.get(id);}
    public synchronized Lease byCharacter(UUID id){return characters.get(id);}
    public synchronized List<Lease> all(){return List.copyOf(characters.values());}
    public synchronized List<Lease> online(){return connections.values().stream().toList();}
    public synchronized Lease disconnect(String connectionId,boolean intentional,long now){
        Lease lease=connections.remove(connectionId);
        // An old socket closing after takeover must never detach the new controller.
        if(lease==null||!connectionId.equals(lease.connectionId))return null;
        lease.session=null;lease.connectionId=null;lease.disconnectedAt=now;lease.player.direction=0;lease.player.jumpHeld=false;lease.player.jumpQueued=false;
        if(lease.resumable&&!intentional){lease.status=Status.DISCONNECTED_GRACE;lease.expiresAt=now+graceMs;lease.player.connectionStatus=lease.status.name();}
        else characters.remove(lease.player.id);
        return lease;
    }
    public synchronized List<Lease> expire(long now){
        List<Lease> expired=characters.values().stream().filter(l->l.status==Status.DISCONNECTED_GRACE&&l.expiresAt<=now).toList();
        expired.forEach(l->characters.remove(l.player.id));return expired;
    }
}
