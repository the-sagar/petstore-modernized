# Account / Customer / Authentication

## Verified legacy baseline

The legacy Storefront used `SignOnFilter` → `SignOnEJB` → `UserEJB`, direct password comparison, and HTTP session state. Registration and customer updates traversed HTMLAction/Event/EJBAction/EJB layers synchronously. Those framework `Event` objects were in-process commands, not JMS messages.

Customer persistence was spread across User, Customer, Account, ContactInfo, Address, CreditCard, and Profile tables. Direct Cloudscape inspection verified these relationships. Legacy logout invalidated the session.

A confirmed registration defect forwarded the original successful POST to `customer.do`, retaining `action=create` and attempting customer creation again. The resulting duplicate-key failure and stateful EJB coupling are recorded in [legacy defects](03-legacy-defects.md).

## Implemented ownership and persistence

All account/authentication functionality lives in `storefront-service`, using `petstore_storefront`.

| Collection | Contents |
|---|---|
| `users` | Normalized unique username, BCrypt `passwordHash`, roles, enabled flag, `customerId`, timestamps |
| `customers` | Embedded account/contact/address/credit-card fields and profile, plus timestamps |

`User.customerId` links identity to the customer aggregate by ID, without DBRef. This is a deliberate two-aggregate split, not seven collections and not a combined credentials/customer document. Other services do not read these repositories.

## Registration — implemented

`POST /api/auth/register` validates required fields and email, then `RegistrationService`:

1. Trims and lowercases the username using `Locale.ROOT`.
2. Checks username availability; a unique Mongo index also enforces it.
3. Builds and saves the embedded customer aggregate.
4. Hashes the password through Spring Security's `PasswordEncoder`/BCrypt and saves the linked user.
5. Commits both writes under `@Transactional` and `MongoTransactionManager`.

The local `rs0` replica set enables transaction semantics. A failed user write rolls back the customer write. Successful registration returns 201; duplicate usernames return 409. The UI redirects to login rather than internally forwarding the original POST into a second create handler.

## Sign-in and session — implemented

`POST /api/auth/login` accepts form credentials. Spring Security's authentication provider loads the user through `MongoUserDetailsService` and checks the BCrypt hash. Success returns JSON; the login page navigates to `/shop`. Invalid credentials return 401.

Authentication uses an HTTP session, not a JWT. Session-fixation protection retains the existing anonymous cart during login, as covered by the page/session-handoff test. The cart itself stores only item IDs and quantities. Business services are not EJB objects held in the session.

## Account ownership and update — implemented

| Endpoint | Behavior |
|---|---|
| `GET /api/account` | Returns the authenticated customer's account DTO; anonymous access returns 401 |
| `PUT /api/account` | Validates and updates that customer's contact, address, payment fields, and profile; requires authentication and CSRF |
| `GET /account` | Renders the account page; anonymous users are redirected to login |
| `POST /api/auth/logout` | Requires authentication and CSRF; returns 204, clears the security context, invalidates the session, and deletes the session cookie |

Ownership resolution is authenticated principal → normalized username → `User.customerId` → `Customer`. Neither GET nor PUT accepts a caller-selected customer ID. Account responses do not expose password hashes or the saved card number.

Logout also ends the session-scoped cart. There is no durable cart persistence or cross-service session sharing.

## CSRF and validation boundaries

CSRF remains enabled for account PUT, cart writes, checkout POST, and logout POST. Registration and login are explicitly exempted by the existing configuration. Thymeleaf pages read the request's CSRF token/header and include them on protected JavaScript mutations.

Required contact/address fields and email syntax are validated. Country and state/province remain independent text fields: country-aware semantic validation is **not implemented**. The expiry field is not backed by dynamic expiry-range validation.

## Payment security: partially corrected

The customer account still stores `cardType`, raw `cardNumber`, and `expiryDate`. The account page does not display the saved number; when saving, leaving its card-number input blank clears the saved value. Registration permits missing payment fields, but checkout requires usable saved payment display information.

Checkout derives only `cardType` and `last4` for the order request. Raw card data does not cross into Order Processing. Removing/tokenizing raw Storefront storage remains an explicit security-hardening follow-up; this is not a PCI-compliant payment integration.

## Evidence and scope

Tests cover registration success, duplicate handling and rollback, authentication/logout, account ownership/update, CSRF, and session retention. Business-event logs identify users/customers and outcomes without intentionally logging passwords or full payment payloads.

Messaging is not part of this synchronous slice. See [order processing](07-order-processing.md) for the implemented HTTP boundary and the separate, planned asynchronous stage.
