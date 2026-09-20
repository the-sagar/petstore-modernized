package com.mdb.petstore.catalog.model;

import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@CompoundIndex(name = "productId_id", def = "{'productId': 1, '_id': 1}")
@Document(collection = "items")
public class Item {

    @Id
    private String id;
    private String productId;
    // Derived from Product.categoryId to support category filtering/search without another product lookup.
    private String categoryId;
    private List<ItemDetails> details;

    public Item() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public List<ItemDetails> getDetails() {
        return details;
    }

    public void setDetails(List<ItemDetails> details) {
        this.details = details;
    }
}
