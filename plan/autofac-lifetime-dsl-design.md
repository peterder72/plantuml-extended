# Autofac Lifetime Composition DSL — Design Specification

**Status:** Generic redesign release preparation · **Revision:** 2.0
**Diagram type:** `@startautofac` / `@endautofac`  
**Targets:** JVM SVG/PNG, browser TeaVM SVG, headless TeaVM SVG.

## Contract

This is a diagram engine, not an Autofac solver. Every lifecycle transition, object identity, graph edge, operation name and outcome must be authored. The engine checks identity, ancestry, chronology and ownership references; it never infers Autofac behavior from registrations, service names, sharing policies or delegate bodies. There are no outcome keywords: `success`, `failure`, `reused`, `recursion`, `cycle`, `path`, `chain`, `confidence` and similar words are ordinary label text.

The README example is `autofac-examples/01-minimal-lifetimes.puml`. Fifteen additional showcases cover sharing, keys, modules, decorators, factory failures, explicit owned scopes, deferred calls, dependency relationships, independent ends, disposal failure, external objects and rejected construction.

## Structural declarations

Statements are one per line. `;` also separates declarations on one line. A declaration or registration may use an alias:

```plantuml
@startautofac
title Recursive factory

module RequestModule {
    register orders: IOrders -> Orders sharing perScope
    decorate IOrders with Logging
}

lifetime "System" as system {
    register risky: IFoo via delegate "ctx => request.Resolve<IFoo>()" sharing single
}
create system

object "module1 : RequestModule" as module1 ownedBy system
create module1

lifetime "Request" as request from system tag "request" {
    install module1 as RequestModule
}
create request
@endautofac
```

- `lifetime ID [from PARENT] [tag "tag"] [ownedBy OBJECT] { ... }` declares a scope and its construction. A declaration alone does not begin the active lifetime; `create` does.
- `module ID { ... }` is a reusable definition, not an executed instance.
- `object "LABEL" as ID [ownedBy LIFETIME]` declares one explicit object identity. `ownedBy` is disposal ownership, never creation context: an `instance` registration may reference an object created before its owner's frame starts.
- `register ID: SERVICE [key "k" | name "n"] (-> TYPE | instance OBJECT | via delegate LABEL) [sharing VALUE] [ownership VALUE]` adds construction configuration. `sharing` and `ownership` are authored metadata; they do not create instances, choose registrations or imply disposal.
- `decorate SERVICE with TYPE` preserves declaration order.
- `install OBJECT as MODULE` expands the definition's registrations into the installing lifetime. Registrations receive installation-local identities such as `request.module1.orders`; the definition and the real source object remain distinguishable.
- `create TARGET` / `destroy TARGET` are the only lifecycle transitions for lifetimes, objects and ordinary participants. Creation is explicit and destruction never cascades to children or owned objects.

Construction blocks contain only `register`, `decorate` and (in a lifetime) `install`. Empty construction needs no braces.

## Occurrences and relationships

Local operations are marker events; calls between lifetimes use ordinary PlantUML arrows.

```plantuml
event pending on request : Resolve IFoo
request -> system : Invoke risky
loop while the factory re-enters
    system -> request : Resolve IFoo
    request -> system : Invoke risky
end
```

- `event [ID] on SCOPE [marker circle|cross|diamond|square] [<<stereotype>>] : LABEL` attaches a point to a lifetime. The label is arbitrary Creole text; the marker shape is presentation only. Omitting `ID` creates an internal identity. A configuration event on a never-created lifetime does not manufacture an active track.
- A message immediately following an event in the same lifetime starts at that event, not at a detached point later on the track. An explicit event alias also anchors a message from anywhere.
- Normal, reversed, self, dotted/return and border arrows use native PlantUML syntax. `A -> B : Invoke`, `B --> A : result`, `A -> A : Retry`, `A ->] : Publish` and `[-> A : Notify` are supported. Arrowheads, line styles, thickness, colors and inclinations come from the native arrow configuration.
- Direction is never changed to suit layout. `A --> B : returns value` carries a response from A to B regardless of their vertical positions; `A ..> B : references` points from the referring element to the referenced one. Labels must agree with the endpoints.
- `activate`/`deactivate` show an explicit activity span. Activation is independent of object ownership.
- `loop`, `opt`, `alt`/`else`, `group`, `critical`, `break` and `par` use native grouping syntax. Source order applies within a branch; `par` adds no order between sibling branches, and `alt` draws mutually exclusive alternatives. Conditions are labels, not evaluated expressions. Parallel-group prefixes are rejected rather than silently ignored.
- Notes (`note`, `hnote`, `rnote`, `note over`, `note across`, and notes on messages) appear only where authored. They are never generated from internal properties.
- `FROM ..> TO : LABEL` is a non-temporal static reference between existing entities, events or registration rows; `<..` reverses it. Use it for authored selection (`resolve ..> system.clock`), object property relationships, decorator wrapping or a declared sharing boundary. Its label is optional and it creates no event. Simple colors, stereotypes, participant glyph types and native note shapes are supported; unsupported native extras (URLs, curly anchors, merged/parallel notes, parallel-group prefixes, compound theme colors) produce a located error.

