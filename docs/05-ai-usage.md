# AI assistance and human decisions

## Role and accountability

AI (including Codex in IntelliJ) assisted investigation, bounded implementation prompts, scaffolding, DTO/mapping code, tests, troubleshooting, and documentation. The developer set scope, service ownership, parity requirements, and security constraints. AI-generated code was treated as a proposal to inspect and verify, not an architectural authority.

This repository is AI-assisted implementation, not a claim that every line was written manually.

## Concrete decisions and checks

| Area | AI contribution | Human decision / verification |
|---|---|---|
| Legacy diagnostics | Generated Java 1.4-compatible, dependency-free temporary instrumentation; helped correlate traces | Developer compiled/deployed the original application, exercised it, and checked logs/source before accepting findings |
| Legacy persistence | Assisted Cloudscape query construction and interpretation | Developer executed queries and verified entity relationships and the card type/expiry mapping defect |
| Migration sequence | Helped formulate bounded work and scaffold each slice | Developer explicitly limited each checkpoint and required tests before expanding scope |
| Service architecture | Created Maven modules and bootstraps | Developer established three independently deployable boundaries: Storefront, Order Processing, Supplier—not a service per entity |
| Order ownership | Implemented local DTOs, RestClient, and order API | Developer rejected direct Storefront ownership/repository access for orders; the boundary is HTTP with separate databases |
| Catalog source | Extracted and parsed catalog XML; built seed validation | Developer required real legacy data and targeted source verification, not invented demo records; user/customer/payment sections were excluded |
| Locale-specific prices | Refactored model and tests | Developer rechecked `Populate-UTF8.xml` and `CatalogDAOSQL.xml`, correcting the earlier model so ListPrice/UnitCost belong to ItemDetails, not Item |
| Cart parity | Implemented session state and tests | Developer supplied verified semantics: add resets to 1, positive updates set exact quantities, nonpositive removes |
| Async boundary | Implemented publishers, listeners and bounded tests | Developer chose to preserve meaningful JMS-style order workflow with Artemis + Spring JMS, introduced after synchronous checkout was verified |
| Payment correction | Implemented display-only order payment DTOs and rejection tests | Developer required that full card data never cross to Order Processing; only card type and last4 are permitted |
| Checkout reliability | Implemented response validation and failure tests | Developer required cart clearing only after a successful, valid order-creation acknowledgement |
| Customer UI | Built Thymeleaf/vanilla JS pages and page tests | Developer constrained the UI to existing APIs, CSRF protection, and no framework/business-logic duplication |

A subsequent bounded hardening step removed raw Storefront card persistence: only display metadata remains, with an idempotent legacy-data migration and raw-BSON assertions. This does not implement tokenization, payment authorization or establish PCI compliance.

## Verification rather than blind acceptance

- Legacy account, cart, approval, supplier replenishment, and completion behavior was manually exercised in the reproduced legacy runtime. Instrumentation findings were checked against source and database evidence.
- Targeted inspection established catalog counts and EST-15's missing Japanese details. Seed validation and tests preserve that irregularity.
- Automated tests cover Mongo rollback, ownership, session isolation, CSRF, current-price snapshots, redacted outbound JSON, order persistence, and HTTP failure handling.
- Storefront checkout tests use a mock HTTP server facility, without a Java dependency on Order Processing. API contracts remain separately owned.
- Page tests exercise real Thymeleaf CSRF tokens and anonymous-cart retention through login. Browser checks and real Artemis flows verified approval/denial and partial fulfilment followed by replenishment, in addition to earlier mocked-API smoke checks.
- Test feedback exposed assumptions about redirects and Thymeleaf-escaped JavaScript URLs. Assertions were checked against actual responses rather than weakening security to make tests pass.
- A duplicate generated-file/build-output issue was diagnosed as an artifact problem and cleaned instead of committing duplicate files or changing business code to mask it.
- Focused suites and root Maven verification cover concurrency, role isolation, payment migration and failure cases. Record the actual test summary for the checked-out commit; this documentation update does not claim a new test execution.

## Explicit engineering tradeoffs

Human scope decisions kept three service-owned databases and no cross-service Java domain dependencies. BCrypt uses Spring Security, and Mongo collections follow aggregates rather than reproducing every relational table.

Mongo commits and JMS sends are separate. The developer explicitly accepted a bounded demo without a transactional outbox while requiring the publication gaps to be reported. Persisted APPROVED orders or Supplier shipments can require replay/reconciliation after a send failure. Duplicate-safe consumers do not make publication exactly-once. Checkout similarly preserves the cart after failure without claiming that an already accepted remote order was rolled back.

Payment hardening was a human security correction: first restrict the inter-service contract to card type/last4, then remove raw Storefront persistence with an idempotent migration and raw-BSON tests. No gateway, tokenization or compliance claim was invented.

## Architecture rationale and review

A developer reviewing the system should be able to trace a request through controller, service, repository/client, and tests; explain which behavior came from verified legacy evidence and which is a modernization decision; and identify unfinished work. AI accelerated execution, while the developer retained responsibility for architecture, review, and acceptance.
