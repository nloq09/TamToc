package vn.tamtoc.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class MapCatalog {
    public record Platform(double x, double y, double width, double height) {}
    public record Portal(double x, String target, String label) {}
    public record MapDefinition(String id, String name, int width, int height, double ground, String color, List<Platform> platforms, List<Portal> portals) {}
    private final Map<String, MapDefinition> maps = new LinkedHashMap<>();
    public MapCatalog(ObjectMapper mapper) throws IOException {
        try (var in = new ClassPathResource("maps.json").getInputStream()) {
            for (MapDefinition m : mapper.readValue(in, MapDefinition[].class)) maps.put(m.id(), m);
        }
    }
    public MapDefinition get(String id) { return Objects.requireNonNull(maps.get(id), "Unknown map"); }
}
