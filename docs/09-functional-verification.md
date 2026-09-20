# External functional-verification exercise

Use this guide **after the application is running**, following [installation and setup](08-installation-and-setup.md). No IDE is required. These are mostly browser actions and apply to Windows and macOS. This is an exercise for an external tester, not a claim that these steps have already passed on their machine.

Record the commit (`git rev-parse HEAD`), OS, browser, date, any overrides, and failures in the checklist at the end. Use demo data only; no real payment credentials are needed. Do not reset Docker volumes during this sequence.

## Preflight and browser sessions

- [ ] `http://localhost:8080` opens the shop.
- [ ] `http://localhost:8025` opens Mailpit.
- [ ] Mongo, Artemis and Mailpit are running; Mongo `rs0` is writable.
- [ ] Storefront (8080), Order Processing (8081), and Supplier (8082) have completed startup.
- [ ] Order Processing has `NOTIFICATION_ENABLED=true`; its local SMTP destination is `localhost:1025`.
- [ ] Admin and Supplier local credentials are known: defaults are `admin/admin` and `supplier/supplier`, unless overridden.
- [ ] Artemis console `http://localhost:8161/console` opens with `petstore/petstore-dev`, unless overridden.

Use **separate browser profiles or different browsers** for Customer, Admin and Supplier so each has its own login/session/cart. Ordinary tabs in one profile share login state. Several private windows of the same browser may also share a private session. Alternatively, log out before switching identity and record order IDs before doing so.

Application UI URLs always use **Storefront :8080**. Do not browse directly to backend APIs to bypass role checks. Mailpit and Artemis are separate infrastructure consoles.

Orders and emails are asynchronous. Use the Admin/Supplier **Refresh** controls and allow several seconds for processing; these tables do not need to update instantly. Record every order ID from checkout and match it in the other views and Mailpit. Unexpected extra messages may belong to earlier runs or redelivery; email is best-effort, not an exactly-once guarantee. Never repeatedly submit checkout just because fulfilment is still running.

## 1. Test A — Registration, login and Account

1. Open `http://localhost:8080/register` in the Customer profile. Choose a new username such as `local.tester` (use a unique suffix on subsequent runs) and a password meeting the form's requirements.
2. Fill required name/address fields with fictional data. Use a syntactically valid fake email such as **`local.tester@example.com`**. It does not need to exist: Mailpit catches local messages.
3. For later checkout, enter demo payment metadata: card type `VISA`, synthetic card-number input `4111111111111111`, and an expiry in the form's `MM/YYYY` format, such as `12/2030`. Do not use a real card. This demo performs format checks, not payment authorization.
4. Create the account. Expect a success message and navigation to `/login`; sign in with the new credentials.
5. Open `http://localhost:8080/account`. Confirm the account/contact fields and saved card display, such as **VISA ending in 1111**. The full-number input should be blank; only card type, last four digits and expiry metadata are retained. Save an ordinary account-field change while leaving the number blank, then reload and confirm the saved metadata remains.
6. Log out with the UI, then log in again. Confirm the account is still usable.

**Expected:** registration succeeds, login/logout works, account edits persist, and the full card number is not returned for display. This browser exercise checks display behavior; automated tests cover persistence restrictions. Do not inspect or collect raw payment/customer database contents as test evidence.

## 2. Test B — Catalog and search

1. Open `http://localhost:8080/shop`. Confirm categories including Fish and Cats display.
2. Follow Fish → a product → item listing; then try Cats. Product pages show selectable items with IDs and prices. There is no separate item-page step required.
3. With locale **en-US**, search for `Angelfish`; expect the matching catalog product. Try an obviously nonexistent term and check the clean empty-result behavior.
4. Switch locale between en-US and ja-JP; verify browsing still works. Where translations exist, catalog content/prices can change.
5. Open `http://localhost:8080/shop/products/FL-DSH-01` in ja-JP and inspect **EST-15**. That item lacks Japanese details in the fixture; English fallback should remain visible instead of a missing/broken item.
6. **Return to en-US before the order tests.** Order thresholds and prices depend on locale.

**Expected:** category/product/item navigation and search work; localized catalog details fall back to en-US, then the first available detail. This is not full UI translation.