Autofac declaration aliases match `[A-Za-z_][A-Za-z0-9_-]*`; native participants follow the native sequence identifier grammar. Display labels are separate from aliases. Aliases are global to a diagram except installation-local registration identities. Unknown or duplicate aliases, unknown parents, cyclic ancestry, references to undeclared entities, use before declaration, object creation after its own destruction, duplicate terminal destruction and destruction of a never-created lifetime are errors reported at the offending source line.

## Visual contract

The primary drawing remains a **lifetime diagram** with horizontal logical chronology, not a vertical call sequence and not a dependency graph.

- Each lifetime is a named outer frame. If the lifetime was created, its **Construction** compartment is followed by an active track; a lifetime configured but never created shows construction only and no active track. Construction is compact when empty.
- Scope start is the construction boundary itself. Lifetime destruction ends the track with a terminal cap; generic failure markers remain distinct.
- Child frames nest inside their parent. If a parent ends before its child, both ends remain truthful: independent lifetime intervals are drawn in aligned ancestry lanes rather than stretching the parent.
- Each explicitly declared object has one rail labelled with its identity. The rail begins at its authored creation and closes at its authored destruction; otherwise the end stays open. A label/leader states disposal ownership, and an object created before its owner appears outside the frame with an explicit ownership leader rather than false containment.
- Marker events are points on their lifetime's track with measured body-font labels and authored Creole formatting. Message arrows connect real track columns and carry the authored native arrowhead; labels avoid frame borders and phase headings.
- Static references terminate on their actual entities or construction rows. Lookups use a local parent/child gutter or a construction-edge column, not a global right-hand gutter that looks like a later event. Opposite object references receive distinct measured channels.
- Groups draw real frames/branches. Module provenance connects the actual installed object to the installation row. Selected registrations, wrap order and object relationships are ordinary visible links.
- There is no automatic diagnostics shelf, property card, confidence text, "unspecified" filler or generated failure story. A note appears only if the author wrote one.

Styles use `<style> autofacDiagram { ... } </style>` inheritance. Lifetime, construction, heading, muted and module roles remain; generic native roles supply circles, arrows, references, objects, lifelines, activations, groups, notes and participant glyphs. Text labels use the same resolved fonts for measurement and drawing. Standalone SVGs paint the configured background; transparent backgrounds remain transparent.

## Validation and error positions

The binder validates structure only:

- duplicate aliases, unknown/ambiguous lifetimes, objects, participants, events and registrations;
- ancestry cycles, references before declaration, configuration/creation ordering;
- installation object and module prerequisites;
- explicit terminal destruction ordering and duplicate destruction;
- group/construction closure;
- unsupported native presentation syntax.

Invalid usage after an explicit destruction, or a child still open when its parent ends, remains drawable and appears through the common warning banner. It does not manufacture a terminal event or cascade.

Errors use PlantUML's standard error diagram. Parsing is incremental; validation runs once when the final significant source line is consumed. The reported position is the offending declaration/reference line, including with trailing blank or comment lines.

## Example map

| Example | Demonstrated behavior |
|---|---|
| `01-minimal-lifetimes` | root/child construction, a request, an explicit returned object and disposal |
| `02-sharing-and-child-overrides` | local override, root-owned clock returned to two requests |
| `03-keyed-services` | keyed/named identities and arbitrary operation labels |
| `04-module-provenance` | module object resolved in System, installed into Request |
| `05-decorator-chain` | declaration order and explicit wrapping/return chain |
| `06-delegate-activation-failure` | factory participant and nested dependency failure |
| `07-owned-worker` | explicitly declared owned child lifetime |
| `08-deferred-factories` | separate acquisition and invocation occurrences |
| `09-constructor-cycle` | two constructor stages with real endpoints, no completed objects |
| `10-supported-property-cycle` | opposite property references between two shared objects |
| `11-recursive-cross-scope-call` | call anchored at the resolve marker, bounded re-entry loop |
| `12-parallel-requests` | unordered sibling branches and independent objects |
| `13-independent-disposal` | parent ends, child continues |
| `14-disposal-failure` | failed cleanup is a marker, not destruction |
| `15-external-and-empty` | externally owned object and an explicit empty collection |
| `16-matching-and-scope-creation` | missing tag and a rejected configuration with no active lifetime |

## Non-goals

