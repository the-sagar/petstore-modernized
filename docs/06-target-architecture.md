# Current target architecture

## Service boundaries

Migration proceeded through bounded vertical slices: verify legacy behavior, preserve or explicitly revise the outcome, replace obsolete mechanisms, then test. This describes the migration approach, not a deployed legacy/modern routing gateway.

There are three independently deployable services, with no shared MongoDB entities, cross-service repository access or service-to-service Maven dependencies. Each service owns its contracts and database.

| Service | Responsibilities | Database |
| --- | --- | --- |
| Storefront Service :8080 | Identity, Customer aggregate, localized catalog/UI, search/pagination, session cart, checkout and Admin/Supplier HTTP proxies | `petstore_storefront` |
| Order Processing Service :8081 | Order snapshots, approval/denial, fulfilment progress, Admin statistics and customer email notifications | `petstore_orders` |
| Supplier Service :8082 | Inventory, allocation, pending fulfilments, shipment-pass history and replenishment | `petstore_supplier` |

## Canonical current-state architecture

![Current Java Pet Store architecture: browser entry, service-owned databases, HTTP calls, Artemis queues and local email delivery](images/01-current-architecture.svg)

Browser application requests enter only through Storefront. Storefront never directly reads the Order Processing or Supplier MongoDB databases; each service owns its DTOs and persistence. HTTP serves synchronous acknowledgement-oriented interactions; Artemis carries asynchronous workflow events. Mailpit supplies local SMTP; production SMTP is configured separately. RestClient handles checkout acknowledgement and operational API calls. ROLE_ADMIN and ROLE_SUPPLIER protect separate Storefront pages/proxies; ADMIN does not imply SUPPLIER. CSRF protects mutations, with the existing login/registration exemptions. Backend APIs have no service-to-service authentication/TLS yet and must not be treated as publicly secured APIs.

| Artemis queue | Payload and consumer |
| --- | --- |
| `petstore.order.submitted` | Order ID; Order Processing reloads the Order for automatic approval |
| `petstore.inventory.requested` | Order ID and line/item IDs with requested quantities; Supplier owns allocation |
| `petstore.inventory.fulfilled` | Event/order IDs, shipped line quantities and completion hint; Order Processing derives status from cumulative quantities |
| `petstore.notification.requested` | Notification/order IDs, type and optional shipment event ID; Order Processing reloads Order for recipient, locale and content |

Messages contain no customer contact, address or payment data. Spring JMS listeners use transacted sessions; thrown processing failures can trigger broker redelivery. SMTP failures are caught and acknowledged at the notification boundary. There is no custom dead-letter management interface or exactly-once guarantee.

## Legacy persistence to MongoDB aggregates

This was **not** a mechanical “one relational table → one MongoDB collection” migration. Embedding, references and separate collections were selected using aggregate ownership, lifecycle, read/write access patterns, service boundaries and concurrency requirements.

| Legacy concept | Modern MongoDB representation | Design rationale |
| --- | --- | --- |
| User / sign-on identity | Storefront `users`, linked by `customerId` | Authentication credentials/roles have a separate lifecycle and security concern; operational users need no Customer document |
| Customer / Account / Profile / ContactInfo / Address / CreditCard CMP relationships | Storefront `customers` embeds `account.contactInfo.address`, `account.creditCard` display metadata and `profile` | Customer-owned 1:1 data is read/updated with its aggregate. Only cardType, last4 and expiry metadata remain; raw PAN is not embedded |
| Category / CategoryDetails | Storefront `categories.details[]` | Localized details are bounded, parent-owned and normally read with Category |
| Product / ProductDetails | Storefront `products.details[]`, retaining `categoryId` | Localized details share Product lifecycle; the ID reference preserves category lookup without copying Category |
| Item / ItemDetails | Storefront `items.details[]`, retaining `productId` and `categoryId` | Locale-specific descriptions, attributes and prices belong to Item; references support direct lookup and cross-aggregate queries |
| PurchaseOrder / LineItem relationship or join table / ProcessManager workflow status | Order Processing `orders`: identity/contact snapshot, payment display snapshot, `lineItems[]`, `totalPrice`, `status`, `fulfilmentEventIds` and version | Lines and workflow state belong to Order. Embedding removes the relationship table. Order-time data is a historical snapshot, not a live Customer reference |
| InventoryEJB / inventory table | Supplier `inventory`, one document per itemId | Inventory belongs to Supplier, changes frequently and independently, and needs conditional atomic stock operations. It is not embedded in Storefront Catalog |
| SupplierOrder / Supplier LineItems / relationship table | Supplier `supplierOrders.lines[]` and `shipments[]`, with status/version | Progress and shipment history share SupplierOrder ownership. Supplier receives fulfilment IDs/quantities, not a duplicate customer/payment model |
| Invoice XML integration document | Typed `InventoryFulfilled` event plus persisted Supplier shipment history | The invoice's fulfilment/integration role is preserved without inventing a financial billing aggregate or `invoices` collection |

