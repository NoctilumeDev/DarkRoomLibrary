# Project provenance and licensing notice

DarkRoomLibrary originated as a personal project idea in May 2026. During a
sophomore short semester in July 2026, the author used a small course assignment
as an opportunity to implement and expand that idea into a complete library
system.

The course environment included an incomplete teaching starter supplied by the
instructor. It was an implementation aid during the early course phase, not the
origin of the project concept and not the main body of the current repository.
The instructor did not provide the starter's original repository or license.

The archived starter supplied for review has SHA-256
`BD62F52B4D384A7DCE199A39B879C7B6051623024CF821D613C7D9DB96377F30`.
A source and configuration comparison against the current public tree found no
byte-identical files. The starter used Vue 2, Vue CLI, Element UI, Java 8,
Spring Boot 2.2, the `cn.kmbeast` package and a two-table sample database. The
current project uses Vue 3, Vite, Element Plus, Java 17, Spring Boot 3.5,
MyBatis-Plus, the `org.darkroomlibrary` package and a nineteen-table domain
model with five role codes and six fixed permission identities.

The current implementation independently defines the reader, administration,
procurement and logistics interfaces; circulation, reservation, review,
moderation, file, notification and audit workflows; database consistency
rules; middleware fallback behavior; automated tests; browser diagnostics; and
delivery documentation. A small number of historically similar page and notice
management structures were independently rewritten before public release.

Git was introduced after the project had already grown beyond its initial demo
stage. The repository therefore starts from an honest reviewed baseline and
does not fabricate earlier commits, authors or dates.

The original source code in this repository is released under the MIT License;
see `LICENSE`. Third-party dependencies, fonts, images and other external
materials remain subject to their own licenses or terms. Generated visual
assets are included only where their applicable generation and use terms permit
redistribution.
