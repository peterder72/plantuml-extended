# Autofac implementation learnings

These notes capture decisions and observed results from implementing and verifying the native Autofac diagram. They are implementation guidance, not a replacement for the [language contract](autofac-lifetime-dsl-design.md). Verification results below describe the runs performed during this work; they are not a claim about subsequent changes.

## 1. Keep the diagram engine separate from Autofac execution

The most important requirement was clarified directly by the user: PlantUML is a **diagram engine, not a solver engine**.

- Registrations, sharing policies, decorators and dependency descriptions are descriptive configuration.
- Selection, activation context, sharing scope, ownership, object identity, new/reused state and runtime paths are independent authored facts.
- A resolve without an expectation is an **unspecified request**, not an inferred success or failure.
- Do not manufacture an object identity from an event alias or predict an outcome from an opaque delegate label.
- `confidence derived` means the author derived the fact; it does not authorize engine inference.
- Delegate-template calls describe possible calls. Only named runtime calls create actual event nodes.
- A supported property cycle can be explicitly successful. A recursive resolve is not automatically a guaranteed stack overflow.

Structural validation is still useful: unknown or ambiguous identities, duplicate declarations, impossible chronology, causal reuse, installation prerequisites and duplicate terminal disposal are language errors. Invalid lifetime use can remain drawable with a structural warning.

## 2. Preserve distinctions in both model and drawing

Scope, registration, resolve event and returned object are different identities. Likewise, request origin, selected registration location, activation scope, sharing scope and disposal owner must not be collapsed into one scope field.

Practical consequences:

- Installed registrations need installation-local qualified identities, such as `request.module1.foo`, while retaining their module-definition provenance.
- Display decorator declaration order separately from an authored outer-to-inner instance chain.
- Parent disposal must not synthesize child disposal. If containment would falsify independent ends, use aligned ancestry lanes rather than stretching the parent envelope.
- A failed disposal attempt does not provide a terminal end. Leave subsequent lifecycle unspecified until another explicit terminal disposal.
- Bare `Owned<T>` does not create a diagram scope. An explicitly bound Owned child shares its acquisition chronology node; do not insert an extra artificial time step.
- `parallel` removes sibling-order edges. Stable placement order is not causal order, and cannot justify object reuse.
- Objects introduced by outcome facts must not also be labeled as **pre-existing** objects. That contradictory duplicate presentation was caught during visual inspection and removed.

## 3. Native diagram integration has more than one dispatcher

The shared implementation is in `src/main/java/net/sourceforge/plantuml/autofac/`.

Native integration required:

- `DiagramType.AUTOFAC`, start-directive recognition and its style-name mapping.
- Factory registration in **both** `PSystemBuilder` and `teavm/PSystemBuilder2`.
- Embedded-diagram recognition in `EmbeddedDiagram`.
- Directive and vocabulary registration in `syntax/LanguageDescriptor`.

The browser and headless TeaVM paths both depend on `PSystemBuilder2`. Registering only the JVM factory can leave a feature working in Java but unsupported in JavaScript.

Keep parser/model/layout state per diagram. Factories and dispatchers can be reused across unrelated diagram types; mixed-type rendering is an important state-leakage smoke check.

## 4. Parsing and diagnostic pitfalls

### Incremental lines, not repeated block rescans

Common PlantUML commands are registered before the Autofac catchall line command. The catchall tokenizes each line once; the retained positioned tokens are parsed and bound when the composition closes.

Avoid using a generic multiline command that terminates at the first `}`: delegate, dependency, lifetime and composition braces nest. Also avoid rescanning all accumulated source for every line, which can become quadratic.

PlantUML preprocessing can hide backslashes using a Jaws private character. The lexer must restore that representation before interpreting DSL string escapes.

### Located errors need the right trace

Returning a replacement error diagram through `CommandExecutionResult.newDiagram(...)` permits the standard error surface without changing shared error infrastructure.

The trace passed to `PSystemErrorUtils.buildV2(...)` must be nonempty and end at the offending original `StringLocated`. Passing the entire composition through its closing brace can report or highlight the wrong line.

### EOF errors have a different location contract

The common factory handles finalization errors differently from normal parser errors:

- Missing composition and unclosed composition must remain distinct diagnostics.
- An unclosed composition message includes its actual opener line.
- Leading comments are not the composition opener. Recording the first nonempty line caused a real bug and now has a regression test.
- EOF-only structured error positions still follow the common factory. Do not promise that this structured position equals the opener line named in the message.

## 5. TeaVM styles have a generated-resource step

Editing `src/main/resources/skin/plantuml.skin` alone is insufficient for TeaVM. `StyleLoader` uses the checked-in `teavm/EmbeddedResources.java` resource there instead of JVM resource loading.

After compiling the existing generator, refresh that resource from the repository root:

```sh
java -cp build/classes/java/main net.sourceforge.plantuml.teavm.EmbeddedResourcesGenerator
```

Then rebuild **both** JavaScript outputs:

```sh
./gradlew teavm :plantuml-mcp-js:generateJavaScript -Pfast
```

The missing embedded-skin refresh initially produced correct shapes but generic grayscale TeaVM colors. Browser inspection exposed it; compilation and shape/geometry checks alone did not.

Also expose the diagram's background through `TextBlock.getBackcolor()` using the skin's background color. The Autofac text block initially left white title/legend text on a transparent canvas in dark mode. Returning the normal skin background corrected the complete browser canvas without changing the shared browser renderer or hardcoding a second palette.

