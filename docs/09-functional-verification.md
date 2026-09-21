# External functional-verification exercise

Use this guide **after the application is running**, following [installation and setup](08-installation-and-setup.md). No IDE is required. The browser-based steps apply to Windows and macOS. Record results from your own environment.

Record the commit (`git rev-parse HEAD`), uncommitted workspace changes (`git status --short`), OS, browser, date, any overrides, and failures in the checklist at the end. Use demo data only; no real payment credentials are needed. Do not reset Docker volumes during this sequence.

## Preflight and browser sessions

- [ ] `http://localhost:8080` opens the shop.
- [ ] `http://localhost:8025` opens Mailpit.
- [ ] Mongo, Artemis and Mailpit are running; Mongo `rs0` is writable.
- [ ] Storefront (8080), Order Processing (8081), and Supplier (8082) have completed startup.
- [ ] Order Processing has `NOTIFICATION_ENABLED=true`; its local SMTP destination is `localhost:1025`.
- [ ] Admin and Supplier local credentials are known: defaults are `admin/admin` and `supplier/supplier`, unless overridden.
- [ ] Artemis console `http://localhost:8161/console` opens with `petstore/petstore-dev`, unless overridden.

Use **separate browser profiles or different browsers** for Customer, Admin and Supplier so each has its own login/session/cart. Ordinary tabs in one profile share login state. Several private windows of the same browser may also share a private session. Alternatively, log out before switching identity and record order IDs before doing so.

**Supplier sign-in sequence:**

1. Open `http://localhost:8080`.
2. Log out of the current Customer/Admin session if one exists.
3. Sign in as **Supplier** with `supplier / supplier`, unless overridden by environment configuration.
4. Allow the normal login redirect to `/supplier/inventory`.
5. Perform Inventory checks there and use the Supplier navigation for Orders/fulfilment checks.

**Admin sign-in sequence:** open `http://localhost:8080`, log out of the current role, then sign in as **Admin** with `admin / admin`, unless overridden. Use `/admin/orders` and its **Statistics** link to `/admin/statistics` after signing in.

`/supplier/**` requires **ROLE_SUPPLIER**. ROLE_ADMIN does not automatically include ROLE_SUPPLIER, and a Customer session cannot access Supplier pages. Manually changing the URL while signed in under the wrong role can return **403 Forbidden**, including a generic Spring error/Forbidden page. This is expected authorization behavior, not evidence that the Supplier Service failed. For normal functional tests, switch accounts through logout/login before opening protected pages. Test I deliberately checks denied access as a separate negative test.

Whenever a scenario says **“Log out, then sign in as…”**, use the account for that role before proceeding. With dedicated browser profiles, perform that login in the matching profile; do not assume another tab has a separate session. Customer means the registered account from Test A.

Application UI URLs always use **Storefront :8080**. Do not browse directly to backend APIs to bypass role checks. Mailpit and Artemis are separate infrastructure consoles.

Orders and emails are asynchronous. Use the Admin/Supplier **Refresh** controls and allow several seconds for processing; these tables do not need to update instantly. Record every order ID from checkout and match it in the other views and Mailpit. Unexpected extra messages may belong to earlier runs or redelivery; email is best-effort, not an exactly-once guarantee. Never repeatedly submit checkout just because fulfilment is still running.

## 1. Test A — Registration, login and Account

1. Open `http://localhost:8080/register` in the Customer profile. Choose a new username such as `local.tester` (use a unique suffix on subsequent runs) and a password meeting the form's requirements.
2. Fill required name/address fields with fictional data. Use a syntactically valid fake email such as **`local.tester@example.com`**. It does not need to exist: Mailpit catches local messages.
3. For later checkout, enter demo payment metadata: card type `VISA`, synthetic card-number input `4111111111111111`, and an expiry in the form's `MM/YYYY` format, such as `12/2030`. Do not use a real card. This demo performs format checks, not payment authorization.
4. Create the account. Expect a success message and navigation to `/login`; sign in with the new credentials.
5. Open `http://localhost:8080/account`. Confirm the account/contact fields and saved card display, with card type **VISA**, mask **•••• •••• •••• 1111** and expiry when available. The full-number input should be blank; only card type, last four digits and expiry metadata are retained. Save an ordinary account-field change while leaving the replacement number blank, then reload and confirm last4 remains 1111. Enter the synthetic replacement `5555 5555 5555 4444`, save, and confirm the summary changes to **•••• •••• •••• 4444** while the replacement input clears. A new account without last4 should show **No saved payment method**. Use only synthetic values; this is not payment authorization.
6. Log out with the UI, then log in again. Confirm the saved account details persist.

