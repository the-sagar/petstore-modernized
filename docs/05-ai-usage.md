# AI assistance and human decisions

## Scope and responsibility

AI assisted source investigation, bounded implementation, scaffolding, tests, troubleshooting and documentation. The developer established service boundaries, MongoDB aggregate ownership, transport choices, payment-data constraints, failure semantics and parity requirements. Generated changes were subject to those constraints and automated verification; architecture and acceptance remained developer decisions.

## Areas of assistance

| Area | AI assistance | Developer-directed decision or evidence |
| --- | --- | --- |
| Legacy investigation | Temporary instrumentation, source tracing and database-query assistance | The original application was reproduced and exercised; runtime findings were checked against source and Cloudscape data |
| Account migration | DTOs, explicit mappings, registration/account code and rollback tests | Separate User identity and embedded Customer aggregate; transactional registration and BCrypt |
| Architecture | Maven service scaffolding, local contracts, HTTP clients and JMS handlers | Three service-owned databases, no cross-service repositories or shared domain module; synchronous checkout acceptance followed by asynchronous workflow |
| Catalog | XML extraction, embedded detail models, MongoDB aggregation/paging and presentation tests | Preserve localized source data and independent fallback; intentionally retain modern Product/ALL-token search rather than legacy Item/ANY semantics |
| Customer presentation | Message bundles, locale-aware pages, accessible catalog fallbacks, masked payment summary and Item Detail | Three customer locales and the existing UI stack; operational consoles remain English |
| Payment treatment | Display-metadata mapping, migration and raw-BSON assertions | Full PAN is transient; only type/last4 reach Orders; no authorization, tokenization or PCI-compliance claim |
| Order workflow | Atomic decisions, Supplier allocation, fulfilment event handling and tests | Preserve whole-line allocation, partial shipment, replenishment and duplicate-event guards without claiming exactly-once processing |
| Notifications/statistics | Spring Mail integration, localized content, MongoDB category aggregation and tests | Customer email stays in Order Processing; non-blocking SMTP; statistics include every workflow status for legacy parity |
| Reliability | Failure/concurrency tests and boundary review | Cart clears only after a valid downstream 201; no distributed rollback, outbox or request-level idempotency is implied |
| Documentation | Source/configuration comparisons, grammar, link checks and setup clarifications | Local workspace is authoritative; independent testers supplied Windows/macOS setup and role-transition feedback |

## Verification and limits

The recorded legacy investigation includes runtime exercises for account, cart, approval, replenishment, completion and optional email. Source inspection establishes catalog counts, profile concepts and legacy search/statistics behavior. These evidence types are distinguished from newly executed automated tests; source inspection does not establish that every browser scenario was manually repeated.

Automated tests cover transaction rollback, identity ownership, roles/CSRF, locale fallback, catalog paging/search, customer pages, payment redaction/migration, HTTP failure handling, order decisions, inventory concurrency, shipment passes, notifications and statistics. JavaScript presentation tests use a lightweight DOM stand-in rather than a full browser framework. See the [current verification result](../README.md#testing) and the [external functional-verification guide](09-functional-verification.md).

Generated duplicate artifacts have recurred in the synced workspace. When necessary, verification uses a source-identical temporary copy outside that folder; tests are not weakened and duplicate source files are not introduced to bypass the issue. A successful automated run does not certify a fresh installation, production readiness or native-speaker/browser review of every translated page.

## Deliberate trade-offs

MongoDB aggregate design follows ownership and access patterns rather than a collection per relational table. Embedded relationships reduce local orchestration, while catalog search still uses a justified cross-collection `$lookup`. This is not a claim that MongoDB eliminates every join or that ordinary indexes accelerate arbitrary substring search.

The developer accepted bounded local functionality while retaining explicit production gaps: MongoDB/JMS publication is not atomic, email is best-effort, checkout retries lack a request-level idempotency key, and backend authentication/TLS, secrets management, HA, monitoring and recovery need further work. Duplicate-safe consumers do not make either publication or email exactly-once.

Payment-data restrictions and search semantics are explicit engineering choices, not incidental generated behavior. Favorite Category/My List, related banners and remember-username behavior remain deferred; persisted flags alone do not establish those features. Readers can trace implemented behavior through its API, service, persistence boundary and tests, then compare it with the [parity record](04-functional-parity.md).
