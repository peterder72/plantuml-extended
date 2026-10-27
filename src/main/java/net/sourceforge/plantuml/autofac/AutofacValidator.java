package net.sourceforge.plantuml.autofac;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sourceforge.plantuml.autofac.AutofacModel.Node;
import net.sourceforge.plantuml.autofac.AutofacModel.StructuralWarning;

/** Checks authored identity, ancestry and chronology, never container behavior. */
public final class AutofacValidator {
	private AutofacValidator() {
	}

	public static AutofacModel validate(AutofacModel model) throws AutofacParseException {
		final Set<String> aliases = new LinkedHashSet<String>();
		for (Map<String, Node> namespace : java.util.Arrays.asList(model.scopes, model.modules, model.objects,
				model.participants, model.registrations, model.events))
			for (Node declaration : namespace.values())
				if (!aliases.add(declaration.id)) fail("Duplicate " + declaration.kind + " '" + declaration.id + "'", declaration);
		final Map<String, Set<String>> edges = chronology(model);
		final Map<String, Set<String>> closure = new LinkedHashMap<String, Set<String>>();
		final Set<String> visiting = new LinkedHashSet<String>();
		for (Node event : model.timeline)
			ancestors(event.id, edges, closure, visiting, event);
		final Map<String, Node> configured = new LinkedHashMap<String, Node>();
		final Map<String, Node> created = new LinkedHashMap<String, Node>();
		final Map<String, Node> destroyed = new LinkedHashMap<String, Node>();
		for (Node event : model.timeline) {
			if (event.kind.equals("configure")) configured.put(event.get("target"), event);
			if (event.kind.equals("create")) {
				final String target = event.get("target");
				requireTrack(model, target, event);
				if (created.put(target, event) != null) fail("Duplicate creation '" + target + "'", event);
			}
			if (event.kind.equals("destroy")) {
				final String target = event.get("target");
				requireTrack(model, target, event);
				if (destroyed.put(target, event) != null) fail("Duplicate destruction '" + target + "'", event);
			}
		}
		for (Node scope : model.scopes.values()) {
			final String parent = scope.get("parent");
			if (parent != null) {
				requireLifetime(model, parent, scope);
				checkAncestry(scope, model, new LinkedHashSet<String>());
			}
			final String ownedBy = scope.get("ownedBy");
			if (ownedBy != null) {
				requireObject(model, ownedBy, scope);
				final Node acquired = created.get(ownedBy);
				if (acquired != null) precedes(acquired, configured.get(scope.id), closure, scope);
				else declarationBefore(model.objects.get(ownedBy), scope, "object '" + ownedBy + "'", "configuration '" + scope.id + "'");
			}
			final Node birth = created.get(scope.id);
			if (birth != null) {
				precedes(configured.get(scope.id), birth, closure, birth);
				if (parent != null && model.scopes.containsKey(parent)) {
					final Node parentBirth = created.get(parent);
					if (parentBirth == null) fail("Invalid order: creation '" + parent + "' must precede creation '" + scope.id + "'", birth);
					if (parentBirth != null) precedes(parentBirth, birth, closure, birth);
				}
			}
			for (Node row : scope.children) {
				if (row.kind.equals("install")) {
					requireObject(model, row.get("target"), row);
					if (!model.modules.containsKey(row.get("module"))) fail("Unknown module '" + row.get("module") + "'", row);
					declarationBefore(model.objects.get(row.get("target")), scope,
							"object '" + row.get("target") + "'", "installation '" + row.id + "'");
					final Node objectBirth = created.get(row.get("target"));
					if (objectBirth != null) precedes(objectBirth, configured.get(scope.id), closure, row);
				}
			}
		}
		for (Node object : model.objects.values()) {
			final String owner = object.get("owner");
			if (owner != null) requireLifetime(model, owner, object);
			final Node birth = created.get(object.id);
			if (birth != null) {
				declarationBefore(object, birth, "object '" + object.id + "'", "creation '" + object.id + "'");
			}
		}
		for (Node registration : model.registrations.values())
			if (registration.get("activator").equals("instance"))
				requireObject(model, registration.get("target"), registration);
		for (Map.Entry<String, Node> end : destroyed.entrySet()) {
			final Node birth = created.get(end.getKey());
			if (birth == null && model.scopes.containsKey(end.getKey()))
				fail("Lifetime '" + end.getKey() + "' was never created", end.getValue());
			if (birth != null) precedes(birth, end.getValue(), closure, end.getValue());
		}
		final List<StructuralWarning> warnings = new ArrayList<StructuralWarning>();
		for (Node event : model.timeline) {
			if (event.kind.equals("event")) {
				final String scope = event.get("scope");
				if (!model.scopes.containsKey(scope) && !model.participants.containsKey(scope)) fail("Unknown lifetime '" + scope + "'", event);
				checkUse(scope, event, destroyed, closure, warnings);
			} else if (event.kind.equals("message")) {
				for (String end : new String[] { "from", "to" }) {
					final String endpoint = event.get(end);
					if (!external(endpoint)) {
						requireEndpoint(model, endpoint, event);
						checkUse(track(model, endpoint), event, destroyed, closure, warnings);
					}
				}
			} else if (event.kind.equals("activate") || event.kind.equals("deactivate")) {
				requireTrack(model, event.get("target"), event);
				checkUse(event.get("target"), event, destroyed, closure, warnings);
			} else if (event.kind.equals("note")) {
				final String targets = event.get("targets", "");
				for (String endpoint : targets.split(","))
					if (!endpoint.isEmpty()) requireEntity(model, endpoint.trim(), event);
			}
			if (event.kind.equals("destroy")) {
				final String parent = event.get("target");
				for (Node child : model.scopes.values()) {
					if (!isDescendant(child, parent, model)) continue;
					final Node birth = created.get(child.id);
					final Node end = destroyed.get(child.id);
					if (birth != null && before(birth, event, closure) && (end == null || !before(end, event, closure)))
						warnings.add(new StructuralWarning("undisposedChild", "Lifetime '" + child.id
								+ "' remains independently open when '" + parent + "' ends", event.id, event.source));
				}
			}
		}
		for (Node relation : model.relations) {
			requireEntity(model, relation.get("from"), relation);
			requireEntity(model, relation.get("to"), relation);
		}
		return new AutofacModel(model.statements, model.timeline, model.relations, model.scopes, model.modules,
				model.objects, model.participants, model.registrations, closure, warnings);
	}

