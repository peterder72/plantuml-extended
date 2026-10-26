package net.sourceforge.plantuml.autofac;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sourceforge.plantuml.autofac.AutofacModel.Node;
import net.sourceforge.plantuml.autofac.AutofacModel.Outcome;
import net.sourceforge.plantuml.autofac.AutofacModel.StructuralWarning;

/** Binds explicit identities and chronology; deliberately never resolves a service. */
public final class AutofacValidator {
	private final AutofacModel model;
	private final Map<String, Node> scopes = new LinkedHashMap<String, Node>();
	private final Map<String, Node> modules = new LinkedHashMap<String, Node>();
	private final Map<String, Node> events = new LinkedHashMap<String, Node>();
	private final Map<String, Node> objects = new LinkedHashMap<String, Node>();
	private final Map<String, Node> registrations = new LinkedHashMap<String, Node>();
	private final Map<String, Node> runtime = new LinkedHashMap<String, Node>();
	private final Map<String, Set<String>> edges = new LinkedHashMap<String, Set<String>>();
	private final List<StructuralWarning> warnings = new ArrayList<StructuralWarning>();
	private final Map<String, String> owned = new LinkedHashMap<String, String>();
	private final Map<String, Integer> disposals = new LinkedHashMap<String, Integer>();
	private final Map<Node, Integer> declarationOrder = new LinkedHashMap<Node, Integer>();
	private final Map<String, String> objectSources = new LinkedHashMap<String, String>();
	private final Map<String, Integer> eventOrder = new LinkedHashMap<String, Integer>();

	private AutofacValidator(AutofacModel model) {
		this.model = model;
	}

	public static AutofacModel validate(AutofacModel model) throws AutofacParseException {
		return new AutofacValidator(model).bind();
	}

	private AutofacParseException error(String message, Node node) {
		return new AutofacParseException(message, node.source, node.offset);
	}

	private AutofacParseException factError(String message, Outcome outcome, String fact) {
		return new AutofacParseException(message,
				outcome.factSources.containsKey(fact) ? outcome.factSources.get(fact) : outcome.source,
				outcome.factOffsets.containsKey(fact) ? outcome.factOffsets.get(fact) : 0);
	}

	private void put(Map<String, Node> map, String id, Node node, String kind) throws AutofacParseException {
		if (map.containsKey(id))
			throw error("Duplicate " + kind + " '" + id + "'", node);
		map.put(id, node);
	}

	private Node copy(Node node, Map<String, String> attrs, List<Node> children) {
		return new Node(node.kind, node.id, attrs, children, node.source, node.offset);
	}

	private boolean operation(Node node) {
		return Arrays.asList("resolve", "call", "invoke", "evaluate", "dispose").contains(node.kind);
	}

	private void collect(List<Node> nodes) throws AutofacParseException {
		for (Node node : nodes) {
			declarationOrder.put(node, declarationOrder.size());
			if (node.kind.equals("lifetime"))
				put(scopes, node.id, node, "lifetime");
			else if (node.kind.equals("module"))
				put(modules, node.id, node, "module");
			else if (node.kind.equals("instance"))
				put(objects, node.id, node, "instance");
			else if (operation(node) && node.id != null) {
				put(events, node.id, node, "event");
				eventOrder.put(node.id, declarationOrder.get(node));
			}
			if (node.kind.equals("parallel"))
				collect(node.children);
		}
	}

	private void requireScope(String id, Node node) throws AutofacParseException {
		if (!scopes.containsKey(id))
			throw error("Unknown lifetime '" + id + "'", node);
	}

	private void edge(String dependent, String prerequisite) {
		if (dependent != null && prerequisite != null)
			edges.get(dependent).add(prerequisite);
	}

	private void addRuntime(Node node) throws AutofacParseException {
		put(runtime, node.id, node, "event");
		edges.put(node.id, new LinkedHashSet<String>());
	}

