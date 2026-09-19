package com.mdb.petstore.identity.bootstrap;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import com.mdb.petstore.identity.model.Role;
import com.mdb.petstore.identity.model.User;
import com.mdb.petstore.identity.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SupplierBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SupplierBootstrap.class);
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;

    public SupplierBootstrap(UserRepository users, PasswordEncoder encoder,
            @Value("${petstore.supplier.bootstrap.username:}") String username,
            @Value("${petstore.supplier.bootstrap.password:}") String password) {
        this.users = users;
        this.encoder = encoder;
        this.username = username.trim().toLowerCase(Locale.ROOT);
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username.isBlank() || password.isBlank()) return;
        if (users.existsByUsername(username)) {
            log.info("Supplier bootstrap skipped existing username={}", username);
            return;
        }
        var now = Instant.now();
        var user = new User();
        user.setUsername(username);
        user.setPasswordHash(encoder.encode(password));
        user.setRoles(Set.of(Role.SUPPLIER));
        user.setEnabled(true);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            users.insert(user);
            log.info("Supplier bootstrap created username={}", username);
        } catch (DuplicateKeyException concurrentCreation) {
            log.info("Supplier bootstrap skipped concurrently created username={}", username);
        }
    }
}