No container execution, C# evaluation, delegate interpretation, automatic instance identity, automatic disclosure of sharing/ownership/activation scopes, implicit `Owned<T>` scopes, inferred failure categories, imported traces, pooled leases, elapsed-time scaling, focus/collapse projections, or dependency-graph-first layout. Ordinary lookup and explicit cross-scope calls remain different authored constructs.

## Implementation

Source: `src/main/java/net/sourceforge/plantuml/autofac/`. `AutofacDiagram` subclasses the native `SequenceDiagram`, reuses its arrow/group/note/participant/activity commands and replaces only the text block with the shared measured `UGraphic` renderer. Lifetime, construction, object, marker-event and static-reference declarations are parsed by the positioned Autofac parser; native sequence commands supply messages and groups. `AutofacValidator` binds identities and chronology into the immutable `AutofacModel`; `AutofacLayout` measures and routes; `AutofacTextBlock` draws. The same code serves JVM and both TeaVM builds. Autofac parsing, layout and drawing have no Graphviz dependency or AWT-specific code.

New source remains Java 8-compatible; the repository's current OpenPDF dependency requires JDK 21 to build. Native syntax keywords must remain disjoint from `src/main/resources/net/sourceforge/plantuml/utils/words.txt`; the obfuscation collision guard must stay enabled.

After changing `src/main/resources/skin/plantuml.skin`, refresh TeaVM's embedded skin:

```sh
java -cp build/classes/java/main net.sourceforge.plantuml.teavm.EmbeddedResourcesGenerator
./gradlew test --tests 'net.sourceforge.plantuml.autofac.*' --tests 'test.vega.VegaTest' \
  jar teavm :plantuml-mcp-js:generateJavaScript -Pfast
```

Browser and headless TeaVM are separate compilations; both require runtime verification. Browser logical dimensions retain the engine's 8192-unit ceiling.

### Release status

The generic redesign is being prepared for npm `@peterder72/plantuml-core@1.2026.7-autofac.2` on dist-tag `autofac` and VS Code prerelease `0.9.0`. Publication is pending; these target versions are not a claim of availability. The earlier npm `1.2026.7-autofac.1` and VS Code prerelease `0.8.0` contain the previous, incompatible DSL and must not be used for these examples. Do not republish the immutable npm version. The redesign's JVM and both compiled TeaVM runtime checks are recorded in [implementation learnings](autofac-implementation-learnings.md); fresh smoke checks of the final release artifacts are still required.

Use the existing Nix environment for builds; do not install a separate toolchain or paste store hashes into commands. Release metadata stamping remains explicit, source must be available on `feature/autofac` in both repositories, and npm publication must use dist-tag `autofac` without moving `latest` or `beta`. Do not create a Git release tag: this fork's CI has broad tag-creation triggers. Vite consumers must preserve generated TeaVM code with `build.minify: false`; re-minification has produced invalid JavaScript. The 16 editable showcase `.puml` files are source-controlled; generated `autofac-examples/*.svg` previews remain local and ignored. Native Vega SVG regression references and the historical `plan/autofac-lifetime-example.svg` visual reference remain tracked.

## Autofac background for authors

1. **Visibility:** a child can use local and ancestor registrations; ordinary lookup does not descend from parent to child.
2. **Override:** local registrations can replace inherited defaults; collection requests, ordering and `PreserveExistingDefaults` affect selection. The diagram displays the author's declared selection, not a computed one.
3. **Ownership context:** a shared component is activated using its owning scope, so child overrides are not automatically injected into root-owned singleton dependencies.
4. **Sharing:** `transient`, `perScope`, `matching(tag)` and `single` are descriptive metadata; identity reuse is shown only by pointing several results at the same declared object.
5. **Disposal:** a scope tracks disposable components it creates; externally owned objects are excluded. Parent destruction does not automatically destroy children.
6. **Dynamic sources and relationships:** registration sources, `Func<T>`, `Lazy<T>`, `Owned<T>` and collection behavior are authored as explicit objects, occurrences and links.
7. **Failures:** missing key, disposed scope, activation exception and recursive service location are different authored stories. They are distinguished by labels, markers, notes and actual arrows, not by an engine taxonomy.

References: [scope hierarchy](https://docs.autofac.org/en/latest/lifetime/index.html), [instance sharing](https://docs.autofac.org/en/latest/lifetime/instance-scope.html), [keyed services](https://docs.autofac.org/en/latest/advanced/keyed-services.html), [decorators](https://docs.autofac.org/en/latest/advanced/adapters-decorators.html), [relationship types](https://docs.autofac.org/en/latest/resolve/relationships.html), [owned instances](https://docs.autofac.org/en/latest/advanced/owned-instances.html), [circular dependencies](https://docs.autofac.org/en/latest/advanced/circular-dependencies.html), [delegate factories](https://docs.autofac.org/en/latest/advanced/delegate-factories.html).