	/** Sibling branches share the incoming frontier, but never acquire sibling edges. */
	private Set<String> sequence(List<Node> nodes, Set<String> previous, String parallel) throws AutofacParseException {
		Set<String> last = new LinkedHashSet<String>(previous);
		for (Node node : nodes) {
			if (node.kind.equals("parallel")) {
				final Set<String> ends = new LinkedHashSet<String>();
				for (Node child : node.children)
					ends.addAll(sequence(Collections.singletonList(child), last, node.id));
				if (!ends.isEmpty())
					last = ends;
				continue;
			}
			Node event = runtimeNode(node);
			if (event == null)
				continue;
			if (parallel != null) {
				final Map<String, String> attrs = new LinkedHashMap<String, String>(event.attrs);
				attrs.put("parallel", parallel);
				event = copy(event, attrs, event.children);
			}
			addRuntime(event);
			for (String prerequisite : last)
				edge(event.id, prerequisite);
			last = new LinkedHashSet<String>();
			last.add(event.id);
		}
		return last;
	}

	private Node runtimeNode(Node node) throws AutofacParseException {
		final Map<String, String> attrs = new LinkedHashMap<String, String>(node.attrs);
		if (node.kind.equals("lifetime")) {
			if (node.get("ownedBy") != null)
				return null;
			attrs.put("scope", node.id);
			return new Node("birth", "birth-" + node.id, attrs, Collections.<Node>emptyList(), node.source, node.offset);
		}
		if (!operation(node))
			return null;
		if (node.kind.equals("dispose")) {
			final String target = node.get("target");
			final boolean scope = scopes.containsKey(target);
			if (scope && owned.containsKey(target))
				throw error("Ambiguous disposal target '" + target + "'", node);
			final String resolved = scope ? target : owned.get(target);
			if (resolved == null)
				throw error("Unknown lifetime '" + target + "'", node);
			attrs.put("scope", resolved);
			final int count = disposals.containsKey(resolved) ? disposals.get(resolved) + 1 : 1;
			disposals.put(resolved, count);
			final String identity = node.id == null ? "dispose-" + resolved + "-" + count : node.id;
			final Node result = new Node(node.kind, identity, attrs, node.children, node.source, node.offset);
			if (node.id != null)
				events.put(node.id, result);
			return result;
		}
		if (node.kind.equals("invoke") || node.kind.equals("evaluate")) {
			final String target = node.get("target");
			final boolean object = objects.containsKey(target) || objectSources.containsKey(target);
			final Node operation = events.get(target);
			if (operation != null && object)
				throw error("Ambiguous event/instance '" + target + "'", node);
			if (operation == null && !object)
				throw error("Unknown event '" + target + "'", node);
			if (operation != null && !operation.kind.equals("resolve"))
				throw error("Invalid deferred target '" + target + "'", node);
			final Node origin = operation == null ? events.get(objectSources.get(target)) : operation;
			if (origin != null) {
				if (eventOrder.get(origin.id) >= declarationOrder.get(node))
					throw error("Invalid order: " + origin.id + " must precede " + node.id, node);
				attrs.put("sourceEvent", origin.id);
				if (attrs.get("scope") == null)
					attrs.put("scope", origin.get("scope"));
			}
			if (origin == null && objects.containsKey(target)
					&& declarationOrder.get(objects.get(target)) >= declarationOrder.get(node))
				throw error("Invalid order: " + target + " must precede " + node.id, node);
			if (attrs.get("scope") == null)
				throw error("Missing origin scope for '" + node.id + "'", node);
		}
		if (owned.containsKey(node.id))
			attrs.put("ownedScope", owned.get(node.id));
		requireScope(attrs.get("scope"), node);
		final Node result = copy(node, attrs, node.children);
		events.put(node.id, result);
		return result;
	}

	private AutofacModel bind() throws AutofacParseException {
		collect(model.statements);
		validateDescriptions(model.statements);
		bindScopeTree();
		discoverObjectSources();
		sequence(model.statements, Collections.<String>emptySet(), null);
		bindPrerequisites();
		bindConstruction();
		final Map<String, Set<String>> ancestors = new LinkedHashMap<String, Set<String>>();
		final List<Node> ordered = topologicalOrder(ancestors);
		validateOutcomes(ancestors);
		for (Node node : model.statements)
			if (node.kind.equals("expand") && !events.containsKey(node.id) && !scopes.containsKey(node.id))
				throw error("Unknown event '" + node.id + "'", node);
		chronology(ordered, ancestors);
		return new AutofacModel(model.title, model.statements, model.outcomes, model.expanded, model.view,
				scopes, modules, events, objects, registrations, ordered, ancestors, warnings);
	}

