package net.sourceforge.plantuml.autofac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.plantuml.autofac.AutofacModel.Node;
import net.sourceforge.plantuml.jaws.Jaws;
import net.sourceforge.plantuml.skin.ArrowBody;
import net.sourceforge.plantuml.skin.ArrowConfiguration;
import net.sourceforge.plantuml.text.StringLocated;

/** Located structural declarations. Runtime arrows, groups and notes are parsed by sequence commands. */
public final class AutofacParser {
	private static final Pattern ID = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");
	private static final Pattern GENERIC_SPACING = Pattern.compile("\\s*([<>,])\\s*");
	private static final Pattern REFERENCE = Pattern.compile(
			"^\\s*([A-Za-z_][A-Za-z0-9_.-]*)\\s*(\\.\\.>|<\\.\\.)\\s*([A-Za-z_][A-Za-z0-9_.-]*)(?:\\s*:\\s*(.*))?\\s*$");
	private final List<Node> statements = new ArrayList<Node>();
	private final List<Node> timeline = new ArrayList<Node>();
	private final List<Node> relations = new ArrayList<Node>();
	private final Map<String, Node> scopes = new LinkedHashMap<String, Node>();
	private final Map<String, Node> modules = new LinkedHashMap<String, Node>();
	private final Map<String, Node> objects = new LinkedHashMap<String, Node>();
	private final Map<String, Node> participants = new LinkedHashMap<String, Node>();
	private final Map<String, Node> registrations = new LinkedHashMap<String, Node>();
	private final Set<String> aliases = new LinkedHashSet<String>();
	private final List<Node> groups = new ArrayList<Node>();
	private Node construction;
	private final List<Node> constructionRows = new ArrayList<Node>();
	private final Set<String> constructionIds = new LinkedHashSet<String>();
	private int constructionIndex;
	private StringLocated constructionOpener;
	private final Map<String, Node> occurrences = new LinkedHashMap<String, Node>();
	private int currentOffset;
	private int serial;

	private static final class Token {
		final String text;
		final int offset;
		final boolean quoted;
		Token(String text, int offset, boolean quoted) {
			this.text = text;
			this.offset = offset;
			this.quoted = quoted;
		}
	}

	private static final class Cursor {
		final List<Token> tokens;
		final StringLocated source;
		int index;
		Cursor(List<Token> tokens, StringLocated source) {
			this.tokens = tokens;
			this.source = source;
		}
		boolean end() { return index == tokens.size(); }
		boolean at(String text) { return !end() && !tokens.get(index).quoted && tokens.get(index).text.equals(text); }
		Token take() throws AutofacParseException {
			if (end())
				throw error("Unexpected end of statement", source.getString().length());
			return tokens.get(index++);
		}
		void need(String text) throws AutofacParseException {
			final Token token = take();
			if (token.quoted || !text.equals(token.text))
				throw error("Unexpected '" + token.text + "', expected '" + text + "'", token.offset);
		}
		String id() throws AutofacParseException {
			final Token token = take();
			if (token.quoted || !ID.matcher(token.text).matches())
				throw error("Invalid alias '" + token.text + "'", token.offset);
			return token.text;
		}
		void finish() throws AutofacParseException {
			if (!end()) {
				final Token token = take();
				throw error("Unexpected '" + token.text + "' in statement", token.offset);
			}
		}
		AutofacParseException error(String message, int offset) {
			return new AutofacParseException(message, source, offset);
		}
	}

