# Functional parity

Parity is measured by business outcome, not reproduction of EJB/JSP/CMP implementation details. **Implemented** means the current code and tests support the behavior. **Partially implemented** identifies a working subset with explicit gaps. **Deliberate modernization difference** identifies an intentional change to verified legacy behavior. **Deferred / excluded** identifies functionality outside the implemented scope.

## Account / authentication

| Capability | Verified legacy behavior | Current modern behavior | Status |
|---|---|---|---|
| Register | Separate user/customer creation; POST-forwarding defect | One validated MongoDB transaction across `users`/`customers`; no create replay | Implemented |
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
| Catalog navigation | Category → Product → `item.jsp` | Category → Product → `/shop/items/{itemId}`; direct Add to Cart also remains on Product cards | Implemented |
| Search result/matching semantics | Item-oriented results; effectively ANY-token/OR matching | Unique Product results; ALL normalized tokens must match locale-resolved Product name/description, category ID or associated Item descriptions | Deliberate modernization difference |
| Paging | `start`/`count`, default count 2; Previous/Next | `page`/`size`, default size 2; MongoDB paging/counting and localized Previous/Next | Implemented with a modern API contract |
| Catalog presentation | Original category/product/item GIF/JPEG references | Image metadata preserved; local category or neutral fallbacks with localized accessible labels | Deliberate presentation difference |
| Locale | en-US, ja-JP, zh-CN; EST-15 lacks Japanese details | Requested locale → en-US → first available; no synthesized details | Implemented |

Seed counts: **5 categories, 16 products, 28 items**, with **15 category, 48 product, 83 item details**. This does not import legacy users, customers, or payment data. Blank search returns an empty result; the UI restores category browsing.

## Cart and checkout

| Capability | Verified legacy behavior | Current modern behavior | Status |
|---|---|---|---|
| Anonymous cart | Session-oriented item ID → quantity | Session-scoped component, not MongoDB persistence | Implemented |
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
| Auto-approval | Strict en-US < 500 and ja-JP < 50000, zh-CN and other policy locale values remain pending | Implemented |
| Manual Admin decisions | ROLE_ADMIN UI/proxy, status filtering, atomic PENDING approve/deny using shared approval boundary | Implemented |
| Order lifecycle | PENDING → APPROVED → SHIPPED_PART/COMPLETED, or PENDING → DENIED | Implemented |
| Supplier | Separate service and database, InventoryRequested/InventoryFulfilled | Implemented |
| Stock and allocation | EST-1 through EST-29 seeded at 10000, conditional stock decrement, whole outstanding line or none | Implemented |
| Partial fulfilment/out-of-stock | Available lines ship independently, unavailable lines remain outstanding | Implemented |
| Replenishment | Exact stock replacement and automatic pending-order retry, optional explicit retry | Implemented |
| Supplier UI | ROLE_SUPPLIER inventory filter/update, pending/completed details and shipment history | Implemented |
| Invoice business effect | Typed JSON event and persisted shipment-pass history replace legacy XML transformation | Implemented replacement |
| Duplicate delivery | Persisted supplier progress and order fulfilment event IDs prevent double allocation/application | Implemented |

## Customer locale behavior

Customer requests resolve locale in this order: explicit `locale` query parameter → persisted Customer Profile preference → session locale → en-US. Underscore variants normalize to hyphenated tags; unsupported or blank values fall back to en-US. Registration without a preference uses the effective request locale. Account explicitly saves preferences; CUSTOMER login applies the saved preference to the session. Operational logins do not look up or create Customer documents.

The selector stores a session display choice and includes it in customer navigation and API URLs, including search and pagination. It does not save the profile. If an authenticated request omits `locale`, the saved preference takes precedence over the session fallback. Catalog detail fallback remains requested locale → en-US → first detail, independently per Product/Item. Display formatting uses the effective locale without currency conversion; checkout snapshots that locale, and email uses the snapshot rather than the customer's later preference. Approval thresholds remain unchanged, including no automatic approval policy for zh-CN.

## Implemented reporting and customer communication

| Capability | Status |
|---|---|
| Optional legacy email notifications | Implemented in Order Processing; en-US/ja-JP/zh-CN subjects and bodies use the immutable Order locale; best-effort delivery |
| Admin statistics | Implemented in Order Processing: date range, totals, revenue donut/pie and units-sold bars; MongoDB aggregation includes all workflow statuses |
| Customer Storefront localization | Implemented for en-US/ja-JP/zh-CN: pages, JavaScript messages, preferences and display formatting. Admin/Supplier consoles remain English |

Statistics preserve `SUM(quantity × unitPrice)` revenue and `SUM(quantity)` units by category. The legacy request named ORDERS counted units, not PurchaseOrder records. Date inputs represent inclusive UTC calendar days. The query runs from the start date through the start of the day after the end date, exclusive. This is legacy sales-statistics parity, not recognized accounting revenue.

Notifications cover ORDER_APPROVED, ORDER_DENIED, ORDER_SHIPPED and ORDER_COMPLETED. Each newly applied shipment pass requests a shipped email; the final pass also requests completion. They use the order-time locale and email snapshot for customer delivery. Delivery is configurable (enabled by default) and best-effort, with MongoDB/JMS publication gaps and possible duplicate emails.

## Deferred legacy features and excluded mechanisms

| Capability | Status |
| --- | --- |
| Favorite Category, My List and favorite-category promotional banner | Deferred; no favorite-category field or My List behavior exists. `linkPreference` is not a verified replacement for legacy `myListPreference`; saved banner/link flags alone do not implement these features |
| Remember-username cookie | Deferred; distinct from Spring Security's authenticated session |
| Country-aware region and expiry-date semantic validation | Deferred |
| JWSDP/JAX-RPC variants and alternate relational deployments | Intentionally not reproduced |
| Swing/Web Start Admin | Replaced by the browser Admin interface, including sales statistics |
| Legacy splash, sidebar, footer, flags and other UI chrome | Intentionally not reproduced; not functional defects |
| Real payment authorization/tokenization | Outside the implemented scope; raw-card persistence is removed |
| Outbox/reconciliation, service authentication/TLS, production secrets/HA | Production-hardening work |

See [legacy observations](01-legacy-system-overview.md), [defect status](03-legacy-defects.md), and [current order flow](07-order-processing.md). Automated tests and manual verification demonstrate implemented behavior, not production readiness.

## MongoDB implementation and remaining limits

Simple Product/Item ownership queries use Spring Data Pageable and compound indexes on `categoryId + _id` and `productId + _id`. Search uses `$lookup`, locale-resolution `$set` stages, literal escaped case-insensitive matching, and `$facet` for count and page data after filtering. Tokens are split on Unicode whitespace, lowercased and deduplicated; all must match. Empty queries return empty pages. Categories are not paginated.

Page responses expose `content`, `page`, `size`, `totalElements`, `totalPages`, `hasPrevious` and `hasNext`; pages start at 0 and size is bounded to 1–20. Ordinary indexes do not accelerate arbitrary contains-style text matching. Atlas Search is a possible production evolution for relevance, fuzzy and multilingual search, not an implemented dependency.

The legacy customer Storefront did not provide a customer order-history page; it is not a missing legacy-parity feature. MongoDB/JMS publication, HTTP retry idempotency and infrastructure hardening remain explicit limits rather than exactly-once guarantees.
