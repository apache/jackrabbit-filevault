<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->

# Apache Jackrabbit FileVault Security Threat Model (draft)

**Why a separate FileVault model (not folded into Jackrabbit or Oak).**
The Jackrabbit PMC owns three functionally distinct codebases.
FileVault is the most architecturally different of the three: it is **a
serialisation / packaging tool**, not a JCR repository. The "thing"
FileVault produces and consumes is a zip file (a "content package")
that describes a slice of JCR content; the "thing" FileVault does is
serialise / deserialise that zip *across a trust boundary*. The most
load-bearing distinction is that FileVault content packages can carry
**install hooks** — Java classes packaged in `META-INF/vault/hooks/`
that FileVault dynamically loads and invokes during package install
*(documented: `InstallHookProcessorImpl.java`)*. That mechanism puts
"who supplied this package?" at the centre of the threat model in a way
that does not exist for Jackrabbit or Oak. Folding it into Oak would
require the Oak model to claim "untrusted-zip-to-arbitrary-code-execution"
as an Oak concern, which it is not — Oak honours whatever JCR
privileges filevault uses. Independent models that cross-reference
each other are the right shape.

## §1 Header

- **Project:** Apache Jackrabbit FileVault (`apache/jackrabbit-filevault`)
  — "Apache Jackrabbit FileVault introduces a JCR repository to
  filesystem mapping" *(documented: `README.md`)*. Distinct from
  Apache Jackrabbit (the JCR repository) and Apache Jackrabbit Oak
  (the JCR repository succeeding Jackrabbit).
- **Commit / version binding:** drafted against the default branch
  (`master`). A report against FileVault version *N* should be
  triaged against the model as it stood at *N* (release tag).
- **Date:** 2026-05-30.
- **Authors:** ASF Security team draft, awaiting Jackrabbit PMC
  review.
- **Status:** draft — under maintainer review.
- **Reporting cross-reference:** findings that may violate a §8
  property should be reported per the ASF Security Team disclosure
  channel (`security@apache.org`) and the Jackrabbit project's
  security mailing list, before public disclosure. Findings under
  §3, §9, §11a will be closed by FileVault triagers citing this
  document.
- **Provenance legend:** *(documented)* — drawn from in-repo docs,
  website docs, or in-repo source-code comments / Javadoc with
  citation; *(maintainer)* — confirmed by a FileVault maintainer
  in response to this draft; *(inferred)* — synthesised from code
  structure or domain knowledge, awaiting PMC ratification (every
  *(inferred)* tag has a matching §14 question).
- **Draft confidence:** 18 documented / 0 maintainer / 30 inferred.

**About the project.** Apache Jackrabbit FileVault is a tool and
library for moving JCR repository content as a portable zip file
("content package"). A content package is a zip whose `jcr_root/`
subtree contains a serialised JCR fragment (nodes, properties, file
binaries) and whose `META-INF/vault/` subtree contains the manifest
(`filter.xml` — what paths are covered, `config.xml`, package
properties, optional `META-INF/vault/hooks/` install hooks). FileVault
ships several modules *(documented: top-level layout):*

- **vault-core** — the core library that reads/writes packages and
  invokes the install pipeline;