**Expected:** registration succeeds, login and logout work, account edits persist, and the full card number is not returned for display. This browser exercise checks display behavior; automated tests cover persistence restrictions. Do not inspect or collect raw payment/customer database contents as test evidence.

## 2. Test B — Catalog and search

1. Open `http://localhost:8080/shop`. Confirm the five categories and their category fallbacks display, including Fish and Cats. Check them while logged out as well.
2. Follow Fish → a Product → an Item ID. The Product page keeps direct Add to Cart buttons; the ID opens `/shop/items/{itemId}`. Confirm Item Detail shows the localized Product name, SKU, description, attributes, list price, Add to Cart and View Cart. For EST-1 in en-US, the list price is **16.50**; unitCost is not the customer price.
3. Check category-symbol placeholders on category Product cards, search results, Product Item cards and Item Detail. Angelfish retains `fish1.jpg` metadata but shows a Fish symbol, without a broken image or external request. Accessible labels use localized entity text; visible names/IDs remain available.
4. Use the Item Detail Shop/Category/Product links and confirm locale is preserved. Its Product back link returns to the default Product page, not a remembered pagination position. An unknown Item URL should display a controlled localized not-found message with no Add to Cart action.
5. With locale **en-US**, search for `Angelfish`. Try `fish australia` and confirm every result matches both tokens across its searchable Product/Item text. Search intentionally returns **Products and requires ALL tokens**; legacy search returned Items with effectively ANY-token matching. An obviously nonexistent term should show a clean empty state.
6. On Fish, use Previous/Next through Products (default two per page). On a Product with several Items, such as `K9-RT-02`, page through Items. Search `fish` and page its Product results. Confirm counts, stable ordering, disabled boundary buttons and no “Page 1 of 0” for empty results. Submitting new search text resets to page 0; paging and refresh preserve `q`, locale and page in the URL.
7. Switch between English (US), 日本語 and 中文. Navigation, Item Detail labels, search messages and Previous/Next controls should change language. Catalog detail fallback remains independent per entity, and numeric prices may differ according to the legacy localized data. No currency conversion occurs.
8. Open `http://localhost:8080/shop/items/EST-15?locale=ja-JP`. EST-15 lacks Japanese details, so its English description remains visible while customer UI labels are Japanese. A Japanese search for `マンクスネコ mouse` should still find its Product through independent Item fallback.
9. **Return to en-US before the order tests.** Order thresholds and prices depend on locale.

**Expected:** category/product/item navigation and search work; localized catalog details fall back to en-US, then the first available detail. Customer-facing UI messages are also localized; the Admin/Supplier consoles remain English.


### Customer language and notification verification

1. On Registration, confirm Preferred language is a select containing only en-US, ja-JP and zh-CN (shown as English (US), 日本語 and 中文). Register with Japanese selected, then log in: Shop should display Japanese. Confirm Account returns that preference.
2. Change the display selector to Chinese. Browse Shop, Cart, Login/Registration and Account; labels, help, validation and dynamic messages should use the effective language. This selection must not change the saved Account preference. Search/paging links retain the explicit locale. Without an explicit locale in the URL, an authenticated customer's saved preference wins over the session fallback.
3. Explicitly save Chinese as Preferred language in Account. Log out and log in again; Shop should use Chinese. Repeat for English if needed. An unsupported or blank `locale` query must fall back to English.
4. Check prices and confirmation dates use locale-aware formatting, without invented currency symbols or currency conversion. Existing catalog prices still depend on locale; EST-15 still independently falls back to English when Japanese details are absent.
5. With Mailpit open, place a Japanese order using EST-1 × 1 (1951 with the default fixture, below the unchanged 50000 Japanese approval threshold). Match the order ID in the Japanese approval, shipped and completed emails. For Chinese orders, create two pending orders as Customer and record both IDs. Log out, then sign in as Admin (`admin / admin`, unless overridden) and open `/admin/orders`: manually approve one and deny the other. zh-CN has no automatic approval policy. Verify Chinese subjects and bodies. The order-time locale governs delivery even if the Account preference later changes. Partial shipment notifications should use that same locale for every pass.
6. Log out, then sign in as Customer. Return the display and saved preference to en-US before the numeric order scenarios below. Admin/Supplier operational pages remain English and have no customer locale selector.

These are manual verification steps, not a claim that a browser/native-speaker review has already been completed.

## 3. Test C — Cart

