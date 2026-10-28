package vn.tamtoc.realtime;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CommandRequests {
    private record Key(UUID actor,UUID request) {}
    private record Receipt(String fingerprint,long expiresAt) {}
    private final LinkedHashMap<Key,Receipt> completed=new LinkedHashMap<>();
    /** Called while holding the world lock. Successful operations are not repeated. */
    public synchronized boolean execute(UUID actor,String requestId,String fingerprint,Runnable action,long now){
        final UUID request;
        try{request=UUID.fromString(requestId);}catch(Exception e){throw new ProtocolException("INVALID_REQUEST_ID","Thao tác cần requestId UUID hợp lệ.");}
        Key key=new Key(actor,request);Receipt receipt=completed.get(key);
        if(receipt!=null&&receipt.expiresAt>now){if(!receipt.fingerprint.equals(fingerprint))throw new ProtocolException("REQUEST_ID_REUSED","requestId đã được dùng cho thao tác khác.");return false;}
        action.run();completed.put(key,new Receipt(fingerprint,now+15*60_000));
        completed.entrySet().removeIf(e->e.getValue().expiresAt<=now);while(completed.size()>10000)completed.remove(completed.keySet().iterator().next());
        return true;
    }
}