- **vault-cli / vault-vlt (vlt)** — a Subversion-like CLI for
  comparing repository content against a working copy and committing
  changes back *(documented: `README.md` — "vlt: Subversion like
  utility to work and develop with repository content")*;
- **vault-davex** — DAVex / WebDAV adapter for the CLI;
- **vault-rcp** — remote-copy protocol;
- **vault-sync** — filesystem-to-repository synchronisation;
- **vault-validation** — a validator framework (vault-validation
  module *(documented: `ValidationContext.java`)*) used by Maven
  plugins and the vlt CLI to check a package's structure before
  install;
- **vault-diff** — package-content diff;
- **vault-hook-example** — example install hook (sample / unsupported
  in production sense).

The build requires Maven 3.9+ and Java 17 *(documented: `README.md`)*.

## §2 Scope and intended use

### Intended use

- **Move repository content across a trust boundary as a portable
  zip.** Typical use cases: dev → CI → staging → production
  promotion of CMS content (this is the dominant use case in the
  AEM-adjacent ecosystem), packaging custom-application content
  for delivery, exporting a repository slice for backup or
  comparison.
- **The `vlt` CLI for repository diff / sync / commit workflows.**
- **The vault-validation module for build-time validation of
  packages** (used by the FileVault Maven plugin).

### Deployment shape

FileVault has **two qualitatively different runtime postures** that
must be modelled separately:

1. **Package installer / package importer running inside a JCR
   container** (Oak / Jackrabbit-2). The installer is invoked from
   inside the host process — typically by Sling, by the FileVault
   Package Manager OSGi service, by AEM's package manager, or by
   the `vault-rcp` HTTP endpoint. The installer reads a package
   from disk or HTTP, opens the zip, parses the manifest, applies
   the JCR mutations, and (if hooks are present and allowed) loads
   and executes Java install hooks **inside the host JVM** with
   whatever JCR session was used to invoke install *(documented:
   `InstallHookProcessorImpl.java`)*.
2. **Standalone `vlt` CLI** invoked by a developer / CI job. `vlt`
   reads / writes packages from the operator's filesystem and
   talks to a remote JCR repository over DAVex / WebDAV. The CLI
   does not load install hooks in the same process — hooks run on
   the *server* side when the package is installed there
   *(inferred — §14 Q1)*.

The single most consequential modelling fact is: **a FileVault
content package is a Java code distribution channel**, because of
install hooks. A package received from any source that the host
does not fully trust is, in effect, an unsigned JAR submitted for
in-JVM execution at install time.

### Caller roles

| Role | Trust level | Notes |
| --- | --- | --- |
| **Package author** | varies wildly | The party who built the zip. Often a developer the operator trusts; sometimes a third-party vendor; rarely (but increasingly with self-service portals) an end-user. |
| **Package installer (host JCR app)** | trusted | The application code that invokes FileVault's import — e.g. Sling, AEM Package Manager, OSGi `Packaging` service. Holds the JCR session that import runs under. |
| **JCR session used for import** | varies | Privileges of this session bound what JCR mutations FileVault can apply. Install hooks run under *this same* session unless they obtain a different one. |
| **Operator** | trusted | Configures whether install hooks are allowed (`ImportOptions.hookClassLoader`), what classloader they load against, what the dependency-resolution strategy is. |
| **Filesystem (for `vlt`)** | trusted | `vlt` reads packages from the developer's filesystem; FS contents trusted. |
| **Remote DAVex / WebDAV endpoint** | trusted control plane | The repository `vlt` talks to. Authentication / TLS to it is the operator's job. |
| **HTTP peer to `vault-rcp` endpoint** | untrusted but authenticated | When the remote-copy-protocol HTTP endpoint is exposed, peers authenticate against the host's HTTP auth and submit packages. |

### Component-family table

| Family | Representative entry | Touches outside the process? | In-model? |
| --- | --- | --- | --- |
| **vault-core — package zip parsing** (`ZipVaultPackage`, `JcrPackageImpl`) | reads `*.zip` bytes | filesystem or HTTP body | **yes** (high security weight — zip + XML parse) |
| **vault-core — install pipeline** (`PackageManagerImpl`, `JcrPackageManagerImpl`, `InstallContextImpl`) | applies JCR mutations | none directly | **yes** |
| **vault-core — install hooks** (`InstallHookProcessorImpl`) | loads hook JARs / classes from package, invokes in-JVM | **yes — in-JVM code execution** | **yes** (highest blast radius; gated by `ImportOptions.hookClassLoader` posture) |
| **vault-core — `ImportOptions`** | configures import behaviour | none | **yes** (deprecated `patch*` methods retained for back-compat; "Several patch-related methods are deprecated as of version 4.2.0 for security reasons" *(documented: `ImportOptions.java` Javadoc)*) |
| **vault-core — workspace filter** (`filter.xml` parser) | XML parse | none | **yes** (XXE-class concerns) |
| **vault-core — DocView / Enhanced DocView XML serialisation** | XML parse / emit | none | **yes** (XXE-class concerns) |
| **vault-validation** — validator SPI | validates package structure | none | **yes** (build-time / pre-install check) |
| **vault-cli / vault-vlt** — CLI | filesystem + DAVex | **yes — HTTP / FS** | **yes** for CLI logic; the auth and TLS to the remote JCR are operator-config |
| **vault-davex** — DAVex adapter | network | **yes — HTTP** | **yes** for adapter logic |
| **vault-rcp** — remote-copy protocol | HTTP server / client of package import | **yes — HTTP** | **yes** (when exposed; brings B-network into scope) |
| **vault-sync** — filesystem ↔ repository sync | filesystem + JCR | **yes — FS** | **yes** for sync engine; FS bytes trusted as for `vlt` |
| **vault-diff** — package diff | none | none | **yes** |
| **vault-hook-example** | sample install hook | none | **out of model** — example *(§3)* |
| **vault-core-it** — integration tests | none | none | **out of model** — test harness *(§3)* |
| **target-osgi-environment** | OSGi test rig | none | **out of model** *(§3)* |

A finding is in-model only if it lands in a row marked **yes**.

## §3 Out of scope (explicit non-goals)

Reports requiring any of these will be closed with the cited
disposition:

1. **Host JCR repository correctness.** The trust posture of the JCR
   session used for import, the ACL evaluation that runs on
   `Session.save()`, the user/group/principal model — these are
   Jackrabbit Oak's / Jackrabbit's threat models. A complaint that
   "FileVault installed a node my permission model should have
   blocked" is a JCR-side issue if the import session had the
   privilege. *(inferred — §14 Q2)*. → `OUT-OF-MODEL:
   adversary-not-in-scope`.
2. **Install hooks executing arbitrary Java code.** This is the
   advertised behaviour of install hooks *(documented:
   `InstallHookProcessorImpl.java` — hooks loaded from
   `META-INF/vault/hooks/` and invoked at PREPARE / INSTALLED phases)*.
   "A package's install hook ran code on the server" is what install
   hooks are. → `BY-DESIGN: property-disclaimed` (§9).
3. **Packages from untrusted sources installed without
   restriction.** The operator chooses whether to allow install
   hooks at all (via `ImportOptions.hookClassLoader`), what
   classloader they load against, and what privileges the import
   session holds. A package author becomes a code author at install
   time *(inferred — §14 Q3)*. → `OUT-OF-MODEL: trusted-input`
   (the package author is the trusted-input source).
4. **Operator-supplied configuration.** `ImportOptions` defaults,
   the auto-save threshold, the import mode, ACL handling, CUG
   handling — all operator decisions. A misconfiguration that lets
   a package's hooks load arbitrary classes is the operator's
   choice. *(inferred — §14 Q3)*. → `OUT-OF-MODEL: non-default-build`.
5. **Failing-to-validate-before-install posture.** vault-validation
   is the SPI for *structural* validation — it does **not** verify
   the package author's intent or scan hook code for malice. A
   build-time validator that passes a malicious package is not a
   FileVault failure. *(inferred — §14 Q4)*. → `OUT-OF-MODEL:
   adversary-not-in-scope`.
6. **Servlet container / `vault-rcp` HTTP authentication.** When
   `vault-rcp` is exposed over HTTP, the operator configures
   container-level auth (Basic / Digest / OAuth / mTLS). Container
   misconfiguration is not FileVault's bug. *(inferred —
   §14 Q5)*. → `OUT-OF-MODEL: adversary-not-in-scope`.
7. **Issues that exist in `jackrabbit` or `jackrabbit-oak`.**
   Cross-references not duplications. → `OUT-OF-MODEL:
   unsupported-component`.
8. **Code that ships but is not part of the supported product:**
   `vault-core-it/` (integration tests), `vault-hook-example/`
   (example hook), `target-osgi-environment/` (test rig). →
   `OUT-OF-MODEL: unsupported-component`.
9. **Build / release / SDLC hygiene.** Out of model per the SKILL.
10. **Side channels.** Out of model. → `OUT-OF-MODEL:
    adversary-not-in-scope`.

