# Interview demo and verification

## Before the demo

Follow [setup](08-installation-and-setup.md). Mongo `rs0` must be writable, Artemis running, and all three services started. Open **http://localhost:8080** only for application browsing. Broker console: **http://localhost:8161/console** (`petstore/petstore-dev`).

Create a customer at `/register` with usable contact/address and payment display metadata. Operational local defaults: **admin/admin**, **supplier/supplier** (or configured overrides). Log out between actors; logout ends the session cart. Account shows card type and last4, with the replacement-number input empty. No real payment is authorized.

Use locale **en-US** throughout these examples. Ensure demo stock is available except where deliberately set to zero. Confirmation shows the initial PENDING acceptance snapshot; inspect later status at `/admin/orders`. Supplier work is at `/supplier/orders`.

## A — Small order automatically completes

1. As customer, browse FISH → Angelfish and add EST-1 (16.50) to cart.
2. Checkout with valid billing/shipping contacts. Note generated order ID and initial PENDING confirmation; cart is now empty.
3. Log in as Admin. Filter COMPLETED and find that ID after asynchronous processing: PENDING → APPROVED → COMPLETED with available stock.

## B — Large order manually approved

1. As customer, add EST-1 and set quantity **31**: total **511.50**.
2. Checkout and note ID. Automatic approval leaves this en-US order PENDING.
3. As Admin, `/admin/orders` defaults to PENDING. Click **Approve** for that ID.
4. Refresh/filter COMPLETED after Supplier processing. Insufficient stock instead leaves outstanding work or SHIPPED_PART.

## C — Large order denied

1. Create another EST-1 × 31 order as customer.
2. As Admin, click **Deny** while PENDING. Filter DENIED to verify it.
3. No inventory request is published for the denied order; it does not create Supplier fulfilment work.

## D — Partial fulfilment and replenishment

1. As Supplier, `/supplier/inventory`: note current quantities, ensure EST-1 has stock, filter **EST-15**, and **Set quantity** to **0**.
2. As customer, create a small two-line en-US order: EST-1 × 1 and EST-15 × 1. Checkout.
3. As Admin, verify SHIPPED_PART. As Supplier, `/supplier/orders` → PENDING shows EST-1 shipped and EST-15 remaining.
4. In Supplier Inventory, set EST-15 to **10**. Automatic retry ships the outstanding unit. The returned stock can be **9**: the UI displays authoritative stock, not the typed value.
5. Supplier fulfilment becomes COMPLETED with another shipment pass. Admin sees the original order COMPLETED.
6. Restore deliberately changed stock if desired after pending work is settled. PUT sets an exact value, not an increment; replenishment can immediately serve other pending orders. **Retry pending fulfilments** is also available for explicit demonstration.

## E — Downstream checkout failure retains cart

1. Keep Storefront running, stop only Order Processing via its terminal/IDE configuration.
2. As customer, add an item and submit checkout.
3. The UI reports Order Processing unavailable. Open Cart and confirm its lines/quantities remain.
4. Restart Order Processing. Failure is not proof of rollback if a remote order was already accepted; this deliberate connection-down example avoids that ambiguity.

## What to explain

Browser → Storefront HTTP session/CSRF → synchronous order acceptance → Artemis approval → Supplier atomic whole-line allocation → typed shipment event → order progress. Orders can complete directly or through SHIPPED_PART. Admin denial stops processing. Duplicate delivery is safe, but Mongo/JMS publication gaps still require production outbox/reconciliation work.

Last reported automated baseline: **215 tests**. Run root `clean verify` with Mongo available; this documentation pass could not reconfirm the baseline because local Docker/MongoDB was unavailable. Email, Admin statistics, Favorite Category/My List, full UI translation, real payment authorization and production service security/HA remain outside the implemented demo.
