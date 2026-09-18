package com.mdb.petstore.customer.service;

import java.time.Instant;
import java.util.Locale;

import com.mdb.petstore.customer.dto.AccountResponse;
import com.mdb.petstore.customer.dto.UpdateAccountRequest;
import com.mdb.petstore.customer.model.Customer;
import com.mdb.petstore.customer.repository.CustomerRepository;
import com.mdb.petstore.identity.model.User;
import com.mdb.petstore.identity.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;

    public AccountService(UserRepository userRepository, CustomerRepository customerRepository) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
    }

    public AccountResponse getAccount(String authenticatedUsername) {
        return toResponse(findCustomer(authenticatedUsername));
    }

    public AccountResponse updateAccount(String authenticatedUsername, UpdateAccountRequest request) {
        Customer customer = findCustomer(authenticatedUsername);
        var contact = customer.getAccount().getContactInfo();
        contact.setFirstName(request.getFirstName());
        contact.setLastName(request.getLastName());
        contact.setEmail(request.getEmail());
        contact.setPhone(request.getPhone());

        var address = contact.getAddress();
        address.setStreet1(request.getStreet1());
        address.setStreet2(request.getStreet2());
        address.setCity(request.getCity());
        address.setStateOrProvince(request.getStateOrProvince());
        address.setPostalCode(request.getPostalCode());
        address.setCountry(request.getCountry());

        var card = customer.getAccount().getCreditCard();
        card.setCardType(request.getCardType());
        card.setCardNumber(request.getCardNumber());
        card.setExpiryDate(request.getExpiryDate());

        var profile = customer.getProfile();
        profile.setLanguagePreference(request.getLanguagePreference());
        profile.setBannerPreference(request.isBannerPreference());
        profile.setLinkPreference(request.isLinkPreference());

        customer.setUpdatedAt(Instant.now());
        return toResponse(customerRepository.save(customer));
    }

    private Customer findCustomer(String authenticatedUsername) {
        String username = authenticatedUsername.trim().toLowerCase(Locale.ROOT);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        if (user.getCustomerId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found");
        }
        return customerRepository.findById(user.getCustomerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private AccountResponse toResponse(Customer customer) {
        var contact = customer.getAccount().getContactInfo();
        var address = contact.getAddress();
        var card = customer.getAccount().getCreditCard();
        var profile = customer.getProfile();
        return new AccountResponse(
                contact.getFirstName(), contact.getLastName(), contact.getEmail(), contact.getPhone(),
                address.getStreet1(), address.getStreet2(), address.getCity(), address.getStateOrProvince(),
                address.getPostalCode(), address.getCountry(), card.getCardType(), card.getExpiryDate(),
                profile.getLanguagePreference(), profile.isBannerPreference(), profile.isLinkPreference());
    }
}
