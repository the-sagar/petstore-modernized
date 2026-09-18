# Legacy Defects and Risks

## Purpose

This document records defects and modernization risks discovered during execution of the legacy application.

Findings are based on observed runtime behavior, source inspection, temporary instrumentation, and direct Cloudscape queries.

The intention is not to catalog every defect in the legacy codebase. It is to identify concrete examples that should influence migration design and regression testing.

---

## Finding 1 — Duplicate Customer Creation After Registration

### Observed behavior

A registration request can:

1. create the user successfully
2. create the customer successfully
3. create related Account / Profile / Contact / Address / CreditCard records
4. continue through a flow handler
5. forward the original POST request to `customer.do`
6. preserve `action=create`
7. execute the customer-create path a second time
8. fail on duplicate primary key
9. return HTTP 500

Observed failure includes:

```text
javax.ejb.DuplicateKeyException: Duplicate primary key
```

### Root cause

The flow uses `RequestDispatcher.forward(...)`.

An internal forward reuses the same request rather than creating a new browser request. The original POST parameters therefore remain available to the destination route.

Because `customer.do` interprets `action=create`, the second route invocation replays a create command that has already succeeded.

### Modernization treatment

- one registration operation per request
- deterministic duplicate-user handling
- unique username constraint / MongoDB unique index
- no internal forward that can replay the registration POST
- regression test reproducing the legacy failure scenario

---

## Finding 2 — Stateful EJB Invalid After Transaction Failure

### Observed behavior

After the duplicate customer-creation failure, later requests can encounter:

```text
javax.ejb.NoSuchObjectLocalException: The EJB does not exist.
```

The failure is associated with reuse of a stateful `ShoppingControllerEJB` reference.

### Risk

The HTTP session and server-side stateful component lifecycle are tightly coupled.

A failure can therefore affect subsequent requests in the same user session.

### Modernization treatment

- stateless application services
- authentication/session state handled through Spring Security
- business services not stored as stateful server objects in the HTTP session
- predictable exception mapping

---

## Finding 3 — Credit-Card Type and Expiry Mapping Defect

### Observed database state

Direct Cloudscape inspection showed values equivalent to:

```text
cardNumber = 0100-001-0001
cardType   = 01/2001
expiryDate = Java(TM) Card
```

The values in the type and expiry fields are reversed.

### Root cause

The caller constructs the credit-card value object using positional `String` arguments in one order while the constructor expects them in another.

Conceptually:

```text
Caller:
(number, type, expiry)

Constructor:
(number, expiry, type)
```

Because all three parameters are strings, the compiler cannot distinguish the semantic mismatch.

### Modernization treatment

- explicit DTO fields
- typed mapping
- mapping-focused tests
- avoid long positional constructors for semantically different values with identical Java types

---

## Finding 4 — Country / State Inconsistency

### Observed behavior

Country and State / Province are independent inputs.

The UI can therefore represent combinations that do not make semantic sense.

The backend validation checks presence of values but does not enforce a valid relationship between country and region.

### Modernization treatment

The target should avoid reproducing the exact hard-coded model.

Options include:

- country-aware validation
- country-aware region lists
- a less restrictive free-text region field with appropriate validation

The chosen approach should remain proportional to the take-home scope.

---

## Finding 5 — Obsolete Hard-Coded Expiry Values

Some legacy account / payment forms contain hard-coded historical expiry-year choices.

### Risk

Hard-coded reference data becomes invalid over time and can make otherwise valid input impossible.

### Modernization treatment

Generate current valid expiry ranges dynamically and validate them server-side.

---

## Finding 6 — Legacy Password Handling

The legacy sign-on code performs direct password comparison.

### Risk

Modern applications should not persist user passwords in a form that can be directly compared as plain values.

### Modernization treatment

Use Spring Security's `PasswordEncoder` abstraction with BCrypt.

The domain/application service should not contain custom password hashing logic.

---

## Finding 7 — Fragmented Customer Persistence

One logical customer spans:

```text
User
Customer
Account
ContactInfo
Address
CreditCard
Profile
```

### Risk

This increases:

- orchestration complexity
- relationship management
- failure points during create/update
- difficulty reasoning about aggregate consistency

### Modernization treatment

Model the customer according to aggregate ownership and access patterns in MongoDB rather than reproducing each legacy table as a separate collection.

---

## Finding 8 — Limited Success-Path Observability

Important flows were difficult to reconstruct from the default logs.

Temporary instrumentation was added to trace:

- registration
- authentication
- request routing
- order submission
- JMS send / receive
- approval
- supplier fulfilment
- invoice processing
- final completion

### Modernization treatment

Introduce structured logs around business transitions and include stable identifiers such as order ID where appropriate.

Do not log secrets, raw passwords, or full payment-card data.

---

## Finding 9 — Deployment Can Reset Persistence State

Legacy deployment configuration manages CMP table lifecycle.

Undeploy/redeploy operations were observed to recreate or reset application data.

### Risk

A deployment operation can change test data, making runtime investigation harder to reproduce.

### Modernization treatment

Separate application deployment from database lifecycle.

Schema/index creation and demo data should be explicit and repeatable.

---

## Patterns to Watch in Other Flows

The confirmed defects suggest several risk patterns that may recur elsewhere:

- internal forwarding after POST operations
- repeated positional `String` arguments
- hard-coded reference values
- weak server-side semantic validation
- stateful session coupling
- duplicate message/request handling
- implicit persistence lifecycle behavior

These are investigation targets, not assumed defects.

Future findings should only be promoted into this document after they are reproduced or supported by source evidence.