## §4 Trust boundaries and data flow

The single most load-bearing boundary in FileVault's threat model is
**the zip-bytes-to-install boundary**: bytes that enter as a content
package (from disk, from HTTP, from a JCR upload, …) are
serialised data until the moment the install pipeline opens them, at
which point they become both **JCR mutations** *and* **Java code
loaded into the host JVM**.

Trust transitions:

| # | Transition | Trust check | Effect |
| --- | --- | --- | --- |
| B1 | Package bytes → zip-archive read | none beyond zip integrity | normal zip-bomb / zip-slip concerns apply *(inferred — §14 Q6)* |
| B2 | Zip entries → `META-INF/vault/*` XML parse (`filter.xml`, `config.xml`, package properties) | XML parser configuration | XXE / Billion-Laughs class *(inferred — §14 Q7)* |
| B3 | Zip entries → `jcr_root/` content + `.content.xml` DocView parse | XML parser configuration | XXE in DocView *(inferred — §14 Q7)* |
| B4 | Content → JCR session apply | the import session's JCR privileges (host JCR's `AccessManager` / Oak's `PermissionProvider`) | **JCR-side**; out-of-model for FileVault per §3 item 1 |
| B5 | Install hook entries → ClassLoader load + `InstallHook` interface check + reflective invoke | `InstallHook` interface check; classloader resolution per `InstallHookProcessorImpl` *(documented)* | **arbitrary Java in the host JVM** — *the* high-blast-radius case |
| B6 | `vault-rcp` HTTP request → package bytes | container-side HTTP auth | brings network adversary into scope (B-network) |
| B7 | `vlt` → remote DAVex | operator-configured auth + TLS | operator-side |

### Reachability preconditions per family

- **vault-core zip-archive read**: in-model for zip-slip (path
  traversal in `ZipEntry` name on extraction) *(inferred —
  §14 Q6)* and zip-bomb (decompression amplification) *(inferred
  — §14 Q8)*. Reachable from any caller that feeds bytes into the
  package reader.
- **vault-core XML parses**: in-model for XXE / DTD / external
  entity / billion-laughs. Reachable from any caller that submits
  a package; the parser configuration matters. *(inferred —
  §14 Q7)*.
- **vault-core install hook loader**: in-model only when the import
  session and the `ImportOptions.hookClassLoader` together permit
  hook loading. The model treats *"hooks loaded from a package
  loaded into a production-importer pipeline"* as **§3 item 2 / 3**:
  hooks running is what they do; the operator chose to allow them.
- **vault-validation**: in-model for the SPI contract; out-of-model
  for the validators' *coverage* — a validator that doesn't scan
  for X is not a FileVault bug.
- **vault-rcp HTTP**: in-model when exposed; auth at the container.
- **vlt CLI**: in-model for the diff/sync logic; FS bytes are
  trusted operator-side.

## §5 Assumptions about the environment

- **JVM / runtime.** Java 17+ *(documented: `README.md`)*. JVM-
  conformant; no SecurityManager required (modern JVMs deprecate it).
- **Maven 3.9+** for build *(documented: `README.md`)*.
- **Host JCR repository.** Jackrabbit Oak or jackrabbit-core (or
  any JCR 2.0 impl). FileVault delegates all JCR-side authorisation
  to the host *(inferred — §14 Q2)*.
- **Filesystem.** `vlt` reads/writes the operator's filesystem.
- **Network.** No listening sockets from vault-core itself.
  `vault-rcp` is a servlet-container-hosted endpoint when deployed.
  `vlt` opens outbound HTTP to DAVex endpoints.
- **What FileVault does NOT do to its host** *(predominantly
  negative claims — §14 Q9)*:
  - vault-core opens **no** listening sockets (vault-rcp is hosted
    by a servlet container; that container owns the port);
  - installs **no** process-wide signal handlers;
  - does **not** spawn child processes from the import pipeline
    *(inferred — §14 Q9; but install hooks **may** spawn child
    processes — they are arbitrary Java)*;
  - reads system properties (`vault.*`) but not arbitrary `LD_*`;
  - writes log entries through SLF4J.

## §5a Build-time and configuration variants

| Knob / configuration | Default | Maintainer stance | Effect |
| --- | --- | --- | --- |
| `ImportOptions.hookClassLoader` | inherited (parent / processor / thread-context) *(documented: `InstallHookProcessorImpl.java` — "two-tier fallback approach: processor's own class loader (OSGi bundle context), falls back to thread context class loader")* | **maintainer ruling required**: is the default classloader-resolution behaviour a `VALID` posture for production, or do operators routinely override to restrict? *(inferred — §14 Q10)* | governs what classes the install hook can load |
| `ImportOptions.strict` | `false` *(inferred — §14 Q11)* | when `true`, stricter validation | structural strictness, not security |
| `ImportOptions.importMode` (`Replace`, `Update`, `Merge`) | `Replace` *(inferred — §14 Q11)* | per-use-case | how content collisions are resolved |
| `ImportOptions.acHandling` (ACL handling on import) | `IGNORE` *(inferred — §14 Q11)* | maintainer ruling: what is the default and what's the production-recommended value? | whether a package can rewrite ACLs on import (huge blast radius if it can) |
| `ImportOptions.cugHandling` | analogous | analogous | analogous for CUG policies |
| `ImportOptions` deprecated `patch*` methods | retained for back-compat | "deprecated as of version 4.2.0 for security reasons" *(documented: `ImportOptions.java` Javadoc)* | **`BY-DESIGN`** disclaim — operators using deprecated APIs are out of supported posture |
| `ImportOptions.autoSaveThreshold` | default value | performance knob; not security | |
| `ImportOptions.dryRun` | `false` | when `true`, the import is simulated, not applied | safe-by-default-for-test |
| `ImportOptions.nonRecursive` | `false` | when `true`, sub-packages are not installed | mitigates sub-package "trojan" risk *(inferred — §14 Q12)* |
| Install hooks allowed? | YES by default, when `META-INF/vault/hooks/` present and `ImportOptions.hookClassLoader` is not denied | **maintainer ruling required**: is "any package with a hook gets to run that hook" the supported production posture, or do operators routinely disable hook execution? *(inferred — §14 Q13)* | **the highest-blast-radius single knob** |
| `vault-rcp` deployment | optional / not exposed by default | when exposed, brings B6 network adversary | servlet-container responsibility |
| `vault-validation` ValidatorSettings | per-validator | not security per se | structural |

