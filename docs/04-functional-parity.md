# Functional Parity Matrix

## Purpose

Modernization is not intended to reproduce every implementation detail of the legacy application.

The target is to preserve required business behavior while deliberately correcting confirmed defects and deferring optional or obsolete capabilities.

The parity model uses three classifications:

- **Preserve** — behavior required for functional compatibility
- **Correct** — observed behavior exists, but represents a confirmed defect or security weakness
- **Defer / Retire** — not required for the initial modernization scope

---

## Account / Customer / Authentication

| Capability | Legacy Behavior | Target Behavior | Classification |
|---|---|---|---|
| Create account | Supported | Supported | Preserve |
| Unique username | Enforced indirectly through persistence; failure handling is poor | MongoDB unique index with deterministic conflict response | Preserve + Correct |
| Registration request handling | Original POST can be replayed by internal forward | Single registration operation; no duplicate replay | Correct |
| Password handling | Direct legacy password comparison | BCrypt via Spring Security `PasswordEncoder` | Correct |
| Authentication | Custom `SignOnFilter` + EJB + session | Spring Security + HTTP session | Preserve, reimplement |
| Sign out | Session invalidation | Spring Security session invalidation | Preserve |
| View account | Supported | Supported | Preserve |
| Update account | Supported | Supported | Preserve |
| Customer persistence | Split across multiple entities/tables | MongoDB-oriented customer aggregate | Reimplement |
| Country / state validation | Weak / independent fields | Server-side semantic validation | Correct |
| Credit-card mapping | Type / expiry defect observed | Correct mapping with tests | Correct |
| Credit-card storage | Legacy raw-style account data | Reduced / masked payment representation for demo | Correct / Reduce |
| Expiry options | Historical hard-coded values | Dynamic current validation | Correct |
| Logging | Limited | Structured business-event logging | Improve |

---

## Catalog / Search

| Capability | Legacy Behavior | Target Direction | Classification |
|---|---|---|---|
| Browse categories | Verified | Preserve | Preserve |
| Browse products | Verified | Preserve | Preserve |
| Browse items | Verified | Preserve | Preserve |
| Search | Verified | Preserve | Preserve |
| Locale switching | Verified | Preserve if included in final scope | Preserve / Scope-dependent |

Detailed target design for this area will be defined when the slice is migrated.

---

## Cart

| Capability | Legacy Behavior | Target Direction | Classification |
|---|---|---|---|
| Add item | Verified | Preserve | Preserve |
| Maintain cart state | Session-oriented | Preserve user-visible behavior with modern implementation | Preserve, reimplement |
| Checkout from cart | Verified | Preserve | Preserve |

Additional validation and edge cases will be documented when this flow is migrated.

---

## Order Processing

Runtime tracing confirmed that checkout transitions into asynchronous processing.

| Capability | Legacy Behavior | Target Direction | Classification |
|---|---|---|---|
| Submit order | Verified | Preserve | Preserve |
| Asynchronous processing | JMS after checkout | Preserve asynchronous business boundary | Preserve, reimplement |
| Automatic approval | Verified for lower-value order | Preserve unless requirements dictate otherwise | Preserve |
| Pending approval | Verified for higher-value order | Preserve | Preserve |
| Admin approval | Verified | Preserve | Preserve |
| Admin commit | Verified | Preserve if still required by target workflow | Preserve / Review |
| Fulfilment | Separate from approval | Preserve state distinction | Preserve |
| Out-of-stock behavior | Approved order can remain incomplete | Preserve | Preserve |
| Replenishment | Supplier update can allow order to complete | Preserve | Preserve |
| Invoice processing | Part of async completion flow | Preserve business effect | Preserve, reimplement |

---

## Admin

| Capability | Legacy Behavior | Target Direction | Classification |
|---|---|---|---|
| View pending orders | Verified | Preserve | Preserve |
| Approve order | Verified | Preserve | Preserve |
| Commit approved order | Verified | Review against target workflow | Preserve / Review |
| View completed/non-pending orders | Verified | Preserve | Preserve |
| Statistics | Verified | Preserve if included in final scope | Scope-dependent |

---

## Supplier

| Capability | Legacy Behavior | Target Direction | Classification |
|---|---|---|---|
| View inventory | Verified | Preserve | Preserve |
| Update inventory | Verified | Preserve | Preserve |
| Notify order processing | Verified | Preserve business effect | Preserve |
| Trigger re-evaluation of unfulfilled order | Verified | Preserve | Preserve |

---

## Optional / Deferred Legacy Capabilities

The following were identified in the legacy codebase but are not part of the initial modernization baseline:

| Capability | Legacy Availability | Initial Target |
|---|---|---|
| JWSDP / JAX-RPC deployment variant | Optional | Defer |
| Optional email notifications | Present but not baseline behavior | Defer |
| Alternate relational DB configurations | Supported by legacy deployment options | Retire from initial target |
| Java Web Start client technology | Used by legacy Admin | Do not reproduce technology choice |

---

## First Slice Acceptance Criteria

The Account / Customer / Authentication slice is considered functionally ready when:

1. a new user can register successfully
2. duplicate username registration returns a deterministic conflict response
3. password is stored only as a BCrypt hash
4. a registered user can sign in
5. invalid credentials are rejected without exposing sensitive details
6. authenticated user can retrieve account data
7. authenticated user can update supported account fields
8. sign out invalidates the authenticated session
9. country / region validation follows the modern rule selected for the target
10. credit-card type and expiry fields cannot be swapped by mapping
11. automated tests cover the duplicate-registration regression
12. logs provide enough context to trace success/failure without logging secrets

---

## Working Principle

Functional parity is judged by **business outcome**, not by preserving obsolete implementation mechanisms.

Examples:

```text
Preserve:
"Customer can sign in"

Do not preserve:
"Authentication must pass through SignOnEJB"
```

and:

```text
Preserve:
"Order processing continues asynchronously"

Do not preserve:
"The target must use the same JMS implementation and EJB MDB structure"
```

This keeps the migration behavior-focused rather than technology-copying.
