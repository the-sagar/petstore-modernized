# Legacy Account / Customer / Authentication Flow

## Purpose

This document records the observed Account / Customer / Authentication behavior of the legacy application.

This is the first vertical slice selected for modernization because it exercises:

- HTTP request routing
- authentication
- HTTP session state
- business-layer orchestration
- persistence
- validation
- error handling

It does **not** require JMS.

---

## Authentication Model

The Storefront uses custom form-based authentication.

At a high level:

```text
Sign-in request
    ↓
SignOnFilter
    ↓
SignOnEJB.authenticate(...)
    ↓
UserEJB lookup
    ↓
Password comparison
    ↓
HTTP session updated
```

The legacy implementation uses server-side HTTP session state rather than JWT-based authentication.

The account slice will initially preserve the session-oriented interaction model using Spring Security.

---

## Sign-In Flow

Observed authentication behavior can be summarized as:

1. A protected Storefront request is intercepted by `SignOnFilter`.
2. The original target URL may be stored in the HTTP session.
3. Credentials are submitted through the legacy sign-on flow.
4. `SignOnEJB` performs authentication.
5. `UserEJB` is used to resolve the user.
6. The stored password is compared directly by the legacy code.
7. Successful authentication updates session state.
8. The request flow continues into the Storefront.

The modern implementation will replace custom authentication plumbing with Spring Security.

---

## Sign-Out Flow

Observed sign-out behavior:

```text
SignOffHTMLAction
    ↓
HttpSession.invalidate()
    ↓
Replacement session created
```

The modern application will preserve the user-visible behavior while relying on Spring Security's session-management support.

---

## Account Creation Flow

Account registration is split into user creation and customer/profile creation.

### User creation

Conceptually:

```text
HTTP POST
    ↓
CreateUserHTMLAction
    ↓
CreateUserEvent
    ↓
CreateUserEJBAction
    ↓
SignOnEJB
    ↓
UserEJB
```

### Customer creation

Customer details are then handled through a separate flow:

```text
CustomerHTMLAction
    ↓
CustomerEvent
    ↓
CustomerEJBAction
    ↓
CustomerEJB
    ↓
AccountEJB
    ↓
ContactInfoEJB
    ↓
AddressEJB
    ↓
CreditCardEJB
    ↓
ProfileEJB
```

The legacy framework's `Event` objects are synchronous, in-process commands. They are not JMS events.

---

## Account Persistence Model

The legacy customer is not stored as a single aggregate.

Instead, it is distributed across:

- user credentials
- customer identity
- account status
- contact information
- postal address
- credit-card data
- customer preferences

Relevant tables include:

```text
UserEJBTable
CustomerEJBTable
AccountEJBTable
ContactInfoEJBTable
AddressEJBTable
CreditCardEJBTable
ProfileEJBTable
```

The relationships were verified directly in Cloudscape.

---

## Account Update

Observed account updates follow the same synchronous application path.

Conceptually:

```text
HTTP Request
    ↓
CustomerHTMLAction
    ↓
CustomerEvent
    ↓
CustomerEJBAction
    ↓
Customer / Account / Contact / Profile entities
    ↓
Cloudscape
```

The update path does not introduce a JMS boundary.

---

## Confirmed Registration Defect

The registration flow exposes a request-forwarding defect.

Observed sequence:

```text
POST /createuser.do
    ↓
User creation succeeds
    ↓
Customer creation succeeds
    ↓
Flow handler reads original destination
    ↓
RequestDispatcher.forward("/customer.do")
    ↓
Original HTTP request is reused
    ↓
action=create remains present
    ↓
CustomerHTMLAction handles CREATE again
    ↓
Second customer creation attempted
    ↓
DuplicateKeyException
    ↓
HTTP 500
```

The key issue is not simply a missing duplicate check.

The root cause is that an internal server-side forward reuses the original POST request and its parameters.

### Target behavior

The modern application must ensure that successful registration cannot accidentally replay a create command.

For the REST/API layer, registration will be a single operation with deterministic success and duplicate-user responses.

If a server-rendered UI is added, successful form submission should follow a redirect-based pattern rather than forwarding the original POST into another create-capable route.

---

## Password Modernization

The legacy application performs direct password comparison.

The modern implementation will use:

```text
Spring Security PasswordEncoder
    ↓
BCryptPasswordEncoder
```

The application service will depend on the `PasswordEncoder` abstraction rather than on ad hoc hashing logic.

Passwords will not be stored in recoverable form.

---

## Target Account Aggregate

The exact MongoDB document will be finalized during implementation, but the working aggregate boundary is:

```text
CustomerAccount
├── username
├── passwordHash
├── status
├── contact
│   ├── firstName
│   ├── lastName
│   ├── email
│   ├── phone
│   └── address
├── preferences
└── payment summary
```

The target model will not copy the seven-table relational structure mechanically.

A unique index on username will enforce account identity at the database level.

---

## Payment Data Scope

The legacy application stores card details directly.

For the modernization exercise, the account model should avoid persisting a full raw card number where it is not necessary for demonstrating the business flow.

A reduced representation such as card type, masked / last-four value, and expiry information is preferred for the demo.

---

## Target API Behavior

The initial slice is expected to support operations equivalent to:

```text
Register account
Sign in
Get current account
Update current account
Sign out
```

Expected characteristics:

- synchronous request handling
- unique username enforcement
- BCrypt password hashing
- session-based authentication
- validation at the HTTP boundary
- deterministic error mapping
- structured logging
- automated regression tests

---

## Explicit Non-Requirement: Messaging

There is no observed JMS dependency in Account / Customer / Authentication.

Therefore the initial slice will **not** introduce:

- Kafka
- RabbitMQ
- Artemis
- JMS

Adding messaging here would increase complexity without preserving an observed business requirement.
