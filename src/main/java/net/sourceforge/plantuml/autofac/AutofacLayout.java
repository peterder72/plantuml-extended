package net.sourceforge.plantuml.autofac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sourceforge.plantuml.autofac.AutofacModel.Node;
import net.sourceforge.plantuml.autofac.AutofacModel.Outcome;
import net.sourceforge.plantuml.autofac.AutofacModel.StructuralWarning;
import net.sourceforge.plantuml.klimt.font.FontConfiguration;
import net.sourceforge.plantuml.klimt.font.StringBounder;
import net.sourceforge.plantuml.klimt.geom.XDimension2D;
import net.sourceforge.plantuml.style.ISkinParam;
import net.sourceforge.plantuml.style.SName;
import net.sourceforge.plantuml.style.Style;
import net.sourceforge.plantuml.style.StyleSignatureBasic;

/** Measured, logical-order geometry. No container behavior is inferred here. */
public final class AutofacLayout {
	public static final double PADDING = 16;
	public static final double GAP = 24;
	public static final double MARKER_RADIUS = 6;

	public final Map<String, ScopeBox> scopes;
	public final List<ScopeBox> scopeOrder;
	public final Map<String, EventBox> events;
	public final List<Connector> connectors;
	public final List<Panel> panels;
	public final List<UnorderedBand> unordered;
	public final boolean lanes;
	public final double width;
	public final double height;
	public final double legendY;
	public final Label legend;
	public final Label orderLabel;

	private AutofacLayout(Builder b, boolean lanes, double width, double height, double legendY,
			Label legend, Label orderLabel) {
		this.scopes = immutableMap(b.boxes);
		final List<ScopeBox> orderedScopes = new ArrayList<ScopeBox>(b.boxes.values());
		Collections.sort(orderedScopes, new Comparator<ScopeBox>() {
			public int compare(ScopeBox left, ScopeBox right) {
				return Integer.compare(left.depth, right.depth);
			}
		});
		this.scopeOrder = Collections.unmodifiableList(orderedScopes);
		this.events = immutableMap(b.eventBoxes);
		this.connectors = immutable(b.connectors);
		this.panels = immutable(b.panels);
		this.unordered = immutable(b.bands);
		this.lanes = lanes;
		this.width = width;
		this.height = height;
		this.legendY = legendY;
		this.legend = legend;
		this.orderLabel = orderLabel;
	}

	public static AutofacLayout compute(AutofacModel model, ISkinParam skinParam, StringBounder bounder) {
		return new Builder(model, skinParam, bounder).compute();
	}

	static Style style(ISkinParam skin, SName role) {
		return StyleSignatureBasic.of(SName.root, SName.element, SName.autofacDiagram, role)
				.getMergedStyle(skin.getCurrentStyleBuilder());
	}

	private static <T> List<T> immutable(List<T> values) {
		return Collections.unmodifiableList(new ArrayList<T>(values));
	}