	private static final class Boundary {
		final Node start;
		final Set<String> branchEnds = new LinkedHashSet<String>();
		Boundary(Node start) { this.start = start; }
	}

	/** Branches of par/alt share an entry and join, rather than gaining source-order edges. */
	private static Map<String, Set<String>> chronology(AutofacModel model) throws AutofacParseException {
		final Map<String, Set<String>> edges = new LinkedHashMap<String, Set<String>>();
		final List<Boundary> stack = new ArrayList<Boundary>();
		Set<String> frontier = new LinkedHashSet<String>();
		Set<String> lastMessageFrontier = new LinkedHashSet<String>();
		for (Node node : model.timeline) {
			if (node.kind.equals("groupElse")) {
				if (stack.isEmpty() || !stack.get(stack.size() - 1).start.id.equals(node.get("group"))) fail("Unexpected 'else' outside group", node);
				final Boundary boundary = stack.get(stack.size() - 1);
				boundary.branchEnds.addAll(frontier);
				frontier = singleton(boundary.start.id);
			} else if (node.kind.equals("groupEnd")) {
				if (stack.isEmpty() || !stack.get(stack.size() - 1).start.id.equals(node.get("group"))) fail("Unexpected 'end' outside group", node);
				frontier.addAll(stack.remove(stack.size() - 1).branchEnds);
			}
			final Set<String> parents = new LinkedHashSet<String>(frontier);
			if (node.kind.equals("message") && node.get("parallel", "false").equals("true")) {
				parents.clear(); parents.addAll(lastMessageFrontier);
			} else if (node.kind.equals("message")) lastMessageFrontier = new LinkedHashSet<String>(frontier);
			for (String endpoint : new String[] { node.get("from"), node.get("to") })
				if (endpoint != null && model.events.containsKey(endpoint)) parents.add(endpoint);
			edges.put(node.id, parents);
			if (node.get("parallel", "false").equals("true")) frontier.add(node.id);
			else frontier = singleton(node.id);
			if (node.kind.equals("groupStart")) stack.add(new Boundary(node));
		}
		return edges;
	}

	private static Set<String> ancestors(String id, Map<String, Set<String>> edges, Map<String, Set<String>> closure,
			Set<String> visiting, Node source) throws AutofacParseException {
		if (closure.containsKey(id)) return closure.get(id);
		if (!visiting.add(id)) fail("Cyclic chronology reference", source);
		final Set<String> result = new LinkedHashSet<String>();
		final Set<String> direct = edges.get(id);
		if (direct != null) for (String parent : direct) {
			result.add(parent);
			result.addAll(ancestors(parent, edges, closure, visiting, source));
		}
		visiting.remove(id);
		closure.put(id, result);
		return result;
	}

