package vn.tamtoc.account;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "users")
public class Account {
    @Id public UUID id;
    public String username;
    @Column(name = "password_hash") public String passwordHash;
    protected Account() {}
    public Account(String username, String hash) {
        id = UUID.randomUUID(); this.username = username; passwordHash = hash;
    }
}
