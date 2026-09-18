# AI Usage Log

## Purpose

AI tools are being used as engineering assistants during the modernization exercise.

This document records where AI materially contributed, what was accepted or rejected, and how the resulting work was validated.

The goal is to make AI usage transparent and to demonstrate that implementation and architecture decisions remain understood and owned by the developer.

---

## Working Principles

AI output is treated as a proposal, not as an authoritative result.

For material changes:

1. inspect the suggestion
2. understand the affected code path
3. review the diff
4. build / run the relevant application
5. verify behavior
6. accept, modify, or reject the suggestion

AI is not used as a substitute for understanding the legacy flow.

---

## Legacy Diagnostic Instrumentation

### Tool

Codex inside IntelliJ IDEA.

### Task

Add temporary Java 1.4-compatible diagnostic logging to selected legacy classes.

### Constraints

- no new dependencies
- no Java syntax unsupported by the original compiler
- no intended business-logic changes
- `System.out.println` only
- instrumentation limited to useful business boundaries

### Areas instrumented

Account / authentication:

- user creation
- customer creation
- account/profile update
- sign in
- sign out
- request routing
- screen-flow forwarding

Order processing:

- checkout
- order creation
- JMS send
- purchase-order receive
- approval processing
- supplier receive
- fulfilment
- invoice handling
- final completion

### Human validation

The generated changes were reviewed and then:

- compiled using the legacy JDK
- deployed to the J2EE runtime
- exercised through the UI
- compared with the observed logs
- used to trace actual request and messaging paths

### Outcome

Instrumentation helped isolate the duplicate-customer creation failure to request forwarding that preserved the original `action=create` POST parameter.

It also confirmed the real asynchronous order-processing boundary.

---

## Legacy Defect Analysis

AI was used to assist with:

- correlating traces with source locations
- narrowing candidate code paths
- explaining framework behavior
- proposing hypotheses for observed failures

The hypotheses were not treated as findings until supported by runtime evidence or direct source/database inspection.

Examples validated manually include:

- duplicate customer creation after internal request forwarding
- stateful EJB invalidation after failure
- credit-card type/expiry mapping mismatch
- lack of country/state semantic validation

---

## Database Analysis

AI assisted with Cloudscape query construction and interpretation.

The developer directly executed the queries and verified:

- account-related table relationships
- ContactInfo to Address linkage
- Account to CreditCard linkage
- fragmented persistence across customer-related tables
- incorrect persisted values in the credit-card type/expiry fields

No production or personal secret data is intended to be committed to this repository.

Documentation uses generic examples rather than raw test-account values.

---

## Modernization Design

AI is being used to help evaluate and document options including:

- Spring Boot structure
- Spring Security integration
- MongoDB aggregate design
- validation strategy
- error-handling conventions
- test strategy
- migration sequencing

Decisions are reviewed against the observed legacy behavior before acceptance.

---

## Password Hashing Decision

BCrypt was selected for the initial modernization.

Alternatives considered included:

- Argon2id
- PBKDF2
- scrypt

Reasons for selecting BCrypt for this exercise include:

- mature and widely understood algorithm
- first-class Spring Security support
- configurable work factor
- automatic salting
- low integration complexity
- appropriate fit for the expected workload and take-home scope

Implementation will use Spring Security's `PasswordEncoder` abstraction so application code is not tightly coupled to custom hashing logic.

---

## AI Suggestions That Are Not Automatically Accepted

Examples of recommendations that require explicit review:

- introducing microservices before domain boundaries are understood
- adding a gateway before multiple target services exist
- adding Kafka / RabbitMQ to synchronous account flows
- reproducing every legacy table as a MongoDB collection
- converting every legacy implementation detail one-for-one
- adding dependencies purely because they are modern

The target architecture will be introduced incrementally from observed requirements.

---

## Planned AI Usage During Implementation

AI may assist with:

- initial class scaffolding
- test scaffolding
- repetitive mappings
- documentation refinement
- code review prompts
- troubleshooting

For each significant feature, the developer remains responsible for:

- understanding the generated code
- validating framework behavior
- deciding whether the suggestion fits the target architecture
- running tests
- verifying the user-visible behavior

---

## Interview Position

The modernization approach is:

```text
Use AI to accelerate investigation and implementation
        +
Understand and validate the resulting system
        +
Be able to navigate, explain, debug, and modify the code without relying on the AI output
```

This document will be updated as additional AI-assisted work is introduced.