**The insecure-default case.** Two defaults are load-bearing for triage:

- **Install hooks allowed by default.** The model assumes the
  operator's production posture is "deny hook execution from packages
  whose author is not trusted" — i.e. operators in security-sensitive
  deployments restrict the hook classloader. PMC must ratify this in
  §14 Q13. If the answer is "no, hooks are allowed by default and
  that is the supported production posture", then §9 needs to gain a
  much louder warning and the project's documentation is the key
  defence.
- **`acHandling` default.** If the default permits a package to
  rewrite ACLs, the package author is *also* the authorisation author.
  §14 Q11 asks for the ruling.

## §6 Assumptions about inputs

### Per-entry-point trust table

| Entry point | Parameter | Attacker-controllable? | Caller must enforce |
| --- | --- | --- | --- |
| `PackageManager.open(InputStream)` / `JcrPackageManager.upload(InputStream)` | zip bytes | **yes** | nothing — these *are* the untrusted input |
| `JcrPackage.install(ImportOptions)` | the loaded package | **yes** | caller picks `ImportOptions`; the JCR session's privileges bound the JCR mutations |
| `vault-rcp` HTTP upload endpoint | request body | **yes** when exposed | container-level auth |
| `vlt` working-copy filesystem | filesystem bytes | **no** — trusted operator | none |
| `vlt` remote DAVex endpoint URL + credentials | URL + creds | **no** — trusted operator | none |
| `vault-validation` package input | package | **yes** | nothing — validators run *on* packages, not vice versa |
| `META-INF/vault/filter.xml` | XML | **yes** | parser hardening (XXE) *(inferred — §14 Q7)* |
| `META-INF/vault/config.xml` | XML | **yes** | parser hardening |
| `.content.xml` DocView XML | XML | **yes** | parser hardening |
| `META-INF/vault/hooks/*.jar` | JAR bytes | **yes** | `ImportOptions.hookClassLoader` controls the load; `InstallHook` interface check is the only type gate *(documented: `InstallHookProcessorImpl.java`)* |
| `META-INF/vault/properties.xml` `installhook.{name}.class` | class name string | **yes** | classloader resolution and `InstallHook` interface check |
| Zip entry names within the package (jcr_root/...) | bytes | **yes** | zip-slip considerations *(inferred — §14 Q6)* |
| Zip-entry sizes (uncompressed) | bytes | **yes** | zip-bomb considerations *(inferred — §14 Q8)* |

### Size / shape / rate

- Package size limit: none enforced by vault-core *(inferred —
  §14 Q14)*.
- Zip-entry expansion ratio: none enforced *(inferred — §14 Q8)*.
- Number of zip entries: none enforced *(inferred — §14 Q8)*.
- Number of install hooks per package: none enforced *(inferred —
  §14 Q14)*.
- XML entity expansion: depends on JVM JAXP defaults; FileVault-
  side hardening **must be confirmed** in §14 Q7.

## §7 Adversary model

### Actors

| Actor | In scope? | Capabilities granted |
| --- | --- | --- |
| Package author (untrusted) | **yes** | the entire package bytes; can supply install hook code; can craft pathological XML / zip |
| Authenticated HTTP peer to `vault-rcp` | **yes** when deployed | submit packages; otherwise as authenticated session in host |
| JCR session with `INSTALL_PACKAGE` privilege | partial | only escapes *from* the privileges they hold count |
| Operator (sets `ImportOptions`, configures hook classloader) | **out of scope** — §3 item 4 |
| Host JCR application code | **out of scope** — §3 item 1 |
| Local operator running `vlt` | **out of scope** — §3 item 4 |
| Same-JVM attacker code | **partial** — host container is the boundary *(inferred — §14 Q15)* |
| Side-channel observer | **out of scope** *(§14 Q16)* |
| Quantum adversary | **out of scope** |

The single highest-priority actor is **the package author**. The
threat model is asymmetric: most JCR-side concerns are out (§3 item 1),
*but* package authors get to choose the JAR bytes that get loaded
into the importer JVM. If the operator's posture is "trust any package
that reaches the importer", the package author has unbounded blast
radius. If the operator's posture is "restrict hook classloader, sign
packages out-of-band, audit by hand" — FileVault's role is to honour
that posture's defaults.

## §8 Security properties the project provides

### P1 — Zip extraction does not traverse outside the package's intended write target ("zip-slip")

- **Condition.** vault-core's zip extraction code refuses
  `ZipEntry` names containing `..` segments or absolute paths that
  would resolve outside the configured destination.
- **Violation symptom.** A zip entry named `../../etc/passwd`
  (or analogous) results in a file write outside the configured
  target directory or workspace root.
- **Severity.** Security-critical (`VALID`).
- *(inferred — §14 Q6)*

### P2 — XML parses in vault-core are hardened against XXE / DTD / external entities / billion-laughs

- **Condition.** vault-core's `DocumentBuilderFactory` /
  `XMLInputFactory` is configured to disable external entity
  resolution, disallow DOCTYPEs (or limit them), and cap entity
  expansion.
- **Violation symptom.** A `filter.xml` / `config.xml` / `.content.xml`
  triggers XXE (file read, SSRF), Billion-Laughs CPU/memory
  exhaustion, or DTD-based remote fetch.
- **Severity.** Security-critical (`VALID`).
- *(inferred — §14 Q7; this is the single highest-confidence-
  required property for the import pipeline)*

### P3 — Install hooks are gated by `ImportOptions.hookClassLoader` and only invocable when `META-INF/vault/hooks/` is present and the gate allows it

