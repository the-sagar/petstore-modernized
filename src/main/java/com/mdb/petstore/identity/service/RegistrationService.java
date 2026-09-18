package com.mdb.petstore.identity.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

import com.mdb.petstore.customer.model.Account;
import com.mdb.petstore.customer.model.Address;
import com.mdb.petstore.customer.model.ContactInfo;
import com.mdb.petstore.customer.model.CreditCard;
import com.mdb.petstore.customer.model.Customer;
import com.mdb.petstore.customer.model.Profile;
import com.mdb.petstore.customer.repository.CustomerRepository;
import com.mdb.petstore.identity.dto.RegisterRequest;
import com.mdb.petstore.identity.model.Role;
import com.mdb.petstore.identity.model.User;
import com.mdb.petstore.identity.repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistrationService(UserRepository userRepository,
            CustomerRepository customerRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterRequest request) {
        String normalizedUsername = request.getUsername().trim().toLowerCase(Locale.ROOT);
        log.debug("Registration request received for username={}", normalizedUsername);
        log.debug("Checking username availability for username={}", normalizedUsername);
        if (userRepository.existsByUsername(normalizedUsername)) {
            log.warn("Customer registration rejected: username already exists, username={}", normalizedUsername);
            throw new IllegalArgumentException("Username already exists: " + normalizedUsername);
        }

        Instant registrationTime = Instant.now();
        log.debug("Building customer aggregate for username={}", normalizedUsername);

        Address address = new Address();
        address.setStreet1(request.getStreet1());
        address.setStreet2(request.getStreet2());
        address.setCity(request.getCity());
        address.setStateOrProvince(request.getStateOrProvince());
        address.setPostalCode(request.getPostalCode());
        address.setCountry(request.getCountry());

        ContactInfo contactInfo = new ContactInfo();
        contactInfo.setFirstName(request.getFirstName());
        contactInfo.setLastName(request.getLastName());
        contactInfo.setEmail(request.getEmail());
        contactInfo.setPhone(request.getPhone());
        contactInfo.setAddress(address);

        CreditCard creditCard = new CreditCard();
        creditCard.setCardType(request.getCardType());
        creditCard.setCardNumber(request.getCardNumber());
        creditCard.setExpiryDate(request.getExpiryDate());

        Account account = new Account();
        account.setContactInfo(contactInfo);
        account.setCreditCard(creditCard);

        Profile profile = new Profile();
        profile.setLanguagePreference(request.getLanguagePreference());
        profile.setBannerPreference(request.isBannerPreference());
        profile.setLinkPreference(request.isLinkPreference());

        Customer customer = new Customer();
        customer.setAccount(account);
        customer.setProfile(profile);
        customer.setCreatedAt(registrationTime);
        customer.setUpdatedAt(registrationTime);
        Customer savedCustomer = customerRepository.save(customer);
        log.debug("Customer persistence completed within registration transaction, customerId={}", savedCustomer.getId());

        log.debug("Building identity record for username={}", normalizedUsername);
        User user = new User();
        user.setUsername(normalizedUsername);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRoles(Set.of(Role.CUSTOMER));
        user.setEnabled(true);
        user.setCustomerId(savedCustomer.getId());
        user.setCreatedAt(registrationTime);
        user.setUpdatedAt(registrationTime);

        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DuplicateKeyException exception) {
            log.warn("Customer registration rejected: username already exists, username={}", normalizedUsername);
            throw exception;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("Registration transaction completed for username={} userId={} customerId={}",
                        normalizedUsername, savedUser.getId(), savedCustomer.getId());
            }
        });
        return savedUser;
    }
}