	private void discoverObjectSources() {
		for (Map.Entry<String, Outcome> entry : model.outcomes.entrySet()) {
			final String object = entry.getValue().get("instance");
			final Node source = events.get(entry.getKey());
			if (object == null || source == null || objects.containsKey(object)
					|| "reused".equals(entry.getValue().get("state")))
				continue;
			final Node previous = events.get(objectSources.get(object));
			if (previous == null || declarationOrder.get(source) < declarationOrder.get(previous))
				objectSources.put(object, source.id);
		}
	}
	/** An explicitly owned scope is acquired at its relationship event, not a later event. */
	private String birth(String scope) {
		final String acquisition = scopes.get(scope).get("ownedBy");
		return acquisition == null ? "birth-" + scope : acquisition;
	}

	private void bindScopeTree() throws AutofacParseException {
		int roots = 0;
		for (Node scope : scopes.values()) {
			if (scope.get("parent") == null)
				roots++;
			else
				requireScope(scope.get("parent"), scope);
			final String alias = scope.get("ownedBy");
			if (alias == null)
				continue;
			final Node source = events.get(alias);
			if (source == null)
				throw error("Unknown event '" + alias + "'", scope);
			if (!source.kind.equals("resolve") || !source.get("service", "").startsWith("Owned<")
					|| !source.get("scope", "").equals(scope.get("parent")))
				throw error("Invalid ownedBy binding '" + alias + "'", scope);
			if (declarationOrder.get(source) >= declarationOrder.get(scope))
				throw error("Invalid order: " + alias + " must precede " + scope.id, scope);
			if (owned.containsKey(alias))
				throw error("Duplicate ownedBy '" + alias + "'", scope);
			owned.put(alias, scope.id);
		}
		if (roots != 1) {
			final Node node = model.statements.isEmpty() ? null : model.statements.get(0);
			throw new AutofacParseException("Expected exactly one root lifetime", node == null ? null : node.source,
					node == null ? 0 : node.offset);
		}
		for (Node scope : scopes.values()) {
			final Set<String> seen = new LinkedHashSet<String>();
			Node current = scope;
			while (current != null) {
				if (!seen.add(current.id))
					throw error("Cyclic lifetime reference", scope);
				current = scopes.get(current.get("parent"));
			}
		}
	}

	private void bindPrerequisites() throws AutofacParseException {
		for (Node node : runtime.values()) {
			if (node.kind.equals("birth")) {
				if (node.get("parent") != null)
					edge(node.id, birth(node.get("parent")));
			} else if (node.get("during") != null) {
				if (!events.containsKey(node.get("during")))
					throw error("Unknown event '" + node.get("during") + "'", node);
				edge(node.id, node.get("during"));
			}
			if (node.get("ownedScope") != null) {
				final Node child = scopes.get(node.get("ownedScope"));
				edge(node.id, birth(child.get("parent")));
			}
			if (node.get("sourceEvent") != null)
				edge(node.id, node.get("sourceEvent"));
		}
	}