- **Condition.** Default `ImportOptions` and the documented
  classloader-fallback behaviour
  *(documented: `InstallHookProcessorImpl.java`)*.
- **Violation symptom.** A class is loaded as an `InstallHook`
  outside the configured classloader, *or* a class that does not
  implement `InstallHook` is invoked as one.
- **Severity.** Security-critical (`VALID`).
- *(documented + inferred — §14 Q13)*

### P4 — Install hooks run as the JCR session used for import (no privilege escalation across that session)

- **Condition.** The `InstallContext` passed to the hook carries
  the import session; hooks that do not obtain a different session
  see the session's privileges.
- **Violation symptom.** A hook executes JCR mutations not permitted
  to the import session.
- **Severity.** Security-critical — but a hook is **already
  in-JVM Java code**, so it can `Repository.login(SimpleCredentials("admin","admin"))`
  or `loginAdministrative` if the host's `SecurityManager` /
  posture does not forbid it. Practical severity is `BY-DESIGN`
  (the hook is trusted code); see §9 false-friend.
- *(inferred — §14 Q17)*

### P5 — Manifest parsing rejects malformed `filter.xml` / `config.xml` rather than applying incomplete mutations

- **Condition.** vault-core validates the workspace filter before
  applying.
- **Violation symptom.** A malformed filter causes a partial /
  ambiguous mutation set to be applied.
- **Severity.** Correctness; security-critical if the partial set
  is a privilege-escalation shape.
- *(inferred — §14 Q18)*

### P6 — Sub-package handling respects the `nonRecursive` option

- **Condition.** When `ImportOptions.nonRecursive=true`, sub-packages
  embedded in the parent package are *not* automatically installed.
- **Violation symptom.** A nested package installs despite the flag.
- **Severity.** Security-critical when the sub-package is a "trojan"
  vector (`VALID`).
- *(inferred — §14 Q12)*

### P7 — Deprecated `patch*` methods on `ImportOptions` are clearly marked deprecated for security reasons

- **Condition.** Javadoc on the deprecated methods.
- **Violation symptom.** A deprecated `patch*` method is silently
  used and applies a patch outside the workspace filter.
- **Severity.** This is more a `VALID-HARDENING` claim than a P-
  property: the deprecation is the project's response to a real
  past concern.
- *(documented: `ImportOptions.java` Javadoc — "Several patch-related
  methods are deprecated as of version 4.2.0 for security reasons")*

### P8 — Memory safety of safe-Java core

- **Condition.** JVM semantics; no `Unsafe` use in core paths.
- **Violation symptom.** OOB / UAF / data race observable.
- **Severity.** Security-critical when reachable from §6 input.
- *(inferred — §14 Q19)*

## §9 Security properties the project does NOT provide

### 9.1 No defence against a malicious package author when the operator's policy permits hook execution

This is the central disclaimer. If the operator's posture is "any
package that lands in the importer is allowed to run its hooks",
the package author becomes a code author at install time. There is
no in-FileVault gate beyond "the hook class must implement
`InstallHook`". *(documented + inferred — §14 Q13)*

### 9.2 No package signing / package authenticity verification

FileVault content packages are zip files with no built-in
cryptographic signature. The package's authenticity is whatever the
operator's distribution channel guarantees (HTTPS download, code
signing on the build pipeline, manual review). *(inferred —
§14 Q20)*

### 9.3 No defence against a malicious host JCR session

If the JCR session used to invoke install has admin privileges,
*everything* the package author writes is admin-level, and *every*
JCR mutation the hook performs is admin-level. Host's call.
*(inferred — §14 Q2)*

### 9.4 No defence against zip-bombs in package size or per-entry expansion ratio

Decompressed-output cap is not enforced by FileVault. A 1 MB zip
that expands to 100 GB will be streamed through to the importer.
*(inferred — §14 Q8)*

### 9.5 No defence against authenticated-DoS on install

A package with 10⁶ entries, deeply nested sub-packages, or a
huge `filter.xml` is parsed and applied without resource bounds.
*(inferred — §14 Q14)*

### 9.6 No defence against the operator who configures
`ImportOptions.hookClassLoader` to load from a parent that exposes
sensitive classes

