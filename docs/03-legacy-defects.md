# Legacy defects and risks

## Purpose

This document records defects and modernization risks discovered during execution of the legacy application.

Findings are based on observed runtime behavior, source inspection, temporary instrumentation, and direct Cloudscape queries.

Not every modernization difference is a legacy defect. Relational decomposition and optional deployment variants are design characteristics whose migration risks must be evaluated separately. The findings guide migration design and regression testing; they are not an exhaustive defect catalog.

## Modernization status

| Finding | Status | Current treatment / remaining gap |
|---|---|---|
| Duplicate registration through POST forwarding | Corrected | Single transactional registration operation, unique username index, deterministic conflict response |
| Invalid stateful EJB after failure | Intentionally not reproduced | Stateless services; session contains security state and a small ID/quantity cart |
| Card type/expiry mapping mismatch | Corrected | Explicit named field mapping and account/registration tests |
| Country/state inconsistency | Still outstanding | Required-field and email validation exists; no country-aware region validation |
| Historical expiry choices | Partially corrected | Historical dropdown removed; free-text expiry remains without dynamic expiry validation |
| Direct password comparison | Corrected | Spring Security and BCrypt password hashes |
| Fragmented customer persistence | Modernized design | Embedded customer aggregate plus separate linked user, registered in one MongoDB transaction |
| Limited observability | Improved for implemented slices | SLF4J business-event logs; approval, inventory and fulfilment events include business identifiers |
| Deployment resets data | Intentionally not reproduced | Persistent MongoDB volume; catalog seeding skips existing data rather than resetting it |
| Hard-coded checkout payment | Corrected | Payment display data comes from the authenticated customer's account; no hard-coded card sent to orders |
| Raw Storefront card persistence | Corrected | Display metadata only; startup migration removes legacy cardNumber fields. No payment authorization/tokenization is implemented |

“Corrected” applies to the migrated implementation, not to patches of the legacy runtime. Remaining semantic-validation and payment gaps are not counted as completed security work.

---

## Finding 1 — Duplicate customer creation after registration

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

The observed failure included:

```text
javax.ejb.DuplicateKeyException: Duplicate primary key
```

### Root cause

The flow uses `RequestDispatcher.forward(...)`.

An internal forward reuses the same request rather than creating a new browser request. The original POST parameters therefore remain available to the destination route.

Because `customer.do` interprets `action=create`, the second route invocation replays a create command that has already succeeded.

### Risk

A successful write can appear to fail, inviting retries and leaving the customer unable to continue reliably.

### Modernization treatment

- one registration operation per request
- deterministic duplicate-user handling
- unique username constraint / MongoDB unique index
- no internal forward that can replay the registration POST
- regression coverage for duplicate registration and transaction rollback

---

## Finding 2 — Stateful EJB invalid after transaction failure

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

## Finding 3 — Card type and expiry mapping defect

### Observed database state

Direct Cloudscape inspection showed values equivalent to:

```text
cardNumber = [omitted]
cardType   = [expiry-like value]
expiryDate = [card-type label]
```

The values in the type and expiry fields are reversed, making saved payment display metadata unreliable.

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

## Finding 4 — Country/state inconsistency

### Observed behavior

Country and state/province are independent inputs.

The UI can therefore represent combinations that do not make semantic sense.

The backend validation checks presence of values but does not enforce a valid relationship between country and region.

### Modernization treatment

The modern forms use free-text country and region fields with required-field/email validation. Country-aware validation or region reference data remains outstanding; semantic consistency is not currently enforced.

---

## Finding 5 — Obsolete hard-coded expiry values

Some legacy account / payment forms contain hard-coded historical expiry-year choices.

### Risk

Hard-coded reference data becomes invalid over time and can make otherwise valid input impossible.

### Modernization treatment

The historical dropdown is not reproduced. Modern forms accept free-text expiry values; dynamic current ranges and expiry-date validation remain deferred.

---

## Finding 6 — Legacy password handling

The legacy sign-on code performs direct password comparison.

### Risk

Modern applications should not persist user passwords in a form that can be directly compared as plain values.

### Modernization treatment

The modern implementation uses Spring Security's `PasswordEncoder` abstraction with BCrypt.

The domain/application service should not contain custom password hashing logic.

---

## Finding 7 — Fragmented customer persistence

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

The Customer aggregate embeds owned account/profile/contact/address/payment metadata, while User remains separate. Registration commits both in one MongoDB transaction. This is a data-model choice, not evidence that relational normalization itself was defective.

---

## Finding 8 — Limited success-path observability

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

Implemented SLF4J business-event logs cover authentication, catalog paging, order decisions, fulfilment, statistics and notification outcomes. Stable order/event IDs support correlation. Production monitoring, alerting and recovery procedures remain outstanding.

Do not log secrets, raw passwords, or full payment-card data.

---

## Finding 9 — Deployment can reset persistence state

Legacy deployment configuration manages CMP table lifecycle.

Undeploy/redeploy operations were observed to recreate or reset application data.

### Risk

A deployment operation can change test data, making runtime investigation harder to reproduce.

### Modernization treatment

Normal service restarts and `docker compose down` preserve MongoDB/Artemis named volumes. Catalog seeding skips existing catalog data and inventory seeding preserves stock. Destructive `docker compose down -v` still deliberately removes local data and requires replica-set initialization again. These local safeguards are not a production backup/recovery strategy.

---

## Finding 10 — Hard-coded checkout payment

Targeted legacy source inspection confirmed that the Storefront constructed an order using a hard-coded card rather than the current customer's payment information.

**Corrected:** modern checkout derives display information from the authenticated customer's saved account. Order Processing accepts/persists only `cardType` and four-digit `last4`, rejecting raw payment fields. It does not perform payment authorization.

## Payment-data risk — legacy and early modernization storage

Raw-number storage exposed sensitive payment data without being necessary for this application’s display-only checkout. **Corrected in the modern implementation:** Storefront now persists only card type, last4 and expiry metadata. Full numbers are transient write-only input; a startup MongoDB migration removes legacy cardNumber fields. Blank Account replacement input preserves last4; the UI shows a masked saved method and clears replacement input after save. This removes raw-number persistence from current customer documents; it is not tokenization or payment authorization. Historical backups are not rewritten. No PCI-compliance claim is made.

---

## Patterns to watch in other flows

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