## 3. Test C — Cart

1. Begin with an empty Customer cart at `http://localhost:8080/cart`.
2. Open `http://localhost:8080/shop/products/FI-SW-01` in en-US and add **EST-1** once. Check quantity **1** and unit price **16.50** with the default seeded catalog.
3. Update its quantity to **2** using the cart's **Update quantity** action. Expect line total **33.00**.
4. Navigate to Shop, then back to Cart in the same session. The line and quantity should remain.
5. Remove the item. Expect an empty cart and updated total.

**Expected:** add, update, remove and totals work; ordinary navigation preserves the session cart. The later order examples assume you start each with an otherwise empty cart.

## 4. Test D — Small automatically approved order

Before ordering, sign into the Supplier profile and open `http://localhost:8080/supplier/inventory`. Ensure **EST-1 has stock**; the fresh seed is 10000. If necessary, set a positive quantity, noting that existing pending fulfilments can consume it immediately.

1. As Customer in **en-US**, add **EST-1 × 1**. With default data the total is **16.50**, below the **500** automatic-approval threshold.
2. Open `/checkout`, review/complete billing and shipping details, and submit once.
3. Record the order ID. The synchronous confirmation reports acceptance as **PENDING**; approval and shipment occur asynchronously afterward.
4. Return to Cart. It should be empty after the successful downstream **201 Created** response.
5. As Admin, open `http://localhost:8080/admin/orders`. Change the default PENDING filter to **All** or **Completed** and refresh. This small order may already have disappeared from the PENDING view. Expect **COMPLETED** when stock and all services are available.
6. As Supplier, open `/supplier/orders` and locate that ID under the appropriate filter. Check completed fulfilment and shipped quantities; inventory should have decreased by the allocation.
7. In Mailpit, match the ID in all three subjects:

   - `Java Pet Store Order Status: <orderId>` — body says **APPROVED**.
   - `Java Pet Store Order Shipped: <orderId>` — identifies the shipment pass.
   - `Java Pet Store Order COMPLETED: <orderId>` — says fulfilment is complete.

**Expected:** one successful checkout clears the cart; the order eventually completes; the normal one-pass flow produces approval, shipment and completion emails. Emails are not payment receipts or proof of payment authorization.

## 5. Test E — Large order and manual approval

1. Empty the Customer cart and keep **en-US** selected.
2. Add **EST-1 × 31**. With the checked-in price, **31 × 16.50 = 511.50**, which is at or above the automatic-approval boundary. Check the actual checkout total before submitting; if local catalog data differs, choose quantities that reach at least 500.
3. Submit once and record the new ID.
4. As Admin on `/admin/orders`, use the **Pending** filter and refresh. Expect this order to remain **PENDING** until an Admin acts. It should not yet have an approval or shipment email.
5. Ensure Supplier has sufficient EST-1 stock, then click **Approve** for this order.
6. Refresh Admin with **All/Completed** selected and check Supplier fulfilment. Expect **COMPLETED** if stock is available.
7. Verify APPROVED status, shipped and completed emails in Mailpit for this ID.

**Expected:** manual approval succeeds once and starts Supplier processing. If stock is insufficient, investigate inventory rather than treating partial fulfilment as an approval failure.

## 6. Test F — Denial

1. As Customer, create another **en-US EST-1 × 31** order with total **511.50**, starting with an empty cart.
2. Record its ID; verify **PENDING** in Admin.
3. Click **Deny**, then use the **Denied/All** filter and refresh.
4. Verify **DENIED** and a Mailpit message with subject `Java Pet Store Order Status: <orderId>` and body **DENIED**.
5. In Supplier Orders, select **All** and refresh. This denied order should have no fulfilment record. Confirm no shipped/completed email arrives for its ID.

**Expected:** denial stops Supplier processing. The denied order remains part of the date-based legacy statistics in Test H.

## 7. Test G — Partial fulfilment and replenishment

Use a controlled local dataset without other outstanding EST-15 demand if you want the exact stock figure below. Record the original quantities before changing stock; a Supplier quantity update **sets an absolute quantity**, it does not add that amount.