The classloader fallback documented in `InstallHookProcessorImpl`
("processor's own class loader (OSGi bundle context), falls back to
thread context class loader") means the hook can reach whatever the
container exposes — which, in OSGi / AEM, is a lot. *(documented +
inferred — §14 Q10)*

### 9.7 No defence against XXE / DTD attacks on the XML parsers if the JVM defaults are not overridden

§8 P2 is the inverse — this is the disclaimer if §14 Q7 reveals
that vault-core does *not* harden parsers explicitly.

### 9.8 No defence against ACL rewriting by packages when `acHandling=OVERWRITE` (or equivalent)

A package can include `rep:policy` nodes; if `acHandling` permits,
FileVault writes them. Whatever the package author shipped is now
the ACL. *(inferred — §14 Q11)*

### 9.9 No defence against sub-package "trojan" without `nonRecursive`

A parent package can embed sub-packages whose contents the operator
did not inspect. If `nonRecursive=false`, they install
*(inferred — §14 Q12)*.

### 9.10 No constant-time comparison anywhere

FileVault is not designed for secret comparison. *(inferred —
§14 Q21)*

### 9.11 No defender stance against `vault-rcp` exposure without authentication

`vault-rcp` is HTTP; the servlet container does auth. An
unauthenticated `vault-rcp` endpoint accepts arbitrary packages from
the public Internet *(inferred — §14 Q5)*. → `OUT-OF-MODEL`
or, depending on §14 Q5 ruling, `BY-DESIGN`.

### False-friend properties (call out separately)

- **`InstallHook` interface check is *not* a sandbox.** It's a type
  gate. A class that implements `InstallHook` can do anything Java
  code can do in the JVM.
- **The "JCR session used for import" boundary is *not* a JVM
  sandbox.** The hook can `Repository.login(SimpleCredentials("admin","admin"))`
  or `loginAdministrative` if the host posture allows it.
- **`vault-validation` is *not* a security validator.** It checks
  package *structure*. A package can fully pass validation and ship
  a malicious hook.
- **`ImportOptions.dryRun=true` is *not* "import the package safely
  to see what it would do".** It applies the JCR mutations and
  rolls back at the end; install hooks **may** still execute
  in some phases *(inferred — §14 Q22)*.
- **Zip integrity (CRC) is *not* package authenticity.** It detects
  corruption, not tampering.
- **`vault-rcp` is *not* a "secure delivery" channel.** It is a
  protocol; auth is the container's.

### Well-known attack classes left to the caller

- **XXE / Billion-Laughs / DTD-fetch** via package XML files —
  *(inferred — §14 Q7)*.
- **Zip-slip** in zip extraction — *(inferred — §14 Q6)*.
- **Zip-bombs** (decompression amplification) — *(inferred —
  §14 Q8)*.
- **Authenticated-DoS** via pathological package shapes — §9.5.
- **Untrusted-code execution via install hook** — §9.1 + §3
  item 2 (the project's documented model is "install hooks are a
  feature; operators choose whether to allow them").
- **ACL trojan** via package `rep:policy` nodes — §9.8.
- **Sub-package trojan** when `nonRecursive=false` — §9.9.

## §10 Downstream responsibilities

The operator deploying FileVault MUST:

1. **Decide whether install hooks are allowed.** For packages
   from untrusted authors, configure `ImportOptions.hookClassLoader`
   to deny hook loading, or disable hook execution entirely at the
   container level *(documented + inferred — §14 Q13)*.
2. **Ensure the JCR session used for import has the smallest
   privileges the import requires.** A package installed by an
   admin session is an admin-level write — and a hook in such a
   package runs admin code.
3. **Pin or audit the install-hook classloader.** A loose parent
   classloader (OSGi-wide, or AEM-wide) means hooks can reach
   anything the container exposes *(documented:
   `InstallHookProcessorImpl.java`)*.
4. **Validate package authorship out-of-band.** FileVault does not
   sign packages; authenticity is whatever the operator's distribution
   pipeline guarantees (HTTPS download, code-signing on build,
   manual review).
5. **Run `vault-validation` at build time, but do not mistake it
   for a security validator.** It checks structure.
6. **Set `ImportOptions.nonRecursive=true` for packages whose
   sub-package list is not pre-audited.**
7. **Set `ImportOptions.acHandling` to the most-restrictive value
   consistent with the use case.** Especially for cross-tenant
   packages, do not allow `OVERWRITE`.
8. **Do not expose `vault-rcp` without strong HTTP auth + TLS**
   at the servlet container *(inferred — §14 Q5)*.
9. **Use TLS** at the servlet container for any FileVault HTTP
   surface; FileVault does not enforce.
10. **Apply request size limits / rate limits / per-package
    resource caps** at the container or pipeline layer; FileVault
    does not.
11. **Restrict OS filesystem permissions** on temp directories used
    for hook JAR extraction (vault-core writes hook JARs to temp
    files before loading; cleanup via `Files.deleteIfExists`).
12. **Do not use deprecated `patch*` methods on `ImportOptions`.**
    They were deprecated in 4.2.0 for security reasons.
13. **For build-time package authoring,** prefer creating packages
    *without* install hooks where possible; explain in package
    documentation why a hook is needed if one is included.

## §11 Known misuse patterns

- **Installing packages from untrusted authors with default
  `ImportOptions`** — equivalent to running unsigned JARs.
- **Exposing `vault-rcp` HTTP endpoint to the public Internet
  without strong authentication.**
- **Using the import session with admin privileges** — the package
  inherits admin authority.
- **Using `ImportOptions.acHandling=OVERWRITE` for cross-tenant
  packages** — the package author becomes the ACL author.
- **Failing to set `nonRecursive`** when the parent package's
  sub-package list has not been audited.
- **Relying on `vault-validation` to detect malicious hooks** — it
  doesn't.
- **Treating zip integrity as package authenticity** — CRC detects
  corruption, not tampering.
- **Reaching into `META-INF/vault/properties.xml`
  `installhook.{name}.class`** to load a class not present in the
  package — the classloader fallback may find one in the
  container.
- **Mixing `dryRun=true` with the assumption that hooks won't
  fire** — *(inferred — §14 Q22)*.

## §11a Known non-findings (recurring false positives)

Highest-leverage input for automated agentic security scans.

- **"Install hook executes arbitrary Java in the importer JVM."**
  Documented and intentional *(documented:
  `InstallHookProcessorImpl.java`)*. The operator chose to allow
  hooks via `ImportOptions`. → `BY-DESIGN: property-disclaimed`
  per §3 item 2 / §9.1.
- **"Package author can write any JCR node permitted by the
  import session."** Documented: the import session's JCR
  privileges are the gate. → `BY-DESIGN: property-disclaimed`.
- **"Package can rewrite ACLs via `rep:policy` nodes when
  `acHandling` permits."** `acHandling` is operator-config. →
  `OUT-OF-MODEL: non-default-build` or `BY-DESIGN` depending on
  §14 Q11.
- **"Hardcoded test password / sample hook in
  `vault-hook-example/`, `vault-core-it/`."** Unsupported
  components. → `OUT-OF-MODEL: unsupported-component`.
- **"`vault-rcp` exposed over HTTP without TLS."** Servlet
  container's job. → `OUT-OF-MODEL: non-default-build`.
- **"DoS via huge package / millions of zip entries."** No
  resource cap by design *(inferred — §14 Q8 / Q14)*. →
  `BY-DESIGN: property-disclaimed` per §9.4 / §9.5.
- **"Deprecated `ImportOptions.patch*` method retained for
  back-compat."** Documented deprecation per `ImportOptions.java`
  Javadoc. → `KNOWN-NON-FINDING`.
- **"Vulnerability X exists only in jackrabbit / jackrabbit-oak."**
  Cross-references. → `OUT-OF-MODEL: unsupported-component`.
- **"`vault-validation` did not detect malicious hook code."**
  Validators do structural validation; not a security scanner. →
  `OUT-OF-MODEL: trusted-input` (the package itself is the
  trusted input, and the validator's coverage is not a security
  claim) or `BY-DESIGN: property-disclaimed`.
- **"Zip integrity (CRC) does not detect tampering."** CRC is not
  a MAC. → `BY-DESIGN: property-disclaimed` per §9 false-friend.
- **"`dryRun=true` did not prevent the install hook from firing."**
  *(inferred — §14 Q22)* → depends on PMC ruling; likely
  `BY-DESIGN`.

## §12 Conditions that would change this model

- A package-signing / package-authenticity mechanism added to
  vault-core (e.g. Sigstore, GPG sig in the package).
- A sandboxing story for install hooks (JVM sandbox, SecurityManager
  posture, dedicated classloader with restricted parent).
- A change in the default of `ImportOptions.hookClassLoader`,
  `acHandling`, `nonRecursive`, or `dryRun` hook semantics.
- A new way to load hook code (e.g. via Groovy scripts or other
  scripting engines).
- A new transport for package delivery (currently zip on disk / over
  HTTP — vault-rcp).
- A vulnerability report that cannot be cleanly routed to one of
  the §13 dispositions — that is evidence the model is incomplete.

## §13 Triage dispositions

A report against FileVault receives exactly one of:

| Disposition | Meaning | Licensed by |
| --- | --- | --- |
| `VALID` | Violates a §8 property via an in-scope §7 adversary using an in-scope §6 input. | §8, §6, §7 |
| `VALID-HARDENING` | No §8 property violated, but a §11 misuse pattern can be made harder by code change. | §11 |
| `OUT-OF-MODEL: trusted-input` | Requires attacker control of a §6 parameter the model marks trusted (operator's `ImportOptions`, package author's intent, JCR session privileges). | §6 |
| `OUT-OF-MODEL: adversary-not-in-scope` | Requires a §7 actor the model excludes (host JCR application, operator, servlet container, side-channel observer). | §7 |
| `OUT-OF-MODEL: unsupported-component` | Lands in `vault-core-it/`, `vault-hook-example/`, `target-osgi-environment/`, or in jackrabbit / jackrabbit-oak. | §3 item 7 / item 8 |
| `OUT-OF-MODEL: non-default-build` | Manifests only under a §5a value the maintainer rules dev/test (e.g. unauthenticated vault-rcp; deprecated `patch*` APIs). | §5a |
| `BY-DESIGN: property-disclaimed` | Concerns a §9 property the project explicitly does not provide (untrusted-hook execution, package authenticity, zip-bomb caps, etc.). | §9 |
| `KNOWN-NON-FINDING` | Matches a §11a recurring false positive. | §11a |
| `MODEL-GAP` | Cannot be cleanly routed to any of the above — triggers §12 model revision. | §12 |

## §14 Open questions for the maintainers

Every *(inferred)* tag in the body maps to one of these. Proposed
answers are inline; confirm, correct, or strike.

### Wave 1 — scope, deployment shapes

**Q1.** Confirm that FileVault has two distinct deployment shapes:
(a) in-JCR-container importer (vault-core invoked by Sling / AEM /
OSGi Packaging / `JcrPackageManager`) and (b) standalone `vlt`
CLI. Confirm that install hooks fire only on the *server* side at
import time, not in the `vlt` CLI. *(maps to §2)*

**Q2.** Confirm that JCR-side authorisation (Oak / Jackrabbit's
`AccessManager` / `PermissionProvider` evaluating on
`Session.save()`) is *the* authorisation gate, and FileVault relies
on it entirely — i.e. FileVault does not duplicate or enforce JCR
permissions itself, just honours what the import session can do.
*(maps to §3 item 1, §4 B4, §9.3)*

**Q3.** The model treats *the package author* as the highest-
priority adversary. Confirm — and is the canonical operator stance
"only install packages from authors the operator trusts (or has
out-of-band signed)"? *(maps to §3 item 3, §6 row "zip bytes",
§7, §9.1, §10 item 4)*