	private void bindConstruction() throws AutofacParseException {
		for (Node scope : new ArrayList<Node>(scopes.values())) {
			final List<Node> bound = new ArrayList<Node>();
			final Set<String> installs = new LinkedHashSet<String>();
			for (Node child : scope.children) {
				if (!child.kind.equals("install")) {
					bound.add(child);
					construction(Collections.singletonList(child), scope.id);
					continue;
				}
				final String alias = child.get("target");
				final Node source = events.get(alias);
				if (source == null)
					throw error("Unknown event '" + alias + "'", child);
				if (!source.kind.equals("resolve"))
					throw error("Installation requires module resolve '" + alias + "'", child);
				if (eventOrder.get(alias) >= declarationOrder.get(scope))
					throw error("Invalid order: " + alias + " must precede " + scope.id, child);
				final Node module = modules.get(source.get("service"));
				if (module == null)
					throw error("Unknown module '" + source.get("service") + "'", child);
				if (!installs.add(alias))
					throw error("Duplicate install '" + alias + "'", child);
				final Outcome outcome = model.outcomes.get(alias);
				if (outcome != null && !outcome.kind.equals("success"))
					throw error("Failed module source '" + alias + "' cannot be installed", child);
				edge(birth(scope.id), alias);
				final Map<String, String> attrs = new LinkedHashMap<String, String>(child.attrs);
				attrs.put("resolvedModule", module.id);
				attrs.put("sourceEvent", alias);
				bound.add(copy(child, attrs, child.children));
				construction(module.children, scope.id + "." + alias);
			}
			scopes.put(scope.id, copy(scope, scope.attrs, bound));
		}
		for (Node module : modules.values())
			templates(module.children);
	}

	private List<Node> topologicalOrder(Map<String, Set<String>> ancestors) throws AutofacParseException {
		final List<Node> ordered = new ArrayList<Node>();
		final Set<String> remaining = new LinkedHashSet<String>(runtime.keySet());
		while (!remaining.isEmpty()) {
			String next = null;
			for (String id : remaining)
				if (ancestors.keySet().containsAll(edges.get(id))) {
					next = id;
					break;
				}
			if (next == null)
				throw error("Cyclic chronology reference", runtime.get(remaining.iterator().next()));
			final Set<String> predecessors = new LinkedHashSet<String>();
			for (String prerequisite : edges.get(next)) {
				predecessors.add(prerequisite);
				predecessors.addAll(ancestors.get(prerequisite));
			}
			ancestors.put(next, predecessors);
			ordered.add(runtime.get(next));
			remaining.remove(next);
		}
		return ordered;
	}

	private void validateDescriptions(List<Node> nodes) throws AutofacParseException {
		final Set<String> local = new LinkedHashSet<String>();
		for (Node node : nodes) {
			if (node.kind.equals("register") || node.kind.equals("depends") || node.kind.equals("dependency")) {
				final String identity = node.kind.equals("depends") ? node.get("type") : node.id;
				if (!local.add(node.kind + ":" + identity))
					throw error("Duplicate " + node.kind + " '" + identity + "'", node);
			}
			if (node.kind.equals("register") && "instance".equals(node.get("activator"))
					&& !objects.containsKey(node.get("target")))
				throw error("Unknown instance '" + node.get("target") + "'", node);
			if (Arrays.asList("module", "lifetime", "depends", "parallel").contains(node.kind))
				validateDescriptions(node.children);
		}
	}

	private void construction(List<Node> nodes, String prefix) throws AutofacParseException {
		for (Node node : nodes) {
			if (node.kind.equals("register"))
				put(registrations, prefix + "." + node.id, node, "registration");
			templates(Collections.singletonList(node));
		}
	}

	private void templates(List<Node> nodes) throws AutofacParseException {
		for (Node node : nodes) {
			if (node.kind.equals("call") && node.id == null)
				requireScope(node.get("scope"), node);
			templates(node.children);
		}
	}

