package com.mdb.petstore.identity.dto;

import java.util.Set;

import com.mdb.petstore.identity.model.Role;

public record RegisterResponse(String id, String username, String customerId, Set<Role> roles) {
}
