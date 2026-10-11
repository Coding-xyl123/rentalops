# RentalOps — Resume and Interview Materials

## Resume version (3 bullets)

**RentalOps — Rental Operations Decision Support Platform** | Java, Spring Boot, PostgreSQL, Docker, Render

- Built and deployed a Spring Boot decision-support application that transforms rental maintenance events into explainable, priority-based recommendations and captures operator decisions through a web dashboard.
- Implemented idempotent event ingestion, conflict detection, PostgreSQL advisory-lock concurrency control, and transactional event/recommendation persistence to prevent duplicate and partial processing.
- Developed REST APIs for event retrieval, decision history, and recommendation replay; validated core backend behavior with **14 passing automated tests** and deployed the Dockerized service with managed PostgreSQL on Render.

## Short portfolio description

RentalOps is a Java/Spring Boot operations decision-support MVP for short-term rentals. It converts maintenance signals into explainable actions, records operator feedback, and uses PostgreSQL transactions, idempotency, and concurrency controls for reliable processing. Deployed using Docker and Render.

## Interview talking points

**Why this project?** Operators need to act on maintenance issues before they affect guests; the product bridges raw events and actionable decisions.

**Why transactions?** An issue event without its intended recommendation would be an inconsistent partial result; both records should commit or roll back together.

**Why advisory locks?** Concurrent retries for the same event ID can race. Database-level coordination makes duplicate handling more predictable.

**What is V1 not?** It is not a Kafka-based distributed system or an ML model yet. V1 focuses on correctness, auditability, and deployment.

**What would you build next?** Asynchronous Kafka ingestion with a reliable publish boundary, idempotent consumers, retries and dead-letter handling, then ranking with measured operator feedback.

## Add before publishing

- Replace any placeholder live-demo link with your verified Render URL.
- Add repository URL: https://github.com/Coding-xyl123/rentalops
- Verify replay end-to-end on the deployed service.
- Add sanitized screenshots to the repository.
- Never publish database credentials or real guest data.
