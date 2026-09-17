package com.example.userservicedb;

import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostMapping("/authenticate")
    public ResponseEntity<Void> authenticate(@RequestBody AuthRequest request) {
        if (request.getName() == null || request.getPassword() == null) {
            return ResponseEntity.badRequest().build();
        }

        Optional<User> userObject = userRepository.findFirstByName(request.getName());
        if (!userObject.isPresent()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        User user = userObject.get();
        String storedPassword = user.getPassword();
        boolean bcryptHash = storedPassword != null && storedPassword.startsWith("$2");
        boolean matches = bcryptHash
                ? passwordEncoder.matches(request.getPassword(), storedPassword)
                : storedPassword != null && storedPassword.equals(request.getPassword());

        if (!matches) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // One-way compatibility migration for databases created by the 2021 app.
        if (!bcryptHash) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            userRepository.save(user);
        }

        return ResponseEntity.noContent().build();
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        if (user.getName() == null || user.getName().trim().isEmpty()
                || user.getPassword() == null || user.getPassword().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        user.setName(user.getName().trim());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return ResponseEntity.status(HttpStatus.CREATED).body(userRepository.save(user));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<User> updateUserById(
            @PathVariable int userId,
            @RequestBody User newUser) {
        Optional<User> userObject = userRepository.findById(userId);
        if (!userObject.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        User user = userObject.get();
        if (newUser.getName() != null && !newUser.getName().trim().isEmpty()) {
            user.setName(newUser.getName().trim());
        }
        if (newUser.getPassword() != null && !newUser.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(newUser.getPassword()));
        }
        if (newUser.getAge() > 0) {
            user.setAge(newUser.getAge());
        }
        if (newUser.getAddress() != null) {
            user.setAddress(newUser.getAddress().trim());
        }

        return ResponseEntity.ok(userRepository.save(user));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteUserByName(@RequestParam String name) {
        Optional<User> user = userRepository.findFirstByName(name);
        if (!user.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        userRepository.deleteByName(name);
        return ResponseEntity.noContent().build();
    }
}
