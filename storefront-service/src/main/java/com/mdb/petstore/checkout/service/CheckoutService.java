package com.mdb.petstore.checkout.service;

import java.util.ArrayList;
import java.util.Locale;

import com.mdb.petstore.cart.model.ShoppingCart;
import com.mdb.petstore.catalog.service.CatalogService;
import com.mdb.petstore.checkout.client.CreateOrderRequest;
import com.mdb.petstore.checkout.client.OrderProcessingClient;
import com.mdb.petstore.checkout.dto.CheckoutRequest;
import com.mdb.petstore.checkout.dto.OrderResponse;
import com.mdb.petstore.customer.repository.CustomerRepository;
import com.mdb.petstore.identity.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);
    private final UserRepository users;
    private final CustomerRepository customers;
    private final ShoppingCart cart;
    private final CatalogService catalog;
    private final OrderProcessingClient orderClient;

    public CheckoutService(UserRepository users, CustomerRepository customers, ShoppingCart cart,
            CatalogService catalog, OrderProcessingClient orderClient) {
        this.users = users;
        this.customers = customers;
        this.cart = cart;
        this.catalog = catalog;
        this.orderClient = orderClient;
    }

    public OrderResponse checkout(String authenticatedUsername, CheckoutRequest request, String locale) {
        var user = users.findByUsername(authenticatedUsername.strip().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        if (user.getCustomerId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }
        var customer = customers.findById(user.getCustomerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found"));
        var quantities = cart.getQuantities();
        if (quantities.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }
        String language = locale == null || locale.isBlank() ? "en-US"
                : Locale.forLanguageTag(locale.strip().replace('_', '-')).toLanguageTag();
        var lines = new ArrayList<CreateOrderRequest.Line>();
        for (var entry : quantities.entrySet()) {
            var item = catalog.getItem(entry.getKey(), language);
            if (item.listPrice() == null || item.listPrice().signum() < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Item price unavailable");
            }
            lines.add(new CreateOrderRequest.Line(lines.size() + 1, item.categoryId(), item.productId(),
                    item.id(), entry.getValue(), item.listPrice()));
        }
        var account = customer.getAccount();
        if (account == null || account.getContactInfo() == null || account.getCreditCard() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Customer checkout information unavailable");
        }
        var card = account.getCreditCard();
        String number = card.getCardNumber() == null ? "" : card.getCardNumber().replaceAll("[ -]", "");
        if (card.getCardType() == null || card.getCardType().isBlank() || !number.matches("[0-9]{4,19}")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment display information unavailable");
        }
        var payment = new CreateOrderRequest.Payment(card.getCardType(), number.substring(number.length() - 4));
        var orderRequest = new CreateOrderRequest(customer.getId(), user.getUsername(),
                account.getContactInfo().getEmail(), language, request.billingInfo(), request.shippingInfo(), payment, lines);
        log.info("Checkout started customerId={} lineCount={}", customer.getId(), lines.size());
        OrderResponse response = orderClient.create(orderRequest);
        cart.clear();
        log.info("Checkout completed orderId={} customerId={} lineCount={} status={}",
                response.orderId(), customer.getId(), lines.size(), response.status());
        return response;
    }
}
