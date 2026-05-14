# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`XME.digital` — an IntelliJ IDEA plugin (`com.icthh.xm.xmeplugin`) that integrates the IDE with XM^online configuration projects and microservices. Its main jobs:

- Push configuration / LEP (Logic Extension Point) changes to a running XM environment in-memory (no redeploy).
- Provide Groovy completion / type inference / navigation for `lepContext`, `lepContext.inArgs`, `lepContext.commons`, and `tenantConfig` inside LEP scripts.
- Run YAML-spec-driven inspections, references, autocompletes, injections, and quickfixes (XmEntity spec + a user-extensible DSL).

## Build / run

```bash
./gradlew clean shadowJar      # full release build (README's canonical command)
./gradlew buildPlugin          # IntelliJ Platform plugin zip
./gradlew runIde               # launch a sandbox IDE with the plugin loaded
./gradlew runIdeForUiTests     # sandbox IDE wired for the robot-server UI test plugin
./gradlew verifyPlugin         # IntelliJ plugin verifier (uses `recommended()` IDEs)
./gradlew check                # also produces Kover XML coverage (onCheck = true)
./gradlew patchChangelog publishPlugin   # release flow; needs CERTIFICATE_CHAIN / PRIVATE_KEY[_PASSWORD] / PUBLISH_TOKEN env vars
```

There is no `src/test` directory — the project currently ships no JVM tests, so "running tests" / "running a single test" doesn't apply until tests are added under `src/test/{kotlin,java}`.

### Embedded Angular webapp

`src/main/webapp/` is an Angular 18 app served inside the plugin's tool window via `ViewServer.kt` (embedded Jetty). It is wired into the JVM build:

- `processResources` depends on `buildAngular`, which depends on `installAngular`.
- Node is downloaded by the `node` Gradle plugin (v18.19.0) — do **not** rely on system Node.
- Build artifacts from `src/main/webapp/dist` are added as a resource source set, so they ship inside the plugin jar.
- For frontend-only iteration: `cd src/main/webapp && npm start` (Angular dev server).

## High-level architecture

### Language stack

Mixed Kotlin + Java on JVM 17 (`kotlin { jvmToolchain(17) }`). New code is Kotlin by default; the small `src/main/java` tree exists because a few classes (`YamlContext`, `YamlNode`, `AntPathMatcher`) are exposed as the public surface JS expressions execute against — see "YAML DSL engine" below.

### IntelliJ extension surface

Wired in `src/main/resources/META-INF/plugin.xml`. The plugin attaches to platform extension points rather than running a long-lived background process; almost every feature is an `extensions.<ep>` registration pointing at a class in one of:

- `extensions/` — Groovy-language contributors that power LEP autocompletes:
  - `LepContextNonCodeMembersContributor`, `LepContextTypeCalculator`, `InArgsNonCodeMembersContributor`
  - `CommonsCompletionContributor`, `CommonsNonCodeMembersContributor`, `CommonsRefactoringElementListenerProvider`
  - `TenantConfigGotoDeclarationHandler`, `TenantConfigMethodCallTypeCalculator`, `HideTenantConfigFieldsCompletionContributor`
  - `LepChooseByNameContributor` / `LepNavigationContributor` — translate `typeKey`/`key` ↔ LEP file names (`.` → `$`, `-` → `_`) so both forms resolve in Goto File.
  - `ConfigIconProvider`, `TreeProvider` — tenant-folder icons (from tenant `logoUrl`/favicon) and domain/parent-tenant decorations in the project tree.
- `extensions/xmentityspec/` — XmEntity spec specifics: color/icon providers and a JSON-schema exclusion.
- `yaml/exts/` — YAML inspection, reference contributor, completion contributor, language injection contributor.
- `actions/` — `DeployCurrentFile`, `DeployToEnv`, `DeployEnvSelector` (drive the in-memory deploy flow).
- `actions/settings/` and `services/settings/` — per-project / per-env configuration UI + persisted state.
- `toolWindow/` — `XmePluginToolWindowFactory` plus the Angular tool window content rendered by `webview/`.

