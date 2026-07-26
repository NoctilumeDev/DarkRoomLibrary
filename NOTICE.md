# Project provenance and licensing status

DarkRoomLibrary began as a sophomore short-semester assignment. The school
instructor supplied an incomplete teaching starter, but did not provide and
could not identify its original repository, author, or license.

The student first completed the course submission and then continued the project
independently during summer. Git was introduced midway through that later work
because the project had initially been treated as a disposable demo. The
repository therefore uses one honest reviewed-baseline commit and does not
fabricate earlier commits, authors, or dates.

Earlier source tracing found visual and structural similarities to material
associated with the Chinese video identifier `BV16d4JenESJ` and a previously
referenced Gitee path named `langlangshan01/library-management-system`. That
relationship has not been proven, and no usable upstream license has been
verified.

The current project contains substantial independent work, including:

- migration from Vue 2-era structure to Vue 3.5 and Vite 8;
- migration from Spring Boot 2.7 to Spring Boot 3.5 and Jakarta APIs;
- redesigned reader, administration, procurement, and logistics interfaces;
- completed circulation, reservation, renewal, review, moderation,
  procurement, logistics, file-governance, notification, and audit workflows;
- database constraints and concurrency controls for inventory and state
  consistency;
- Redis and RabbitMQ fallback/recovery behavior;
- automated tests, real full-chain verification, browser diagnostics, and
  rewritten documentation.

Those changes do not by themselves prove that every remaining fragment is free
of upstream copyright. No license for the teaching starter has been verified.
Accordingly:

- this repository does not currently include a project-wide open-source license;
- publishing the repository makes its source visible but does not grant reuse,
  modification, or redistribution rights;
- the repository must not claim that all historical code is completely
  original;
- a project-wide license should be added only after the starter's permission is
  confirmed, or after potentially inherited material is independently replaced
  and reviewed.

Third-party dependencies and generated visual assets remain subject to their
own applicable terms and provenance records.
