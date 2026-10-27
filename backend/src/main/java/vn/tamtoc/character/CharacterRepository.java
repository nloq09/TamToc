package vn.tamtoc.character;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CharacterRepository extends JpaRepository<GameCharacter, UUID> {
    List<GameCharacter> findByUserIdOrderByName(UUID userId);
    long countByUserId(UUID userId);
}