1. Log out, then sign in as Customer. Begin with an empty Customer cart at `http://localhost:8080/cart`.
2. Open `http://localhost:8080/shop/products/FI-SW-01` in en-US and follow **EST-1** to Item Detail and add it once. Check quantity **1** and unit price **16.50** with the default seeded catalog.
3. Update its quantity to **2** using the cart's **Update quantity** action. Expect line total **33.00**. Return to the Product page and use its direct Add to Cart for EST-1: quantity should reset to **1**. Set it back to **2** for the navigation check.
4. Navigate to Shop, then back to Cart in the same session. The line and quantity should remain.
5. Remove the item. Expect an empty cart and updated total.

**Expected:** add, update, remove and totals work; ordinary navigation preserves the session cart. The later order examples assume you start each with an otherwise empty cart.

## 4. Test D — Small automatically approved order

Before ordering, open `http://localhost:8080`. Log out, then sign in as Supplier (`supplier / supplier`, unless overridden) and allow the login redirect to `/supplier/inventory`. Ensure **EST-1 has stock**; the fresh seed is 10000. If necessary, set a positive quantity, noting that existing pending fulfilments can consume it immediately.

1. Log out, then sign in as Customer. In **en-US**, add **EST-1 × 1**. With default data the total is **16.50**, below the **500** automatic-approval threshold.
2. Open `/checkout`, review/complete billing and shipping details, and submit once.
3. Record the order ID. The synchronous confirmation reports acceptance as **PENDING**; approval and shipment occur asynchronously afterward.
4. Return to Cart. It should be empty after the successful downstream **201 Created** response.
5. Log out, then sign in as Admin (`admin / admin`, unless overridden). Open `http://localhost:8080/admin/orders`. Change the default PENDING filter to **All** or **Completed** and refresh. This small order may already have disappeared from the PENDING view. Expect **COMPLETED** when stock and all services are available.
6. Log out, then sign in as Supplier and allow the redirect to `/supplier/inventory`. Use Supplier navigation to open `/supplier/orders` and locate that ID under the appropriate filter. Check completed fulfilment and shipped quantities; inventory should have decreased by the allocation.
7. In Mailpit, match the ID in all three subjects:

   - `Java Pet Store Order Status: <orderId>` — body says **APPROVED**.
   - `Java Pet Store Order Shipped: <orderId>` — identifies the shipment pass.
   - `Java Pet Store Order COMPLETED: <orderId>` — says fulfilment is complete.

**Expected:** one successful checkout clears the cart; the order eventually completes; the normal one-pass flow produces approval, shipment and completion emails. Emails are not payment receipts or proof of payment authorization.

## 5. Test E — Large order and manual approval

1. Log out, then sign in as Customer. Empty the cart and keep **en-US** selected.
2. Add **EST-1 × 31**. With the checked-in price, **31 × 16.50 = 511.50**, which is at or above the automatic-approval boundary. Check the actual checkout total before submitting; if local catalog data differs, choose quantities that reach at least 500.
3. Submit once and record the new ID.
4. Log out, then sign in as Admin. On `/admin/orders`, use the **Pending** filter and refresh. Expect this order to remain **PENDING** until an Admin acts. It should not yet have an approval or shipment email.
5. Log out, then sign in as Supplier; allow the redirect to Inventory and ensure sufficient EST-1 stock. Log out, then sign in as Admin; return to `/admin/orders` and click **Approve** for the recorded order ID.
6. Refresh Admin with **All/Completed** selected. Log out, then sign in as Supplier and open Supplier Orders to check fulfilment. Expect **COMPLETED** if stock is available.
7. Verify APPROVED status, shipped and completed emails in Mailpit for this ID.

**Expected:** manual approval succeeds once and starts Supplier processing. If stock is insufficient, investigate inventory rather than treating partial fulfilment as an approval failure.

## 6. Test F — Denial

1. Log out, then sign in as Customer. Create another **en-US EST-1 × 31** order with total **511.50**, starting with an empty cart.
2. Record its ID. Log out, then sign in as Admin and verify **PENDING** in `/admin/orders`.
3. Click **Deny**, then use the **Denied/All** filter and refresh.
4. Verify **DENIED** and a Mailpit message with subject `Java Pet Store Order Status: <orderId>` and body **DENIED**.
5. Log out, then sign in as Supplier; allow the Inventory redirect, then open Supplier Orders. Select **All** and refresh. This denied order should have no fulfilment record. Confirm no shipped/completed email arrives for its ID.

**Expected:** denial stops Supplier processing. The denied order remains part of the date-based legacy statistics in Test H.

## 7. Test G — Partial fulfilment and replenishment

Use a controlled local dataset without other outstanding EST-15 demand if you want the exact stock figure below. Record the original quantities before changing stock. A Supplier quantity update **sets an absolute quantity**; it does not add that amount.

