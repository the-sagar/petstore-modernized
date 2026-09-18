# Petstore Modernization

Modernization of the legacy **Java Pet Store 1.3.1_02** application to a modern Java platform using **Java 21, Spring Boot, Spring Security, and MongoDB**.

This project is being treated as a modernization exercise rather than a framework-only rewrite. Before implementing the target application, the legacy system was reproduced in its original runtime, functionally tested, instrumented, and inspected across the web, persistence, and messaging layers.

The objective is to preserve required business behavior while removing obsolete platform dependencies, correcting confirmed legacy defects, simplifying the domain model, and introducing modern engineering practices around security, validation, testing, and observability.

---

## Project Status

**Current phase:** Legacy analysis completed for the initial **Account / Customer / Authentication** vertical slice.

The modern implementation will begin with:

- account registration
- sign in
- sign out
- account retrieval
- account update
- MongoDB persistence
- validation
- BCrypt password hashing
- automated tests
- structured logging

Additional business flows will be migrated after the first vertical slice establishes the target application patterns.

---

## Legacy Application Baseline

The original Java Pet Store application was reproduced using its legacy-era stack:

- JDK 1.4.1
- J2EE SDK 1.3.1 Reference Implementation
- EJB 2.0
- JMS
- JSP / Servlets
- Cloudscape
- Java Web Start for the Admin client

The runtime was isolated in a 32-bit Debian virtual machine so that original application behavior could be observed without changing the platform.

### Functional areas examined

- Storefront
- Account / Customer management
- Shopping cart
- Checkout
- Order Processing Center (OPC)
- Supplier fulfilment
- Admin order management
- Reporting / statistics

### Legacy behaviors manually verified

- catalog browsing
- product search
- locale switching
- account creation
- sign in / sign out
- account update
- shopping cart
- checkout
- automatic approval for lower-value orders
- pending approval for higher-value orders
- Admin approve / commit
- supplier inventory updates
- out-of-stock fulfilment behavior
- inventory replenishment
- order completion
- Admin statistics

---

## Legacy Architecture Observations

The application combines synchronous web request processing with asynchronous order processing.

### Account / Authentication

The Account / Customer / Authentication flow is synchronous.

```text
HTTP Request
    ↓
HTMLAction
    ↓
Event
    ↓
EJBAction
    ↓
EJB
    ↓
Cloudscape
```

The legacy framework uses classes named `Event`, but these are in-process application commands rather than JMS messages.

Authentication follows a custom form/session model:

```text
SignOnFilter
    ↓
SignOnEJB
    ↓
UserEJB
    ↓
HTTP Session
```

### Order Processing

Checkout crosses a real asynchronous boundary:

```text
Checkout
    ↓
OrderEJBAction
    ↓
AsyncSenderEJB
    ↓
JMS
    ↓
PurchaseOrderMDB / OPC
    ↓
Approval
    ↓
Supplier
    ↓
Invoice
    ↓
Order Completion
```

The target architecture will preserve asynchronous processing only where the observed business workflow justifies it.

---

## Legacy Persistence Model

A single logical customer is distributed across several relational persistence entities:

```text
User
  │
Customer
  │
  ├── Account
  │     ├── ContactInfo
  │     │      └── Address
  │     └── CreditCard
  │
  └── Profile
```

Corresponding tables include:

- `UserEJBTable`
- `CustomerEJBTable`
- `AccountEJBTable`
- `ProfileEJBTable`
- `ContactInfoEJBTable`
- `AddressEJBTable`
- `CreditCardEJBTable`

The MongoDB target model will be designed around logical aggregates and application access patterns rather than reproducing this relational structure one-for-one.

---

## Confirmed Legacy Findings

Runtime testing, source inspection, temporary instrumentation, and direct Cloudscape queries identified several concrete issues.

### Duplicate customer creation after registration

A successful registration can be followed by an internal forward to `customer.do` while the original POST parameter `action=create` remains present.

That causes the customer creation path to execute a second time and can result in:

```text
DuplicateKeyException
HTTP 500
```

