# Account and authentication

## Verified legacy baseline

The legacy Storefront used `SignOnFilter` → `SignOnEJB` → `UserEJB`, direct password comparison, and HTTP session state. Registration and customer updates traversed HTMLAction/Event/EJBAction/EJB layers synchronously. Those framework `Event` objects were in-process commands, not JMS messages.

Customer persistence was spread across User, Customer, Account, ContactInfo, Address, CreditCard, and Profile tables. Direct Cloudscape inspection verified these relationships. Legacy logout invalidated the session.

A confirmed registration defect forwarded the original successful POST to `customer.do`, retaining `action=create` and attempting customer creation again. The resulting duplicate-key failure and stateful EJB coupling are recorded in [legacy defects](03-legacy-defects.md).

## Implemented ownership and persistence

The Storefront Service owns account and authentication functionality in `petstore_storefront`.

| Collection | Contents |
|---|---|
| `users` | Normalized unique username, BCrypt `passwordHash`, roles, enabled flag, `customerId`, timestamps |
| `customers` | Embedded account/contact/address/credit-card fields and profile, plus timestamps |

`User.customerId` links identity to the customer aggregate by ID, without DBRef. This separates authentication from the customer aggregate while embedding customer-owned data. Other services do not read these repositories.

## Registration — implemented

`POST /api/auth/register` validates required fields and email syntax. `RegistrationService` then:

1. Trims and lowercases the username using `Locale.ROOT`.
2. Checks username availability; a unique MongoDB index also enforces it.
3. Builds and saves the embedded customer aggregate.
4. Hashes the password through Spring Security's `PasswordEncoder`/BCrypt and saves the linked user.
5. Commits both writes under `@Transactional` and `MongoTransactionManager`.

The local `rs0` replica set enables transaction semantics. A failed user write rolls back the customer write. Successful registration returns 201; duplicate usernames return 409. The UI redirects to login rather than internally forwarding the original POST into a second create handler.

## Sign-in and session — implemented

`POST /api/auth/login` accepts form credentials. Spring Security's authentication provider loads the user through `MongoUserDetailsService` and checks the BCrypt hash. Success returns JSON; the login page selects `/admin/orders` for ADMIN, then `/supplier/inventory` for SUPPLIER, otherwise `/shop` (in that precedence). Invalid credentials return 401.

Authentication uses an HTTP session, not a JWT. Session-fixation protection retains the existing anonymous cart during login, as covered by the page/session-handoff test. The cart itself stores only item IDs and quantities. Business services are not EJB objects held in the session.

## Account ownership and update — implemented

| Endpoint | Behavior |
|---|---|
| `GET /api/account` | Returns the authenticated customer's account DTO; anonymous access returns 401 |
| `PUT /api/account` | Validates and updates that customer's contact, address, payment fields, and profile; requires authentication and CSRF |
| `GET /account` | Renders the account page; anonymous users are redirected to login |
| `POST /api/auth/logout` | Requires authentication and CSRF; returns 204, clears the security context, invalidates the session, and deletes the session cookie |

Ownership resolution is authenticated principal → normalized username → `User.customerId` → `Customer`. Neither GET nor PUT accepts a caller-selected customer ID. Account responses do not expose password hashes or the `cardNumber` field.

Logout also ends the session-scoped cart. There is no durable cart persistence or cross-service session sharing.

## CSRF and validation boundaries

CSRF remains enabled for account PUT, cart writes, checkout POST, Admin/Supplier mutations, and logout POST. Registration and login are explicitly exempted by the existing configuration. Thymeleaf pages read the request's CSRF token/header and include them on protected JavaScript mutations.

Required contact/address fields and email syntax are validated. Country and state/province remain independent text fields: country-aware semantic validation is **not implemented**. The expiry field has no current-date semantic validation.

## Payment storage hardening — implemented

The customer account stores only `cardType`, `last4`, and `expiryDate`. Registration/account requests accept a number transiently, validate its format and derive last4. Blank account input preserves existing last4; nonblank input replaces it. Account displays a saved payment method with card type, a presentation-only mask such as `•••• •••• •••• 1111`, and expiry when present. The password-type replacement input remains blank on load and is cleared after a successful save; the summary refreshes from the safe response. Missing last4 produces a no-saved-payment-method state. No full number is placed in summary text or accessibility attributes. Registration permits missing payment metadata, but checkout requires usable saved display information.

Checkout reads stored `cardType` and `last4` directly. A startup MongoDB migration derives last4 from valid legacy numbers and unsets cardNumber atomically per document. No full number is persisted by current code or sent to Order Processing. The application performs no payment authorization or tokenization and makes no PCI-compliance claim.

## Evidence and scope

Tests cover registration success, duplicate handling and rollback, authentication/logout, account ownership/update, CSRF, and session retention. Business-event logs identify users/customers and outcomes without intentionally logging passwords or full payment payloads.

Messaging is not part of this synchronous slice. See [order processing](07-order-processing.md) for the implemented HTTP boundary and asynchronous approval/fulfilment stage.

## Operational roles

Storefront protects `/admin/**` and `/api/admin/**` with ROLE_ADMIN, and `/supplier/**` and `/api/supplier/**` with ROLE_SUPPLIER. ADMIN alone does not grant Supplier access. Registration creates CUSTOMER only. Bootstrap creates enabled BCrypt-backed operational users without Customer records and leaves existing usernames unchanged. Local defaults and environment overrides are documented in [setup](08-installation-and-setup.md#environment-and-demo-accounts).

## Language preference and legacy profile scope

The customer Storefront supports en-US, ja-JP and zh-CN. Locale precedence is explicit `locale` query → persisted `Profile.languagePreference` → session → en-US. Underscore variants normalize to hyphenated tags; unsupported or blank values fall back to English. Registration with an omitted preference uses the current effective locale; Account explicitly saves the selected preference. CUSTOMER login applies the persisted preference to the session. ADMIN/SUPPLIER users have no Customer document and bypass that lookup.

The selector refreshes server-rendered messages and preserves explicit locale in customer navigation/API URLs. It changes display/session locale without silently saving the profile. If a subsequent authenticated request omits `locale`, the persisted preference takes precedence over session locale.

The legacy profile had `preferredLanguage`, `favoriteCategory`, `myListPreference` and `bannerPreference`. The modern profile has `languagePreference`, `bannerPreference` and `linkPreference`. No verified equivalence between `linkPreference` and legacy My List is claimed. Favorite Category, My List, favorite-category banners and remember-username behavior remain deferred.