	private static void checkAncestry(Node scope, AutofacModel model, Set<String> visiting) throws AutofacParseException {
		if (!visiting.add(scope.id)) fail("Cyclic lifetime reference", scope);
		final Node parent = model.scopes.get(scope.get("parent"));
		if (parent != null) checkAncestry(parent, model, visiting);
		visiting.remove(scope.id);
	}

	private static boolean isDescendant(Node child, String parent, AutofacModel model) {
		Node current = model.scopes.get(child.get("parent"));
		while (current != null) {
			if (current.id.equals(parent)) return true;
			current = model.scopes.get(current.get("parent"));
		}
		return false;
	}

	private static void checkUse(String id, Node event, Map<String, Node> destroyed,
			Map<String, Set<String>> closure, List<StructuralWarning> warnings) {
		if (id == null) return;
		final Node end = destroyed.get(id);
		if (end != null && before(end, event, closure))
			warnings.add(new StructuralWarning("disposedScope", "Element '" + id + "' is used after its explicit destruction", event.id, event.source));
	}

	private static String track(AutofacModel model, String id) {
		final Node event = model.events.get(id);
		if (event == null) return id;
		if (event.kind.equals("event")) return event.get("scope");
		return event.get("target");
	}

	private static void requireLifetime(AutofacModel model, String id, Node at) throws AutofacParseException {
		if (!model.scopes.containsKey(id)) fail("Unknown lifetime '" + id + "'", at);
	}

	private static void requireObject(AutofacModel model, String id, Node at) throws AutofacParseException {
		if (!model.objects.containsKey(id)) fail("Unknown instance '" + id + "'", at);
	}

	private static void requireTrack(AutofacModel model, String id, Node at) throws AutofacParseException {
		if (!model.scopes.containsKey(id) && !model.objects.containsKey(id) && !model.participants.containsKey(id))
			fail("Unknown lifetime '" + id + "'", at);
	}

	private static void requireEndpoint(AutofacModel model, String id, Node at) throws AutofacParseException {
		if (model.scopes.containsKey(id) || model.objects.containsKey(id) || model.participants.containsKey(id)) return;
		final Node event = model.events.get(id);
		if (event == null || !event.kind.equals("event")) fail("Unknown event '" + id + "'", at);
		preexisting(event, at);
	}

	private static void requireEntity(AutofacModel model, String id, Node at) throws AutofacParseException {
		if (!model.entities.containsKey(id))
			fail((id.indexOf('.') >= 0 ? "Unknown registration '" : "Unknown entity '") + id + "'", at);
		preexisting(model.entities.get(id), at);
	}

	private static void preexisting(Node declaration, Node at) throws AutofacParseException {
		if (declaration.source.getLocation().getPosition() > at.source.getLocation().getPosition())
			fail("Invalid order: '" + declaration.id + "' must precede its reference", at);
	}

	private static boolean external(String id) { return "@left".equals(id) || "@right".equals(id); }
	private static Set<String> singleton(String id) {
		final Set<String> result = new LinkedHashSet<String>(); result.add(id); return result;
	}
	private static boolean before(Node prerequisite, Node dependent, Map<String, Set<String>> closure) {
		return prerequisite != null && dependent != null && closure.containsKey(dependent.id) && closure.get(dependent.id).contains(prerequisite.id);
	}
	private static void precedes(Node prerequisite, Node dependent, Map<String, Set<String>> closure, Node at) throws AutofacParseException {
		if (!before(prerequisite, dependent, closure))
			fail("Invalid order: " + describe(prerequisite) + " must precede " + describe(dependent), at);
	}
	private static String describe(Node node) {
		if (node == null) return "missing prerequisite";
		return node.kind + " '" + node.get("target", node.id) + "'";
	}
	private static void declarationBefore(Node declaration, Node dependent, String prerequisiteName, String dependentName) throws AutofacParseException {
		final int declarationLine = declaration.source.getLocation().getPosition();
		final int dependentLine = dependent.source.getLocation().getPosition();
		if (declarationLine > dependentLine || declarationLine == dependentLine && declaration.offset >= dependent.offset)
			fail("Invalid order: " + prerequisiteName + " must precede " + dependentName, dependent);
	}
	private static void fail(String message, Node at) throws AutofacParseException {
		throw new AutofacParseException(message, at.source, at.offset);
	}
}
