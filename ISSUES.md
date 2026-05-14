# XME.digital — Plugin Audit: Issues, Bugs, Improvements

> **Scope.** Full read-through of every Kotlin / Java source file, `plugin.xml`,
> `build.gradle.kts`, gradle properties, resource bundle, JSON schemas, and
> inspection descriptions in `xme-online-idea-plugin`. Each finding cites a
> file and line range so it can be navigated directly from the IDE.
>
> **Constraint.** All suggestions preserve current user-visible behavior;
> they replace a broken-but-working implementation with a correct one, never
> change the feature set.
>
> **Severity legend.**
> - **BUG** — clearly wrong; misbehaves on a real input today.
> - **ISSUE** — correctness / perf / threading risk; works most of the time.
> - **IMPROVEMENT** — better idiom, maintainability, or platform-recommended.
> - **NIT** — cosmetic / style.
>
> **Per-subsystem reports** (full prose, including this same content with
> additional rationale) are also stored at:
> `/tmp/xme-review/group-{A..H}-*.md`.

---

## Table of contents

1. [Executive summary](#1-executive-summary)
2. [Top-priority fix order](#2-top-priority-fix-order)
3. [Cross-cutting themes](#3-cross-cutting-themes)
4. [Security findings](#4-security-findings)
5. [Findings by subsystem](#5-findings-by-subsystem)
   - 5.A [LEP / Groovy extensions](#5a-lep--groovy-extensions)
   - 5.B [Project view, tree, xmentityspec](#5b-project-view-tree-xmentityspec)
   - 5.C [YAML DSL core engine](#5c-yaml-dsl-core-engine)
   - 5.D [YAML inspection / completion / refs / injection](#5d-yaml-inspection--completion--refs--injection)
   - 5.E [Services & deploy actions](#5e-services--deploy-actions)
   - 5.F [Webview / Tool window / ViewServer](#5f-webview--tool-window--viewserver)
   - 5.G [Utilities](#5g-utilities)
   - 5.H [plugin.xml / build / resources / domain](#5h-pluginxml--build--resources--domain)

---

## 1. Executive summary

The plugin is feature-rich, but several systemic issues will become visible
on any non-trivial codebase:

* **Threading model is ad-hoc.** Three patterns coexist — `project.doAsync`,
  raw `Application.executeOnPooledThread`, and direct `runReadAction`. None
  use the recommended `ReadAction.nonBlocking { … }.submit(NonUrgentExecutor)`,
  none use a project-scoped `CoroutineScope`, and several hot paths
  (`IconProvider.getIcon`, `LineMarkerProvider`, `update()`) do file I/O on
  EDT. Tasks survive project close and throw `AlreadyDisposedException`.

* **Sandbox of user-supplied JS is open.** `GraalJsRunner` is configured with
  `allowHostClassLookup { true }` + `allowAllImplementations(true)`. Any
  `xme-plugin/*.yml` spec can call `Java.type("java.lang.Runtime").getRuntime().exec(...)`
  at IDE privilege level. The reflection deny-list does not stop this.

* **HTTPS verification is disabled JVM-wide** as a side effect of one
  utility (`HttpUtils.disableHttpsCertificateValidation()`). After it runs
  once, every HTTPS connection in the IDE (Marketplace, telemetry, VCS,
  License Server) becomes trust-all for the rest of the session. This is
  the single most severe finding.

* **The embedded Jetty server (`ViewServer`)** binds to `0.0.0.0` (LAN
  reachable), interpolates `request.queryString` into served HTML
  (XSS), keeps `dirAllowed=true`, has no Host/Origin defense, and is
  never stopped — `Disposable` is not wired anywhere.

* **`SnakeYAML 1.x Yaml().loadAs(...)` is used** in `YamlUtils.parseYaml`,
  exposing CVE-2022-1471-class deserialization gadgets when reading user
  configs.

* **PSI invalidation is racy.** Several contributors store `PsiElement` /
  `PsiFile` references in `UserDataHolder.userData`, `MutableSet<PsiFile>`,
  and quick-fix closures. After reparse those become invalid; some retain
  forever (`Key.create` is called per string in `FileUtils`/`PsiUtils` —
  unbounded leak).

* **Inspection short-name does not match description filename.**
  `shortName="XmEntitySpecValidation"` in `plugin.xml` has no
  `XmEntitySpecValidation.html` in `inspectionDescriptions/`; the IDE
  Settings → Inspections panel renders blank, and `verifyPlugin` flags it.

* **`shadowJar` is wired into the build** and even documented as the
  canonical command (`./gradlew clean shadowJar`). For an IntelliJ Platform
  plugin this produces a fat jar with platform-duplicated classes
  (Jackson, SnakeYAML, Commons, Kotlin stdlib) — `LinkageError` material.
  The shippable artifact is `buildPlugin` (zip).

* **Plugin.xml registration churn.** Three actions are registered *twice*
  (old-UI / new-UI variants) with different ids but the same class, so the
  IDE creates two instances; one wins shortcut binding silently. Two
  groups depend on actions declared *below* them in the same file, so
  ordering is unstable.

* **Caching layer is fragile.** Cache keys mix names (not paths), drop
  spec-key segregation, and the `withCache` dependency is `listOf(this)`
  (only the element itself, not `PsiModificationTracker.MODIFICATION_COUNT`),
  so completion / type inference goes stale after edits.

* **No tests.** `src/test/` does not exist. Half of the findings here would
  have been caught by a single `BasePlatformTestCase` per subsystem.

---

## 2. Top-priority fix order

Strictly ordered: each item makes the next one safer or easier.

1. **`HttpUtils.disableHttpsCertificateValidation()`** — stop mutating
   JVM-wide `HttpsURLConnection.setDefaultSSLSocketFactory` /
   `setDefaultHostnameVerifier`. Apply per-connection only.
2. **`GraalJsRunner.allowHostClassLookup`** — restrict to plugin's own
   FQNs (`fqn.startsWith("com.icthh.xm.xmeplugin.")`). Track + close
   thread-local `Context`s in `dispose()`.
3. **`YamlUtils.parseYaml`** — replace SnakeYAML 1.x `Yaml().loadAs(...)`
   with Jackson YAML (already in deps) or `LoadSettings` allow-list.
4. **`ViewServer`** — bind to `127.0.0.1`, validate `pipeId` against UUID
   regex, set `dirAllowed=false`, disable directory dump, drop the
   `internal` `com.jetbrains.performancePlugin.commands` import in
   `WebDialog`, register `Disposable` so the server stops on plugin
   unload.
5. **`shadowJar` block in `build.gradle.kts`** — remove (plus
   `generateManifestFile`); update `README.md` + `CLAUDE.md` to use
   `./gradlew buildPlugin`. Drop platform-duplicated deps
   (`snakeyaml-engine`, `commons-lang3`, `commons-text`, jackson) — switch
   to `compileOnly` where needed.
6. **Inspection description filename mismatch** — rename
   `inspectionDescriptions/YamlSpecLocal.html` (or `XmEntityValidation.html`)
   to `XmEntitySpecValidation.html` to match the `shortName`.
7. **`YamlCompletionContributor.kt:94`** — drop the unconditional
   `result.stopHere()`; it suppresses the platform's built-in YAML key
   completion in every config-project YAML file.
8. **`YamlLanguageInjector.getInjection`** — remove the
   `invokeLater { TemporaryPlacesRegistry.addHostWithUndo(...) }` side
   effect. `getInjection` must be read-only.
9. **`OkHttpClient`** — single application service, with explicit
   timeouts; close `dispatcher.executorService` + `connectionPool.evictAll()`
   on dispose. Stop creating a new client per HTTP call.
10. **`XmEntitySpecUtils.isEntitySpecification` memoization** — add the
    missing `putUserData(IS_ENTITY_SPEC, true)` at line 30. One-line fix,
    biggest perf win for icon / line-marker / color hot paths.
11. **`CommonsCompletionContributor.getLepFolder`** — fix the off-by-one
    parent-walk that breaks `commons` resolution for all three callers.
12. **`SettingService` credentials** — move `xmSuperAdminPassword` and
    `clientPassword` from `XmlSerializerUtil` state to `PasswordSafe`
    via `CredentialAttributes`.
13. **plugin.xml duplicate action registrations** — consolidate
    `deployToEnvSelectorOldUi` + `deployToEnvSelectorNewUi` (and the
    other two pairs) into one `<action>` with two `<add-to-group>`
    children. Reorder so anchor actions appear before dependent ones.
14. **`AnAction.update` violations** — `DeployEnvSelector.update` calls
    `JComponent.updateUI()` from BGT; `DeployToEnv.update` compares by
    `name`, breaking on user-named envs.
15. **Missing `package` declarations** — `FileUtils.kt`, `GitUtils.kt`,
    `OkHttpClient.kt`, `InspectionUtils.kt`. Currently leak top-level
    helpers into the *default* JVM package.

---

## 3. Cross-cutting themes

### 3.1 Threading & lifecycle

* Hot platform callbacks (`IconProvider.getIcon`, `LineMarkerProvider.getLineMarkerInfo`,
  `ElementColorProvider.getColorFrom`, `TreeStructureProvider.modify`,
  `AnAction.update`, `CompletionContributor.fillCompletionVariants`,
  `PsiReferenceProvider.getReferencesByElement`,
  `LanguageInjectionContributor.getInjection`,
  `LocalInspectionTool.buildVisitor`) all do work that should be cached
  or off-EDT — synchronous file reads (`File.exists()`, `File.readLines()`,
  YAML parse), HTTP fetches, JS evaluations, VFS refresh-true calls.
* No file uses `ReadAction.nonBlocking { … }.submit(NonUrgentExecutor)`,
  the post-2022 recommended pattern.
* `project.doAsync` (`ThreadUtils.kt`) does not check `project.isDisposed`,
  catches only `Exception` (not `Error`/`Throwable`), and lives on the
  application thread pool, so tasks survive project close.
* No service holds a `CoroutineScope` injected via `@Service` constructor
  (the 2023+ pattern), so cancellation on project close is impossible.
* `XmePluginSpecService.reload` schedules `fileAdded` jobs and then runs
  the daemon-restart synchronously *before* they complete.
* `LepChooseByNameContributor.processNames` ignores the `Processor`
  return value — Goto File cannot cancel mid-iteration.
* `LineMarkerProvider`s call `XmEntitySpecUtils.isEntitySpecification`
  on every leaf without proper memoization (missing one `putUserData`).

### 3.2 PSI / VFS lifecycle

* Several contributors stash `PsiElement` / `PsiFile` into `UserDataHolder.userData`
  (`COMMONS_METHOD_FILE`, `LEP_EXPRESSION`, `TENANT_CONFIG_FIELD`,
  `TENANT_CONFIG_FIELD_PATH`, `LAST_SPEC_STATE`, `LAST_ENTITY_SPEC`,
  `IS_ENTITY_SPEC`) which is never invalidated on rename / reparse.
* `XmePluginSpecService.filesBySpec : Map<String, MutableSet<PsiFile>>`
  retains `PsiFile`s — invalid after reparse — and inner `MutableSet`s
  are not concurrent, so the snapshot `.toList()` can throw `CMEx`.
* Quick fix in `YamlSpecLocalInspection` captures the `YamlContext`
  (containing live PSI) across the user's potential edits before clicking.
* `FileUtils.keyCache` and `PsiUtils.cacheKeys` both call `Key.create(name)`
  per unique string — `Key.create` is intended as a *static singleton*;
  this is a session-long memory leak.
* `withCache` (PsiUtils.kt:41) depends only on `listOf(this)`, not
  `PsiModificationTracker.MODIFICATION_COUNT` — stale type inference
  after edits in another file.
* Multiple places call `VfsUtil.findFile(..., refreshIfNeeded=true)` /
  `VfsUtil.findFileByIoFile(File, true)` synchronously from EDT-callable
  paths (Goto Declaration, `AnAction.update`, `IconProvider.getIcon`).
  Synchronous refresh from EDT is documented as forbidden.

### 3.3 Dumb-mode handling

No contributor declares `DumbAware`. `InArgsNonCodeMembersContributor`,
`LepContextNonCodeMembersContributor`, `TenantConfigMethodCallTypeCalculator`,
`TenantConfigPropertyTypeCalculator` all hit index APIs without catching
`IndexNotReadyException`, so a single completion / type-calc invocation
during indexing kills the whole resolution chain.

### 3.4 Caching layering

* Cache keys keyed on `file.name` (not `file.path`) cause collisions
  across tenants (`XmePluginSpecMetaInfoService`).
* `getFilesState` cache key drops `specKey` (only `getYamlNode` includes
  it).
* `withMultipleFilesCache` sorts on every call.
* `XmeProjectStateService` caches with `VFS_STRUCTURE_MODIFICATIONS` —
  text edits don't bust the cache. Should be
  `PsiModificationTracker.MODIFICATION_COUNT`.
* `TenantConfigService.withCache` same issue.
* `IconProvider` cache `iconCache` is application-wide, keyed by tenant
  name, never invalidated, and not project-scoped — two open projects
  collide; closed projects leak.
* HTTP `filecache` (`HttpUtils.kt`) is process-wide and cleared on every
  spec reload — punishes other projects.

### 3.5 Default-package files

`FileUtils.kt`, `GitUtils.kt`, `OkHttpClient.kt`, `InspectionUtils.kt`
have *no* `package` declaration. Their top-level functions
(`createProjectFile`, `addToGit`, `readTextAndClose`, `httpGet`,
`macthPattern`, …) live in the default JVM package. Consumers therefore
do bare `import addToGit`, which works only by Kotlin's same-classloader
luck. Java cannot call them, and the logger names degrade to
`FileUtilsKt`, `GitUtilsKt`, etc.

### 3.6 Service registration

`@Service(Service.Level.PROJECT)` is correctly used everywhere; no
`<projectService>` entries are needed in `plugin.xml`. **However,**
several services hold pooled / native resources without implementing
`Disposable`:

* `XmeMsConfigRestService` — creates a new `OkHttpClient` per call
  (dispatcher threads + connection pool leak).
* `GraalJsRunner` — has a `dispose()` method that never actually closes
  the thread-local `Context`s (it walks `executionContexts.values`, but
  that map is never written to).
* `ViewServer` — `object` with `startServer()` but no `stopServer()`.
* CEF scheme-handler factories registered in `WebDialog.init` are never
  unregistered — every dialog open leaks one application-wide handler.

### 3.7 i18n

`<resource-bundle>messages.MessagesBundle</resource-bundle>` is declared
but actions, notification group, action group labels, and quick-fix
family names are all hardcoded inline. `MessagesBundle.kt` is a copy of
the IntelliJ Plugin Template `DynamicBundle`; the template leftovers
`projectService`, `randomLabel`, `shuffle` are still in
`MessagesBundle.properties`.

### 3.8 i18n bundle

`MessagesBundle.kt` uses single-arg `DynamicBundle(BUNDLE)` rather than
`DynamicBundle(MessagesBundle::class.java, BUNDLE)`. The single-arg
ctor depends on stack-walk classloader resolution that can fail at
runtime in dynamic-plugin reload scenarios.

---

## 4. Security findings

| # | Severity | Where | Risk |
|---|---|---|---|
| S1 | **CRITICAL** | `utils/HttpUtils.kt:48-69` | `HttpsURLConnection.setDefault…` mutates JVM-wide TLS; whole IDE drops cert validation for the session |
| S2 | **CRITICAL** | `utils/GraalJsRunner.kt:77-83` | `allowHostClassLookup { true }` — RCE via `Java.type` from any `xme-plugin/*.yml` JS spec |
| S3 | **CRITICAL** | `yaml/YamlUtils.kt:19-22` | `Yaml().loadAs(...)` — SnakeYAML 1.x deserialization gadget (CVE-2022-1471 class) |
| S4 | **HIGH** | `ViewServer.kt` (all of it) | Jetty binds `0.0.0.0`; LAN-reachable; XSS via `request.queryString → index.html`; no Host/Origin check; `dirAllowed=true` |
| S5 | **HIGH** | `services/settings/SettingService.kt:48-90` | `xmSuperAdminPassword` + `clientPassword` plaintext in `.idea/xm^Online.Settings.xml` (VCS-committed by users) |
| S6 | **HIGH** | `services/XmeMsConfigRestService.kt:78-95` | Token-fetch error notification surfaces response bodies (may contain server-side hints / usernames); error path logs to `idea.log` |
| S7 | **MEDIUM** | `yaml/YamlUtils.kt:13-17` | `LoadSettings.builder()` default — billion-laughs / anchor-bomb possible on user configs |
| S8 | **MEDIUM** | `webview/SettingsDialog.kt` | Jackson reads arbitrary JSON from CEF (no size/depth caps), combined with wildcard bind |
| S9 | **MEDIUM** | `webview/WebDialog.kt:17` | Stray internal import from `com.jetbrains.performancePlugin.commands` (not in `<depends>`) |
| S10 | **LOW** | `extensions/ConfigIconProvider.kt:112-114` | HTTP fetch of user-controlled `logoUrl` on project open; combined with S1 = downgraded TLS for whole session |

---

## 5. Findings by subsystem

### 5.A — LEP / Groovy extensions

Files: `extensions/CommonsCompletionContributor.kt`,
`CommonsNonCodeMembersContributor.kt`,
`CommonsRefactoringElementListenerProvider.kt`,
`InArgsNonCodeMembersContributor.kt`,
`LepContextNonCodeMembersContributor.kt`, `LepContextTypeCalculator.kt`,
`LepNavigationContributor.kt`, `TenantConfigGotoDeclarationHandler.kt`,
`TenantConfigMethodCallTypeCalculator.kt`.

#### Bugs

* **BUG** `CommonsCompletionContributor.kt:82-106` — `getLepFolder` parent
  walk advances `parent = parent.parent` *before* adding to the list,
  so the path is off-by-one. All three callers' `commons` lookup either
  returns null or resolves the wrong tenant's `commons` folder.
* **BUG** `CommonsCompletionContributor.kt:83` — no null-check on
  `place.originalFile.virtualFile` before `.path` (NPE in scratch /
  in-memory files).
* **BUG** `CommonsNonCodeMembersContributor.kt:80-91` — strong
  `PsiFile` / `PsiElement` references stamped via
  `putUserData(COMMONS_METHOD_FILE, element)`; never invalidated on
  reparse → memory leak + `CommonsRefactoringElementListenerProvider`
  dereferences stale `VirtualFile`.
* **BUG** `CommonsNonCodeMembersContributor.kt:43-48` — chain detection
  via `place.text.startsWith("lepContext.commons")` breaks on aliasing,
  parens, multi-line, dummy-id, etc.
* **BUG** `CommonsRefactoringElementListenerProvider.kt:27-35` — rename
  callback wraps `virtualFile.rename` in a fresh `runWriteAction`,
  breaking undo grouping with the original rename (undo leaves
  method/file half-renamed).
* **BUG** `CommonsRefactoringElementListenerProvider.kt:13-21` —
  captured `originalMethod` may be invalid by the time `elementRenamed`
  fires.
* **BUG** `CommonsRefactoringElementListenerProvider.kt:25` —
  `newElement.name?.takeIf { it.isNotBlank() } ?: return` is missing;
  null/blank produces `Commons$$null.groovy`.
* **BUG** `InArgsNonCodeMembersContributor.kt:39` — `name.substringBefore("$$")`
  on a file without `$$` returns the whole name including `.groovy`,
  so the LEP-key match never succeeds.
* **BUG** `InArgsNonCodeMembersContributor.kt:47-52` — `ClassUtil.findPsiClass`
  + `AnnotatedMembersSearch.search` throw `IndexNotReadyException` during
  indexing; not caught.
* **BUG** `InArgsNonCodeMembersContributor.kt:55-57` — `lepFolder ?: ""`
  → `relativePath.startsWith("")` always true; if the folder cannot be
  resolved, every annotated method matches.
* **BUG** `LepContextNonCodeMembersContributor.kt:54-57` —
  `PsiShortNamesCache.getClassesByName` throws in dumb mode (not
  caught); `candidates[0]` silently picks the first `LepContext` class
  in multi-module projects.
* **BUG** `LepContextTypeCalculator.kt:31-37` — `LEP_EXPRESSION` user-data
  accumulates on PSI without invalidation; stale type info after edits.
* **BUG** `LepNavigationContributor.kt:22-35` — `processor.process(...)`
  return value ignored (Processor contract: `false` means stop); Goto
  File cannot be cancelled.
* **BUG** `LepNavigationContributor.kt:26-30` — `processor.process(name)`
  called twice; benign dedupe today, misleading.
* **BUG** `LepNavigationContributor.kt:72` — `replaceDots` actually replaces
  underscores (`symbol.replace("_".toRegex(), "-")`); misnamed.
* **BUG** `TenantConfigGotoDeclarationHandler.kt:28` —
  `VfsUtil.findFile(path, true)` triggers VFS refresh on EDT inside
  Goto Declaration.
* **BUG** `TenantConfigGotoDeclarationHandler.kt:39` — array contains
  `null`s; platform contract is non-null.
* **BUG** `TenantConfigMethodCallTypeCalculator.kt:98-102` —
  `TenantConfigPsiType.isValid() = true` unconditionally; hides invalidity
  from the platform, doesn't avoid downstream `PsiInvalidElementAccessException`.
* **BUG** `TenantConfigMethodCallTypeCalculator.kt:89-90` — `qualifiedName`
  on a `PsiClassReferenceType.reference` may throw `IndexNotReadyException`
  in dumb mode.
* **BUG** `TenantConfigMethodCallTypeCalculator.kt:123` —
  `place.originalFile.virtualFile.toPsiFile(project)` — `virtualFile`
  nullable.
* **BUG** `TenantConfigMethodCallTypeCalculator.kt:221-237` (via
  `PsiUtils.withCache`) — cache dependency is `listOf(this)`, doesn't
  invalidate when the referenced declaration's type changes.
* **BUG** `TenantConfigMethodCallTypeCalculator.kt:133-135` — race on
  `TENANT_CONFIG_FIELD_PATH` user-data mutation (read-transform-write
  on shared `MutableList`).
* **BUG** `TenantConfigMethodCallTypeCalculator.kt:127-130` — synthetic
  field name `"'${field.name}'"` doesn't escape internal quote
  characters.

#### Issues / Improvements

* **ISSUE** Multiple files — no `DumbAware` and no `IndexNotReadyException`
  handling.
* **ISSUE** `CommonsCompletionContributor.kt:23-55` — runs on every
  Groovy keystroke in every project (no `isSupportProject` guard).
* **ISSUE** `InArgsNonCodeMembersContributor.kt:52` — `AnnotatedMembersSearch.search`
  per resolve, no caching.
* **ISSUE** `LepContextNonCodeMembersContributor.kt:57-62` — `candidates[0]`
  drops same-name `LepContext` from other modules.
* **ISSUE** `LepNavigationContributor.kt:67-68` —
  `FindSymbolParameters(pattern, name, parameters.searchScope)` drops
  `idFilter`.
* **ISSUE** `LepNavigationContributor.kt:31-32` — per-name
  `getTenantDomains(name)` lookup; pull tenant enumeration out of the
  per-name loop.
* **ISSUE** `TenantConfigMethodCallTypeCalculator.kt:179-199` —
  `getFields` not wrapped in `IndexNotReadyException` catch.
* **IMPROVEMENT** Hoist `getLepFolder` / `getVariants` into
  `utils/LepUtils.kt`; deduplicate `TENANT_CONFIG` constant.
* **IMPROVEMENT** `LepContextTypeCalculator.kt:23` — singleton
  `DefaultMethodReferenceTypeCalculator` (companion object).
* **NIT** `InArgsNonCodeMembersContributor.kt:18` —
  `UNIQ_ID = UUID.randomUUID()` dead.

---

### 5.B — Project view, tree, xmentityspec

Files: `extensions/ConfigIconProvider.kt`, `TreeProvider.kt`,
`extensions/xmentityspec/XmEntityIconLineMarkerProvider.kt`,
`XmEntitySpecColorLineMarkerProvider.kt`,
`XmEntitySpecSchemaExclusion.kt`, `XmEntitySpecUtils.kt`.

#### Bugs

* **BUG** `ConfigIconProvider.kt:54-66` — for every tenant folder the
  provider does `File(webappPath).exists()`, `lastModified()`,
  `publicSettingsFile.readLines()` synchronously on EDT (called per
  project-tree repaint).
* **BUG** `ConfigIconProvider.kt:40, 60-75` — `iconCache` keyed only by
  tenant name; application-scoped; collides across projects, retains
  Project on close.
* **BUG** `ConfigIconProvider.kt:112-116, 71-76, 159-188` — HTTP-fetch
  race writes `PACKAGE_ICON` placeholder under the same mtime key as the
  later async result; if placeholder wins, the real icon is never
  served.
* **BUG** `ConfigIconProvider.kt:110-111` — `bufferedImage.scaleToIcon()`
  NPE when `ImageIO.read` returns `null` (WebP, ICO).
* **BUG** `ConfigIconProvider.kt:139-143` — `svgDocument!!.size()` on
  unparseable SVG.
* **BUG** `ConfigIconProvider.kt:190-204` — hardcoded `16` ignores
  HiDPI / font scale; use `JBUI.scale(16)`.
* **BUG** `XmEntityIconLineMarkerProvider.kt:48-54` — global
  `GraphicsEnvironment.registerFont(...)` triggered as object-init side
  effect on a daemon-thread line-marker call. AWT-affine; can race with
  AWT init in headless verifier runs.
* **BUG** `XmEntitySpecColorLineMarkerProvider.kt:42` — `Color.decode`
  rejects `#fff` short-form; pre-expand `#rgb`.
* **BUG** `XmEntitySpecUtils.kt:28-30` — missing
  `putUserData(IS_ENTITY_SPEC, true); return true` for the directory-
  children case. Memoization only happens for literal `xmentityspec.yml`
  (line 34) → most entity-spec leaves redo VFS lookup on every line
  marker / color check.
* **BUG** `XmEntitySpecUtils.kt:14-34` — `IS_ENTITY_SPEC` never
  invalidated on file move.
* **BUG** `XmEntitySpecUtils.kt:17` — NPE on light/synthetic PSI
  (`containingFile?.virtualFile`).
* **BUG** `XmEntitySpecUtils.kt:42-58` — `LAST_ENTITY_SPEC` written
  racily.
* **BUG** `XmEntitySpecUtils.kt:86, 90` — `VfsUtil.findFile(...,
  refreshIfNeeded=true)` blocks; sync VFS refresh forbidden off-EDT.
* **BUG** `XmEntitySpecUtils.kt:66` — unchecked cast to `Collection<String>`.

#### Issues / Improvements

* **ISSUE** `ConfigIconProvider.kt:66, 93-94` — YAML parsed line-by-line
  via `startsWith`; indented or quoted keys fail silently.
* **ISSUE** `ConfigIconProvider.kt:71-76` — on parse failure cache stores
  `PACKAGE_ICON` but returns `null`.
* **ISSUE** `TreeProvider.kt:19` — `String.equals` for path compare;
  use `FileUtil.pathsEqual`.
* **ISSUE** `TreeProvider.kt:24-34` — direct mutation of live
  `PresentationData.locationString` (subclass `PsiDirectoryNode`
  instead).
* **ISSUE** `XmEntitySpecColorLineMarkerProvider.kt:15` — class
  `XmEntitySpecElementColorProvider` in file
  `XmEntitySpecColorLineMarkerProvider.kt` (file/class mismatch).
* **ISSUE** `XmEntitySpecUtils.kt:101-104` — `getEntityRootFolder()`
  uses `project.basePath`, not config-root.
* **ISSUE** `XmEntitySpecUtils.kt:70-76` — `joinEntitySpecInfo` ignores
  `tenantName` parameter.
* **NIT** Typos: `handeImage` (ConfigIconProvider), `getElementArCursor`
  (ActionGroup).

---

### 5.C — YAML DSL core engine

Files: `yaml/InspectionUtils.kt`, `ReadConfigFileStartupActivity.kt`,
`XmePluginSpec.kt`, `XmePluginSpecMetaInfoService.kt`,
`XmePluginSpecService.kt`, `YamlAsyncFileListener.kt`,
`YamlContextHelper.kt`, `YamlPsiUtils.kt`, `YamlUtils.kt`,
`java/yaml/YamlContext.java`, `java/utils/YamlNode.java`,
`java/utils/AntPathMatcher.java`.

#### Bugs

* **BUG** `YamlUtils.kt:19-22` — `Yaml().loadAs(...)` SnakeYAML 1.x —
  RCE-class gadget (CVE-2022-1471). Switch to Jackson YAML.
* **BUG** `YamlUtils.kt:13-17` — `readYaml` uses default `LoadSettings`,
  unlimited aliases / recursion.
* **BUG** `YamlUtils.kt:71-75` — `saveItem` keys `itemToFiles` by value
  equality; duplicate scalars collapse to one origin.
* **BUG** `YamlAsyncFileListener.kt:17-22` — filter excludes
  `VFileContentChangeEvent`; editing `xme-plugin/*.yml` silently does
  not refresh the engine.
* **BUG** `YamlAsyncFileListener.kt:62-69` — `getProject` returns only
  the first matching project; wrong in multi-project setup.
* **BUG** `ReadConfigFileStartupActivity.kt:21-25` — `?: return` inside
  `forEach` aborts the whole startup activity on the first missing
  embedded resource; downstream specs never load.
* **BUG** `XmePluginSpec.kt:25-28` — `Specification.matchPath` uses
  `substringAfter(basePath)`; in microservice symlink layouts paths
  aren't under `basePath` so the whole path is returned.
* **BUG** `XmePluginSpec.kt:9-11` — `specifications: MutableList`
  publicly mutable; `joinSpec` adds spec lists in place.
* **BUG** `XmePluginSpecMetaInfoService.kt:63, 73` — cache key joins
  `it.name` (not `it.path`) and iterates an unsorted Set; cache key
  thrashes.
* **BUG** `XmePluginSpecMetaInfoService.kt:73` — `getFilesState` key
  drops `specKey` (unlike `getYamlNode` on line 63).
* **BUG** `XmePluginSpecMetaInfoService.kt:77-86` — on parse failure
  silently resurrects stale `LAST_SPEC_STATE` user-data.
* **BUG** `XmePluginSpecMetaInfoService.kt:91-97` — `itemToFiles` keyed
  by yaml value equality; duplicate scalars from different files
  collapse.
* **BUG** `XmePluginSpecService.kt:42-44` — inner `MutableSet<PsiFile>` not
  concurrent; `toList()` snapshot + `removeIf` → `ConcurrentModificationException`.
* **BUG** `XmePluginSpecService.kt:42-44, 120` — caches store `PsiFile`
  references that become invalid on reparse.
* **BUG** `XmePluginSpecService.kt:56-77` — `reload()` runs daemon
  restart synchronously before nested `fileAdded` `doAsync` jobs
  complete.
* **BUG** `XmePluginSpecService.kt:98-110, 147-157` — `loadAllFiles`
  walks the OS filesystem, not VFS; bypasses excluded folders; no
  `ProgressManager.checkCanceled`.
* **BUG** `XmePluginSpecService.kt:112-128, 131-139` — no `project.isDisposed`
  guard before scheduled work.
* **BUG** `YamlContext.java:18-19` — no-arg constructor leaves
  `helper = null`; every delegating method NPEs.
* **BUG** `YamlContext.java:87-89` — `isFileExists` uses `java.io.File.exists()`.
* **BUG** `YamlNode.java:33-42` — `equals`/`hashCode` recurse through
  `parent`; O(depth), order-dependent, contract-broken.
* **BUG** `YamlNode.java:11` — `HashMap` not thread-safe (built on pool,
  read by many).
* **BUG** `YamlNode.java:48` — `toString` recurses up the tree and
  labels `parent` as `children`.
* **BUG** `YamlPsiUtils.kt:141-152` — `[key=value]` predicate parser
  breaks on escapes / multiple conditions / missing `=`.
* **BUG** `YamlPsiUtils.kt:178-237` — `splitByDot` doesn't handle `\'` /
  `\"` escapes.
* **BUG** `InspectionUtils.kt:79` — function typo `macthPattern`.
* **BUG** `InspectionUtils.kt:1` — no `package` declaration (default
  package).
* **BUG** `InspectionUtils.kt:44-51` — `runJsScriptWithResult` logs
  via `thisLogger().error` AND rethrows; user gets two error notifications.

#### Issues / Improvements

* **ISSUE** Process-wide `parsePattern` cache never evicted.
* **ISSUE** `XmePluginSpecService.kt:46, 63` — `filecache.clear()` cross-
  project punishment.
* **ISSUE** `YamlAsyncFileListener.kt:15-27` — `prepareChange` doesn't
  call `ProgressManager.checkCanceled()`; doesn't filter disposed
  projects.
* **ISSUE** `YamlContext.java:11-14, 32-55` — public mutable fields +
  public setters; JS spec can desync state from captured `helper`.
* **ISSUE** `YamlNode.java:9-11` — public mutable fields exposed to JS.
* **ISSUE** `AntPathMatcher.java:17` — Apache Commons Collections 3.x
  used only for `CollectionUtils.isEmpty`; drop the dep.
* **ISSUE** `AntPathMatcher.java:16` — JSR-305 `javax.annotation.Nullable`;
  use JetBrains' `@Nullable`.
* **IMPROVEMENT** `GraalJsRunner.kt:30, 92-95` — `dispose()` walks
  `executionContexts.values` but that map is never written to; the per-
  thread `Context`s in the `ThreadLocal` never get closed → native-memory
  leak.
* **IMPROVEMENT** `GraalJsRunner.kt:71, 76` — `allowAllImplementations(true)`
  set twice.
* **IMPROVEMENT** No tests cover the whole pipeline.

---

### 5.D — YAML inspection / completion / refs / injection

Files: `yaml/exts/YamlCompletionContributor.kt`,
`YamlJsonSchemaContributor.kt`, `YamlLanguageInjector.kt`,
`YamlReferenceContributor.kt`, `YamlSpecLocalInspection.kt`,
`yaml/actions/ActionGroup.kt`.

#### Bugs

* **BUG** `YamlCompletionContributor.kt:94` — unconditional
  `result.stopHere()` kills built-in YAML completion in every config-
  project file.
* **BUG** `YamlCompletionContributor.kt:81` — NPE risk on null items
  from `variantsExpression` JS.
* **BUG** `YamlReferenceContributor.kt:40-47` — pattern registered as
  `psiElement<PsiElement>()` (any element); `accepts` iterates
  specs × references × every PSI element per highlight pass.
* **BUG** `YamlReferenceContributor.kt:107, 127` — `getReferencesByElement`
  evaluates JS templates on every call.
* **BUG** `YamlReferenceContributor.kt:62-68, 115, 135` — `refSource`
  may be a `YAMLKeyValue` → `TextRange` covers key+value.
* **BUG** `YamlLanguageInjector.kt:40-45` — `invokeLater {
  TemporaryPlacesRegistry.addHostWithUndo(...) }` is a write op inside
  `getInjection`, which is documented as read-only.
* **BUG** `YamlLanguageInjector.kt:34` — `BaseInjection("comment")`
  placeholder support id; should be `"yaml"`.
* **BUG** `YamlJsonSchemaContributor.kt:26, 51` — synchronous HTTP
  download on EDT.
* **BUG** `YamlJsonSchemaContributor.kt:27, 52` — `getSchemaFile()`
  returns `null` after `/tmp` cleanup (silent validation loss).
* **BUG** `YamlSpecLocalInspection.kt` × `plugin.xml:60-66` —
  `shortName="XmEntitySpecValidation"` has no matching
  `inspectionDescriptions/XmEntitySpecValidation.html`.
* **BUG** `YamlSpecLocalInspection.kt:50-54` — checks
  `element.references` on `YAMLScalar`, but
  `YamlReferenceContributor` registers references on `YAMLKeyValue`.
* **BUG** `YamlSpecLocalInspection.kt:108-114` — `LocalQuickFix`
  captures `YamlContext` (with hard PSI reference) across edits.
* **BUG** `YamlSpecLocalInspection.kt:111-113` — quick fix's JS
  side-effects bypass `WriteCommandAction` (no undo grouping).
* **BUG** `ActionGroup.kt:40, 61-92` — captured `YamlContext` becomes
  stale after PSI reparse before user clicks.
* **BUG** `ActionGroup.kt:119-123` + `plugin.xml:81-90` — separators in
  every editor popup in every IDE project (not gated by
  `isSupportProject`).

#### Issues / Improvements

* **ISSUE** All these contributors are not `DumbAware`.
* **ISSUE** `YamlCompletionContributor.kt:87-88` — `prefix.lowercase()`
  defeats CamelHumpMatcher.
* **ISSUE** `YamlReferenceContributor.kt:118-119, 138-139` —
  `PsiReferenceImpl` is `isSoft=true`; inspection looks for unresolved
  soft refs, but Goto Declaration goes nowhere — pick one mechanism.
* **ISSUE** `YamlSpecLocalInspection.kt:36, 45` — `getSpecifications`
  called twice; cache.
* **ISSUE** `YamlSpecLocalInspection.kt:13-18` — null severity →
  `ERROR`.
* **ISSUE** `YamlJsonSchemaContributor.kt:54-62` — `isAvailable`
  re-evaluates pattern + injection host per call; cache.
* **ISSUE** `ActionGroup.kt:99-110` vs `InspectionUtils.kt:66-77` —
  `runCondition` duplicated with different defaults.

---

### 5.E — Services & deploy actions

Files: `services/TenantConfigService.kt`,
`services/XmeMsConfigRestService.kt`,
`services/XmeProjectStateService.kt`,
`services/settings/SettingService.kt`,
`actions/DeployCurrentFile.kt`, `actions/DeployEnvSelector.kt`,
`actions/DeployToEnv.kt`, `actions/settings/MainSettingAction.kt`.

#### Bugs

* **BUG** `SettingService.kt:48-90` — plaintext credentials in
  `PersistentStateComponent`. Use `PasswordSafe`.
* **BUG** `SettingService.kt:14-23` — service *is* the state object; no
  migration hook; `xmlSerializerUtil` reads `xmUrl` getter that does
  `field.trim('/')`.
* **BUG** `SettingService.kt:16` — `MutableList<EnvironmentSettings>` mutated
  on EDT, read on BGT (action update) without synchronization.
* **BUG** `XmeMsConfigRestService.kt:85, 98, 174` — new `OkHttpClient()`
  per request; dispatcher / connection-pool leak; same in
  `utils/OkHttpClient.kt:6, 22, 38`.
* **BUG** `XmeMsConfigRestService.kt:116, 143` — Apache `HttpClients.createDefault()`
  per request, second HTTP stack.
* **BUG** `XmeMsConfigRestService.kt:67-76` — `getToken` race; two
  concurrent callers both fetch.
* **BUG** `XmeMsConfigRestService.kt:78-95` — error messages from token
  endpoint may surface response bodies / credentials.
* **BUG** `XmeMsConfigRestService.kt:51` — `version` parameter not
  URL-encoded.
* **BUG** `XmeMsConfigRestService.kt:97-108` — `refresh()` ignores
  response status code.
* **BUG** `XmeMsConfigRestService.kt:185-212` — state mutation (`lastChangedFiles`)
  from BGT racing the persistence layer; mutation happens before deploy
  success is known.
* **BUG** `XmeMsConfigRestService.kt:126, 145` — URLs built from
  `getSettings().selected()?.xmUrl` while `env` is already provided;
  if user switches env mid-deploy, requests land on the wrong host.
* **BUG** `TenantConfigService.kt:74-88, 107-129` — VFS + PSI access
  without explicit `ReadAction.compute`.
* **BUG** `TenantConfigService.kt:12, 85` — uses internal
  `com.intellij.openapi.fileEditor.impl.LoadTextUtil`.
* **BUG** `TenantConfigService.kt:66, 152` — class-name `counter` leaks
  across recomputes, polluting cache keys.
* **BUG** `XmeProjectStateService.kt:13-18` — constructor eagerly does
  `toVirtualFile()` / `toPsiFile()` on whatever thread instantiates.
* **BUG** `XmeProjectStateService.kt` — `getConfigRootDir()` returns
  `"null/config/..."` when no env selected at init time; never re-resolved.
* **BUG** `XmeProjectStateService.kt:31, 70` — `aliasFile.refresh(true, false)`
  asynchronous; next-line `inputStream` sees stale bytes.
* **BUG** `XmeProjectStateService.kt:33, 72` — `aliasFile.inputStream`
  not closed on Jackson failure path.
* **BUG** `DeployCurrentFile.kt:31-52` — nested
  `doAsync { invokeLater { dialog ; doAsync { network } } }`; no
  progress, uncancellable.
* **BUG** `DeployEnvSelector.kt:19-30` — `update()` mutates per-instance
  `envs` list and calls `JComponent.updateUI()` on BGT.
* **BUG** `DeployEnvSelector.kt:17, 41-45` — `component: ComboBoxButton?`
  retained as strong field on the singleton AnAction.
* **BUG** `DeployToEnv.kt:68` — compares `selected.name != NULL_ENV.name`;
  user-named "No env" disables the action.
* **BUG** `MainSettingAction.kt:29-30` — `envs.clear() / addAll(...)` on
  EDT while BGT `update()` iterates — `CMEx`.
* **BUG** `HttpUtils.kt:48-69` — JVM-wide TLS-trust-all (see Security S1).
* **BUG** `HttpUtils.kt:11-20` — `/tmp/<sha>.json` cache; non-Windows;
  hash uses base64 + Java `hashCode` (collisions).
* **BUG** `HttpUtils.kt:30-37` — recursive redirect without depth cap.
* **BUG** `ThreadUtils.kt:17-28` — `doAsync` catches `Exception` (not
  `Throwable`); calls `showError(project, e)` without `isDisposed` guard.

#### Issues / Improvements

* **ISSUE** Three HTTP stacks (OkHttp, Apache HttpClient, JDK
  `HttpURLConnection`); consolidate on OkHttp.
* **ISSUE** No `Disposable` on services holding pooled / native
  resources.
* **ISSUE** `TenantConfigService.withCache` keyed on
  `VFS_STRUCTURE_MODIFICATIONS` (file create/delete), not
  `PsiModificationTracker.MODIFICATION_COUNT` → stale autocompletes
  after edits.
* **ISSUE** `DeployToEnv.kt:54` — `runWriteAction { saveAllDocuments() }`
  not needed; `DeployCurrentFile` doesn't wrap — pick one.
* **ISSUE** `MainSettingAction.kt:36` — `project.save()` fires whether
  dialog was OK'd or cancelled.

---

### 5.F — Webview / Tool window / ViewServer

Files: `ViewServer.kt`, `MessagesBundle.kt`,
`toolWindow/XmePluginToolWindowFactory.kt`,
`webview/SettingsDialog.kt`, `webview/WebDialog.kt`,
`webview/WebFileListDialog.kt`.

#### Bugs

* **BUG** `ViewServer.kt` — `Server(serverPort)` default connector binds
  `0.0.0.0`.
* **BUG** `ViewServer.kt` — `serverPort` reserved at class-load time
  (TOCTOU re-bind race); bind to port 0 instead and read
  `connector.localPort`.
* **BUG** `ViewServer.kt` — XSS via `out.print(index.replace("${pipeId}", request.queryString))`.
* **BUG** `ViewServer.kt` — `print(String)` on `ServletOutputStream`
  defaults to ISO-8859-1 despite `text/html;charset=UTF-8` header.
* **BUG** `ViewServer.kt` — `dirAllowed=true`.
* **BUG** `ViewServer.kt` — `embeddedServer.dump(System.err)` in
  production.
* **BUG** `ViewServer.kt` — `object` with `startServer()` but no
  `stopServer()` / `Disposable` registration; blocks dynamic plugin
  unload.
* **BUG** `MessagesBundle.kt` — `DynamicBundle(BUNDLE)` single-arg
  ctor; should be `DynamicBundle(MessagesBundle::class.java, BUNDLE)`.
* **BUG** `XmePluginToolWindowFactory.kt` — `shouldBeAvailable` invoked
  before `postStartupActivity`; first open may hide tool window until
  restart.
* **BUG** `XmePluginToolWindowFactory.kt` — button listener captures
  `ToolWindow` (→ `Project`), soft leak + `AlreadyDisposedException`.
* **BUG** `WebDialog.kt:17` — stray internal import from
  `com.jetbrains.performancePlugin.commands` (plugin not in
  `<depends>`).
* **BUG** `WebDialog.kt` — no `JBCefApp.isSupported()` guard.
* **BUG** `WebDialog.kt` — `CefApp.registerSchemeHandlerFactory`
  registrations never unregistered; leak per dialog open.
* **BUG** `WebDialog.kt` — `Disposer.register` after `JBCefBrowser` and
  `JBCefJSQuery` are constructed; intermediate exception leaks native
  CEF.
* **BUG** `WebDialog.kt` — `JBCefJSQuery.inject("data")` passes the JS
  value raw to `cefQuery.request` (a String); silently `"[object Object]"`.
* **BUG** `WebDialog.kt` — duplicate-named `JBCefJSQuery` silently
  overwrites the prior handler.
* **BUG** `SettingsDialog.kt` — `componentReady` rebuilds `this.data`
  from settings on every browser-ready event; races with `envsUpdated`,
  rolls back unsaved edits.
* **BUG** `SettingsDialog.kt` — `connectionResult` does not surface the
  error message to the Angular side.
* **BUG** `WebFileListDialog.kt` — `ignoredFiles` captured from
  `selected()?.ignoredFiles ?: HashSet()`; if `selected()` is null at
  capture, mutations vanish.
* **BUG** `WebFileListDialog.kt` — keyed by `virtualFile.path` but read
  by raw `path`; Windows path separators mismatch.
* **BUG** `WebFileListDialog.kt` — `virtualFile.inputStream` read off
  the read-action lock.
* **BUG** `WebFileListDialog.kt` — trim/whitespace inconsistency
  between `equals(content)` (L126) and `sha256Hex(config.trim())`
  (L152).

#### Issues / Improvements

* **ISSUE** `ViewServer.kt` — no auth / Host / Origin validation.
* **ISSUE** `WebDialog.kt` — `ViewServer.startServer()` in `init {}` on
  EDT.
* **ISSUE** `WebDialog.kt` — `uiThreadAnchor` is a visible `JLabel(".")`
  at the bottom of every web dialog.
* **NIT** `WebDialog.kt` — `FocusHandlerStub` dead code; missing CSP.

---

### 5.G — Utilities

Files: `utils/Constants.kt`, `FileUtils.kt`, `FontIcon.kt`,
`GeneralUtils.kt`, `GitUtils.kt`, `GraalJsRunner.kt`, `HttpUtils.kt`,
`LepUtils.kt`, `MapUtils.kt`, `OkHttpClient.kt`, `ProjectUtils.kt`,
`PsiUtils.kt`, `SocketUtils.kt`, `ThreadUtils.kt`.

#### Bugs

* **BUG** `FileUtils.kt:1`, `GitUtils.kt:1`, `OkHttpClient.kt:1`,
  `InspectionUtils.kt:1` — no `package` declaration.
* **BUG** `FileUtils.kt:36-55` — `createProjectFile` writes via
  `java.io.File` from `doAsync`; no `WriteAction`; VFS sees stale; JGit
  staged before refresh.
* **BUG** `FileUtils.kt:64` — `VfsUtil.findFileByIoFile(File, true)`
  synchronous refresh.
* **BUG** `FileUtils.kt:71-81` — `deleteSymlink` deletes parent
  directories blindly; potential silent data loss.
* **BUG** `FileUtils.kt:72` — `walkTopDown()` follows symlinks; cyclic
  Windows junctions spin.
* **BUG** `FileUtils.kt:167-198` — `keyCache` does `Key.create` per
  unique path string; session-long memory leak.
* **BUG** `FileUtils.kt:25-30, 168-200` — VFS / userdata access without
  read action.
* **BUG** `FileUtils.kt:103-118` — recursive `toAbsolutePath`/`toRelatedPath`
  unbounded on symlink loop.
* **BUG** `FileUtils.kt:202-246` — `@Synchronized` on a `Project`
  extension locks `Project` monitor for heavy IO + 2 VFS refreshes.
* **BUG** `FontIcon.kt:19-39` — not HiDPI-aware.
* **BUG** `FontIcon.kt:15` — default `iconColor = Color.BLACK` invisible
  on Darcula.
* **BUG** `GitUtils.kt:147-153` — `FileRepositoryBuilder` per call,
  competes with `GitRepositoryManager` for locks/FDs.
* **BUG** `GitUtils.kt:131-143` — `TreeWalk` not closed.
* **BUG** `GitUtils.kt:155-161` — `repository()?.use { Git(it).use {} }`
  — `Git.close()` closes its repository; double-close.
* **BUG** `GitUtils.kt:125` — `toRelatedPath(path).substring(1)` assumes
  leading `/`; Windows `C:` → `:Users\…`.
* **BUG** `GitUtils.kt:85-105` — `calculateChangedFilesState` reads
  whole files into `ByteArray`, duplicates 3× into streams.
* **BUG** `GraalJsRunner.kt:30, 92-95` — `executionContexts` never
  populated; thread-local Contexts never closed (see C.7).
* **BUG** `GraalJsRunner.kt:46-52` — script-Context vs current-thread-
  Context can desync.
* **BUG** `GraalJsRunner.kt:127-128` — `copyToJavaLand` throws from a
  "return Any?" function.
* **BUG** `GraalJsRunner.kt:77-83` — open `allowHostClassLookup`
  (Security S2).
* **BUG** `HttpUtils.kt:48-69` — JVM-wide TLS trust-all (Security S1).
* **BUG** `HttpUtils.kt:14-18` — hardcoded `/tmp` path; broken on
  Windows.
* **BUG** `HttpUtils.kt:24-42` — no connect/read timeouts.
* **BUG** `HttpUtils.kt:31-37` — unbounded redirect recursion.
* **BUG** `HttpUtils.kt:38` — error stream not consumed on failure.
* **BUG** `LepUtils.kt:20` — `translateToLepConvention` uses regex
  replace with `\\$` as literal; works by accident.
* **BUG** `OkHttpClient.kt:6, 22, 38` — new `OkHttpClient` per call.
* **BUG** `OkHttpClient.kt:16-18, 32-34` — non-2xx returned as success.
* **BUG** `OkHttpClient.kt` — `httpGetResponse` returns a `Response`
  callers must close; easy to leak.
* **BUG** `ProjectUtils.kt:30` — `isConfigProject` race window
  (re-read userdata after put).
* **BUG** `ProjectUtils.kt:94-95` — `gradleProperties.inputStream` not
  closed.
* **BUG** `ProjectUtils.kt:114-115` — `loadBootStrapYml` uses
  `VfsUtil.findFile(..., refresh=true)` from `AnAction.update`.
* **BUG** `ProjectUtils.kt:104` — `ProjectConfig(null)` cached forever
  after first lookup before bootstrap exists.
* **BUG** `ProjectUtils.kt:42, 52` — `root()` builds `"null/config"` when
  `selected?.basePath` is null.
* **BUG** `PsiUtils.kt:25, 28, 45` — `cacheKeys` map keyed on
  `(call-site + per-element uuid)` with `Key.create` per combination —
  unbounded leak.
* **BUG** `PsiUtils.kt:41` — `withCache` dependency `listOf(this)`; cache
  goes stale on related-file edits.
* **BUG** `PsiUtils.kt:54, 66` — `withMultipleFilesCache`:
  `it.virtualFile.path` NPE risk.
* **BUG** `PsiUtils.kt:11, 107-115` — `originalFile` piggybacks on
  platform-owned `PsiFileFactory.ORIGINAL_FILE` Key.
* **BUG** `PsiUtils.kt:121` — `containerFile()!!` NPE in injected files.
* **BUG** `PsiUtils.kt:73-82` — `getCountSubstring` off-by-one (returns
  1 for input without `$$`).
* **BUG** `SocketUtils.kt` — re-implements Spring 5.x `SocketUtils`;
  TOCTOU.
* **BUG** `SocketUtils.kt:21, 34` — checks `localhost` only; misses
  `0.0.0.0` collisions.
* **BUG** `ThreadUtils.kt:10-15` — `doPseudoAsync` is effectively
  synchronous, unused — dead code.
* **BUG** `ThreadUtils.kt:17` — no `project.isDisposed` guard.

#### Issues / Improvements

* **ISSUE** `Project.basePath` used 20+ places; replace with
  `ProjectUtil.guessProjectDir`.
* **ISSUE** `MapUtils.kt` reinvents stdlib helpers; unsafe `as` casts
  proliferate at call sites.
* **ISSUE** `FontIcon.kt:16` — eager `renderIcon()` in constructor
  (~2 MB on startup).
* **IMPROVEMENT** Replace bespoke `doAsync` / `doPseudoAsync` with
  per-service `CoroutineScope`.
* **IMPROVEMENT** `LepUtils.kt:31-34` — `lepCreationTip` repeats
  `VfsUtil.findFile` over 6 variants per element; wrap in `withCache`.

---

### 5.H — plugin.xml / build / resources / domain

Files: `META-INF/plugin.xml`, `build.gradle.kts`, `gradle.properties`,
`gradle/libs.versions.toml`, `messages/MessagesBundle.properties`,
`domain/ChangesFiles.kt`, `domain/FilesState.kt`, `specs/*.json`,
`inspectionDescriptions/*.html`.

#### Bugs

* **BUG** `plugin.xml:60-66` vs `inspectionDescriptions/` — `shortName`
  has no matching description HTML.
* **BUG** `plugin.xml:81` — `<group id="editorPopupMenu">` collides
  case-insensitively with platform `EditorPopupMenu`.
* **BUG** `plugin.xml:94-96 / 113-115` — `add-to-group relative-to-action`
  references actions declared later in the same file.
* **BUG** `plugin.xml` — three actions registered twice
  (`*OldUi` + `*NewUi`) with the same class; produces two `AnAction`
  instances per class.
* **BUG** `build.gradle.kts:15, 216-235` — `shadowJar` is wrong for an
  IntelliJ Platform plugin; produces a fat jar with platform-duplicated
  Jackson / Commons / SnakeYAML / Kotlin stdlib.
* **BUG** `build.gradle.kts:80` — `snakeyaml-engine:2.7` duplicates
  platform-bundled snakeyaml.
* **BUG** `build.gradle.kts:66-67` — `jackson 2.13.1` vs platform-
  bundled ~2.17; classloader hazard.
* **BUG** `domain/ChangesFiles.kt:17` — `updatedFileContent:
  MutableMap<String, InputStream>` inside a `data class`; broken
  `equals`/`hashCode`, single-read streams, `refresh()` silently mutates
  a `val`.
* **BUG** `domain/ChangesFiles.kt:5` — top-level import
  `configPathToRealPath` (default package).

#### Issues / Improvements

* **ISSUE** `plugin.xml:11` — unused `<depends>com.intellij.modules.vcs</depends>`.
* **ISSUE** `plugin.xml:7` vs `gradle.properties:9` — `<idea-version since-build="242"/>`
  duplicates Gradle-managed value.
* **ISSUE** `gradle.properties:17` — `com.intellij.modules.json` not in
  `platformBundledPlugins`.
* **ISSUE** `build.gradle.kts:68-69, 74, 75-77` — Apache Commons,
  servlet 3.0.1, Jetty 9.x; all aged.
* **ISSUE** `plugin.xml:132-135` — `JsonSchema.ProviderFactory` under
  `defaultExtensionNs="JavaScript"` pulls in JS-language module; move
  to `com.intellij`.
* **ISSUE** `gradle.properties:23-25` — `configuration-cache=false` /
  `caching=false` need an inline comment with root cause; can be flipped
  once `shadowJar` is removed.
* **ISSUE** `gradle/libs.versions.toml` — `intelliJPlatform = "2.1.0"`
  behind current 2.11.x.
* **ISSUE** `inspectionDescriptions/xmentityschema.json` — uses draft-04
  `id` instead of `$id`; no `$schema`.
* **IMPROVEMENT** Most action labels are hardcoded in `plugin.xml`; use
  `action.<id>.text` bundle keys (resource bundle is declared but
  unused for actions).
* **IMPROVEMENT** Several classes-per-file naming mismatches make
  navigation harder (e.g. `LepChooseByNameContributor` in
  `LepNavigationContributor.kt`; `XmEntitySpecElementColorProvider` in
  `XmEntitySpecColorLineMarkerProvider.kt`).
* **IMPROVEMENT** `domain/FilesState.kt:4-8` — `MutableSet` props in a
  `data class`; `files` getter allocates per access.
* **NIT** `build.gradle.kts:2` — unused `groovy.xml.dom.DOMCategory.attributes`.
* **NIT** `plugin.xml:56` — Cyrillic comment "хз для чогось треба"
  (= "dunno, needed for something"); replace with the actual reason.

---

*End of audit. Total findings: ≈230, across 8 subsystems. The fastest
unblocker is the [top-priority fix order](#2-top-priority-fix-order) at
the top. The per-subsystem reports under `/tmp/xme-review/` contain the
full prose of every finding (rationale, "Why it matters", "Suggested
fix").*
