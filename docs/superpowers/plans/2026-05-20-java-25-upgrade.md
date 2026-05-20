# Java 25 Upgrade Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the whole `game-server` project from Java 21 to Java 25 across build settings, docs, and inline version references, then verify with Maven on JDK 25.

**Architecture:** Keep the change narrow and explicit. Use the root Maven property as the canonical Java version, update module-level compiler plugin overrides that still hardcode `21`, and synchronize human-facing docs/comments so the repository no longer advertises Java 21.

**Tech Stack:** Maven multi-module build, Java 25, Apache Maven verify lifecycle, Markdown documentation.

---

### Task 1: Align Build Configuration

**Files:**
- Modify: `pom.xml`
- Modify: `orion-core/pom.xml`
- Modify: `orion-player/pom.xml`

- [ ] **Step 1: Update the root Java version property to 25**

Change:

```xml
<java.version>21</java.version>
```

To:

```xml
<java.version>25</java.version>
```

- [ ] **Step 2: Update hardcoded compiler source and target values in `orion-core/pom.xml`**

Change:

```xml
<source>21</source>
<target>21</target>
```

To:

```xml
<source>25</source>
<target>25</target>
```

- [ ] **Step 3: Update hardcoded compiler source and target values in `orion-player/pom.xml`**

Change:

```xml
<source>21</source>
<target>21</target>
```

To:

```xml
<source>25</source>
<target>25</target>
```

### Task 2: Synchronize Repository Messaging

**Files:**
- Modify: `README.md`
- Modify: `orion-core/src/main/java/game/engine/core/VirtualThreadExecutorConfigurator.java`

- [ ] **Step 1: Replace Java 21 references in `README.md` with Java 25**

Update the product overview, feature section, environment requirement, and version table so the repository consistently advertises Java 25.

- [ ] **Step 2: Update the virtual-thread inline comment**

Change the inline comment in `VirtualThreadExecutorConfigurator` so it refers to Java 25 instead of Java 21.

### Task 3: Verify on JDK 25

**Files:**
- Verify: `pom.xml`
- Verify: `orion-core/pom.xml`
- Verify: `orion-player/pom.xml`
- Verify: `README.md`

- [ ] **Step 1: Run the Maven verification lifecycle with the local JDK 25**

Run:

```bash
mvn verify
```

Expected:

```text
BUILD SUCCESS
```

- [ ] **Step 2: If verification fails, fix the specific Java 25 compatibility issue and re-run `mvn verify`**

Use the failing module or plugin output as the source of truth. Do not claim completion until a fresh `mvn verify` run succeeds or the remaining blocker is explicitly documented.
