package vn.tamtoc.realtime;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.tamtoc.character.CharacterService;

@Service
public class TicketService {
    public record Ticket(UUID owner, UUID character, long expires) {}
    private final ConcurrentHashMap<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final CharacterService characters;
    public TicketService(CharacterService c) { characters=c; }
    public String issue(UUID owner, UUID character) {
        characters.owned(owner,character);
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String key=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tickets.put(key,new Ticket(owner,character,System.currentTimeMillis()+30_000)); return key;
    }
    public Ticket consume(String key) {
        Ticket ticket=tickets.remove(key);
        if (ticket == null || ticket.expires() < System.currentTimeMillis()) throw new IllegalArgumentException("Vé kết nối không hợp lệ hoặc hết hạn.");
        return ticket;
    }
    @Scheduled(fixedDelay=30000) void clean() { tickets.entrySet().removeIf(e -> e.getValue().expires() < System.currentTimeMillis()); }
}
