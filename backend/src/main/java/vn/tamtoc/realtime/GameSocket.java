package vn.tamtoc.realtime;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import vn.tamtoc.character.CharacterService;
import vn.tamtoc.security.RateLimits;
import vn.tamtoc.world.GameWorld;

@Component
public class GameSocket extends TextWebSocketHandler {
    private static class State {
        final WebSocketSession session; final long opened=System.currentTimeMillis();
        long window=opened; int messages; volatile boolean authenticated;volatile long lastReceived=opened;
        State(WebSocketSession s) { session=new ConcurrentWebSocketSessionDecorator(s,1000,65536); }
    }
    private final Map<String,State> sessions=new ConcurrentHashMap<>();
    private final ObjectMapper json; private final TicketService tickets; private final CharacterService characters; private final GameWorld world; private final RateLimits limits;
    public GameSocket(ObjectMapper j,TicketService t,CharacterService c,GameWorld w,RateLimits l) { json=j; tickets=t; characters=c; world=w; limits=l; }
    @Value("${game.ws-idle-timeout-ms:15000}")private long idleTimeout;
    private final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(GameSocket.class);
    @org.springframework.beans.factory.annotation.Autowired private NetworkMetrics metrics;
    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            limits.check("ws-open:" + session.getRemoteAddress().getAddress().getHostAddress(),60);
            session.setTextMessageSizeLimit(2048); sessions.put(session.getId(),new State(session));
        } catch(Exception e) { session.close(CloseStatus.POLICY_VIOLATION); }
    }
    @Override protected void handleTextMessage(WebSocketSession raw,TextMessage message) throws Exception {
        State s=sessions.get(raw.getId()); if(s==null) return;
        s.lastReceived=System.currentTimeMillis();
        long now=System.currentTimeMillis(); if(now-s.window>1000) { s.window=now; s.messages=0; }
        if(++s.messages>60) { raw.close(CloseStatus.POLICY_VIOLATION); return; }
        String messageType="";
        try {
            JsonNode root=json.readTree(message.getPayload());
            if(!root.isObject() || !root.path("type").isTextual() || !root.path("payload").isObject()) throw new ProtocolException("INVALID_MESSAGE","Sai định dạng message.");
            String type=root.path("type").asText(); JsonNode p=root.path("payload");
            messageType=type;
            if(!s.authenticated) {
                if(!type.equals("AUTH")) throw new ProtocolException("AUTH_REQUIRED","AUTH phải là message đầu tiên.");
                var ticket=tickets.consume(p.path("ticket").asText());
                if(p.has("characterId")&&!ticket.character().toString().equals(p.path("characterId").asText()))throw new ProtocolException("CHARACTER_MISMATCH","Nhân vật không khớp với vé kết nối.");
                String resume=p.has("resumeToken")?p.path("resumeToken").asText():null;
                if(resume!=null&&(resume.length()<32||resume.length()>128))throw new ProtocolException("INVALID_RESUME_TOKEN","Khóa phục hồi không hợp lệ.");
                world.join(s.session,characters.owned(ticket.owner(),ticket.character()),resume,p.path("protocolVersion").asInt(1)==2);s.authenticated=true;return;
            }
            switch(type) {
                case "INPUT" -> {
                    if(!p.path("direction").isInt() || Math.abs((long)p.path("direction").asInt())>1 || !p.path("jump").isBoolean()
                        || !p.path("sequence").isIntegralNumber() || !p.path("sequence").canConvertToLong() || p.path("sequence").asLong()<0 || p.has("x") || p.has("y")) throw new ProtocolException("INVALID_INPUT","Input không hợp lệ.");
                    world.input(raw.getId(),p.path("direction").asInt(),p.path("jump").asBoolean(),p.path("sequence").asLong());
                }
                case "INTERACT" -> { limits.check("interact:"+raw.getId(),60); world.interact(raw.getId()); }
                case "ATTACK" -> world.cast(raw.getId(),"ATTACK",target(p));
                case "CAST_SKILL" -> world.cast(raw.getId(),p.path("skillId").asText(),target(p));
                case "CHAT" -> { limits.check("chat:"+raw.getId(),30); world.chat(raw.getId(),p.path("message").asText()); }
                case "NEARBY_PLAYERS_REQUEST","PARTY_STATE_REQUEST","PARTY_INVITE","PARTY_ACCEPT","PARTY_DECLINE","PARTY_LEAVE","PARTY_KICK","PARTY_TRANSFER_LEADER","PARTY_CHAT" -> {
                    limits.check("party:"+raw.getId(),60);
                    String requestId=root.has("requestId")?root.path("requestId").asText():p.path("requestId").asText();
                    world.partyCommand(raw.getId(),type,p,requestId);
                }
                case "PING" -> world.send(s.session,"PONG",Map.of("serverTime",now));
                case "DUNGEON_LIST_REQUEST","DUNGEON_READY_SET","DUNGEON_START_REQUEST","DUNGEON_LEAVE_REQUEST" -> {limits.check("dungeon:"+raw.getId(),60);world.dungeonCommand(raw.getId(),type,p,root.has("requestId")?root.path("requestId").asText():p.path("requestId").asText());}
                case "DUEL_STATE_REQUEST","DUEL_CHALLENGE","DUEL_ACCEPT","DUEL_DECLINE","DUEL_FORFEIT" -> {limits.check("duel:"+raw.getId(),30);world.duelCommand(raw.getId(),type,p,root.has("requestId")?root.path("requestId").asText():p.path("requestId").asText());}
                case "RESUME_WORLD" -> {if(p.has("characterId")||p.has("mapId")||p.has("instanceId"))throw new ProtocolException("INVALID_RESUME_REQUEST","Phiên server quyết định nhân vật và bản đồ phục hồi.");limits.check("snapshot:"+raw.getId(),10);world.resumeSnapshot(raw.getId());}
                case "LEAVE_WORLD" -> {world.disconnect(raw.getId(),true);raw.close(CloseStatus.NORMAL);}
                default -> throw new ProtocolException("UNSUPPORTED_MESSAGE","Message chưa được hỗ trợ: " + type);
            }
        } catch(Exception e) {
            String reason=e instanceof IllegalArgumentException ? e.getMessage() : "Yêu cầu không hợp lệ.";
            String code=e instanceof ProtocolException problem?problem.code():e instanceof org.springframework.web.server.ResponseStatusException status&&status.getStatusCode().value()==429?"RATE_LIMITED":e instanceof IllegalArgumentException?"GAMEPLAY_REJECTED":"REQUEST_FAILED";
            if(!s.authenticated)metrics.resumeFailures.increment();
            if(!(e instanceof IllegalArgumentException)&&!(e instanceof org.springframework.web.server.ResponseStatusException)){metrics.exceptions.increment();log.error("WebSocket request failed, type/session {}",raw.getId(),e);}
            world.send(s.session,messageType.startsWith("PARTY_")?"PARTY_ERROR":messageType.startsWith("DUNGEON_")?"DUNGEON_ERROR":messageType.startsWith("DUEL_")?"DUEL_ERROR":"ERROR",Map.of("message",reason,"code",code,"retryable",code.equals("RATE_LIMITED")||code.equals("REQUEST_FAILED")));
            if(!s.authenticated) raw.close(CloseStatus.POLICY_VIOLATION);
        }
    }
    private static UUID target(JsonNode p){if(!p.has("targetCharacterId"))return null;try{return UUID.fromString(p.path("targetCharacterId").asText());}catch(Exception e){throw new ProtocolException("INVALID_TARGET","ID mục tiêu không hợp lệ.");}}
    @Scheduled(fixedDelay=1000) void expireUnauthenticated() {
        long now=System.currentTimeMillis();
        for(State s:sessions.values())if((!s.authenticated&&now-s.opened>10000)||(s.authenticated&&now-s.lastReceived>idleTimeout))try{world.disconnect(s.session.getId(),false);s.session.close(new CloseStatus(4000,"Heartbeat timeout"));}catch(Exception ignored){}
    }
    @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status) { sessions.remove(session.getId()); world.disconnect(session.getId(),false); }
    @Override public void handleTransportError(WebSocketSession session,Throwable error) throws Exception { session.close(CloseStatus.SERVER_ERROR); }
}
