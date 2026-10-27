package vn.tamtoc.realtime;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;
import vn.tamtoc.character.CharacterService;
import vn.tamtoc.security.RateLimits;
import vn.tamtoc.world.GameWorld;

@Component
public class GameSocket extends TextWebSocketHandler {
    private static class State {
        final WebSocketSession session; final long opened=System.currentTimeMillis();
        long window=opened; int messages; volatile boolean authenticated;
        State(WebSocketSession s) { session=new ConcurrentWebSocketSessionDecorator(s,1000,65536); }
    }
    private final Map<String,State> sessions=new ConcurrentHashMap<>();
    private final ObjectMapper json; private final TicketService tickets; private final CharacterService characters; private final GameWorld world; private final RateLimits limits;
    public GameSocket(ObjectMapper j,TicketService t,CharacterService c,GameWorld w,RateLimits l) { json=j; tickets=t; characters=c; world=w; limits=l; }
    @Override public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            limits.check("ws-open:" + session.getRemoteAddress().getAddress().getHostAddress(),60);
            session.setTextMessageSizeLimit(2048); sessions.put(session.getId(),new State(session));
        } catch(Exception e) { session.close(CloseStatus.POLICY_VIOLATION); }
    }
    @Override protected void handleTextMessage(WebSocketSession raw,TextMessage message) throws Exception {
        State s=sessions.get(raw.getId()); if(s==null) return;
        long now=System.currentTimeMillis(); if(now-s.window>1000) { s.window=now; s.messages=0; }
        if(++s.messages>60) { raw.close(CloseStatus.POLICY_VIOLATION); return; }
        try {
            JsonNode root=json.readTree(message.getPayload());
            if(!root.isObject() || !root.path("type").isTextual() || !root.path("payload").isObject()) throw new IllegalArgumentException("Sai định dạng message.");
            String type=root.path("type").asText(); JsonNode p=root.path("payload");
            if(!s.authenticated) {
                if(!type.equals("AUTH")) throw new IllegalArgumentException("AUTH phải là message đầu tiên.");
                var ticket=tickets.consume(p.path("ticket").asText());
                world.join(s.session,characters.owned(ticket.owner(),ticket.character())); s.authenticated=true; return;
            }
            switch(type) {
                case "INPUT" -> {
                    if(!p.path("direction").isInt() || Math.abs((long)p.path("direction").asInt())>1 || !p.path("jump").isBoolean()
                        || !p.path("sequence").isIntegralNumber() || p.path("sequence").asLong()<0 || p.has("x") || p.has("y")) throw new IllegalArgumentException("Input không hợp lệ.");
                    world.input(raw.getId(),p.path("direction").asInt(),p.path("jump").asBoolean(),p.path("sequence").asLong());
                }
                case "INTERACT" -> { limits.check("interact:"+raw.getId(),60); world.interact(raw.getId()); }
                case "ATTACK" -> world.cast(raw.getId(),"ATTACK");
                case "CAST_SKILL" -> world.cast(raw.getId(),p.path("skillId").asText());
                case "CHAT" -> { limits.check("chat:"+raw.getId(),30); world.chat(raw.getId(),p.path("message").asText()); }
                case "LEAVE_WORLD" -> raw.close(CloseStatus.NORMAL);
                default -> throw new IllegalArgumentException("Message chưa được hỗ trợ: " + type);
            }
        } catch(Exception e) {
            String reason=e instanceof IllegalArgumentException ? e.getMessage() : "Yêu cầu không hợp lệ.";
            world.send(s.session,"ERROR",Map.of("message",reason));
            if(!s.authenticated) raw.close(CloseStatus.POLICY_VIOLATION);
        }
    }
    @Scheduled(fixedDelay=5000) void expireUnauthenticated() {
        long now=System.currentTimeMillis();
        for(State s:sessions.values()) if(!s.authenticated && now-s.opened>10000) try { s.session.close(CloseStatus.POLICY_VIOLATION); } catch(Exception ignored) {}
    }
    @Override public void afterConnectionClosed(WebSocketSession session,CloseStatus status) { sessions.remove(session.getId()); world.leave(session.getId()); }
    @Override public void handleTransportError(WebSocketSession session,Throwable error) throws Exception { session.close(CloseStatus.SERVER_ERROR); }
}