### Stateful EJB coupling

After certain transaction failures, later requests can encounter:

```text
javax.ejb.NoSuchObjectLocalException
```

because the application attempts to reuse stateful EJB session state that is no longer valid.

### Credit-card field mapping defect

Credit-card type and expiry values can be persisted into the wrong fields.

The defect was traced to a positional constructor argument mismatch where multiple parameters share the same `String` type, allowing the code to compile despite incorrect mapping.

### Weak geographic validation

Country and State / Province are independent inputs. Invalid combinations can therefore be accepted because the backend does not enforce semantic consistency between them.

### Obsolete hard-coded reference data

Some form values, including credit-card expiry choices, are hard-coded to historical values.

### Limited observability

Temporary instrumentation was required to trace important account, authentication, request-routing, JMS, fulfilment, and order-completion paths.

The modern application will add structured logging at meaningful business boundaries.

---

## Modernization Goals

The target application aims to:

- migrate the application to Java 21
- replace legacy J2EE / EJB dependencies with Spring Boot
- replace custom authentication infrastructure with Spring Security
- replace legacy password handling with BCrypt hashing
- simplify customer persistence using a MongoDB-oriented aggregate
- enforce deterministic validation and error handling
- prevent duplicate request processing
- reduce coupling to server-side stateful business components
- introduce automated unit and integration tests
- improve observability with structured logging
- preserve asynchronous processing only where required
- maintain documented functional parity with the legacy application

---

## Target Technology

Initial target stack:

- Java 21
- Spring Boot
- Spring Web
- Spring Security
- Spring Data MongoDB
- Bean Validation
- BCrypt
- Maven
- JUnit
- MongoDB

Additional infrastructure will be introduced only where supported by the analyzed legacy behavior and modernization requirements.

---

## First Modernization Slice

The first vertical slice covers **Account / Customer / Authentication**.

```text
Register
   ↓
Persist customer aggregate
   ↓
Authenticate
   ↓
Create session
   ↓
View account
   ↓
Update account
   ↓
Logout
```

The slice will include:

- unique username enforcement
- BCrypt password hashing
- server-side validation
- account retrieval
- account updates
- session-based authentication
- deterministic duplicate-account handling
- structured logging
- automated tests

---

## Functional Parity Strategy

Legacy behavior is classified into three categories:

1. **Preserve** — behavior required for functional compatibility.
2. **Correct** — confirmed legacy defects that should not be reproduced.
3. **Retire or defer** — optional or obsolete capabilities outside the initial modernization scope.

The detailed parity matrix is maintained in:

[`docs/04-functional-parity.md`](docs/04-functional-parity.md)

---

## Documentation

Detailed analysis and modernization notes are maintained under `docs/`.

- [Legacy System Overview](docs/01-legacy-system-overview.md)
- [Account and Authentication Flow](docs/02-account-auth-flow.md)
- [Observed Legacy Defects](docs/03-legacy-defects.md)
- [Functional Parity](docs/04-functional-parity.md)
- [AI Usage](docs/05-ai-usage.md)

These documents will evolve alongside the implementation.

---

## AI Usage

AI tooling is being used as an engineering assistant for selected activities such as:

- diagnostic instrumentation
- code scaffolding
- test scaffolding
- implementation suggestions
- documentation refinement

AI-generated changes are reviewed before acceptance.

Architecture decisions, scope decisions, runtime verification, defect interpretation, database inspection, and modernization choices are explicitly validated by the developer.

See:

[`docs/05-ai-usage.md`](docs/05-ai-usage.md)

---

## Running the Application

The modern implementation has not yet been completed.

Build and local run instructions will be added once the first modernized vertical slice is available.

---

## Repository Approach

The repository history is intentionally incremental:

```text
Legacy analysis
    ↓
Documented findings
    ↓
Functional parity definition
    ↓
Target design
    ↓
Vertical-slice implementation
    ↓
Automated verification
```

This keeps modernization decisions traceable to observed legacy behavior instead of treating the exercise as a framework-only rewrite.
