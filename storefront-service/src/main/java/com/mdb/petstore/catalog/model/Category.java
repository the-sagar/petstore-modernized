package com.mdb.petstore.catalog.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "categories")
public class Category {

    @Id
    private String id;
    private List<CategoryDetails> details;

    public Category() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<CategoryDetails> getDetails() {
        return details;
    }

    public void setDetails(List<CategoryDetails> details) {
        this.details = details;
    }
}
