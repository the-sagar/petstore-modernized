package com.mdb.petstore.identity.api;

import com.mdb.petstore.identity.dto.RegisterRequest;
import com.mdb.petstore.identity.dto.RegisterResponse;
import com.mdb.petstore.identity.model.User;
import com.mdb.petstore.identity.service.RegistrationService;

import jakarta.validation.Valid;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = registrationService.register(request);
        RegisterResponse response = new RegisterResponse(
                user.getId(), user.getUsername(), user.getCustomerId(), user.getRoles());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @ExceptionHandler({IllegalArgumentException.class, DuplicateKeyException.class})
    public ResponseEntity<String> handleDuplicateUsername() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Username already exists");
    }
}
