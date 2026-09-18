package com.mdb.petstore.identity.dto;

import java.util.Set;

public record LoginResponse(String username, Set<String> roles) {
}
