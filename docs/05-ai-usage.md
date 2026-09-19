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
| Async boundary | Documented the later migration stage | Developer chose to preserve meaningful JMS-style order workflow with future Artemis + Spring JMS; messaging was explicitly excluded from current implementation |
| Payment correction | Implemented display-only order payment DTOs and rejection tests | Developer required that full card data never cross to Order Processing; only card type and last4 are permitted |
| Checkout reliability | Implemented response validation and failure tests | Developer required cart clearing only after a successful, valid order-creation acknowledgement |
| Customer UI | Built Thymeleaf/vanilla JS pages and page tests | Developer constrained the UI to existing APIs, CSRF protection, and no framework/business-logic duplication |

A later bounded hardening step removes raw Storefront card persistence: only display metadata remains, with an idempotent legacy-data migration and raw-BSON assertions. This does not implement tokenization, payment authorization or establish PCI compliance.

## Verification rather than blind acceptance

- Legacy account, cart, approval, supplier replenishment, and completion behavior was manually exercised in the reproduced legacy runtime. Instrumentation findings were checked against source and database evidence.
- Targeted inspection established catalog counts and EST-15's missing Japanese details. Seed validation and tests preserve that irregularity.
- Automated tests cover Mongo rollback, ownership, session isolation, CSRF, current-price snapshots, redacted outbound JSON, order persistence, and HTTP failure handling.
- Storefront checkout tests use a mock HTTP server facility, without a Java dependency on Order Processing. API contracts remain separately owned.
- Page tests exercise real Thymeleaf CSRF tokens and anonymous-cart retention through login. A headless Chrome smoke check exercised the UI with mocked APIs. This is not evidence that a live three-process browser checkout was manually verified.
- Test feedback exposed assumptions about absolute versus relative login redirects and Thymeleaf's escaped JavaScript URLs. Assertions were corrected after inspecting actual responses; working security behavior was not changed to satisfy them.
- Focused tests were followed by root Maven verification. The documented checkpoint has 106 passing tests; compile/test results and diffs are reviewed rather than accepting generated output on appearance alone.

## Engineering boundaries retained

BCrypt is implemented through Spring Security's `PasswordEncoder`, not custom hashing. Legacy tables were not copied one-for-one into Mongo collections. There is no shared domain/entity module, cross-service repository access, invented catalog data, or messaging in synchronous account flows.

No Artemis broker, JMS listener/publisher, approval processing, inventory flow, or payment tokenization has been implemented. Those exclusions were deliberate human scope decisions, not completed features hidden behind a scaffold.

## Interview use

The candidate should be able to trace a request through controller, service, repository/client, and tests; explain which behavior came from verified legacy evidence and which is a modernization decision; and identify unfinished work. AI accelerated execution, while the developer retained responsibility for architecture, review, and acceptance.
