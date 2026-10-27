package vn.tamtoc.character;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CharacterService {
    private final CharacterRepository repo;
    public CharacterService(CharacterRepository r) { repo=r; }
    @Transactional(readOnly=true) public GameCharacter owned(UUID owner, UUID id) {
        return repo.findById(id).filter(c -> c.userId.equals(owner)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy nhân vật."));
    }
    @Transactional public GameCharacter create(UUID owner, String name, Race race) {
        if (name.strip().length() < 3) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên cần ít nhất 3 ký tự.");
        // Lock the account row so concurrent create requests cannot exceed the character limit.
        repoAccountLock(owner);
        if (repo.countByUserId(owner) >= 6) throw new ResponseStatusException(HttpStatus.CONFLICT, "Mỗi tài khoản tối đa 6 nhân vật.");
        return repo.saveAndFlush(new GameCharacter(owner, name.strip(), race));
    }
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager em;
    private void repoAccountLock(UUID owner) { em.find(vn.tamtoc.account.Account.class, owner, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); }
    @Transactional public void savePosition(UUID id, String map, double x, double y) {
        repo.findById(id).ifPresent(c -> { c.mapId=map; c.x=x; c.y=y; });
    }
}
