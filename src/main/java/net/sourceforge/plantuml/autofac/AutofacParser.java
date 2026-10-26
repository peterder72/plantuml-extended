package net.sourceforge.plantuml.autofac;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import net.sourceforge.plantuml.autofac.AutofacModel.Node;
import net.sourceforge.plantuml.autofac.AutofacModel.Outcome;
import net.sourceforge.plantuml.jaws.Jaws;
import net.sourceforge.plantuml.text.StringLocated;

/** Incremental located lexer; parsing consumes retained tokens, never source text. */
public final class AutofacParser {
	private static final Pattern ID = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");
	private static final Pattern TYPE = Pattern.compile("[A-Za-z_][A-Za-z0-9_.$`]*");
	private static final Pattern LAZY_VALUE = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*\\.Value");
	private static final Pattern REGISTRATION_REF = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*(\\.[A-Za-z_][A-Za-z0-9_-]*){1,2}");
	private static final List<String> CATEGORIES = Arrays.asList("missingRegistration", "missingKey",
			"missingMatchingScope", "disposedScope", "activationFailure", "disposalError", "constructorCycle",
			"propertyCycle", "recursiveResolve", "captiveDependency", "scopeCreation", "custom");

	private static final class Token {
		final String text;
		final StringLocated source;
		final int offset;
		final boolean quoted;

		Token(String text, StringLocated source, int offset, boolean quoted) {
			this.text = text;
			this.source = source;
			this.offset = offset;
			this.quoted = quoted;
		}
	}

	private final List<Token> tokens = new ArrayList<Token>();
	private final Map<String, Outcome> outcomes = new LinkedHashMap<String, Outcome>();
	private final Set<String> expanded = new LinkedHashSet<String>();
	private int depth;
	private int position;
	private int groups;
	private boolean opened;
	private boolean closed;
	private String view = "lifetimes";