**Q4.** `vault-validation` — confirmed that this is a structural
validator (filter coherence, package metadata correctness,
dependency resolution), not a security validator (it does not scan
hook code or detect malicious XML)? *(maps to §3 item 5, §9
false-friend)*

**Q5.** `vault-rcp` HTTP exposure: is "vault-rcp deployed
unauthenticated" `OUT-OF-MODEL: non-default-build` (operator misconfig)
or a `VALID` FileVault-side concern? Proposed: the former. Does the
operator-facing documentation say "always behind an authenticated
servlet container endpoint"? *(maps to §3 item 6, §10 item 8,
§11a)*

### Wave 2 — zip and XML safety

**Q6.** **The zip-slip question.** Does vault-core's zip extraction
explicitly refuse `ZipEntry` names containing `..` segments or
absolute paths that would resolve outside the configured target?
Cite the validator (`ZipVaultPackage` / `Archive` impl).
§8 P1 depends on this. *(maps to §4 B1, §6, §8 P1, §11a)*

**Q7.** **The XXE question.** Are vault-core's XML parses (`filter.xml`
and `config.xml` in `META-INF/vault/`, `.content.xml` DocView /
Enhanced DocView in `jcr_root/`, `META-INF/vault/properties.xml`)
configured with hardened `DocumentBuilderFactory` /
`XMLInputFactory` (external entities disabled, DOCTYPEs disabled,
entity expansion capped)? Or do they inherit JVM JAXP defaults?
This is the highest-leverage single question for the import pipeline.
§8 P2 collapses into §9.7 if FileVault relies on JVM defaults.
*(maps to §4 B2/B3, §6, §8 P2, §9.7)*

**Q8.** **The zip-bomb question.** Are there enforced caps on:
total uncompressed bytes per package, per-entry uncompressed size,
per-entry compression ratio, or number of entries? Proposed: no
(per §9.4). Confirm. *(maps to §6, §9.4)*