1. In the Supplier profile, open `/supplier/inventory`. Set **EST-15 quantity = 0** and confirm the returned/displayed quantity. Ensure EST-1 still has stock.
2. In the Customer profile, empty the cart, select **en-US**, and add two lines:

   - **EST-1 × 1** at **16.50**.
   - **EST-15 × 1** at **23.50** (product `/shop/products/FL-DSH-01`).

3. Confirm total **40.00**, submit checkout once, and record the ID.
4. In Admin Orders, choose **Partially shipped/All** and refresh. Expect **SHIPPED_PART** after the available EST-1 line ships.
5. In Supplier Orders, choose **Pending** and inspect the order's line/shipment details. EST-1 should be shipped and EST-15 outstanding. Supplier uses its own **PENDING/COMPLETED** fulfilment status; do not expect Supplier's status label to be SHIPPED_PART.
6. In Mailpit, expect the APPROVED status email plus **one shipped notification**. There should be **no COMPLETED email yet**.
7. In Supplier Inventory, set **EST-15 quantity = 10**.
8. A positive quantity update automatically retries pending fulfilments. Refresh Supplier/ Admin. With only this outstanding demand, the authoritative returned quantity may already be **9**, because one unit has immediately been allocated.
9. Expect another `Java Pet Store Order Shipped: <orderId>` email for the later pass, followed by `Java Pet Store Order COMPLETED: <orderId>`.
10. Confirm Admin **COMPLETED**, Supplier **COMPLETED**, and all line quantities fulfilled.

**Expected:** two shipment passes produce two shipped notifications, but completion is notified only when all lines are fulfilled. The returned stock can be lower than the entered value because retry/allocation occurs before the response. Other pending orders can consume additional units. Record the final stock; restoring stock with another positive update can also trigger retries. The Supplier Inventory page also offers **Retry pending fulfilments** for an explicit retry.

## 8. Test H — Admin statistics

1. As Admin, open `http://localhost:8080/admin/statistics`, or use **Statistics** from Admin Orders.
2. Confirm the initial range covers the last **30 inclusive UTC calendar days**. Select start/end dates that include your recorded orders and click **Apply**. Inputs use `YYYY-MM-DD`; both dates include their entire UTC day.
3. Confirm **Total Revenue** and **Units Sold**.
4. Confirm **Revenue by Category** is a donut/pie visualization. The legend must show category, **exact revenue**, and **percentage of total revenue**. Display percentages are rounded to two decimal places.
5. Confirm **Units Sold by Category** remains a bar visualization with category and **exact units**, not a count of order records.
6. Check category totals against recorded orders: revenue is `SUM(quantity × unitPrice)` and units are `SUM(quantity)`, grouped by category. Summary values must equal the corresponding category sums.
7. For a clean date range containing **only Tests D, E, F and G, once each**, the default-fixture check is:

   | Category | Revenue | Units |
   | --- | ---: | ---: |
   | FISH | 1056.00 | 64 |
   | CATS | 23.50 | 1 |
   | **Total** | **1079.50** | **65** |

   If other orders exist in that range, they also contribute; use the recorded baseline/deltas instead of expecting these absolute totals. Approval, denial and shipment do not add another copy of an order's sales values.
8. Set both dates to the UTC creation date of a known order. It should still contribute. Select a date range known to contain no orders: expect **“No sales data for the selected period.”**
9. Try a start date after the end date: expect a controlled validation response/page rather than a server crash. If a range has line items but zero revenue, the chart should show **No revenue** with zero percentages, while units remain visible; do not change catalog data just to force this optional case.
10. Follow the **Orders** link back to Admin Orders.

**Deliberate legacy parity:** statistics include **all orders selected by order date**, regardless of PENDING, APPROVED, DENIED, SHIPPED_PART or COMPLETED status. The denied order in Test F still contributes. These are **legacy Pet Store sales statistics, not recognized accounting revenue**. No payment authorization, currency conversion, item-level drill-down or accounting reporting is implied. Keep the verification orders in en-US for directly comparable fixture totals.

## 9. Test I — Role separation

Use the correct independent browser profile for each row:

