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

| Capability | Verified legacy behavior | Current status / remaining work |
|---|---|---|
| Order acceptance | OPC persists PurchaseOrder and starts PENDING | Implemented: immutable snapshot, server-generated ID/time/status and recalculated total |
| Order lifecycle | PENDING, APPROVED, DENIED, SHIPPED_PART, COMPLETED | Partially implemented: enum exists; only PENDING creation is implemented |
| Async transport | JMS / MDB processing | Not yet implemented: future Artemis + Spring JMS |
| Auto-approval | en-US total < 500; ja-JP total < 50000 | Not yet implemented; no threshold rules run today |
| Manual/Admin approval and commit | Verified | Not yet implemented; no admin API/UI |
| Fulfilment/invoice/completion | Distinct from approval | Not yet implemented |
| Supplier boundary | Separate legacy subsystem | Partially implemented: independently bootstrappable service only |
| Inventory, allocation, out-of-stock, replenishment | Verified supplier behavior | Not yet implemented; no business documents, endpoints, or calls |
| Order querying/history and Admin statistics | Legacy order-management/reporting | Deferred; confirmation panel is not an order-history feature |

## Optional / deferred capabilities

JWSDP/JAX-RPC variants, optional email notifications, and alternate relational database configurations are not migrated. Java Web Start technology is not being reproduced. Payment tokenization, production service-access controls, and operational hardening are separate follow-up work.

See [legacy observations](01-legacy-system-overview.md), [defect status](03-legacy-defects.md), and [current order flow](07-order-processing.md). Tests verify the implemented slices; they do not demonstrate deferred asynchronous functionality.