**Q9.** "What FileVault does NOT do to its host" inventory: no
listening sockets in vault-core (vault-rcp is container-hosted);
no signal handlers; no child processes from the importer (install
hooks may, but that's hook behaviour). Confirm. *(maps to §5)*

### Wave 3 — install hooks and `ImportOptions`

**Q10.** The default `ImportOptions.hookClassLoader` resolution
documented in `InstallHookProcessorImpl` ("processor's own class
loader (OSGi bundle context), falls back to thread context class
loader"). Is this the **supported production posture**, or is the
canonical production posture "operators override to a restricted
classloader"? *(maps to §5a, §9.6, §10 item 3)*

**Q11.** `ImportOptions` defaults (`strict`, `importMode`,
`acHandling`, `cugHandling`, `nonRecursive`, `dryRun`,
`autoSaveThreshold`). For each, what is the shipped default, and
which defaults are the supported production posture vs. dev/test?
The two with highest blast radius are `acHandling` (can a package
rewrite ACLs?) and `nonRecursive` (do sub-packages auto-install?).
*(maps to §5a, §9, §10)*

**Q12.** Sub-package handling: confirm `nonRecursive=true` reliably
prevents sub-packages from installing? *(maps to §8 P6, §9.9)*

**Q13.** **The install-hook policy question.** Is "package author
ships a hook → hook runs" the supported production posture for
the default `ImportOptions`? Or is the supported production posture
"hook execution should be disabled by default, operators opt in
per package"? The §9.1 disclaimer's wording depends on the
answer. *(maps to §5a, §9.1, §10 item 1)*

**Q14.** Package / hook count / filter complexity caps: any
enforced? Proposed: none. *(maps to §6, §9.5)*

### Wave 4 — environment, JCR-session-as-hook-context

**Q15.** Same-JVM (e.g. OSGi co-installed bundle) attackers:
confirmed out-of-model? *(maps to §7, §9)*

**Q16.** Side-channel adversaries: confirmed out-of-model? *(maps
to §3 item 10, §7)*

**Q17.** Install-hook trust context: when a hook obtains
`InstallContext.getSession()`, is the session *guaranteed* to be
the same session that invoked `JcrPackage.install`, or can the
session be elevated by the hook (e.g. by calling
`Repository.login` with admin credentials)? The hook is Java; it
**can** open any session if the host JVM permits — confirmed?
*(maps to §8 P4, §9 false-friend)*

**Q18.** Workspace-filter parsing: does FileVault treat a malformed
`filter.xml` as fatal (abort import) or recoverable (apply what's
valid)? §8 P5 assumes fatal. *(maps to §8 P5)*

**Q19.** Memory safety of safe-Java core: any JNI bridges in
vault-core / vault-validation that could expose native memory
unsafety? Proposed: none. *(maps to §8 P8)*

**Q20.** Package signing / authenticity: confirmed that FileVault
ships *no* signature verification at the package level (no MAC,
no GPG, no Sigstore); package authenticity is the operator's
out-of-band concern? *(maps to §9.2, §10 item 4)*

**Q21.** Constant-time comparison: confirmed out of model
(nothing FileVault does requires it; secret comparison is
container-side)? *(maps to §9.10)*

**Q22.** `ImportOptions.dryRun=true` semantics with install hooks:
do hooks (PREPARE / INSTALLED phases) fire during a dry run, or
are they suppressed? §9 false-friend assumes they may fire.
*(maps to §9 false-friend, §11)*

### Wave 5 — meta

**Q23.** Should this document live at `vault-core/src/site/markdown/threat-model.md`,
or at the project root as `docs/threat-model.md`? *(meta)*

**Q24.** Is there an existing FileVault threat-model document
(JIRA, Confluence, prior PMC discussion) that this should
reconcile with rather than supersede? In particular: the 4.2.0
`patch*` deprecation strongly suggests prior security work the
PMC has on the record; can the PMC point at it? *(meta — §3.1a)*

**Q25.** §11a known-non-findings is currently 11 entries, mostly
documented-disclaim shaped. Could the PMC contribute 3–5 patterns
the FileVault triage queue sees recur in inbound reports that
you close as not-a-bug? Patterns like "install hook ran arbitrary
code — by design", "package overwrote ACLs — operator's `acHandling`
setting", "zip-bomb DoS — no resource cap by design" would harden
§11a substantially. *(meta — §11a)*

**Q26.** Should this FileVault model cross-reference the (still-to-
be-drafted) `jackrabbit` and `jackrabbit-oak` models for items
§3 item 1 / item 7 / item 8? *(meta)*

**Q27.** §8 P7 (deprecated `patch*` methods) is documented as
"deprecated as of version 4.2.0 for security reasons". Can the
PMC cite the JIRA / CVE / advisory that motivated this
deprecation, so the model can carry the precedent? *(meta — §3.1a)*

---

## Appendix: Existing security artefact → §x back-map

FileVault does not currently ship an in-repo `SECURITY.md`. The
de-facto security-policy artefacts are:

- Javadoc on `ImportOptions.java` — "Several patch-related methods
  are deprecated as of version 4.2.0 for security reasons" — *the*
  closest thing to an in-repo statement about a security-relevant
  posture change;
- the `InstallHookProcessorImpl.java` source comments — they
  describe the classloader-fallback behaviour without explicit
  security framing;
- the website documentation under `https://jackrabbit.apache.org/filevault/`;
- the project `README.md`.

| Source | Claim | Lands in |
| --- | --- | --- |
| `README.md` | "Apache Jackrabbit FileVault introduces a JCR repository to filesystem mapping" + "vlt: Subversion like utility to work and develop with repository content" | §2 intended use |
| `README.md` | Maven 3.9+, Java 17+ | §5 |
| `InstallHookProcessorImpl.java` | scans `META-INF/vault/hooks/` for `.jar`; reads `installhook.{name}.class` from properties; classloader fallback (processor / thread-context); `InstallHook` interface check | §4 B5, §6 rows, §8 P3, §9.6 |
| `ImportOptions.java` Javadoc | "Several patch-related methods are deprecated as of version 4.2.0 for security reasons" | §8 P7, §10 item 12, §11a |
| `ValidationContext.java` | vault-validation SPI exposes filter / properties / dependencies / incremental flag for structural validation | §3 item 5, §9 false-friend |
