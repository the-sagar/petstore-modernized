package com.mdb.petstore.catalog.dto;

public record ProductResponse(String id, String categoryId, String name, String image, String description) {
}
