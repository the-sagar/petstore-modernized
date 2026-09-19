# Functional parity

Parity is measured by business outcome, not reproduction of EJB/JSP/CMP implementation details. **Implemented** means the current code and tests support the behavior. **Partially implemented** identifies a working subset with explicit gaps. **Deferred / Not yet implemented** is not a claim of completion.

## Account / authentication

| Capability | Verified legacy behavior | Current modern behavior | Status |
|---|---|---|---|
| Register | Separate user/customer creation; POST-forwarding defect | One validated Mongo transaction across `users`/`customers`; no create replay | Implemented |
| Unique username | Persistence constraint with poor failure handling | Normalized username, unique index, deterministic 409 | Implemented |
| Password/authentication | Direct comparison through custom sign-on/session code | BCrypt + Spring Security HTTP session | Implemented |
| Account GET/PUT | Customer account viewing/editing | Principal-derived ownership; validated DTOs; no saved card number in response | Implemented |
| Logout | Session invalidation | Spring Security logout; session invalidation and cookie removal | Implemented |
| CSRF | Legacy interaction model | Protection on account/cart/checkout/logout writes; existing auth endpoint exemptions | Implemented modernization control |
| Card type/expiry mapping | Positional mapping defect | Explicit field mapping | Implemented correction |
| Country/region semantics | Independent, weakly validated fields | Required-field/email checks; independent text country/region | Partially implemented; semantic validation outstanding |
| Expiry input | Historical choices | Free-text input; no current-date expiry validation | Partially implemented |
| Payment storage | Raw account card data | Display metadata only; startup migration removes legacy cardNumber; only cardType/last4 reach orders | Implemented display-only storage; no real payment authorization |

## Catalog / search

| Capability | Verified legacy behavior | Current modern behavior | Status |
|---|---|---|---|
| Categories/products/items | Six relational base/detail tables | Three collections with embedded localized details and legacy IDs | Implemented |
| Real catalog migration | Original `Populate-UTF8.xml` catalog | Catalog-only resource; validated transactional seed; skips any existing catalog | Implemented |
| Prices | Prices belong to locale-specific item details | `BigDecimal`/Decimal128 inside `ItemDetails` | Implemented |
| Browse/search | Catalog navigation and tokenized search intent | Public REST/UI; case-insensitive all-token substring search across locale-resolved names/descriptions, category IDs, associated item descriptions | Implemented |
| Locale | en-US, ja-JP, zh-CN; EST-15 lacks Japanese details | Requested locale → en-US → first available; no synthesized details | Implemented |

Seed counts: **5 categories, 16 products, 28 items**, with **15 category, 48 product, 83 item details**. This does not import legacy users, customers, or payment data. Blank search returns an empty result; the UI restores category browsing.

## Cart and checkout

| Capability | Verified legacy behavior | Current modern behavior | Status |
|---|---|---|---|
| Anonymous cart | Session-oriented item ID → quantity | Session-scoped component, not Mongo persistence | Implemented |
| Add | Sets quantity to 1, including repeat add | Same behavior | Implemented |
| Update/remove | Positive quantity stored exactly; nonpositive removes | Same behavior; DELETE idempotent | Implemented |
| Display/subtotal | Resolve current catalog data; price × quantity | CatalogService reused; current locale-resolved prices; BigDecimal totals | Implemented |
| Checkout identity | Authentication required entering order information | Authenticated session + CSRF; server-derived identity/cart/prices | Implemented |
| Billing/shipping | Separate order-time contacts | Prefilled editable UI snapshots; independent contacts accepted | Implemented |
| Order submission | PurchaseOrder sent into OPC messaging flow | Synchronous HTTP creation acknowledgement, then cart clearing | Implemented with deliberate transport change |
| Confirmation | Order acceptance precedes later processing | Storefront confirmation with generated ID, time, total, PENDING | Implemented |
| Downstream failure | Modern reliability decision | Checkout fails without clearing cart | Implemented |

## Order processing, Admin, and Supplier

| Capability | Current modernization | Status |
|---|---|---|
| Order acceptance | HTTP snapshot creation, generated ID/time, PENDING and recalculated BigDecimal/Decimal128 total | Implemented |
| Async transport | Artemis 2.57.0 and Spring JMS | Implemented |
| Auto-approval | Strict en-US < 500 and ja-JP < 50000, unsupported locales remain pending | Implemented |
| Manual Admin decisions | ROLE_ADMIN UI/proxy, status filtering, atomic PENDING approve/deny using shared approval boundary | Implemented |
| Order lifecycle | PENDING → APPROVED → SHIPPED_PART/COMPLETED, or PENDING → DENIED | Implemented |
| Supplier | Separate service and database, InventoryRequested/InventoryFulfilled | Implemented |
| Stock and allocation | EST-1 through EST-29 seeded at 10000, conditional stock decrement, whole outstanding line or none | Implemented |
| Partial fulfilment/out-of-stock | Available lines ship independently, unavailable lines remain outstanding | Implemented |
| Replenishment | Exact stock replacement and automatic pending-order retry, optional explicit retry | Implemented |
| Supplier UI | ROLE_SUPPLIER inventory filter/update, pending/completed details and shipment history | Implemented |
| Invoice business effect | Typed JSON event and persisted shipment-pass history replace legacy XML transformation | Implemented replacement |
| Duplicate delivery | Persisted supplier progress and order fulfilment event IDs prevent double allocation/application | Implemented |

## Actual remaining parity gaps and deliberate exclusions

| Capability | Status |
|---|---|
| Optional legacy email notifications | Not implemented |
| Admin statistics/revenue charts | Not implemented |
| Favorite Category/My List behavior | Not implemented as a customer feature; saved preferences alone do not implement it |
| Full UI translation | Not implemented; catalog locale data/fallback is implemented |
| Customer order history | Deferred; Admin queries and confirmation are separate features |
| Country-aware region and expiry-date semantic validation | Deferred |
| JWSDP/JAX-RPC variants, alternate relational databases, Swing/Web Start | Intentionally not reproduced |
| Real payment authorization/tokenization | Outside demo scope; raw-card persistence is removed |
| Outbox/reconciliation, service authentication/TLS, production secrets/HA | Production-hardening work |

See [legacy observations](01-legacy-system-overview.md), [defect status](03-legacy-defects.md), and [current order flow](07-order-processing.md). Local tests and demos demonstrate implemented behavior, not production readiness.