	private List<Token> tokenize(StringLocated source) throws AutofacParseException {
		final String text = source.getString().replace(Jaws.BLOCK_E1_REAL_BACKSLASH, '\\');
		final List<Token> result = new ArrayList<Token>();
		for (int i = 0; i < text.length();) {
			char c = text.charAt(i);
			if (Character.isWhitespace(c)) { i++; continue; }
			if (c == '\'' || c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/')
				break;
			final int start = i;
			if (text.startsWith("<<", i)) {
				final int end = text.indexOf(">>", i + 2);
				if (end < 0) throw new AutofacParseException("Unclosed stereotype", source, start);
				result.add(new Token(text.substring(i, end + 2), i, false));
				i = end + 2;
			} else if (c == '"') {
				final StringBuilder value = new StringBuilder();
				boolean closed = false;
				for (i++; i < text.length();) {
					c = text.charAt(i++);
					if (c == '"') { closed = true; break; }
					if (c == '\\') {
						if (i == text.length())
							throw new AutofacParseException("Unclosed string", source, start);
						c = text.charAt(i++);
						if (c == 'n') c = '\n';
						else if (c == 't') c = '\t';
						else if (c != '"' && c != '\\')
							throw new AutofacParseException("Invalid string escape", source, i - 2);
					}
					value.append(c);
				}
				if (!closed)
					throw new AutofacParseException("Unclosed string", source, start);
				result.add(new Token(value.toString(), start, true));
			} else if (c == '-' && i + 1 < text.length() && text.charAt(i + 1) == '>') {
				result.add(new Token("->", i, false)); i += 2;
			} else if ("{}:;".indexOf(c) >= 0) {
				result.add(new Token(String.valueOf(c), i++, false));
			} else {
				i++;
				while (i < text.length() && !Character.isWhitespace(text.charAt(i))
						&& "{}:;\"'".indexOf(text.charAt(i)) < 0
						&& !(text.charAt(i) == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/')
						&& !(text.charAt(i) == '-' && i + 1 < text.length() && text.charAt(i + 1) == '>'))
					i++;
				result.add(new Token(text.substring(start, i), start, false));
			}
		}
		return result;
	}

	public void appendLine(StringLocated source) throws AutofacParseException {
		final Matcher reference = REFERENCE.matcher(source.getString());
		if (reference.matches()) {
			if (construction != null)
				throw new AutofacParseException("Unexpected reference in construction", source, 0);
			currentOffset = reference.start(1);
			final boolean reverse = reference.group(2).equals("<..");
			final Map<String, String> attrs = attrs("from", reference.group(reverse ? 3 : 1),
					"to", reference.group(reverse ? 1 : 3), "label", reference.group(4) == null ? "" : reference.group(4));
			relations.add(node("reference", generated(), attrs, source,
					ArrowConfiguration.withDirectionNormal().withBody(ArrowBody.DOTTED)));
			return;
		}
		final String text = source.getString().trim();
		if (text.equals("event") || text.startsWith("event ") || text.startsWith("event\t")) {
			final int colon = eventColon(source.getString());
			final StringLocated header = colon < 0 ? source : source.substring(0, colon + 1);
			parseStatement(new Cursor(tokenize(header), source));
			return;
		}
		final List<Token> tokens = tokenize(source);
		final Cursor cursor = new Cursor(tokens, source);
		while (!cursor.end()) {
			if (cursor.at(";")) { cursor.take(); continue; }
			if (cursor.at("}")) {
				final Token closer = cursor.take();
				if (construction == null)
					throw cursor.error("Unexpected '}' outside construction", closer.offset);
				finishConstruction();
				continue;
			}
			final List<Token> part = new ArrayList<Token>();
			while (!cursor.end() && !cursor.at(";") && !cursor.at("}")) {
				final Token token = cursor.take();
				part.add(token);
				if (!token.quoted && token.text.equals("{"))
					break;
			}
			parseStatement(new Cursor(part, source));
		}
	}

	private static int eventColon(String text) {
		for (int i = 0; i < text.length(); i++) {
			if (text.startsWith("<<", i)) {
				final int end = text.indexOf(">>", i + 2);
				if (end < 0) return -1;
				i = end + 1;
			} else if (text.charAt(i) == ':') {
				return i;
			}
		}
		return -1;
	}

	private void parseStatement(Cursor c) throws AutofacParseException {
		final Token keyword = c.take();
		currentOffset = keyword.offset;
		if (construction != null) {
			if (keyword.text.equals("register")) register(c);
			else if (keyword.text.equals("decorate")) decorate(c);
			else if (keyword.text.equals("install") && construction.kind.equals("lifetime")) install(c);
			else throw c.error("Unexpected '" + keyword.text + "' in " + construction.kind + " construction", keyword.offset);
			return;
		}
		if (keyword.text.equals("lifetime") || keyword.text.equals("module") || keyword.text.equals("object")) {
			declaration(keyword.text, c);
		} else if (keyword.text.equals("create") || keyword.text.equals("destroy")) {
			final String target = c.id();
			c.finish();
			if (!scopes.containsKey(target) && !objects.containsKey(target) && !participants.containsKey(target))
				throw c.error("Unknown lifetime '" + target + "'", keyword.offset);
			addTimeline(keyword.text, null, attrs("target", target), c.source, null);
		} else if (keyword.text.equals("event")) {
			String id = null;
			if (!c.at("on")) id = c.id();
			c.need("on");
			final String scope = c.id();
			if (!scopes.containsKey(scope) && !participants.containsKey(scope))
				throw c.error("Unknown lifetime '" + scope + "'", keyword.offset);
			final Map<String, String> attrs = attrs("scope", scope, "marker", "circle");
			if (c.at("marker")) {
				c.take();
				final Token marker = c.take();
				if (!(marker.text.equals("circle") || marker.text.equals("cross") || marker.text.equals("diamond") || marker.text.equals("square")))
					throw c.error("Unknown marker '" + marker.text + "'", marker.offset);
				attrs.put("marker", marker.text);
			}
			if (!c.end() && c.tokens.get(c.index).text.startsWith("<<")) {
				final StringBuilder stereotype = new StringBuilder(c.take().text);
				while (!stereotype.toString().endsWith(">>") && !c.end()) stereotype.append(' ').append(c.take().text);
				if (!stereotype.toString().endsWith(">>")) throw c.error("Unclosed stereotype", keyword.offset);
				attrs.put("stereotype", stereotype.toString());
			}
			c.need(":");
			final Token delimiter = c.tokens.get(c.index - 1);
			attrs.put("label", c.source.getString().substring(delimiter.offset + 1).trim()
					.replace(Jaws.BLOCK_E1_REAL_BACKSLASH, '\\'));
			c.index = c.tokens.size();
			addTimeline("event", id, attrs, c.source, null);
		} else {
			throw c.error("Unexpected '" + keyword.text + "' in Autofac diagram", keyword.offset);
		}
	}

	private void declaration(String kind, Cursor c) throws AutofacParseException {
		final Token first = c.take();
		final String id;
		final String label;
		if (first.quoted || c.at("as")) {
			label = first.text;
			c.need("as");
			id = c.id();
		} else {
			if (!ID.matcher(first.text).matches())
				throw c.error("Invalid alias '" + first.text + "'", first.offset);
			id = first.text;
			label = id;
		}
		claim(id, kind, c.source, first.offset);
		final Map<String, String> attrs = attrs("label", label);
		while (!c.end() && !c.at("{")) {
			final Token clause = c.take();
			if (kind.equals("lifetime") && clause.text.equals("from")) putOnce(attrs, "parent", c.id(), c, clause);
			else if (kind.equals("lifetime") && clause.text.equals("tag")) putOnce(attrs, "tag", c.take().text, c, clause);
			else if ((kind.equals("lifetime") || kind.equals("object")) && clause.text.equals("ownedBy"))
				putOnce(attrs, kind.equals("object") ? "owner" : "ownedBy", c.id(), c, clause);
			else throw c.error("Unexpected '" + clause.text + "' in " + kind + " declaration", clause.offset);
		}
		final Node result = node(kind, id, attrs, c.source, null);
		statements.add(result);
		if (kind.equals("lifetime")) {
			scopes.put(id, result);
			addTimeline("configure", null, attrs("target", id), c.source, null);
		} else if (kind.equals("module")) modules.put(id, result);
		else objects.put(id, result);
		if (c.at("{")) {
			if (kind.equals("object")) throw c.error("Unexpected '{' in object declaration", c.take().offset);
			c.take();
			construction = result;
			constructionOpener = c.source;
			constructionRows.clear();
			constructionIds.clear();
			constructionIndex = statements.size() - 1;
		}
		c.finish();
	}

	private void register(Cursor c) throws AutofacParseException {
		final String local = c.id();
		c.need(":");
		final Map<String, String> attrs = new LinkedHashMap<String, String>();
		attrs.put("scope", construction.id);
		attrs.put("service", typeUntil(c, "key", "name", "->", "instance", "via"));
		if (c.at("key") || c.at("name")) {
			final String key = c.take().text;
			attrs.put(key, c.take().text);
		}
		if (c.at("->")) {
			c.take(); attrs.put("activator", "type");
			attrs.put("type", typeUntil(c, "sharing", "ownership"));
		} else if (c.at("instance")) {
			c.take(); attrs.put("activator", "instance"); attrs.put("target", c.id());
		} else if (c.at("via")) {
			c.take(); c.need("delegate"); attrs.put("activator", "delegate");
			attrs.put("label", textUntil(c, "sharing", "ownership"));
		} else throw c.error("Expected registration activator", c.end() ? c.source.getString().length() : c.tokens.get(c.index).offset);
		while (!c.end()) {
			final Token policy = c.take();
			if (!(policy.text.equals("sharing") || policy.text.equals("ownership")))
				throw c.error("Unexpected '" + policy.text + "' in registration", policy.offset);
			putOnce(attrs, policy.text, textUntil(c, "sharing", "ownership"), c, policy);
		}
		final Node result = node("register", construction.id + "." + local, attrs, c.source, null);
		addConstruction(result);
	}

	private void decorate(Cursor c) throws AutofacParseException {
		final String service = typeUntil(c, "with");
		c.need("with");
		final String type = typeUntil(c);
		addConstruction(node("decorate", generated(), attrs("service", service, "type", type,
				"scope", construction.id), c.source, null));
	}

	private void install(Cursor c) throws AutofacParseException {
		final String target = c.id();
		c.need("as");
		final String module = c.id();
		c.finish();
		final Node definition = modules.get(module);
		if (definition == null) throw new AutofacParseException("Unknown module '" + module + "'", c.source, 0);
		final List<Node> rows = new ArrayList<Node>();
		for (Node row : definition.children) {
			final Map<String, String> attrs = new LinkedHashMap<String, String>(row.attrs);
			attrs.put("scope", construction.id);
			attrs.put("module", module);
			attrs.put("installation", target);
			final String id = row.kind.equals("register") ? construction.id + "." + target + row.id.substring(module.length()) : generated();
			rows.add(new Node(row.kind, id, attrs, row.children, row.source, row.offset, row.arrow));
		}
		addConstruction(new Node("install", construction.id + "." + target, attrs("scope", construction.id,
				"target", target, "module", module), rows, c.source, currentOffset, null));
	}

	private void addConstruction(Node row) throws AutofacParseException {
		if (!constructionIds.add(row.id))
			throw new AutofacParseException("Duplicate " + row.kind + " '" + row.id + "'", row.source, row.offset);
		constructionRows.add(row);
		if (construction.kind.equals("lifetime")) {
			if (row.kind.equals("register")) registrations.put(row.id, row);
			for (Node child : row.children) if (child.kind.equals("register")) {
				if (registrations.containsKey(child.id)) throw new AutofacParseException("Duplicate registration '" + child.id + "'", row.source, 0);
				registrations.put(child.id, child);
			}
		}
	}

	private void finishConstruction() {
		final Node complete = new Node(construction.kind, construction.id, construction.attrs, constructionRows,
				construction.source, construction.offset, null);
		statements.set(constructionIndex, complete);
		if (construction.kind.equals("lifetime"))
			scopes.put(complete.id, complete);
		else
			modules.put(complete.id, complete);
		construction = null;
		constructionOpener = null;
	}

	private static void putOnce(Map<String, String> attrs, String key, String value, Cursor c, Token token) throws AutofacParseException {
		if (attrs.containsKey(key)) throw c.error("Conflicting fact '" + key + "'", token.offset);
		attrs.put(key, value);
	}

	private String typeUntil(Cursor c, String... stops) throws AutofacParseException {
		final String value = textUntil(c, stops);
		int depth = 0;
		for (int i = 0; i < value.length(); i++) {
			if (value.charAt(i) == '<') depth++;
			if (value.charAt(i) == '>' && --depth < 0) throw c.error("Unbalanced generic type", 0);
		}
		if (depth != 0) throw c.error("Unbalanced generic type", 0);
		return GENERIC_SPACING.matcher(value).replaceAll("$1");
	}

	private String textUntil(Cursor c, String... stops) throws AutofacParseException {
		final StringBuilder text = new StringBuilder();
		while (!c.end()) {
			boolean stop = false;
			for (String candidate : stops) if (c.at(candidate)) stop = true;
			if (stop) break;
			if (text.length() > 0) text.append(' ');
			text.append(c.take().text);
		}
		if (text.length() == 0) throw c.error("Expected value", c.source.getString().length());
		return text.toString();
	}

	void addParticipant(String id, String label, String type, StringLocated source, Map<String, String> presentation)
			throws AutofacParseException {
		if (scopes.containsKey(id) || objects.containsKey(id) || event(id) != null) return;
		if (registrations.containsKey(id))
			throw new AutofacParseException("Registration '" + id + "' is not a temporal endpoint", source, 0);
		if (participants.containsKey(id)) return;
		claim(id, "participant", source, 0);
		final Map<String, String> attrs = new LinkedHashMap<String, String>(presentation);
		attrs.put("label", label);
		attrs.put("type", type);
		final Node participant = node("participant", id, attrs, source, null);
		participants.put(id, participant);
		statements.add(participant);
	}

	Node addTimeline(String kind, String id, Map<String, String> attrs, StringLocated source,
			ArrowConfiguration arrow) throws AutofacParseException {
		if (construction != null) throw new AutofacParseException("Unexpected '" + kind + "' in construction", source, 0);
		if (id == null) id = generated();
		else claim(id, "event", source, 0);
		final Node result = node(kind, id, attrs, source, arrow);
		timeline.add(result);
		occurrences.put(id, result);
		return result;
	}

	void addGroup(String mode, String label, StringLocated source, Map<String, String> presentation)
			throws AutofacParseException {
		final Map<String, String> attrs = new LinkedHashMap<String, String>(presentation);
		if (mode.equals("end")) {
			if (groups.isEmpty()) throw new AutofacParseException("Unexpected 'end' outside group", source, 0);
			final Node start = groups.remove(groups.size() - 1);
			attrs.put("group", start.id);
			addTimeline("groupEnd", null, attrs, source, null);
		} else if (mode.equals("else")) {
			if (groups.isEmpty()) throw new AutofacParseException("Unexpected 'else' outside group", source, 0);
			attrs.put("group", groups.get(groups.size() - 1).id);
			attrs.put("label", label);
			addTimeline("groupElse", null, attrs, source, null);
		} else {
			attrs.put("mode", mode);
			attrs.put("label", label);
			groups.add(addTimeline("groupStart", null, attrs, source, null));
		}
	}

	Node lastTimeline() { return timeline.isEmpty() ? null : timeline.get(timeline.size() - 1); }
	Node event(String id) { return occurrences.get(id); }
	int timelineSize() { return timeline.size(); }
	Node timelineAt(int index) { return timeline.get(index); }
	int statementSize() { return statements.size(); }
	Node statementAt(int index) { return statements.get(index); }
	void beginNative() {
		currentOffset = 0;
	}
	boolean hasConstruction() { return construction != null; }
	String constructionKind() { return construction == null ? null : construction.kind; }
	String generated() { return "$af-" + (++serial); }

	private void claim(String id, String kind, StringLocated source, int offset) throws AutofacParseException {
		if (!aliases.add(id)) throw new AutofacParseException("Duplicate " + kind + " '" + id + "'", source, offset);
	}

	public AutofacModel parse() throws AutofacParseException {
		if (construction != null)
			throw new AutofacParseException("Unclosed " + construction.kind + " construction block (missing '}')", constructionOpener, 0);
		if (!groups.isEmpty()) {
			final Node group = groups.get(groups.size() - 1);
			throw new AutofacParseException("Unclosed '" + group.get("mode") + "' group (missing 'end')", group.source, 0);
		}
		return snapshot();
	}

	AutofacModel snapshot() {
		return new AutofacModel(statements, timeline, relations, scopes, modules, objects, participants,
				registrations, Collections.<String, Set<String>>emptyMap(), Collections.<AutofacModel.StructuralWarning>emptyList());
	}

	private Node node(String kind, String id, Map<String, String> attrs, StringLocated source, ArrowConfiguration arrow) {
		return new Node(kind, id, attrs, Collections.<Node>emptyList(), source, currentOffset, arrow);
	}

	static Map<String, String> attrs(String... values) {
		final Map<String, String> result = new LinkedHashMap<String, String>();
		for (int i = 0; i < values.length; i += 2) result.put(values[i], values[i + 1]);
		return result;
	}
}
