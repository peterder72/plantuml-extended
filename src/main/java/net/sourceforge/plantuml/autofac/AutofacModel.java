package net.sourceforge.plantuml.autofac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sourceforge.plantuml.text.StringLocated;

/** Authored facts only: this model does not perform container resolution. */
public final class AutofacModel {
	public final String title;
	public final String view;
	public final List<Node> statements;
	public final List<Node> timeline;
	public final Map<String, Node> scopes;
	public final Map<String, Node> modules;
	public final Map<String, Node> events;
	public final Map<String, Node> objects;
	public final Map<String, Node> registrations;
	public final Map<String, Outcome> outcomes;
	public final Map<String, Set<String>> predecessors;
	public final List<StructuralWarning> warnings;
	public final Set<String> expanded;

	public AutofacModel(String title, List<Node> statements, Map<String, Outcome> outcomes,
			Set<String> expanded, String view) {
		this(title, statements, outcomes, expanded, view, Collections.<String, Node>emptyMap(),
				Collections.<String, Node>emptyMap(), Collections.<String, Node>emptyMap(),
				Collections.<String, Node>emptyMap(), Collections.<String, Node>emptyMap(),
				Collections.<Node>emptyList(), Collections.<String, Set<String>>emptyMap(),
				Collections.<StructuralWarning>emptyList());
	}

	public AutofacModel(String title, List<Node> statements, Map<String, Outcome> outcomes,
			Set<String> expanded, String view, Map<String, Node> scopes, Map<String, Node> modules,
			Map<String, Node> events, Map<String, Node> objects, Map<String, Node> registrations,
			List<Node> timeline, Map<String, Set<String>> predecessors, List<StructuralWarning> warnings) {
		this.title = title;
		this.statements = list(statements);
		this.outcomes = map(outcomes);
		this.expanded = Collections.unmodifiableSet(new LinkedHashSet<String>(expanded));
		this.view = view;
		this.scopes = map(scopes);
		this.modules = map(modules);
		this.events = map(events);
		this.objects = map(objects);
		this.registrations = map(registrations);
		this.timeline = list(timeline);
		this.warnings = list(warnings);
		final Map<String, Set<String>> copied = new LinkedHashMap<String, Set<String>>();
		for (Map.Entry<String, Set<String>> entry : predecessors.entrySet())
			copied.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<String>(entry.getValue())));
		this.predecessors = map(copied);
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

		public Node(String kind, String id, Map<String, String> attrs, List<Node> children,
				StringLocated source, int offset) {
			this.kind = kind;
			this.id = id;
			this.attrs = map(attrs);
			this.children = list(children);
			this.source = source;
			this.offset = offset;
		}

		public String get(String key) {
			return attrs.get(key);
		}

		public String get(String key, String fallback) {
			final String value = get(key);
			return value == null ? fallback : value;
		}
	}

	public static final class Outcome {
		public final String kind;
		public final String category;
		public final Map<String, String> facts;
		public final Map<String, List<String>> paths;
		public final StringLocated source;
		public final Map<String, StringLocated> factSources;
		public final Map<String, Integer> factOffsets;

		public Outcome(String kind, String category, Map<String, String> facts,
				Map<String, List<String>> paths, StringLocated source) {
			this(kind, category, facts, paths, source, Collections.<String, StringLocated>emptyMap(),
					Collections.<String, Integer>emptyMap());
		}

		public Outcome(String kind, String category, Map<String, String> facts,
				Map<String, List<String>> paths, StringLocated source,
				Map<String, StringLocated> factSources, Map<String, Integer> factOffsets) {
			this.kind = kind;
			this.category = category;
			this.facts = map(facts);
			final Map<String, List<String>> copied = new LinkedHashMap<String, List<String>>();
			for (Map.Entry<String, List<String>> entry : paths.entrySet())
				copied.put(entry.getKey(), list(entry.getValue()));
			this.paths = map(copied);
			this.source = source;
			this.factSources = map(factSources);
			this.factOffsets = map(factOffsets);
		}

		public String get(String key) {
			return facts.get(key);
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