	private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
		return Collections.unmodifiableMap(new LinkedHashMap<K, V>(values));
	}

	public static final class Label {
		public final List<String> lines;
		public final FontConfiguration font;
		public final double width;
		public final double height;
		public final double lineHeight;

		private Label(String text, FontConfiguration font, StringBounder bounder, double wrap) {
			this.font = font;
			final List<String> result = new ArrayList<String>();
			for (String paragraph : text.replace('\t', ' ').split("\n", -1)) {
				String remaining = paragraph;
				while (remaining.length() > 0 && measure(remaining, font, bounder) > wrap) {
					int low = 1;
					int high = remaining.length();
					while (low < high) {
						final int middle = (low + high + 1) / 2;
						if (measure(remaining.substring(0, middle), font, bounder) <= wrap)
							low = middle;
						else
							high = middle - 1;
					}
					int end = low;
					if (end < remaining.length() && Character.isHighSurrogate(remaining.charAt(end - 1))
							&& Character.isLowSurrogate(remaining.charAt(end)))
						end++;
					int split = end;
					for (int i = end - 1; i > end / 2; i--)
						if (Character.isWhitespace(remaining.charAt(i)) || remaining.charAt(i) == ',' || remaining.charAt(i) == '>') {
							split = i + 1;
							break;
						}
					result.add(remaining.substring(0, split));
					remaining = remaining.substring(split);
				}
				result.add(remaining);
			}
			this.lines = immutable(result);
			double maxWidth = 0;
			double maxHeight = bounder.calculateDimension(font.getFont(), "Ag").getHeight();
			for (String line : result) {
				final XDimension2D dim = bounder.calculateDimension(font.getFont(), line);
				maxWidth = Math.max(maxWidth, dim.getWidth());
				maxHeight = Math.max(maxHeight, dim.getHeight());
			}
			this.width = maxWidth;
			this.lineHeight = maxHeight + 3;
			this.height = result.size() * lineHeight;
		}

		private static double measure(String s, FontConfiguration font, StringBounder bounder) {
			return bounder.calculateDimension(font.getFont(), s).getWidth();
		}
	}

	public static final class ConstructionRow {
		public final Node node;
		public final String uid;
		public final Label label;
		public final double y;
		public final boolean module;

		private ConstructionRow(Node node, String uid, Label label, double y, boolean module) {
			this.node = node;
			this.uid = uid;
			this.label = label;
			this.y = y;
			this.module = module;
		}
	}

	public static final class ScopeBox {
		public final Node node;
		public final Label label;
		public final List<ConstructionRow> rows;
		public final double x, y, width, height, constructionWidth, constructionHeight, trackY;
		public final int depth;
		public final boolean open, failed;

		private ScopeBox(Draft d, double y, double height, double end) {
			this.node = d.node;
			this.label = d.label;
			this.rows = immutable(d.rows);
			this.x = d.x;
			this.y = y;
			this.width = Math.max(24, end - d.x);
			this.height = height;
			this.constructionWidth = d.constructionWidth;
			this.constructionHeight = d.ownHeight - PADDING;
			this.trackY = y + d.trackOffset;
			this.depth = d.depth;
			this.open = d.open;
			this.failed = d.failed;
		}

		public double right() {
			return x + width;
		}

		public double bottom() {
			return y + height;
		}
	}

	public static final class EventBox {
		public final Node node;
		public final Outcome outcome;
		public final Label label;
		public final double x, y, labelX, labelY, width, height;

		private EventBox(Node node, Outcome outcome, Label label, double x, double y) {
			this.node = node;
			this.outcome = outcome;
			this.label = label;
			this.x = x;
			this.y = y;
			this.labelX = x + 12;
			this.labelY = y - label.height - 12;
			this.width = label.width + 24;
			this.height = label.height + 32;
		}
	}

	public static final class Connector {
		public final String uid, kind;
		public final Node source;
		public final Label label;
		public final double x1, y1, x2, y2, channelY;

		private Connector(String uid, String kind, Node source, Label label,
				double x1, double y1, double x2, double y2, double channelY) {
			this.uid = uid;
			this.kind = kind;
			this.source = source;
			this.label = label;
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
			this.channelY = channelY;
		}
	}

	public static final class Panel {
		public final String target;
		public final Node node;
		public final Outcome outcome;
		public final Label label;
		public final List<Label> chain;
		public final SName role;
		public final double x, y, width, height;

		private Panel(String target, Node node, Outcome outcome, Label label, List<Label> chain,
				SName role, double x, double y) {
			this.target = target;
			this.node = node;
			this.outcome = outcome;
			this.label = label;
			this.chain = immutable(chain);
			this.role = role;
			this.x = x;
			this.y = y;
			double w = label.width + 2 * PADDING;
			double h = label.height + 2 * PADDING;
			for (int i = 0; i < chain.size(); i++) {
				w = Math.max(w, chain.get(i).width + 2 * PADDING + 2 * PADDING * (i + 1));
				h += chain.get(i).height + 2 * PADDING;
			}
			if (outcome != null && outcome.paths.containsKey("cycle"))
				h += 44;
			this.width = w;
			this.height = h;
		}
	}

	public static final class UnorderedBand {
		public final String id;
		public final Node source;
		public final Label label;
		public final double x, y, width, height;

		private UnorderedBand(String id, Node source, Label label, double x, double y, double width, double height) {
			this.id = id;
			this.source = source;
			this.label = label;
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}
	}

	private static final class Draft {
		final Node node;
		final Label label;
		final List<ConstructionRow> rows = new ArrayList<ConstructionRow>();
		final List<Draft> children = new ArrayList<Draft>();
		double constructionWidth, ownHeight, trackOffset, x, end, stackHeight;
		int depth;
		boolean open = true, failed;

		Draft(Node node, Label label) {
			this.node = node;
			this.label = label;
		}
	}

	private static final class Builder {
		final AutofacModel model;
		final ISkinParam skin;
		final StringBounder bounder;
		final Map<String, Draft> drafts = new LinkedHashMap<String, Draft>();
		final Map<String, ScopeBox> boxes = new LinkedHashMap<String, ScopeBox>();
		final Map<String, EventBox> eventBoxes = new LinkedHashMap<String, EventBox>();
		final Map<String, Label> eventLabels = new LinkedHashMap<String, Label>();
		final Map<String, Double> xs = new LinkedHashMap<String, Double>();
		final Map<String, Double> extents = new LinkedHashMap<String, Double>();
		final Map<String, Integer> stacks = new LinkedHashMap<String, Integer>();
		final Map<SName, FontConfiguration> fonts = new EnumMap<SName, FontConfiguration>(SName.class);
		final List<Connector> connectors = new ArrayList<Connector>();
		final List<Panel> panels = new ArrayList<Panel>();
		final List<UnorderedBand> bands = new ArrayList<UnorderedBand>();
		final List<Draft> roots = new ArrayList<Draft>();
		double horizon = 0;
		int maxDepth = 0;
		double channelStep;
		double scopeGap = GAP;

		Builder(AutofacModel model, ISkinParam skin, StringBounder bounder) {
			this.model = model;
			this.skin = skin;
			this.bounder = bounder;
		}

		Label label(String text, SName role, double wrap) {
			FontConfiguration font = fonts.get(role);
			if (font == null) {
				font = style(skin, role).getFontConfiguration(skin.getIHtmlColorSet());
				fonts.put(role, font);
			}
			return new Label(text, font, bounder, wrap);
		}

		AutofacLayout compute() {
			final int nesting = parallelDepth(model.statements);
			if (nesting > 0)
				scopeGap = Math.max(GAP, label("unordered except shown prerequisites", SName.autofacModule, 300).height
						+ 16 + nesting * 24);
			for (Node scope : model.scopes.values()) {
				String title = scope.id;
				if (scope.get("tag") != null)
					title += " [tag \"" + scope.get("tag") + "\"]";
				if (scope.get("parent") != null)
					title += "\nchild of " + scope.get("parent");
				if (scope.get("ownedBy") != null)
					title += "\nowned by " + scope.get("ownedBy");
				final Outcome creation = model.outcomes.get(scope.id);
				if (isFailure(creation))
					title += "\ncreation failure · " + creation.category;
				final Draft d = new Draft(scope, label(title, SName.autofacLifetime, 330));
				d.failed = isFailure(model.outcomes.get(scope.id));
				drafts.put(scope.id, d);
				construction(d);
			}
			for (Draft d : drafts.values()) {
				final Draft parent = drafts.get(d.node.get("parent"));
				if (parent == null)
					roots.add(d);
				else
					parent.children.add(d);
			}
			for (Draft root : roots)
				depth(root, 0);
			placeChronology();
			boolean lanes = false;
			for (Draft d : drafts.values()) {
				if (d.open)
					d.end = horizon + 48 + (maxDepth - d.depth) * PADDING;
				final Draft p = drafts.get(d.node.get("parent"));
				if (p != null && (d.x < p.x || (!p.open && (d.open || d.end > p.end))))
					lanes = true;
			}
			for (Node event : model.timeline) {
				final Draft d = drafts.get(event.get("scope"));
				if (d != null && !"birth".equals(event.kind) && (xs.get(event.id) < d.x || (!d.open && xs.get(event.id) > d.end)))
					lanes = true;
			}
			// Channels live above all envelopes, not through construction labels.
			final int channelCount = countConnectors();
			double y = 112 + channelCount * channelHeight() + parallelDepth(model.statements) * 32;
			if (lanes) {
				for (Draft root : roots)
					y = placeLanes(root, y);
			} else {
				for (Draft root : roots)
					y = placeNested(root, y) + GAP;
			}
			placeEvents();
			placeConnectors();
			placeBands();
			double width = horizon + 96 + maxDepth * PADDING;
			for (ScopeBox box : boxes.values())
				width = Math.max(width, box.right() + GAP);
			for (EventBox event : eventBoxes.values())
				width = Math.max(width, event.labelX + event.label.width + GAP);
			for (UnorderedBand band : bands) {
				width = Math.max(width, band.x + band.width + GAP);
				y = Math.max(y, band.y + band.height + GAP);
			}
			for (Connector c : connectors)
				width = Math.max(width, Math.min(c.x1, c.x2) + c.label.width + GAP);
			y = placePanels(y + GAP);
			for (Panel panel : panels)
				width = Math.max(width, panel.x + panel.width + GAP);
			final Label legend = label("□ registration   ○ unspecified request   ● declared success   ↶ reused object\n× declared failure   // disposal   ↩ finite authored cycle   ⇢ open lifetime", SName.autofacLifetime, 850);
			final Label order = label("Logical event order →", SName.autofacLifetime, 600);
			width = Math.max(width, legend.width + 64);
			return new AutofacLayout(this, lanes, width, y + legend.height + 48, y, legend, order);
		}

		void depth(Draft d, int value) {
			d.depth = value;
			maxDepth = Math.max(maxDepth, value);
			for (Draft child : d.children)
				depth(child, value + 1);
		}

		void construction(Draft d) {
			double y = d.label.height + 2 * PADDING;
			d.constructionWidth = d.label.width + 2 * PADDING;
			for (Node node : d.node.children) {
				if ("install".equals(node.kind)) {
					final String alias = node.get("sourceEvent", node.get("target", node.id));
					y = addRow(d, node, "install " + alias, "af-install-" + d.node.id + "-" + alias, y, true);
					final Node module = model.modules.get(node.get("resolvedModule"));
					if (module != null)
						for (Node contribution : module.children)
							y = constructionRow(d, contribution, d.node.id + "." + alias + ".", y);
				} else {
					y = constructionRow(d, node, d.node.id + ".", y);
				}
			}
			if (d.rows.isEmpty())
				y = addRow(d, d.node, "No registrations", "af-empty-" + d.node.id, y, false);
			d.ownHeight = y + 2 * PADDING;
		}

		double constructionRow(Draft d, Node n, String prefix, double y) {
			if ("register".equals(n.kind)) {
				String text = n.id + ": " + service(n);
				if ("delegate".equals(n.get("activator")))
					text += "\nvia delegate " + n.get("target", "{ declared calls }");
				else if ("instance".equals(n.get("activator")))
					text += "\ninstance " + n.get("target", "unspecified");
				else
					text += " → " + n.get("type", "unspecified");
				if (n.get("sharing") != null)
					text += "\nsharing " + n.get("sharing");
				if (n.get("ownership") != null)
					text += " · ownership " + n.get("ownership");
				return addRow(d, n, text, "af-reg-" + prefix + n.id, y, false);
			}
			if ("decorate".equals(n.kind))
				return addRow(d, n, "decorate " + service(n) + "\nwith " + n.get("type", "unspecified"), "af-decorator-" + prefix + d.rows.size(), y, false);
			if ("depends".equals(n.kind)) {
				String text = "depends " + n.get("type", "unspecified");
				for (Node dependency : n.children)
					text += "\n" + dependency.id + ": " + service(dependency) + " via " + dependency.get("mode", "unspecified");
				return addRow(d, n, text, "af-dependency-" + prefix + d.rows.size(), y, false);
			}
			return y;
		}

		double addRow(Draft d, Node n, String text, String uid, double y, boolean module) {
			final Label label = label(text, module ? SName.autofacModule : SName.autofacConstruction, 340);
			d.rows.add(new ConstructionRow(n, uid, label, y, module));
			d.constructionWidth = Math.max(d.constructionWidth, label.width + 3 * PADDING);
			return y + label.height + 12;
		}

		void placeChronology() {
			for (Node n : model.timeline) {
				final boolean birth = "birth".equals(n.kind);
				final Draft d = drafts.get(n.get("scope"));
				final Label text = birth ? null : label(eventText(n, model.outcomes.get(n.id)), eventRole(n, model.outcomes.get(n.id)), 175);
				if (text != null)
					eventLabels.put(n.id, text);
				double x = 40;
				final Set<String> predecessors = model.predecessors.get(n.id);
				if (predecessors != null)
					for (String predecessor : predecessors) {
						final Double right = extents.get(predecessor);
						if (right != null)
							x = Math.max(x, right + GAP);
					}
				if (birth && d != null && d.node.get("ownedBy") != null && xs.containsKey(d.node.get("ownedBy")))
					x = xs.get(d.node.get("ownedBy"));
				xs.put(n.id, x);
				double span = birth && d != null ? d.constructionWidth : text.width + 24;
				final Draft owned = drafts.get(n.get("ownedScope"));
				if (owned != null) {
					owned.x = x;
					span = Math.max(span, owned.constructionWidth);
					xs.put("birth-" + owned.node.id, x);
					if (owned.failed) {
						owned.open = false;
						owned.end = x + owned.constructionWidth;
					}
				}
				final double extent = x + span;
				if (owned != null)
					extents.put("birth-" + owned.node.id, extent);
				extents.put(n.id, extent);
				horizon = Math.max(horizon, extent);
				if (d == null)
					continue;
				if (birth) {
					d.x = x;
					if (d.failed) {
						d.open = false;
						d.end = x + d.constructionWidth;
					}
				} else if ("dispose".equals(n.kind) && !isFailure(model.outcomes.get(n.id))) {
					d.open = false;
					d.end = x;
				}
			}
			// Stack genuinely simultaneous markers vertically; this adds no causal edges.
			for (Draft d : drafts.values()) {
				double maxLabel = 0;
				final Map<Double, Integer> slots = new LinkedHashMap<Double, Integer>();
				for (Node n : model.timeline)
					if (!"birth".equals(n.kind) && d.node.id.equals(n.get("scope"))) {
						final Double x = xs.get(n.id);
						final int stack = slots.containsKey(x) ? slots.get(x) : 0;
						slots.put(x, stack + 1);
						stacks.put(n.id, stack);
						maxLabel = Math.max(maxLabel, eventLabels.get(n.id).height + 32);
					}
				int count = 1;
				for (int c : slots.values())
					count = Math.max(count, c);
				d.trackOffset = Math.max(d.label.height + PADDING + maxLabel, d.ownHeight - 24);
				d.ownHeight = d.trackOffset + 32 + (count - 1) * (maxLabel + 24);
				d.stackHeight = maxLabel + 24;
			}
		}

		double placeNested(Draft d, double y) {
			double bottom = y + d.ownHeight;
			for (Draft child : d.children)
				bottom = placeNested(child, bottom + scopeGap);
			bottom += d.children.isEmpty() ? 0 : PADDING;
			boxes.put(d.node.id, new ScopeBox(d, y, bottom - y, d.end));
			return bottom;
		}

		double placeLanes(Draft d, double y) {
			boxes.put(d.node.id, new ScopeBox(d, y, d.ownHeight, d.end));
			y += d.ownHeight + scopeGap;
			for (Draft child : d.children)
				y = placeLanes(child, y);
			return y;
		}

		void placeEvents() {
			for (Node n : model.timeline) {
				if ("birth".equals(n.kind))
					continue;
				final ScopeBox scope = boxes.get(n.get("scope"));
				if (scope == null)
					continue;
				final double y = scope.trackY + stacks.get(n.id) * drafts.get(scope.node.id).stackHeight;
				eventBoxes.put(n.id, new EventBox(n, model.outcomes.get(n.id), eventLabels.get(n.id), xs.get(n.id), y));
			}
		}

		int countConnectors() {
			int count = 0;
			channelStep = label("selected registration", SName.autofacLifetime, 400).height + 12;
			for (Draft d : drafts.values())
				if (d.node.get("ownedBy") != null) {
					count++;
					reserveChannel("owned acquisition " + d.node.get("ownedBy"));
				}
			for (Draft d : drafts.values())
				for (ConstructionRow row : d.rows) {
					if (row.module) {
						count++;
						final String alias = row.node.get("sourceEvent", row.node.get("target", row.node.id));
						reserveChannel("module " + alias + " → " + d.node.id + " construction");
					}
					if ("register".equals(row.node.kind))
						for (Node call : row.node.children) {
							count++;
							reserveChannel("declared call: " + service(call));
						}
				}
			for (Node n : model.timeline) {
				if (n.get("during") != null) {
					count++;
					reserveChannel("explicit call");
				}
				final Outcome o = model.outcomes.get(n.id);
				if (o != null && o.get("selected") != null)
					count++;
			}
			return count;
		}

		void reserveChannel(String text) {
			channelStep = Math.max(channelStep, label(text, SName.autofacModule, 400).height + 12);
		}

		double channelHeight() {
			return channelStep;
		}

		void addConnector(String uid, String kind, Node node, String text, double x1, double y1, double x2, double y2) {
			final Label label = label(text, "lookup".equals(kind) ? SName.autofacLifetime : SName.autofacModule, 400);
			final double channel = 40 + (connectors.size() + 1) * channelHeight();
			connectors.add(new Connector(uid, kind, node, label, x1, y1, x2, y2, channel));
		}

		void placeConnectors() {
			for (Draft d : drafts.values()) {
				final EventBox acquisition = eventBoxes.get(d.node.get("ownedBy"));
				final ScopeBox scope = boxes.get(d.node.id);
				if (acquisition != null)
					addConnector("af-owned-" + d.node.id, "provenance", d.node,
							"owned acquisition " + d.node.get("ownedBy"), acquisition.x, acquisition.y,
							scope.x + scope.constructionWidth, scope.y);
			}
			for (Draft draft : drafts.values()) {
				final ScopeBox box = boxes.get(draft.node.id);
				for (ConstructionRow row : box.rows) {
					if (row.module) {
						final String alias = row.node.get("sourceEvent", row.node.get("target", row.node.id));
						final EventBox source = eventBoxes.get(alias);
						if (source != null)
							addConnector("af-provenance-" + box.node.id + "-" + alias, "provenance", row.node,
									"module " + alias + " → " + box.node.id + " construction", source.x, source.y,
									box.x + box.constructionWidth, box.y + row.y + row.label.height / 2);
					}
					if ("register".equals(row.node.kind))
						for (Node call : row.node.children) {
							final ScopeBox target = boxes.get(call.get("scope"));
							if (target != null)
								addConnector(row.uid + "-call-" + connectors.size(), "call", call,
										"declared call: " + service(call), box.x + box.constructionWidth,
										box.y + row.y + row.label.height / 2, target.x + target.constructionWidth, target.y);
						}
				}
			}
			for (EventBox event : eventBoxes.values()) {
				final EventBox during = eventBoxes.get(event.node.get("during"));
				if (during != null)
					addConnector("af-call-" + event.node.id, "call", event.node, "explicit call", during.x, during.y, event.x, event.y);
				if (event.outcome == null || event.outcome.get("selected") == null || (!model.expanded.contains(event.node.id) && !"diagnostics".equals(model.view)))
					continue;
				for (ScopeBox box : boxes.values())
					for (ConstructionRow row : box.rows)
						if (row.uid.equals("af-reg-" + event.outcome.get("selected")))
							addConnector("af-lookup-" + event.node.id, "lookup", event.node, "selected registration",
									event.x, event.y, box.x + box.constructionWidth, box.y + row.y + row.label.height / 2);
			}
		}

		int parallelDepth(List<Node> nodes) {
			int depth = 0;
			for (Node node : nodes)
				depth = Math.max(depth, ("parallel".equals(node.kind) ? 1 : 0) + parallelDepth(node.children));
			return depth;
		}

		void collectParallel(List<Node> nodes, List<String> enclosing, Map<String, List<Node>> groups,
				Map<String, Integer> depths) {
			for (Node node : nodes) {
				List<String> active = enclosing;
				if ("parallel".equals(node.kind)) {
					active = new ArrayList<String>(enclosing);
					active.add(node.id);
					groups.put(node.id, new ArrayList<Node>());
					depths.put(node.id, enclosing.size());
				}
				for (Node runtime : model.timeline)
					if (runtime.source == node.source && runtime.offset == node.offset)
						for (String id : active)
							if (!groups.get(id).contains(runtime))
								groups.get(id).add(runtime);
				collectParallel(node.children, active, groups, depths);
			}
		}

		void placeBands() {
			final Map<String, List<Node>> groups = new LinkedHashMap<String, List<Node>>();
			final Map<String, Integer> depths = new LinkedHashMap<String, Integer>();
			collectParallel(model.statements, Collections.<String>emptyList(), groups, depths);
			final int nesting = parallelDepth(model.statements);
			for (Node n : model.timeline) {
				final String id = n.get("parallel");
				if (id == null)
					continue;
				if (!groups.containsKey(id))
					groups.put(id, new ArrayList<Node>());
				if (!groups.get(id).contains(n))
					groups.get(id).add(n);
			}
			for (Map.Entry<String, List<Node>> entry : groups.entrySet()) {
				double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = 0, maxY = 0;
				final Set<Double> columns = new LinkedHashSet<Double>();
				for (Node n : entry.getValue()) {
					columns.add(xs.get(n.id));
					final EventBox e = eventBoxes.get(n.id);
					final ScopeBox s = boxes.get(n.get("scope"));
					if (s == null)
						continue;
					minX = Math.min(minX, xs.get(n.id) - 10);
					maxX = Math.max(maxX, extents.get(n.id) + 10);
					minY = Math.min(minY, e == null ? s.y : e.labelY - 8);
					maxY = Math.max(maxY, e == null ? s.y + s.constructionHeight : e.y + 16);
				}
				if (minX == Double.MAX_VALUE)
					continue;
				final Label label = label(columns.size() > 1 ? "unordered except shown prerequisites" : "unordered", SName.autofacModule, 300);
				final int level = depths.containsKey(entry.getKey()) ? depths.get(entry.getKey()) : 0;
				final double inset = Math.max(0, nesting - level - 1) * 12;
				final double left = Math.max(4, minX - inset);
				final double top = minY - label.height - 8 - inset * 2;
				bands.add(new UnorderedBand(entry.getKey(), entry.getValue().get(0), label, left, top,
						Math.max(maxX + inset - left, label.width + 16), maxY + inset + 8 - top));
			}
		}

		double placePanels(double y) {
			final Set<String> targets = new LinkedHashSet<String>(model.expanded);
			if ("diagnostics".equals(model.view))
				for (Map.Entry<String, Outcome> e : model.outcomes.entrySet())
					if (isFailure(e.getValue()) || "recursion".equals(e.getValue().kind) || e.getValue().category != null || e.getValue().paths.containsKey("cycle"))
						targets.add(e.getKey());
			for (String id : targets) {
				final Node n = model.events.containsKey(id) ? model.events.get(id) : model.scopes.get(id);
				final Outcome o = model.outcomes.get(id);
				final StringBuilder text = new StringBuilder(id + ": " + (n == null ? "detail" : eventDescription(n)));
				text.append("\noutcome: ").append(o == null ? "unspecified" : o.kind);
				if (o != null && o.category != null)
					text.append(" · ").append(o.category);
				text.append("\nselected registration: ").append(fact(o, "selected"));
				final Node registration = o == null ? null : model.registrations.get(o.get("selected"));
				text.append("\nactivator: ").append(registration == null ? "unspecified" : registration.get("activator", "unspecified"));
				if (registration != null)
					text.append(" ").append(registration.get("type", registration.get("target", "")));
				text.append("\nactivation: ").append(fact(o, "activation"));
				text.append("\nsharing scope: ").append(fact(o, "sharing"));
				text.append("\nowner: ").append(fact(o, "owner"));
				text.append("\ninstance: ").append(fact(o, "instance")).append(" · state: ").append(fact(o, "state"));
				text.append("\nconfidence: ").append(o == null ? "unspecified" : o.get("confidence", "declared"));
				if (o != null) {
					for (String key : new String[] { "cause", "risk", "exception", "message", "tag" })
						if (o.get(key) != null)
							text.append('\n').append(key).append(": ").append(o.get(key));
					if (o.get("empty") != null)
						text.append("\nempty collection (declared)");
					for (String key : new String[] { "path", "cycle" })
						if (o.paths.containsKey(key))
							text.append('\n').append(key).append(": ").append(join(o.paths.get(key), " → "));
				}
				final List<Label> chain = new ArrayList<Label>();
				if (o != null && o.paths.containsKey("chain"))
					for (String object : o.paths.get("chain"))
						chain.add(label(object, SName.autofacResolve, 400));
				final SName role = isFailure(o) || (o != null && "recursion".equals(o.kind)) ? SName.autofacFailure : SName.autofacLifetime;
				final Panel panel = new Panel(id, n, o, label(text.toString(), role, 650), chain, role, 40, y);
				panels.add(panel);
				y += panel.height + GAP;
			}
			for (Node object : model.objects.values()) {
				if ("instance".equals(object.kind) == false || object.attrs.containsKey("introducedBy"))
					continue;
				final Panel panel = new Panel("instance-" + object.id, object, null,
						label("pre-existing instance " + object.id + ": " + object.get("type", "unspecified")
								+ "\nowner: " + object.get("owner", "unspecified"), SName.autofacLifetime, 650),
						Collections.<Label>emptyList(), SName.autofacLifetime, 40, y);
				panels.add(panel);
				y += panel.height + GAP;
			}
			for (Node definition : model.statements) {
				if (!"depends".equals(definition.kind))
					continue;
				String text = "declared dependencies: " + definition.get("type", "unspecified");
				for (Node dependency : definition.children)
					text += "\n" + dependency.id + ": " + service(dependency) + " via " + dependency.get("mode", "unspecified");
				final Panel panel = new Panel("dependencies-" + panels.size(), definition, null,
						label(text, SName.autofacConstruction, 650), Collections.<Label>emptyList(),
						SName.autofacConstruction, 40, y);
				panels.add(panel);
				y += panel.height + GAP;
			}
			for (StructuralWarning warning : model.warnings) {
				Node n = model.events.containsKey(warning.target) ? model.events.get(warning.target)
						: model.scopes.get(warning.target);
				if (n == null)
					for (Node event : model.timeline)
						if (event.id.equals(warning.target)) {
							n = event;
							break;
						}
				final Panel panel = new Panel("warning-" + warning.target + "-" + panels.size(), n, null,
						label(warning.code + ": " + warning.message, SName.autofacWarning, 650), Collections.<Label>emptyList(), SName.autofacWarning, 40, y);
				panels.add(panel);
				y += panel.height + GAP;
			}
			return y;
		}
	}

	static boolean isFailure(Outcome outcome) {
		return outcome != null && "failure".equals(outcome.kind);
	}

	static SName eventRole(Node node, Outcome outcome) {
		if (isFailure(outcome) || (outcome != null && "recursion".equals(outcome.kind)))
			return SName.autofacFailure;
		if ("dispose".equals(node.kind))
			return SName.autofacDispose;
		return outcome != null && "success".equals(outcome.kind) ? SName.autofacResolve : SName.autofacLifetime;
	}

	static String service(Node n) {
		final String type = n.get("service", n.get("type", "unspecified"));
		if (n.get("key") == null)
			return type;
		final String key = "true".equals(n.get("keyWildcard")) ? "*" : "\"" + n.get("key") + "\"";
		return type + " " + n.get("keyKind", "key") + " " + key;
	}

	static String eventDescription(Node n) {
		if ("invoke".equals(n.kind))
			return "invoke " + n.get("target", "unspecified") + "() from " + n.get("scope", "unspecified");
		if ("evaluate".equals(n.kind))
			return "evaluate " + n.get("target", "unspecified") + ".Value from " + n.get("scope", "unspecified");
		if ("dispose".equals(n.kind))
			return "dispose " + n.get("target", n.get("scope", "unspecified"));
		if ("lifetime".equals(n.kind))
			return "lifetime " + n.id;
		return n.kind + " " + service(n) + " from " + n.get("scope", "unspecified");
	}

	static String eventText(Node n, Outcome outcome) {
		String text = n.id + ": " + eventDescription(n);
		if (outcome == null)
			return text + ("dispose".equals(n.kind) ? "\nterminal disposal" : "\nunspecified");
		text += "\n" + outcome.kind;
		if (outcome.category != null)
			text += " · " + outcome.category;
		if (outcome.get("state") != null)
			text += " · " + outcome.get("state");
		if (outcome.get("instance") != null)
			text += "\nobject " + outcome.get("instance");
		if (outcome.get("empty") != null)
			text += "\nempty collection";
		if ("dispose".equals(n.kind) && isFailure(outcome))
			text += "\nlifecycle unspecified";
		if ("recursion".equals(outcome.kind))
			text += "\n" + outcome.get("confidence", "declared") + " · risk " + outcome.get("risk", "unspecified");
		if (outcome.paths.containsKey("cycle"))
			text += "\ncycle: " + join(outcome.paths.get("cycle"), " → ");
		return text;
	}

	private static String fact(Outcome outcome, String name) {
		return outcome == null ? "unspecified" : outcome.get(name, "unspecified");
	}

	private static String join(List<String> items, String separator) {
		final StringBuilder result = new StringBuilder();
		for (String item : items) {
			if (result.length() > 0)
				result.append(separator);
			result.append(item);
		}
		return result.toString();
	}
}
