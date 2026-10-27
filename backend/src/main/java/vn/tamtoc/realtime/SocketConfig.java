package vn.tamtoc.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration @EnableWebSocket
public class SocketConfig implements WebSocketConfigurer {
    private final GameSocket socket; private final String[] origins;
    public SocketConfig(GameSocket s,@Value("${game.allowed-origins}") String origins) { socket=s; this.origins=origins.split(","); }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) { registry.addHandler(socket,"/ws").setAllowedOrigins(origins); }
}