	public void appendLine(StringLocated line) throws AutofacParseException {
		final String text = line.getString().replace(Jaws.BLOCK_E1_REAL_BACKSLASH, '\\');
		for (int i = 0; i < text.length();) {
			char c = text.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
				continue;
			}
			if (isComment(text, i))
				break;
			final int start = i;
			String value;
			boolean quoted = false;
			if (c == '"') {
				quoted = true;
				final StringBuilder string = new StringBuilder();
				i++;
				boolean terminated = false;
				while (i < text.length()) {
					c = text.charAt(i++);
					if (c == '"') {
						terminated = true;
						break;
					}
					if (c == '\\') {
						if (i == text.length())
							throw new AutofacParseException("Unclosed string", line, start);
						c = text.charAt(i++);
						if (c == 'n')
							c = '\n';
						else if (c == 't')
							c = '\t';
						else if (c != '\\' && c != '"')
							throw new AutofacParseException("Invalid string escape", line, i - 2);
					}
					string.append(c);
				}
				if (!terminated)
					throw new AutofacParseException("Unclosed string", line, start);
				value = string.toString();
			} else if (isArrow(text, i)) {
				value = "->";
				i += 2;
			} else if ("{}:;<>(),".indexOf(c) >= 0) {
				value = String.valueOf(c);
				i++;
			} else {
				i++;
				while (i < text.length() && !Character.isWhitespace(text.charAt(i))
						&& "{}:;<>(),\"".indexOf(text.charAt(i)) < 0 && !isArrow(text, i) && !isComment(text, i))
					i++;
				value = text.substring(start, i);
			}
			if (closed)
				throw new AutofacParseException("Unexpected '" + value + "' after composition", line, start);
			tokens.add(new Token(value, line, start, quoted));
			if (!quoted && value.equals("{")) {
				depth++;
				opened = true;
			}
			if (!quoted && value.equals("}")) {
				depth--;
				if (depth == 0 && opened)
					closed = true;
			}
		}
		tokens.add(new Token(";", line, text.length(), false));
	}

	private boolean isComment(String text, int index) {
		return text.charAt(index) == '\'' || text.charAt(index) == '/' && index + 1 < text.length()
				&& text.charAt(index + 1) == '/';
	}

	private boolean isArrow(String text, int index) {
		return text.charAt(index) == '-' && index + 1 < text.length() && text.charAt(index + 1) == '>';
	}

	public boolean isCompositionClosed() {
		return closed;
	}

	private Token peek() {
		return position < tokens.size() ? tokens.get(position) : tokens.get(tokens.size() - 1);
	}

	private boolean at(String value) {
		return position < tokens.size() && !peek().quoted && peek().text.equals(value);
	}

	private Token take() throws AutofacParseException {
		if (position >= tokens.size())
			throw error("Unexpected end of composition", peek());
		return tokens.get(position++);
	}

	private AutofacParseException error(String message, Token token) {
		return new AutofacParseException(message, token.source, token.offset);
	}

	private void need(String value) throws AutofacParseException {
		final Token token = take();
		if (token.quoted || !token.text.equals(value))
			throw error("Unexpected '" + token.text + "', expected '" + value + "'", token);
	}

	private void separators() {
		while (at(";"))
			position++;
	}

	private String id() throws AutofacParseException {
		final Token token = take();
		if (token.quoted || ID.matcher(token.text).matches() == false
				|| token.text.equals("none") || token.text.equals("external"))
			throw error("Invalid ID '" + token.text + "'", token);
		return token.text;
	}

	private String string() throws AutofacParseException {
		final Token token = take();
		if (!token.quoted)
			throw error("Expected quoted string", token);
		return token.text;
	}

	private String choice(String context, String... choices) throws AutofacParseException {
		final Token token = take();
		if (token.quoted || !Arrays.asList(choices).contains(token.text))
			throw error("Invalid " + context + " '" + token.text + "'", token);
		return token.text;
	}

	private String type() throws AutofacParseException {
		final Token token = take();
		if (token.quoted)
			return token.text;
		if (TYPE.matcher(token.text).matches() == false)
			throw error("Invalid type '" + token.text + "'", token);
		final StringBuilder result = new StringBuilder(token.text);
		if (at("<")) {
			take();
			result.append('<').append(type());
			while (at(",")) {
				take();
				result.append(',').append(type());
			}
			need(">");
			result.append('>');
		}
		return result.toString();
	}

	private void service(Map<String, String> attrs, boolean wildcard) throws AutofacParseException {
		attrs.put("service", type());
		if (at("key") || at("name")) {
			final String spelling = take().text;
			if (wildcard && spelling.equals("key") && at("*")) {
				take();
				attrs.put("key", "*");
				attrs.put("keyWildcard", "true");
			} else
				attrs.put("key", string());
			attrs.put("keyKind", spelling);
		}
	}

	public AutofacModel parse() throws AutofacParseException {
		position = 0;
		outcomes.clear();
		expanded.clear();
		groups = 0;
		view = "lifetimes";
		separators();
		if (position == tokens.size())
			throw new AutofacParseException("Missing composition block", null, 0);
		need("composition");
		final String title = string();
		need("{");
		final List<Node> statements = body("composition");
		separators();
		if (position < tokens.size())
			throw error("Unexpected '" + peek().text + "' after composition", peek());
		return new AutofacModel(title, statements, outcomes, expanded, view);
	}

	private List<Node> body(String context) throws AutofacParseException {
		final List<Node> result = new ArrayList<Node>();
		separators();
		while (!at("}")) {
			if (position >= tokens.size())
				throw error("Unclosed " + context + " block", peek());
			final String keyword = peek().text;
			final Node node = statement(context);
			if (node != null)
				result.add(node);
			final boolean block = Arrays.asList("lifetime", "module", "parallel", "depends", "expect").contains(keyword);
			if (!at("}") && !at(";") && !block)
				throw error("Unexpected '" + peek().text + "' in " + context, peek());
			separators();
		}
		need("}");
		return result;
	}

	private Node statement(String context) throws AutofacParseException {
		final Token start = take();
		final String kind = start.text;
		final boolean construction = context.equals("lifetime") || context.equals("module");
		if (start.quoted || construction && !Arrays.asList("register", "install", "decorate", "depends").contains(kind)
				|| context.equals("module") && kind.equals("install")
				|| context.equals("parallel") && !Arrays.asList("lifetime", "parallel", "resolve", "call", "invoke", "evaluate", "dispose").contains(kind))
			throw error("Unexpected '" + kind + "' in " + context, start);
		final Map<String, String> attrs = new LinkedHashMap<String, String>();
		List<Node> children = new ArrayList<Node>();
		String identity = null;
		if (kind.equals("module") || kind.equals("lifetime")) {
			identity = id();
			if (kind.equals("lifetime")) {
				if (at("root"))
					take();
				else {
					need("from");
					attrs.put("parent", id());
				}
				while (at("tag") || at("ownedBy")) {
					final Token clause = take();
					if (attrs.containsKey(clause.text))
						throw error("Conflicting fact '" + clause.text + "'", clause);
					attrs.put(clause.text, clause.text.equals("tag") ? string() : id());
				}
			}
			need("{");
			children = body(kind);
		} else if (kind.equals("register")) {
			if (!construction)
				throw error("Unexpected 'register' in " + context, start);
			identity = id();
			need(":");
			service(attrs, true);
			registration(attrs, children);
		} else if (kind.equals("install")) {
			if (!context.equals("lifetime"))
				throw error("Unexpected 'install' in " + context, start);
			identity = id();
			attrs.put("target", identity);
		} else if (kind.equals("decorate")) {
			if (!construction)
				throw error("Unexpected 'decorate' in " + context, start);
			service(attrs, false);
			need("with");
			attrs.put("type", type());
		} else if (kind.equals("depends")) {
			attrs.put("type", type());
			children = dependencies();
		} else if (kind.equals("parallel")) {
			identity = "parallel-" + (++groups);
			need("{");
			children = body("parallel");
		} else if (kind.equals("instance")) {
			identity = id();
			need(":");
			attrs.put("type", type());
			if (at("owner")) {
				take();
				attrs.put("owner", scopeOr("external"));
			} else if (at("external")) {
				take();
				attrs.put("owner", "external");
			}
		} else if (kind.equals("resolve") || kind.equals("call")) {
			if (kind.equals("call"))
				need("resolve");
			identity = id();
			need(":");
			service(attrs, false);
			need("from");
			attrs.put("scope", id());
			if (kind.equals("call")) {
				need("during");
				attrs.put("during", id());
			}
		} else if (kind.equals("invoke") || kind.equals("evaluate")) {
			identity = id();
			need(":");
			if (kind.equals("invoke")) {
				attrs.put("target", id());
				need("(");
				need(")");
			} else {
				final Token target = take();
				if (target.quoted || LAZY_VALUE.matcher(target.text).matches() == false)
					throw error("Expected object.Value", target);
				attrs.put("target", target.text.substring(0, target.text.length() - 6));
			}
			if (at("from")) {
				take();
				attrs.put("scope", id());
			}
		} else if (kind.equals("dispose")) {
			String target = id();
			if (at(":")) {
				take();
				identity = target;
				target = id();
			}
			attrs.put("target", target);
		} else if (kind.equals("expect")) {
			expectation(start);
			return null;
		} else if (kind.equals("view")) {
			view = choice("view", "lifetimes", "diagnostics");
			return null;
		} else if (kind.equals("expand")) {
			identity = id();
			expanded.add(identity);
			if (at("trace"))
				take();
		} else
			throw error("Unexpected '" + kind + "' in " + context, start);
		return new Node(kind, identity, attrs, children, start.source, start.offset);
	}

	private void registration(Map<String, String> attrs, List<Node> children) throws AutofacParseException {
		if (at("->")) {
			take();
			attrs.put("activator", "type");
			attrs.put("type", type());
		} else if (at("instance")) {
			take();
			attrs.put("activator", "instance");
			attrs.put("target", id());
		} else {
			need("via");
			need("delegate");
			attrs.put("activator", "delegate");
			if (at("{")) {
				take();
				separators();
				while (!at("}")) {
					final Token start = peek();
					need("call");
					need("resolve");
					final Map<String, String> call = new LinkedHashMap<String, String>();
					service(call, false);
					need("from");
					call.put("scope", id());
					children.add(new Node("call", null, call, new ArrayList<Node>(), start.source, start.offset));
					if (!at(";") && !at("}"))
						throw error("Unexpected '" + peek().text + "' in delegate", peek());
					separators();
				}
				need("}");
			} else
				attrs.put("target", delegateLabel());
		}
		while (at("sharing") || at("ownership")) {
			final Token clause = take();
			if (attrs.containsKey(clause.text))
				throw error("Conflicting fact '" + clause.text + "'", clause);
			if (clause.text.equals("ownership"))
				attrs.put("ownership", choice("ownership", "scope", "external"));
			else {
				String sharing = choice("sharing", "single", "perScope", "transient", "matching");
				if (sharing.equals("matching")) {
					need("(");
					sharing += "(" + string() + ")";
					need(")");
				}
				attrs.put("sharing", sharing);
			}
		}
	}

	private String delegateLabel() throws AutofacParseException {
		if (peek().quoted)
			return take().text;
		final StringBuilder result = new StringBuilder();
		Token previous = null;
		while (!at("sharing") && !at("ownership") && !at(";") && !at("}")) {
			final Token token = take();
			if (previous != null && token.source == previous.source && token.offset > previous.offset + previous.text.length())
				result.append(' ');
			result.append(token.text);
			previous = token;
		}
		if (result.length() == 0)
			throw error("Expected delegate label", peek());
		return result.toString();
	}

	private List<Node> dependencies() throws AutofacParseException {
		final List<Node> result = new ArrayList<Node>();
		need("{");
		separators();
		while (!at("}")) {
			final Token start = peek();
			final String identity = id();
			need(":");
			final Map<String, String> attrs = new LinkedHashMap<String, String>();
			service(attrs, false);
			if (at("via")) {
				take();
				attrs.put("mode", choice("dependency mode", "ctor", "property"));
			}
			result.add(new Node("dependency", identity, attrs, new ArrayList<Node>(), start.source, start.offset));
			if (!at(";") && !at("}"))
				throw error("Unexpected '" + peek().text + "' in depends", peek());
			separators();
		}
		need("}");
		return result;
	}

	private String scopeOr(String reserved) throws AutofacParseException {
		if (at(reserved))
			return take().text;
		return id();
	}

	private void expectation(Token start) throws AutofacParseException {
		final String target = id();
		final String kind = choice("outcome", "success", "failure", "recursion");
		String category = kind.equals("failure") ? category() : kind.equals("recursion") ? "recursiveResolve" : null;
		final Map<String, String> facts = new LinkedHashMap<String, String>();
		final Map<String, List<String>> paths = new LinkedHashMap<String, List<String>>();
		final Map<String, StringLocated> factSources = new LinkedHashMap<String, StringLocated>();
		final Map<String, Integer> factOffsets = new LinkedHashMap<String, Integer>();
		if (at("{")) {
			take();
			separators();
			while (!at("}")) {
				final Token fact = take();
				final String key = fact.text;
				if (fact.quoted || facts.containsKey(key) || paths.containsKey(key))
					throw error("Conflicting fact '" + key + "'", fact);
				factSources.put(key, fact.source);
				factOffsets.put(key, fact.offset);
				if (Arrays.asList("path", "cycle", "chain").contains(key)) {
					final List<String> labels = new ArrayList<String>();
					labels.add(label());
					while (at("->")) {
						take();
						labels.add(label());
					}
					paths.put(key, labels);
				} else if (key.equals("category")) {
					final String value = category();
					if (category != null && !category.equals(value))
						throw error("Conflicting fact 'category'", fact);
					category = value;
					facts.put(key, value);
				} else
					facts.put(key, factValue(key, fact));
				if (!at(";") && !at("}"))
					throw error("Unexpected '" + peek().text + "' in expectation", peek());
				separators();
			}
			need("}");
		}
		if (!facts.containsKey("confidence"))
			facts.put("confidence", "declared");
		if (outcomes.containsKey(target))
			throw error("Duplicate expectation '" + target + "'", start);
		outcomes.put(target, new Outcome(kind, category, facts, paths, start.source, factSources, factOffsets));
	}

	private String factValue(String key, Token fact) throws AutofacParseException {
		if (key.equals("empty"))
			return "true";
		if (key.equals("selected")) {
			final Token reference = take();
			if (reference.quoted || REGISTRATION_REF.matcher(reference.text).matches() == false)
				throw error("Invalid qualified registration '" + reference.text + "'", reference);
			return reference.text;
		}
		if (key.equals("activation") || key.equals("instance"))
			return id();
		if (key.equals("sharing"))
			return scopeOr("none");
		if (key.equals("owner"))
			return scopeOr("external");
		if (key.equals("message") || key.equals("tag"))
			return string();
		if (key.equals("state"))
			return choice("state", "new", "reused");
		if (key.equals("confidence"))
			return choice("confidence", "declared", "derived", "observed", "uncertain");
		if (key.equals("exception"))
			return type();
		if (key.equals("cause") || key.equals("risk"))
			return label();
		throw error("Unexpected '" + key + "' in expectation", fact);
	}

	private String label() throws AutofacParseException {
		final Token token = take();
		if (!token.quoted && Arrays.asList(";", "}", "{", "->").contains(token.text))
			throw error("Expected display label", token);
		return token.text;
	}

	private String category() throws AutofacParseException {
		final Token token = take();
		if (token.quoted || !CATEGORIES.contains(token.text))
			throw error("Invalid category '" + token.text + "'", token);
		return token.text;
	}
}
