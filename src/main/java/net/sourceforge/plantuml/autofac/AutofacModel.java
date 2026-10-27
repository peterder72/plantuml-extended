package net.sourceforge.plantuml.autofac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sourceforge.plantuml.skin.ArrowConfiguration;
import net.sourceforge.plantuml.text.StringLocated;

/** Explicit drawing entities and occurrences. No Autofac execution or inferred outcomes. */
public final class AutofacModel {
	public final List<Node> statements;
	public final List<Node> timeline;
	public final List<Node> relations;
	public final Map<String, Node> scopes;
	public final Map<String, Node> modules;
	public final Map<String, Node> objects;
	public final Map<String, Node> participants;
	public final Map<String, Node> registrations;
	public final Map<String, Node> events;
	public final Map<String, Node> entities;
	public final Map<String, Set<String>> predecessors;
	public final List<StructuralWarning> warnings;

	public AutofacModel(List<Node> statements, List<Node> timeline, List<Node> relations,
			Map<String, Node> scopes, Map<String, Node> modules, Map<String, Node> objects,
			Map<String, Node> participants, Map<String, Node> registrations,
			Map<String, Set<String>> predecessors, List<StructuralWarning> warnings) {
		this.statements = list(statements);
		this.timeline = list(timeline);
		this.relations = list(relations);
		this.scopes = map(scopes);
		this.modules = map(modules);
		this.objects = map(objects);
		this.participants = map(participants);
		this.registrations = map(registrations);
		final Map<String, Node> occurrences = new LinkedHashMap<String, Node>();
		for (Node node : timeline)
			occurrences.put(node.id, node);
		this.events = map(occurrences);
		final Map<String, Node> all = new LinkedHashMap<String, Node>();
		all.putAll(scopes);
		all.putAll(modules);
		all.putAll(objects);
		all.putAll(participants);
		all.putAll(registrations);
		all.putAll(occurrences);
		this.entities = map(all);
		final Map<String, Set<String>> order = new LinkedHashMap<String, Set<String>>();
		for (Map.Entry<String, Set<String>> entry : predecessors.entrySet())
			order.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<String>(entry.getValue())));
		this.predecessors = map(order);
		this.warnings = list(warnings);
	}

	private static <T> List<T> list(List<T> values) {
		return Collections.unmodifiableList(new ArrayList<T>(values));
	}

	private static <K, V> Map<K, V> map(Map<K, V> values) {
		return Collections.unmodifiableMap(new LinkedHashMap<K, V>(values));
	}

	public static final class Node {
		public final String kind;
		public final String id;
		public final Map<String, String> attrs;
		public final List<Node> children;
		public final StringLocated source;
		public final int offset;
		public final ArrowConfiguration arrow;

		public Node(String kind, String id, Map<String, String> attrs, List<Node> children,
				StringLocated source, int offset, ArrowConfiguration arrow) {
			this.kind = kind;
			this.id = id;
			this.attrs = map(attrs);
			this.children = list(children);
			this.source = source;
			this.offset = offset;
			this.arrow = arrow;
		}

		public String get(String key) {
			return attrs.get(key);
		}

		public String get(String key, String fallback) {
			final String value = get(key);
			return value == null ? fallback : value;
		}
	}

	public static final class StructuralWarning {
		public final String code;
		public final String message;
		public final String target;
		public final StringLocated source;

		public StructuralWarning(String code, String message, String target, StringLocated source) {
			this.code = code;
			this.message = message;
			this.target = target;
			this.source = source;
		}
	}
}
