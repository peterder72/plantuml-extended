# Autofac implementation learnings

These notes record implementation decisions and observed results for the native Autofac diagram. They are engineering guidance, not a replacement for the [language contract](autofac-lifetime-dsl-design.md).

## 1. The diagram is an authored document, not a solver

The user's requirement is explicit: PlantUML is a **diagram engine**, not an Autofac executor.

- Registrations, sharing policies, decorators and delegate bodies are descriptive configuration. They do not create objects, choose registrations, predict failures or imply execution.
- Lifetimes, objects, events, messages, creation/destruction, ownership and relationships are authored. Identity is never manufactured from an operation alias.
- A factory body is an opaque label. Only an explicit message shows that the factory was invoked.
- A marker's shape, label and color are presentation. There is no closed outcome enum and no engine taxonomy that changes lifecycle semantics.
- The binder checks only structural facts: aliases, ancestry, declaration order, installation prerequisites, explicit chronology and destruction ordering.

An earlier implementation used `expect ... { ... }` facts (`selected`, `instance`, `state`, `category`, `path`, `cycle`, `chain`, `confidence`). Most of them became bottom-panel text; `cycle` even produced a floating arc not connected to any real element, and template calls were drawn as runtime trajectories. That design was removed rather than repaired.

## 2. Generic language principles

- **A marker is a real occurrence.** `event [alias] on SCOPE marker SHAPE : LABEL` is a point on that scope's track. A message immediately following it in the same scope starts at the marker, not at a later detached point. An explicit alias can anchor later messages.
- **Messages are native arrows.** `->`, `-->`, `<--`, self, dotted, external and multicasts use the native sequence command/model and retain the native `ArrowConfiguration`. Direction must not be flipped to make drawing convenient. A return points at its caller regardless of vertical position; a static reference points from the referring element to the referenced element.
- **Static references are not events.** `A ..> B : label` connects existing entities, event occurrences or registration rows without adding chronology. Use it for selections, property relationships, decorator wrapping, provenance or sharing boundaries.
- **Objects are explicit identities.** One `object` declaration is one rail. Several results/references point to the same rail. `ownedBy` means disposal ownership, not creation context; an object can exist before its owner frame starts.
- **Creation and destruction are explicit.** A lifetime declaration configures construction; `create` starts activity. `destroy` never cascades. A failed disposal is an ordinary labelled event with no `destroy`. A rejected construction is construction plus an event/note and no active track.
- **Do not display internal IDs.** Generated `$af-*` identities exist only for parsing/drawing. Labels come from the authored label.

## 3. Native sequence reuse and its seams

`AutofacDiagram` subclasses `SequenceDiagram`, which is public, non-final and exposes an overridable `getTextBlock`.

- Native commands require a `SequenceDiagram` receiver; the generic command bridge casts to it. Passing them to a `TitledDiagram` cannot work.
- A protected constructor accepting `DiagramType` is needed because `TitledDiagram.getDiagramType()` is final. The existing public `SequenceDiagram` constructor remains unchanged.
- A narrow protected event-append seam is used for custom marker events. Do not widen this into a general abstraction over sequence internals.
- `autonumber`, `newpage`, `autoactivate`, `hide unlinked`, footbox and similar bookkeeping commands are deliberately not registered. Only forms with a real Autofac rendering interpretation are accepted.
- Native `create` in a sequence has pending-reception semantics. Autofac's explicit `create`/`destroy` must bypass that protocol or a lifetime cannot be created without an unrelated following message.
- Native `return` targets its activation context. When the caller is an event alias, normalize the generated return endpoint back to the owning scope so the return does not attach to the old resolve marker.
- Native stereotyping is propagated through narrow `getStereotype()` accessors rather than reparsing source text. Participant labels already contain stereotype display text; do not prepend it twice.
- Unsupported native extras must fail with a located error, not be silently accepted: URLs, curly anchors, merged/parallel notes, parallel-group prefixes and compound theme colors.

## 4. Parsing and binding pitfalls

### Incremental parsing, not full rescans

Structural declarations are parsed line by line by a positioned parser. Native sequence commands handle messages, groups, notes, activations and participants. Do not make a catch-all multiline command: nested braces and `}` inside quoted labels break it.

Construction rows are accumulated in a simple mutable list and frozen once when `}` is reached. Earlier code cloned the complete child list and rescanned the statement list for every registration, which was quadratic.