### YAML DSL engine (the custom-specification system)

The headline differentiator described in README under "Custom yaml specification support without programing". Users drop YAML files into an `xme-plugin/` folder of their config project, and those files declaratively add inspections / references / autocompletes / injections / actions to other YAML files. The implementation lives in `kotlin/.../yaml/` and `java/.../yaml/`:

- `ReadConfigFileStartupActivity` — post-startup activity that discovers `xme-plugin/*.yml` files.
- `YamlAsyncFileListener` (vfs.asyncListener) — keeps each spec's matched-file list current as files are added / moved / deleted.
- `XmePluginSpec`, `XmePluginSpecService`, `XmePluginSpecMetaInfoService` — parsed-spec model + project-scoped caches.
- `YamlPsiUtils`, `YamlUtils`, `YamlContextHelper`, `InspectionUtils` — PSI navigation, "psi-path" DSL evaluation (`specifications[].inspections[key='x'].elementPath`), template/expression rendering.
- `YamlSpecLocalInspection`, `YamlReferenceContributor`, `YamlCompletionContributor`, `YamlLanguageInjectionContributor` — apply the spec to files the user is editing.

JavaScript expressions in user specs (`condition`, `action`, `variantsExpression`, `errorMessageTemplate`, `actionMessageTemplate`, `includeFunctions`, …) are evaluated by **GraalVM JS** (`org.graalvm.js:js:22.3.2`). They run against the `YamlContext` API in `java/.../yaml/YamlContext.java` — fields: `psiElement`, `fullSpec`, `project`, `yamlNode` (which is a `YamlNode` from `java/.../utils/YamlNode.java`). README §"Source code" explicitly invites contributors to extend `YamlContext` when expressions need new helpers — treat this class as a stable public API surface; renames / removals are breaking changes for downstream specs. JSON schemas live in `src/main/resources/specs/` (`xme-plugin-spec-schema.json`, `xmentityschema.json`, `scheduler-tasks-schema.json`).

### Services / runtime state

- `services/XmeProjectStateService.kt` — per-project plugin state (env list, selected env, linked LEP folder, etc.).
- `services/XmeMsConfigRestService.kt` — talks to a running XM microservice config endpoint (uses OkHttp; pushes file diffs / single files for in-memory updates).
- `services/TenantConfigService.kt` — reads/caches `tenant-config.yml` for autocomplete and type inference.
- `webview/ViewServer.kt` — embedded Jetty server backing the Angular tool window UI.

### Platform target

`gradle.properties`: `platformType=IC` (IntelliJ Community), `platformVersion=2024.2.5`, `pluginSinceBuild=242`. Bundled-plugin deps: `org.intellij.intelliLang`, `org.intellij.groovy`, `org.jetbrains.plugins.yaml`, `com.intellij.java` — anything you touch in the extension classes can rely on these being present.

Gradle build flags worth knowing: `org.gradle.configuration-cache = false` and `org.gradle.caching = false` — do not flip these without checking; the IntelliJ Platform Gradle Plugin + the Angular task graph have historically not played well with config cache here.

## Conventions to follow when editing

- New IntelliJ extensions go in Kotlin under `kotlin/com/icthh/xm/xmeplugin/...` and must also be registered in `plugin.xml`.
- User-facing strings live in `src/main/resources/messages/MessagesBundle.properties` (declared as the plugin's `resource-bundle`).
- When adding a YAML-spec feature, update both the JSON schema in `src/main/resources/specs/` and the docs block in `README.md` (the `<!-- Plugin description -->` section is also injected into the plugin manifest at build time — keep it valid).
- Don't add methods to `YamlContext` / `YamlNode` casually; they are the JS-expression API consumed by external config projects.
