# Legacy System Overview

## Purpose

This document captures the baseline established before modernizing Java Pet Store 1.3.1_02.

The objective of the baseline was to understand how the application actually behaves at runtime before changing frameworks, persistence technology, or deployment architecture.

The analysis combined:

- execution of the original application
- manual functional testing
- source-code inspection
- temporary diagnostic instrumentation
- Cloudscape database inspection
- tracing of JMS-based order processing

---

## Runtime Reproduction

The legacy application was reproduced using its original-era technology stack.

| Area | Legacy Technology |
|---|---|
| Java runtime | JDK 1.4.1 |
| Application platform | J2EE SDK 1.3.1 Reference Implementation |
| Component model | EJB 2.0 |
| Web layer | JSP / Servlets |
| Messaging | JMS |
| Database | Cloudscape |
| Admin client | Java Web Start |
| Operating environment | 32-bit Debian virtual machine |

The application was intentionally kept on this stack during analysis so that modernization decisions could be based on observed legacy behavior rather than assumptions.

---

## Major Functional Areas

### Storefront

The Storefront provides the customer-facing web application.

Observed responsibilities include:

- catalog browsing
- product search
- locale switching
- account creation
- sign in / sign out
- account maintenance
- shopping cart
- checkout

### Order Processing Center

The Order Processing Center receives orders from the Storefront and participates in approval and order-state processing.

Runtime tracing confirmed that order processing crosses a JMS boundary after checkout.

### Supplier

The Supplier component manages inventory and participates in fulfilment.

Observed behavior included:

- inspecting inventory
- updating available quantity
- notifying order processing after inventory becomes available

A customer order can be approved without being complete when inventory is unavailable.

### Admin

The Admin application provides order-management and reporting capabilities.

Observed behavior included:

- viewing pending and non-pending orders
- approving pending orders
- committing approved orders
- viewing sales / revenue statistics

The Admin rich client was run through Java Web Start.

---

## Functional Baseline

The following behaviors were manually exercised before beginning the target implementation.

### Storefront

- browse categories
- browse products
- browse items
- search
- switch locale
- add items to cart
- create account
- sign in
- sign out
- update account
- checkout

### Order Approval

Two distinct behaviors were observed:

- lower-value orders can be approved automatically
- higher-value orders can enter a pending state requiring Admin action

The approval threshold is treated as legacy behavior to be preserved or explicitly revised during modernization, rather than assumed from UI behavior alone.

### Supplier / Fulfilment

An out-of-stock scenario was tested:

1. Inventory was reduced to zero.
2. A customer order was submitted.
3. The order was approved.
4. The order remained incomplete because inventory was unavailable.
5. Inventory was replenished.
6. Supplier notification caused the order to be re-evaluated.
7. The order subsequently reached completion.

This confirmed that **approval** and **fulfilment** are distinct business states.

---

## Synchronous and Asynchronous Boundaries

The legacy system uses both synchronous request processing and asynchronous messaging.

### Synchronous example: Account / Customer

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

The `Event` objects used by the web application framework are in-process command objects.

They are not JMS messages.

### Asynchronous example: Order Processing

Runtime instrumentation confirmed a flow equivalent to:

```text
Checkout
    ↓
OrderHTMLAction
    ↓
OrderEJBAction
    ↓
AsyncSenderEJB
    ↓
JMS
    ↓
PurchaseOrderMDB
    ↓
Approval processing
    ↓
SupplierOrderMDB
    ↓
Order fulfilment
    ↓
InvoiceMDB
    ↓
Order completion
```

This distinction is important to the target design: messaging is used for the genuine asynchronous approval and fulfilment boundaries, while account operations remain synchronous.

---

## Legacy Persistence Characteristics

The account domain is implemented using EJB CMP/CMR-style persistence.

One logical customer is spread across multiple entities and tables:

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

Relevant Cloudscape tables include:

- `UserEJBTable`
- `CustomerEJBTable`
- `AccountEJBTable`
- `ContactInfoEJBTable`
- `AddressEJBTable`
- `CreditCardEJBTable`
- `ProfileEJBTable`

Direct database inspection was used to verify the relationships between these records.

The implemented MongoDB models follow logical ownership and access patterns rather than mapping each legacy table to a separate collection. The legacy observations above remain the historical baseline.

---

## Deployment Behavior

The legacy deployment configuration manages CMP tables as part of deployment lifecycle behavior.

During analysis, undeploy/redeploy operations were observed to recreate or reset persisted application data.

This matters when interpreting test results because redeployment is not data-neutral in the legacy environment.

---

## Optional Legacy Capabilities

The codebase also contains capabilities that are not part of the default baseline used for this modernization.

These include:

- an optional JWSDP / JAX-RPC Web Services deployment variant
- optional email notification configuration
- alternate relational database configurations

These optional capabilities were identified but remain outside the completed migration scope.

---

## Key Modernization Implications

The baseline produces several concrete design implications:

1. Do not reproduce EJB-era layering mechanically in Spring Boot.
2. Do not model seven account-related tables as seven MongoDB collections by default.
3. Keep Account / Authentication synchronous.
4. Preserve asynchronous messaging only for business flows that genuinely require it.
5. Separate order approval from fulfilment state.
6. Introduce automated tests around defects discovered during baseline analysis.
7. Add structured observability so business flow tracing does not require temporary `System.out.println` instrumentation.


## Modern target mapping

| Verified legacy subsystem | Modern boundary | Implemented behavior |
|---|---|---|
| Storefront | `storefront-service` | Account/authentication, catalog/search, cart, checkout and browser UIs |
| Order Processing Center (OPC) | `order-processing-service` | Order creation, automatic/manual approval, denial and fulfilment progress |
| Supplier | `supplier-service` | Inventory, atomic allocation, partial fulfilment, replenishment and shipment history |
| JMS / EJB MDB boundary | ActiveMQ Artemis + Spring JMS | OrderSubmitted, InventoryRequested and InventoryFulfilled queues |
| Swing/Web Start Admin | Storefront Admin UI and HTTP proxy | Status filtering and individual approve/deny decisions |
| XML invoices | Typed fulfilment events and shipment-pass history | Records exactly which lines shipped in each pass |

Storefront submits orders synchronously over HTTP to confirm acceptance before clearing the cart. Subsequent approval and fulfilment preserve the asynchronous business boundary. See [current architecture](06-target-architecture.md), [order processing](07-order-processing.md), and [parity gaps](04-functional-parity.md).