## 6. SVG backend conventions matter

- Use native `UGraphic` shapes and the supplied `StringBounder`; do not assemble raw SVG or depend on Graphviz for Autofac layout.
- Measure text before placing construction, events, connectors and panels. Browser Canvas metrics and deterministic headless metrics are different contexts.
- Use `UGroupType.DATA_UID` for stable SVG IDs. `UGroupType.ID` is ignored by this backend.
- Preserve source locations on semantic groups, including warnings tied to unnamed disposal events.
- Keep cycles finite: one explicit feedback arc rather than recursive expansion.
- The browser retains its existing 8192-unit pre-scale dimension ceiling. Do not silently truncate oversized diagrams.

## 7. Vocabulary registration can break unrelated features

Adding the Autofac keyword `confidence` caused five failures in the **full** JVM suite even though the narrower diagram tests had passed.

Root cause: `confidence` was also present in the bundled obfuscation replacement dictionary:

```text
src/main/resources/net/sourceforge/plantuml/utils/words.txt
```

`Obfuscate.addException(...)` deliberately rejects that collision. The correct fix was to remove the newly reserved word from the replacement dictionary, not suppress the collision guard. CLI obfuscation and rendering of its output were exercised after the fix.

When adding language vocabulary, check the obfuscation dictionary and run the full existing suite, not only the new diagram's tests.

## 8. Verification needs real compiled runtimes

The following are separate checks:

1. JVM parsing/export.
2. Compiled headless TeaVM under Node.
3. Compiled browser TeaVM in an actual browser.

Calling the TeaVM dispatcher on the JVM is not JavaScript runtime proof. A nonempty SVG is also insufficient: an error diagram is valid SVG.

Useful checks performed during this work included:

- Expected diagram type, successful export status, visible labels and actual graphical primitives.
- JVM PNG decoding, dimensions and non-background pixels.
- Source-located malformed-input diagnostics.
- Stable semantic groups, labels, primitive geometry and palette between JVM deterministic SVG and headless TeaVM where the metrics match.
- Actual browser screenshots, light/dark themes, text bounds, larger fonts, title/legend overrides and normal DOM insertion.
- Mixed Autofac/non-Autofac rendering sequences to exercise cached factory dispatch.

Do not compare browser pixel coordinates directly with deterministic headless coordinates.

### Browser harness pitfalls

- Await each browser render callback before submitting another request. The API keeps only the latest pending request; it does not queue every render.
- In the browser automation environment used here, page script globals and the execution realm used by `page.evaluate` were not always shared. Importing the engine into a realm without `Viz` caused a misleading asynchronous failure for class diagrams.
- Loading the existing `/viz-global.js` bundle in the same realm as the engine resolved the harness issue. The normal demo UI was checked separately to distinguish a harness failure from an application failure.
- For headless Node, the existing Viz UMD bundle could be reused without an npm installation. The smoke harness initialized it with Node's `vm.runInThisContext`, `createRequire` and an absolute `__filename`.

## 9. Nix and build environment observations

Use the existing Nix environment rather than installing a parallel toolchain.

- The existing JDK 17 could run the exercised JVM rendering paths, but could not compile the current OpenPDF 3.0.5 dependency: that dependency contains Java 21 bytecode.
- An existing Nix JDK 21 was used for builds. New shared Autofac sources were separately compiled with `javac --release 8` against the main classes.
- The Gradle distribution and build dependencies were already cached.
- Offline test execution lacked the JaCoCo agent artifact. A throwaway Gradle init script disabled coverage instrumentation only; tests still ran. No permanent build configuration change was made for this.
- `-Pfast` does **not** disable an explicitly requested `test` task in this fork.
- Do not run the Ant compile target merely as another check: it deletes `build/`.

Native Graphviz was subsequently provisioned through the existing pinned Nix registry, without changing a global profile:

```sh
nix shell nixpkgs#graphviz --command dot -V
```

The provisioned version was **12.2.1**. Selecting its executable explicitly through `GRAPHVIZ_DOT` removed uncertainty about which native binary PlantUML used. Nix-store paths are machine-specific; resolve the executable in the shell instead of committing a store hash.

## 10. Recorded verification results and remaining issue

Observed final results from this implementation/compatibility work:

| Check | Recorded result |
|---|---|
| Full JVM suite, including a forced rerun with native Graphviz selected | 3,079 passed; 10 skipped; 0 failed |
| Autofac authored runtime scenarios | 22 exercised on JVM and compiled headless TeaVM |
| Representative non-Autofac JVM renders | 20 diagram types, SVG and PNG passed |
| Representative non-Autofac headless TeaVM renders | 19 diagram types passed |
| Representative browser renders | 19 diagram types rendered in light and dark modes; 18 had no detected text clipping |
| Native Graphviz smoke | Class, state, component, use-case, object and deployment passed SVG and PNG |

Salt was included in the JVM matrix only. These are representative behavior checks, not proof of every syntax combination or every diagram type supported by PlantUML.

**Unresolved visual finding:** in the browser packet diagram sample, the first `0` tick began at `x = -3.89`, outside the SVG viewBox, and was visibly clipped in both themes. Packet layout was not changed. Whether that issue predates the Autofac work was not established; do not describe it as a confirmed Autofac regression or as fixed.