Type normalization uses a precompiled generic-spacing pattern. Do not allocate a regex per type token.

### Finalize once, at the last significant line

Validation must run once, after the last significant original body line. Waiting for `checkFinalError` gives the end directive as the position and repeats work. The parser tracks whether a construction block or group is still open and reports that opener on failure.

For a located binder error, return `CommandExecutionResult.newDiagram(PSystemErrorUtils.buildV2(...))` with a trace ending at the offending `StringLocated`. Trailing blank lines and trailing comments must not move the error to EOF.

### Alias and reference rules

Aliases are global. Installed registrations use installation-local identities (`request.module1.foo`). An event alias used as a message endpoint must not become a visible lifetime; it is a command adapter only. The custom renderer draws model entities, not native participants invented for parsing.

## 5. Rendering is measured geometry, not decoration

The renderer uses `UGraphic`/`StringBounder` and no Graphviz or AWT. It must measure text before placing any obstacle.

Observed failure modes in earlier renderings:

- A boolean "cycle" flag drew a floating arc with no endpoints. Never synthesize a relationship glyph from metadata.
- Delegate template calls terminated on a runtime track, implying an execution that was never authored.
- Static references and temporal calls detoured to a global right-hand gutter, so a lookup looked like a later event and inflated diagrams to thousands of pixels. Route locally between real ports: event → parent/child gutter → construction row, or event → object rail, with measured separate channels for opposite references.
- Every object/participant rail extended to the global horizon, stretching frames and creating empty space. End rails at their last authored use, explicit destruction, or a small open continuation.
- Message labels sat exactly on a child frame border. Frame borders and phase headings are obstacles.
- Event labels inherited the marker glyph's bold font. Marker labels use body text; phase headings use heading style.
- Lifetime creation was drawn as an unexplained crosshair, and destruction as a bare cross. Start the active track at the construction boundary; give lifetime destruction a terminal cap distinct from a failure/object cross.
- Notes were placed without reserving rail/label space and covered arrows. Notes are measured obstacles.
- A construction card undercounted its row heights and clipped the last registration. Card height must include titles, all rows, metadata and padding.
- Group frames occupied rows but did not enclose the child lifetime envelopes they logically surrounded. Their borders must remain readable and must not overwrite scope frames.
- `Label` must measure Creole using the same resolved font it draws; otherwise line wrapping and collision checks disagree with the output.

Object ownership and sharing are distinct: place the object in its owner only when containment is truthful. A pre-owner object uses an explicit ownership leader. A sharing boundary is a reference to the sharing scope, not an ownership move.

## 6. TeaVM and styles

`StyleLoader` in TeaVM uses the checked-in `src/main/java/net/sourceforge/plantuml/teavm/EmbeddedResources.java`, not the JVM resource file. After editing `src/main/resources/skin/plantuml.skin`, regenerate:

```sh
java -cp build/classes/java/main net.sourceforge.plantuml.teavm.EmbeddedResourcesGenerator
```

Then build both JavaScript outputs:

```sh
./gradlew teavm :plantuml-mcp-js:generateJavaScript -Pfast
```

`TextBlock.getBackcolor()` returns the skin background so dark-mode canvases are correct. Do not hardcode a second palette in Java.

Use `UGroupType.DATA_UID` for stable SVG IDs. `UGroupType.ID` is ignored by the backend.

## 7. Vocabulary and obfuscation

Native Autofac keyword names are registered in `syntax/LanguageDescriptor` so help output and `--obfuscate` preserve them. `Obfuscate.addException` throws if a reserved word also exists in `src/main/resources/net/sourceforge/plantuml/utils/words.txt`.

Adding `confidence` previously broke five full-suite tests for exactly this reason. When adding generic words such as `from`, `name` or `cross`, remove those entries from `words.txt`; do not weaken the collision guard. Run `LanguageDescriptorTest` and the full suite after vocabulary changes.

## 8. Verification is multi-runtime

These are separate checks:

1. JVM parsing/export, including actual PNG/SVG inspection.
2. Compiled headless TeaVM under Node.
3. Compiled browser TeaVM in a real browser.

Calling the TeaVM dispatcher on the JVM is not JavaScript runtime proof. A nonempty SVG is not proof: an error diagram is also valid SVG.

Useful checks:

- expected diagram type, no error diagram, visible labels, real primitives;
- endpoint geometry: a marker-attached call starts at the marker; returns point toward the caller; reference direction matches the source;
- semantic groups/IDs and source lines;
- JVM vs headless TeaVM identity/text consistency for the same input;
- actual browser screenshots in light and dark themes;
- repeated Autofac renders interleaved with other diagram types;
- representative non-Autofac diagrams (class, sequence, state, activity, component, object, deployment, usecase, gantt, mindmap, wbs, timing, json, yaml, ebnf, regex, nwdiag, packet, chart).

Do not compare browser pixel coordinates directly with deterministic headless coordinates.

## 9. Environment

Use the existing Nix environment (`nix shell nixpkgs#zulu21 nixpkgs#graphviz --command ...`). Do not paste `/nix/store` paths into commands or environment variables; derive them inside the shell.

`-Pfast` does not disable an explicitly requested `test` task. Offline test execution may lack the JaCoCo agent; a throwaway init script can disable coverage for that run without changing the repository.

## 10. Recorded results

Observed for the generic cutover (2026-10-27):

| Check | Observed result |
|---|---|
| Full JVM suite | 3,058 passed, 10 skipped, 0 failures; coverage disabled for the offline run |
| Autofac unit/geometry tests | 22 passed; canonical JUnit XML confirms 6 parser, 8 validator and 8 geometry cases |
| Vega fixtures | 205 passed, 4 skipped; four Autofac SVG references regenerated after visual inspection |
| Autofac JVM exports | 25 SVG/PNG pairs rendered without error diagrams; all 16 showcases inspected |
| Headless TeaVM renders | 46 passed, including interleaved/repeated rendering and a located invalid-reference diagnostic; Autofac SVG IDs and text match JVM exports |
| Browser TeaVM renders | 44 light and 25 dark; all 25 Autofac cases match visible semantic IDs and normalized text, with no labels outside their SVG viewboxes |
| Representative non-Autofac JVM exports | 26 SVG/PNG pairs, including six actual Nix Graphviz layouts |
| Java 8 compatibility | Autofac sources and modified native sequence/style/vocabulary seams compile with `javac --release 8` |

Browser parity ignores empty lifecycle groups omitted by the JVM SVG writer, normalizes nonbreaking spaces and trailing whitespace on Creole text fragments, and does not compare backend-dependent pixel coordinates. Actual light/dark Autofac screenshots and all 19 other browser diagram surfaces were inspected.

The browser packet fixture still clips its leftmost `0` tick, an issue observed before this redesign. Packet rendering was not changed; a successful export is not a claim that this existing visual defect is fixed.

The earlier iterative implementation's results are deliberately not carried forward; its language and renderer no longer exist.

## 11. Generic redesign prerelease

Published npm `@peterder72/plantuml-core@1.2026.7-autofac.2` on dist-tag `autofac`, and VS Code prerelease `0.9.0`, from `feature/autofac` in both repositories. Engine source commit: `9214f9eff1f9396c5b3c4958853431ba2c273378`; viewer release commit: `9205fc4cd2c35da6509e73a9a42ce09cd4c066d4`. Npm `latest` and `beta` remain `1.2026.6-limit8192.1`; the VS Code stable release remains `0.7.0`. No Git release tags were created.

All 16 showcase sources use the generic lifetime/object/event/native-message language. Keep their editable `autofac-examples/*.puml` sources in Git and generated SVG previews local and ignored. This policy does not remove the native Vega SVG regression goldens or the historical `plan/autofac-lifetime-example.svg` visual reference.

Final stamped release artifacts passed the complete JVM suite (3,058 passed, 10 skipped), 25 JVM SVG/PNG cases, 45 headless TeaVM renders, and 44 light/25 dark packaged-browser renders. The first 25 headless Autofac cases ran without Viz loaded. Java 8 source compatibility passed. The engine installed from npm has the same SHA-256 as the verified package build.

The viewer passed typecheck, lint and all 161 unit tests. Its actual offline `file://` artifact rendered the checked-in Autofac sample with no HTTP requests. A cached real VS Code runtime validated all 16 engine showcases plus the viewer sample, exported SVG and PNG, and reported the malformed-source error on line 4. [CI run 40598543888](https://github.com/peterder72/plantuml-live-editor/actions/runs/40598543888) additionally passed browser and VS Code integration/scenario gates and published the tested VSIX without rebuilding it. The downloaded artifact's checksum and `Microsoft.VisualStudio.Code.PreRelease=true` manifest property were verified. Stable extension publishing, GitHub Pages deployment and GitHub release creation were skipped.
