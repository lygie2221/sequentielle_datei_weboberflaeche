Codex instructions — COBOL Record Explorer
Goal
Build a small, locally running browser application that lets a user upload (1) a COBOL copybook describing a record layout and (2) a matching sequential data file. Decode the records using the copybook, display one record per row and one elementary field per column, and allow editing individual cells. This is an early prototype for a trade-fair demonstration of development with Codex, not a production application.
Technology and constraints
- Java 21, Maven, Spring Boot, Vaadin Flow and its Grid/Upload components.
- Embedded web server bound to localhost; document the startup command.
- Single user; in-memory data only. No database, authentication, cloud services, telemetry, separate JavaScript frontend, or unnecessary frameworks.
- Choose mutually compatible stable dependency versions. Keep the project easy to build and understand.
- Prefer simple Java classes and small, reviewable changes over elaborate architecture.
  Functional requirements
1. Uploads: Accept one copybook and one sequential data file. Show helpful validation and parsing errors. Enforce reasonable file-size limits.
2. Copybook parsing: Interpret level numbers, nested groups, PIC X(n), PIC 9(n), PIC S9(n) and implied decimal V. Calculate each elementary field's byte length, offset, type and qualified name/path. Display the interpreted layout for inspection. Reject unsupported or ambiguous constructs explicitly; do not guess.
3. Record decoding: Initially support fixed-length records with no separators and no RDW. Derive the record length from the copybook and read the uploaded file by byte offsets, not readLine() or character positions. Report incomplete trailing records. Offer a selectable character encoding, including an appropriate single-byte EBCDIC code page and single-byte ASCII-compatible encoding; UTF-8 may be offered only when its use is safe for the fixed-width layout, otherwise explain/reject it. Document the conventions assumed for signed DISPLAY numeric fields.
4. Grid: Create columns dynamically from elementary fields. Show record numbers, field values and meaningful errors. Support editing cells with appropriate validation (type, numeric format, encoded byte width). Keep edits in memory; never silently truncate or pad invalid input.
5. Examples: Supply a small matching sample copybook and sequential file, plus an easy-to-follow demonstration workflow.
   Architecture
- Keep copybook parsing and byte-level record decoding independent of Spring and Vaadin so they can be unit-tested.
- Separate the UI, application orchestration, layout metadata, and record decoding/editing.
- Represent field metadata explicitly (qualified path, data type, byte offset, byte length).
- Distinguish original raw bytes, decoded values and edited values. Preserve original bytes where practical for a possible later export feature.
- Do not introduce a database or a generic plugin system.
  Quality and safety
- Use JUnit tests for copybook parsing, nested fields, offsets, record length, decoding, malformed inputs and invalid edits.
- Include at least one integration-style test using matching sample files.
- Treat uploaded files as untrusted. Avoid unbounded memory usage and document demo file limits.
- Do not silently accept unsupported COBOL constructs or incompatible encodings.
- Maintain a README covering prerequisites, startup, sample workflow, supported syntax and known limitations.
- After each implementation step, run the relevant Maven build/tests and report what actually passed.
  Working style for Codex
- Work incrementally. Implement only the milestone currently requested. Do not build the entire application in one go unless explicitly asked.
- Make reasonable low-risk decisions autonomously; briefly document important assumptions and trade-offs.
- Favor a working vertical slice over speculative abstractions. Keep code readable for a live demonstration.
- If a feature is ambiguous or unsupported, fail clearly rather than silently interpreting COBOL data incorrectly.
- Avoid unrelated refactoring and unnecessary dependencies.
  Suggested milestones
1. Minimal runnable Spring Boot + Vaadin application, two upload controls, README.
2. Copybook parser with unit tests and a layout preview.
3. Fixed-length record decoder and dynamically populated Vaadin Grid.
4. Editable cells with validation and clear error handling.
5. Sample fixtures, integration test and trade-fair polish.
   Out of scope for the initial prototype
   COMP-3, COMP/BINARY, OCCURS, REDEFINES, variable-length records/RDW, line-delimited records, export of modified records, large-file streaming/pagination, multi-user support and production hardening. Implement these only on explicit request.