| Identity | Action | Expected |
| --- | --- | --- |
| CUSTOMER | Open `/admin/orders` and `/admin/statistics` | Controlled **403 Forbidden** |
| CUSTOMER | Open `/supplier/inventory` and `/supplier/orders` | Controlled **403 Forbidden** |
| ADMIN | Open Admin Orders and Statistics | Access allowed |
| SUPPLIER | Open Supplier Inventory and Orders | Access allowed |
| ADMIN-only | Open Supplier Inventory | **403**; ADMIN is not automatically SUPPLIER |
| SUPPLIER-only | Open Admin Statistics | **403** |
| Logged out | Open an Admin or Supplier browser page | Redirect to `/login` |

Protected Storefront Admin/Supplier **API** calls return **401** when unauthenticated and **403** for an authenticated wrong role. A controlled 403/error page is expected here, not a login success or hidden access to data. Do not grant extra roles to make a negative test pass.

## 10. Test J — Checkout failure and cart retention (optional, recommended)

Do this after the normal order/statistics exercises so it does not disrupt their processing.

1. Stop **Order Processing only**, using Ctrl+C in its terminal. Keep Storefront, Supplier, Docker and infrastructure running.
2. As Customer, add an item to an otherwise empty cart and record its quantity.
3. Attempt checkout once. Expect a controlled message that order creation could not be confirmed; the Storefront checkout request should return **502** (visible in browser developer tools if desired).
4. Return to Cart. The item and quantity must remain intact.
5. Restart Order Processing from the repository root. In Windows PowerShell:

   ```powershell
   $env:NOTIFICATION_ENABLED = "true"
   .\mvnw.cmd -pl order-processing-service spring-boot:run
   ```

   On macOS, use `NOTIFICATION_ENABLED=true ./mvnw -pl order-processing-service spring-boot:run` in its terminal.

6. Wait for successful startup and check Admin for any recorded attempt before deciding whether to retry checkout.

**Expected:** Storefront retains the cart when it cannot confirm downstream order creation. This demonstrates a failure boundary, **not distributed rollback**. In general, a timeout/502 can occur after persistence, so do not assume every failure means no order exists. With Order Processing fully stopped before the request, it cannot accept that new attempt.

## Architecture rationale for verification

Browser requests pass through Storefront session/CSRF checks, synchronous order acceptance, Artemis approval, Supplier atomic whole-line allocation, and typed shipment events that update order progress. Orders can complete in one pass or through SHIPPED_PART; denial stops further processing. Business-event idempotency protects against duplicate allocation/application, while Mongo/JMS publication gaps still require production outbox/reconciliation work. Email remains best-effort rather than exactly-once.

## Results and evidence

Fill this in during actual execution; nothing is pre-marked as passed. Record order IDs, dates, non-sensitive screenshots, exact errors and relevant startup logs. Do not include passwords, full card inputs or unnecessary personal/address data.

| Functional area | Expected result | Pass/Fail | Notes |
| --- | --- | --- | --- |
| Registration | New customer created | | |
| Login/logout | Sessions start/end correctly | | |
| Account | Updates persist; payment display metadata only | | |
| Catalog | Category/product/item navigation and locale fallback | | |
| Search | Known product and clean no-match results | | |
| Cart | Add/update/remove, totals and session retention | | |
| Small auto order | Accepted PENDING, eventually COMPLETED with stock | | |
| Admin approval | Large order remains PENDING until approved | | |
| Admin denial | DENIED; no Supplier fulfilment | | |
| Supplier inventory | Set quantity returns authoritative stock | | |
| Partial fulfilment | SHIPPED_PART with outstanding line | | |
| Replenishment | Positive stock update retries and completes | | |
| Email notifications | Approval/denial, each shipment pass, completion in Mailpit | | |
| Admin statistics | All-status date totals, revenue donut, units bars | | |
| Role separation | Correct roles allowed; wrong roles 403 | | |
| Checkout failure/cart preservation | Controlled 502; cart unchanged | | |

When finished, stop applications with Ctrl+C and use normal `docker compose down` if desired. Do not use `down -v` unless intentionally discarding all local state. Preserve Mailpit evidence before recreating its container.