	private void validateOutcomes(Map<String, Set<String>> ancestors) throws AutofacParseException {
		final Map<String, String> creators = new LinkedHashMap<String, String>();
		for (String id : objects.keySet())
			creators.put(id, null);
		// Source order of expectation declarations adds no time.
		creators.putAll(objectSources);
		final Set<String> declaredNew = new LinkedHashSet<String>();
		for (Map.Entry<String, Outcome> entry : model.outcomes.entrySet()) {
			final Outcome outcome = entry.getValue();
			final String object = outcome.get("instance");
			if (object != null && "new".equals(outcome.get("state"))
					&& (!declaredNew.add(object) || objects.containsKey(object)
							|| !entry.getKey().equals(creators.get(object))))
				throw factError("Duplicate instance '" + object + "'", outcome, "instance");
		}
		for (Map.Entry<String, Outcome> entry : model.outcomes.entrySet()) {
			final String target = entry.getKey();
			final Outcome outcome = entry.getValue();
			Node node = events.get(target);
			if (scopes.containsKey(target) && node != null)
				throw new AutofacParseException("Ambiguous expectation target '" + target + "'", outcome.source, 0);
			if (node == null)
				node = scopes.get(target);
			if (node == null)
				throw new AutofacParseException("Unknown event '" + target + "'", outcome.source, 0);
			if (scopes.containsKey(target) && !(outcome.kind.equals("failure") && "scopeCreation".equals(outcome.category)))
				throw new AutofacParseException("Lifetime expectation must declare scopeCreation failure", outcome.source, 0);
			final String selected = outcome.get("selected");
			if (selected != null && !registrations.containsKey(selected))
				throw factError("Unknown registration '" + selected + "'", outcome, "selected");
			for (String fact : Arrays.asList("activation", "sharing", "owner")) {
				final String scope = outcome.get(fact);
				final boolean reserved = fact.equals("sharing") && "none".equals(scope)
						|| fact.equals("owner") && "external".equals(scope);
				if (scope != null && !reserved && !scopes.containsKey(scope))
					throw factError("Unknown lifetime '" + scope + "'", outcome, fact);
			}
			final String object = outcome.get("instance");
			if (outcome.get("state") != null && object == null)
				throw factError("State requires an instance fact", outcome, "state");
			if ("reused".equals(outcome.get("state"))) {
				if (!creators.containsKey(object))
					throw factError("Unknown instance '" + object + "'", outcome, "instance");
				final String creator = creators.get(object);
				if (creator != null && (ancestors.get(target) == null || !ancestors.get(target).contains(creator)))
					throw factError("Invalid order: " + creator + " must precede " + target, outcome, "state");
			}
			if (object != null && !objects.containsKey(object)) {
				final Map<String, String> attrs = new LinkedHashMap<String, String>();
				attrs.put("introducedBy", creators.get(object));
				objects.put(object, new Node("instance", object, attrs, Collections.<Node>emptyList(), outcome.source, 0));
			}
		}
		for (Node object : objects.values())
			if (object.get("owner") != null && !object.get("owner").equals("external"))
				requireScope(object.get("owner"), object);
	}

	private void warning(String code, String message, Node node) {
		warnings.add(new StructuralWarning(code, message, node.id, node.source));
	}

	private void chronology(List<Node> ordered, Map<String, Set<String>> ancestors) throws AutofacParseException {
		final Map<String, Node> ended = new LinkedHashMap<String, Node>();
		final Set<String> born = new LinkedHashSet<String>();
		for (Node node : ordered) {
			final String scope = node.get("scope");
			if (node.kind.equals("birth")) {
				final Outcome creation = model.outcomes.get(scope);
				if (creation == null || !"scopeCreation".equals(creation.category))
					born.add(scope);
				continue;
			}
			if (node.get("ownedScope") != null) {
				final String child = node.get("ownedScope");
				final Outcome creation = model.outcomes.get(child);
				if (creation == null || !"scopeCreation".equals(creation.category))
					born.add(child);
			}
			if (!born.contains(scope) || !ancestors.get(node.id).contains(birth(scope)))
				warning("notCreated", "Lifetime '" + scope + "' is not causally created before this event", node);
			if (ended.containsKey(scope) && ancestors.get(node.id).contains(ended.get(scope).id))
				warning("disposedScope", "Event uses disposed lifetime '" + scope + "'", node);
			if (!node.kind.equals("dispose"))
				continue;
			final Outcome outcome = model.outcomes.get(node.id);
			if (outcome != null && outcome.kind.equals("failure"))
				continue;
			if (ended.containsKey(scope))
				throw error("Duplicate terminal disposal '" + scope + "'", node);
			ended.put(scope, node);
			for (String child : born)
				if (scope.equals(scopes.get(child).get("parent")) && !ended.containsKey(child)
						&& ancestors.get(node.id).contains(birth(child)))
					warning("undisposedChild", "Child lifetime '" + child + "' remains open when '" + scope + "' is disposed", node);
		}
	}
}