1. Open `http://localhost:8080`. Log out, then sign in as Supplier and allow the normal redirect to `/supplier/inventory`. Set **EST-15 quantity = 0** and confirm the returned/displayed quantity. Ensure EST-1 still has stock.
2. Log out, then sign in as Customer. Empty the cart, select **en-US**, and add two lines:

   - **EST-1 × 1** at **16.50**.
   - **EST-15 × 1** at **23.50** (product `/shop/products/FL-DSH-01`).

3. Confirm total **40.00**, submit checkout once, and record the ID.
4. Log out, then sign in as Admin. In `/admin/orders`, choose **Partially shipped/All** and refresh. Expect **SHIPPED_PART** after the available EST-1 line ships.
5. Log out, then sign in as Supplier; allow the Inventory redirect, then open Supplier Orders. Choose **Pending** and inspect the order's line/shipment details. EST-1 should be shipped and EST-15 outstanding. Supplier uses its own **PENDING/COMPLETED** fulfilment status; do not expect Supplier's status label to be SHIPPED_PART.
6. In Mailpit, expect the APPROVED status email plus **one shipped notification**. There should be **no COMPLETED email yet**.
7. In Supplier Inventory, set **EST-15 quantity = 10**.
8. A positive quantity update automatically retries pending fulfilments. Stay signed in as Supplier and refresh Inventory and Orders. With only this outstanding demand, the authoritative returned quantity may already be **9**, because one unit has immediately been allocated.
9. Expect another `Java Pet Store Order Shipped: <orderId>` email for the later pass, followed by `Java Pet Store Order COMPLETED: <orderId>`.
10. Confirm Supplier **COMPLETED** and all line quantities fulfilled. Log out, then sign in as Admin and confirm **COMPLETED** in `/admin/orders`.

**Expected:** two shipment passes produce two shipped notifications, but a completion notification is sent only when all lines are fulfilled. The returned stock can be lower than the entered value because retry/allocation occurs before the response. Other pending orders can consume additional units. Record the final stock; restoring stock with another positive update can also trigger retries. The Supplier Inventory page also offers **Retry pending fulfilments** for an explicit retry.

## 8. Test H — Admin statistics

1. Log out, then sign in as Admin (`admin / admin`, unless overridden). Open `/admin/orders`, then use **Statistics** to reach `http://localhost:8080/admin/statistics`.
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

Before each identity change, log out, then sign in as Customer, Admin or Supplier as specified in the row (or use its independently authenticated browser profile). For the logged-out row, log out first. The wrong-role URL visits below are intentional negative authorization tests, not the normal way to switch roles:

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
2. Log out, then sign in as Customer. Add an item to an otherwise empty cart and record its quantity.
3. Attempt checkout once. Expect a controlled message that order creation could not be confirmed; the Storefront checkout request should return **502** (visible in browser developer tools if desired).
4. Return to Cart. The item and quantity must remain intact.
5. Restart Order Processing from the repository root. In Windows PowerShell:

   ```powershell
   $env:NOTIFICATION_ENABLED = "true"
   .\mvnw.cmd -pl order-processing-service spring-boot:run
   ```

   On macOS, use `NOTIFICATION_ENABLED=true ./mvnw -pl order-processing-service spring-boot:run` in its terminal.

6. Wait for successful startup. In a separate browser profile, log out, then sign in as Admin and check `/admin/orders` for any recorded attempt before deciding whether to retry checkout. Keep the original Customer session open so its retained cart is preserved.

**Expected:** Storefront retains the cart when it cannot confirm downstream order creation. This demonstrates a failure boundary, **not distributed rollback**. In general, a timeout/502 can occur after persistence, so do not assume every failure means no order exists. With Order Processing fully stopped before the request, it cannot accept that new attempt.

## Results and evidence

Fill this in during actual execution; nothing is pre-marked as passed. Record order IDs, dates, non-sensitive screenshots, exact errors and relevant startup logs. Do not include passwords, full card inputs or unnecessary personal/address data.

| Functional area | Expected result | Pass/Fail | Notes |
| --- | --- | --- | --- |
| Registration | New customer created | | |
| Login/logout | Sessions start/end correctly | | |
| Account | Masked saved method; blank replacement preserves last4; replacement clears after save | | |
| Catalog | Local fallbacks, category/product/item navigation and locale fallback | | |
| Item Detail | Correct SKU, localized data, listPrice, links and Add to Cart | | |
| Catalog pagination | Product/Item/search pages retain locale and search context | | |
| Customer localization | Three locales, profile preference and explicit display override | | |
| Search | Product-level ALL-token results and clean no-match state | | |
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
