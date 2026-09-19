package com.mdb.petstore.cart.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

@Component
@SessionScope
public class ShoppingCart implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Only IDs and quantities belong in the session; display data is resolved on every request.
    private final Map<String, Integer> quantities = new TreeMap<>();

    public synchronized Map<String, Integer> getQuantities() {
        return Collections.unmodifiableMap(new TreeMap<>(quantities));
    }

    public synchronized void setQuantity(String itemId, int quantity) {
        if (quantity <= 0) {
            quantities.remove(itemId);
        } else {
            quantities.put(itemId, quantity);
        }
    }

    public synchronized void removeItem(String itemId) {
        quantities.remove(itemId);
    }

    public synchronized void clear() {
        quantities.clear();
    }
}