Embedding removes owned relationship tables; it does not eliminate every join. Catalog search deliberately uses `$lookup` to include descriptions from the separate Item collection. Money remains BigDecimal/Decimal128. No service reads another service's repositories, and no cross-service domain-model Maven dependency exists.

Registration commits User and Customer writes in one MongoDB transaction. Catalog seeding validates and inserts only the catalog data, excluding legacy users/payment data. Supplier commits conditional stock decrements and shipment progress/history in a local MongoDB transaction. None of these transactions spans services or JMS.

### Catalog search and paging

Product-by-category and Item-by-product lists use Spring Data Pageable with ascending ID order, supported by `categoryId + _id` and `productId + _id` compound indexes. APIs expose a typed `CatalogPage`, default size 2, zero-based page and size bounds 1–20. Root categories remain unpaginated.

Search starts with Products, uses `$lookup` for Items, `$set` for independently resolved localized details/text, `$match` for all normalized tokens, and `$facet` for total count and sorted `$skip`/`$limit` page data. Matching is case-insensitive literal substring search, with escaped tokens. Requested locale → en-US → first detail is resolved for each Product and each Item independently. Search intentionally returns Products with ALL-token matching, differing from legacy Item/ANY-token search.

Filtering/paging run in MongoDB without loading all Products/Items into Java. Ordinary indexes do not optimize arbitrary contains matching. Atlas Search could support production relevance/fuzzy/multilingual search, but it is not implemented or required.

Catalog image metadata is preserved. Storefront uses local category-specific symbols or neutral fallbacks with localized accessible labels, without making image requests. `/shop/items/{itemId}` uses the existing Item API, localized details and listPrice. Presentation does not alter catalog persistence.

### Admin statistics

Order Processing aggregates `petstore_orders.orders` through `$match` on `createdAt`, `$unwind` of `lineItems`, and `$group` by category, followed by projection/sort. Revenue sums quantity × Decimal128 unitPrice; units sum quantity. Storefront proxies `/api/admin/statistics` and renders `/admin/statistics` with a revenue donut/pie and units bars.

ISO date inputs describe inclusive UTC calendar days, queried through the next day's start, exclusive. All workflow statuses contribute, preserving legacy behavior; these statistics are not recognized accounting revenue. No additional statistics collection or cross-service query is used.

## Locale, concurrency and reliability

Customer locale precedence is explicit query → persisted preference → session → en-US, allow-listed to en-US/ja-JP/zh-CN. Checkout snapshots the effective locale. Notifications use Order.locale and the immutable order-time email, independent of later Account edits. Operational consoles remain English.

Atomic PENDING-only approval/denial allows one concurrent decision to win. Supplier's unique order ID, original-line comparison, conditional quantity decrement and persisted progress prevent duplicate allocation. Its versioned transactions retry aborted conflicts, not JMS sends or unknown commit outcomes. Order Processing applies each fulfilment event with a version/status predicate and `$addToSet` receipt in the same atomic update as quantities/status. Duplicate events do not intentionally generate another shipment notification.

Storefront clears its cart only after a valid downstream 201. Failure retains the cart, but a lost acknowledgement after Order persistence can produce duplicate orders on retry: there is no request-level idempotency key.

MongoDB writes and JMS publication are not atomic. Committed PENDING orders, approvals or Supplier shipments can require replay/reconciliation after publication failure. Email publication/delivery is best-effort; a lost publication can omit email, while redelivery or a crash after SMTP acceptance can duplicate it. There is no outbox, distributed transaction or automatic reconciliation. See [order processing](07-order-processing.md).

## Local infrastructure and production work

Compose uses `mongo:7.0` with single-node `rs0`, `apache/artemis:2.57.0-alpine` with a persistent volume and 128–256 MB configured heap, and `axllent/mailpit:v1.27.8`. Mailpit is a local SMTP sink on 1025 with UI on 8025; notifications are enabled by default and externally configurable. Neither local MongoDB nor Artemis is a production HA deployment.

Production work includes an outbox/reconciliation strategy, checkout idempotency, backend service authentication/TLS, secrets management, backup/recovery procedures, monitoring and HA deployments. Payment remains display metadata only, without authorization/tokenization or PCI certification. See [setup](08-installation-and-setup.md) and [functional verification](09-functional-verification.md).
