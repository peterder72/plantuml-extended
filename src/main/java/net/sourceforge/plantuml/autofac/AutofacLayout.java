package net.sourceforge.plantuml.autofac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.sourceforge.plantuml.autofac.AutofacModel.Node;
import net.sourceforge.plantuml.autofac.AutofacModel.Outcome;
import net.sourceforge.plantuml.klimt.font.FontConfiguration;
import net.sourceforge.plantuml.klimt.font.StringBounder;
import net.sourceforge.plantuml.klimt.geom.XDimension2D;
import net.sourceforge.plantuml.style.ISkinParam;
import net.sourceforge.plantuml.style.SName;
import net.sourceforge.plantuml.style.Style;
import net.sourceforge.plantuml.style.StyleSignatureBasic;

/** Measured logical order, not elapsed time or inferred container behavior. */
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
	public final double width, height, legendX, legendY, orderX, orderY;
	public final Label legend, orderLabel;

	private AutofacLayout(Builder b, boolean lanes, double width, double height, double footer,
			Label legend, Label order) {
		this.scopes = immutableMap(b.boxes);
		final List<ScopeBox> ordered = new ArrayList<ScopeBox>(b.boxes.values());
		Collections.sort(ordered, new Comparator<ScopeBox>() {
			public int compare(ScopeBox a, ScopeBox c) {
				return Integer.compare(a.depth, c.depth);
			}
		});
		this.scopeOrder = immutable(ordered);
		this.events = immutableMap(b.eventBoxes);
		this.connectors = immutable(b.connectors);
		this.panels = immutable(b.panels);
		this.unordered = immutable(b.bands);
		this.lanes = lanes;
		this.width = width;
		this.height = height;
		this.legendX = PADDING;
		this.legendY = footer;
		this.orderX = Math.max(PADDING, width - order.width - PADDING);
		this.orderY = footer + legend.height + 6;
		this.legend = legend;
		this.orderLabel = order;
	}

	public static AutofacLayout compute(AutofacModel model, ISkinParam skin, StringBounder bounder) {
		return new Builder(model, skin, bounder).compute();
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
		public final double width, height, lineHeight;

		private Label(String text, FontConfiguration font, StringBounder bounder, double wrap) {
			this.font = font;
			final List<String> result = new ArrayList<String>();
			if (text.length() > 0)
				for (String paragraph : text.replace('\t', ' ').split("\n", -1)) {
					String remaining = paragraph;
					while (remaining.length() > 0 && measure(remaining, font, bounder) > wrap) {
						int low = 1, high = remaining.length();
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
			double w = 0;
			double h = bounder.calculateDimension(font.getFont(), "Ag").getHeight();
			for (String line : result) {
				final XDimension2D dim = bounder.calculateDimension(font.getFont(), line);
				w = Math.max(w, dim.getWidth());
				h = Math.max(h, dim.getHeight());
			}
			this.width = w;
			this.lineHeight = h + 3;
			this.height = result.size() * lineHeight;
		}

		private static double measure(String text, FontConfiguration font, StringBounder bounder) {
			return bounder.calculateDimension(font.getFont(), text).getWidth();
		}
	}

	/** Row text coordinates are relative to its scope; all other public geometry is absolute. */
	public static final class ConstructionRow {
		public final Node node;
		public final String uid;
		public final Label label, metadata;
		public final double labelX, y, metadataY;
		public final boolean module;

		private ConstructionRow(Node node, String uid, Label label, Label metadata, double y, boolean module) {
			this.node = node;
			this.uid = uid;
			this.label = label;
			this.metadata = metadata;
			this.labelX = PADDING + 12;
			this.y = y;
			this.metadataY = y + label.height + (metadata.height > 0 ? 2 : 0);
			this.module = module;
		}
	}

	public static final class ScopeBox {
		public final Node node;
		public final Label header, subtitle, status, constructionTitle, constructionFooter, activeTitle;
		public final List<ConstructionRow> rows;
		public final double x, y, width, height, constructionX, constructionY, constructionWidth, constructionHeight;
		public final double headerX, headerY, subtitleX, subtitleY, statusX, statusY;
		public final double constructionTitleX, constructionTitleY, constructionFooterX, constructionFooterY;
		public final double activeTitleX, activeTitleY, activeStartX, trackY, trackEndX;
		public final int depth;
		public final boolean open, failed;

		private ScopeBox(Draft d, double y, double height, double right) {
			this.node = d.node;
			this.header = d.header;
			this.subtitle = d.subtitle;
			this.status = d.status;
			this.constructionTitle = d.constructionTitle;
			this.constructionFooter = d.footer;
			this.activeTitle = d.activeTitle;
			this.rows = immutable(d.rows);
			this.x = d.x;
			this.y = y;
			this.width = right - x;
			this.height = height;
			this.headerX = x + PADDING;
			this.headerY = y + PADDING;
			this.subtitleX = headerX;
			this.subtitleY = headerY + header.height + 3;
			this.statusX = right - PADDING - status.width;
			this.statusY = headerY;
			this.constructionX = x + PADDING;
			this.constructionY = y + d.cardTop;
			this.constructionWidth = d.constructionWidth;
			this.constructionHeight = d.constructionHeight;
			this.constructionTitleX = constructionX + 12;
			this.constructionTitleY = constructionY + 10;
			this.constructionFooterX = constructionX + 12;
			this.constructionFooterY = constructionY + constructionHeight - d.footer.height - 10;
			this.activeStartX = constructionX + constructionWidth;
			this.activeTitleX = activeStartX + PADDING;
			this.activeTitleY = constructionTitleY;
			this.trackY = y + d.trackOffset;
			this.trackEndX = d.end;
			this.depth = d.depth;
			this.open = d.open;
			this.failed = d.failed;
		}

		public double right() { return x + width; }
		public double bottom() { return y + height; }
	}

	public static final class EventBox {
		public final Node node;
		public final Outcome outcome;
		public final Label label, action, detail;
		public final double x, y, labelX, labelY, actionX, actionY, detailX, detailY;
		public final double width, height, left, top, feedbackX, feedbackY, feedbackWidth, feedbackHeight;

		private EventBox(EventDraft d, double x, double y) {
			this.node = d.node;
			this.outcome = d.outcome;
			this.label = d.label;
			this.action = d.action;
			this.detail = d.detail;
			this.x = x;
			this.y = y;
			this.labelX = x - label.width / 2;
			this.labelY = y + 14;
			this.actionX = x - action.width / 2;
			this.actionY = y - 14 - action.height;
			this.detailX = x - detail.width / 2;
			this.detailY = labelY + label.height + (detail.height > 0 ? 3 : 0);
			this.width = d.width;
			this.height = d.above + d.below;
			this.left = x - width / 2;
			this.top = y - d.above;
			this.feedbackWidth = d.feedback ? Math.min(88, Math.max(40, width - 12)) : 0;
			this.feedbackHeight = d.feedback ? 18 : 0;
			this.feedbackX = x - feedbackWidth / 2;
			this.feedbackY = actionY - 26;
		}

		public double right() { return left + width; }
		public double bottom() { return top + height; }
	}

	public static final class Connector {
		public final String uid, kind;
		public final Node source;
		public final Label label;
		public final double x1, y1, x2, y2, channelY, labelX, labelY;
		public final boolean local;

		private Connector(String uid, String kind, Node source, Label label, double x1, double y1,
				double x2, double y2, double channelY, double labelX, double labelY, boolean local) {
			this.uid = uid;
			this.kind = kind;
			this.source = source;
			this.label = label;
			this.x1 = x1;
			this.y1 = y1;
			this.x2 = x2;
			this.y2 = y2;
			this.channelY = channelY;
			this.labelX = labelX;
			this.labelY = labelY;
			this.local = local;
		}
	}

	public static final class Panel {
		public final String target;
		public final Node node;
		public final Outcome outcome;
		public final Label title, label;
		public final List<Label> chain;
		public final SName role;
		public final double x, y, width, height, titleX, titleY, labelX, labelY, chainY, targetX, targetY;
		public final boolean leader;

		private Panel(Card card, double x, double y, double targetY, boolean leader) {
			this.target = card.target;
			this.node = card.node;
			this.outcome = card.outcome;
			this.title = card.title;
			this.label = card.body;
			this.chain = immutable(card.chain);
			this.role = card.role;
			this.x = x;
			this.y = y;
			this.width = card.width;
			this.height = card.height;
			this.titleX = x + PADDING;
			this.titleY = y + 12;
			this.labelX = titleX;
			this.labelY = titleY + title.height + 6;
			this.chainY = labelY + label.height + (chain.isEmpty() ? 0 : 10);
			this.targetX = card.targetX;
			this.targetY = targetY;
			this.leader = leader;
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

	private static final class BandBounds {
		double left, top, right, bottom;

		BandBounds(double left, double top, double right, double bottom) {
			this.left = left;
			this.top = top;
			this.right = right;
			this.bottom = bottom;
		}
	}

	private static final class Draft {
		final Node node;
		final Label header, subtitle, constructionTitle, footer, activeTitle;
		Label status;
		final List<ConstructionRow> rows = new ArrayList<ConstructionRow>();
		final List<Draft> children = new ArrayList<Draft>();
		final List<EventDraft> events = new ArrayList<EventDraft>();
		double constructionWidth, constructionHeight, cardTop, ownHeight, trackOffset;
		double x, end, right, headerWidth, above, below, traceHeight, traceWidth;
		double bandBottomPadding = PADDING;
		int depth;
		boolean open = true, failed, lookup;

		Draft(Node node, Label header, Label subtitle, Label constructionTitle, Label footer, Label activeTitle) {
			this.node = node;
			this.header = header;
			this.subtitle = subtitle;
			this.constructionTitle = constructionTitle;
			this.footer = footer;
			this.activeTitle = activeTitle;
		}
	}

	private static final class EventDraft {
		final Node node;
		final Outcome outcome;
		final Label label, action, detail;
		final double width, above, below;
		final boolean feedback;
		double stackOffset, gutter;

		EventDraft(Node node, Outcome outcome, Label label, Label action, Label detail) {
			this.node = node;
			this.outcome = outcome;
			this.label = label;
			this.action = action;
			this.detail = detail;
			this.feedback = outcome != null && ("reused".equals(outcome.get("state"))
					|| outcome.paths.containsKey("cycle") || "recursion".equals(outcome.kind));
			this.width = Math.max(40, Math.max(label.width, Math.max(action.width, detail.width))) + 12;
			this.above = action.height + 14 + (feedback ? 30 : 0);
			this.below = 14 + label.height + (detail.height > 0 ? 3 + detail.height : 0);
		}
	}

	private static final class Card {
		final String target;
		final Node node;
		final Outcome outcome;
		final Label title, body;
		final List<Label> chain;
		final SName role;
		final double width, height, targetX;

		Card(String target, Node node, Outcome outcome, Label title, Label body, List<Label> chain,
				SName role, double targetX) {
			this.target = target;
			this.node = node;
			this.outcome = outcome;
			this.title = title;
			this.body = body;
			this.chain = chain;
			this.role = role;
			this.targetX = targetX;
			double w = Math.max(title.width, body.width) + 2 * PADDING;
			double h = 24 + title.height + 6 + body.height;
			for (int i = 0; i < chain.size(); i++) {
				w = Math.max(w, chain.get(i).width + 2 * PADDING * (i + 2));
				h += chain.get(i).height + 2 * PADDING;
			}
			if (chain.isEmpty() == false)
				h += 10;
			if (outcome != null && outcome.paths.containsKey("cycle"))
				h += 44;
			this.width = Math.max(240, w);
			this.height = h;
		}
	}

	private static final class TraceLane {
		final Draft scope;
		final Label label;
		final double offset;

		TraceLane(Draft scope, Label label) {
			this.scope = scope;
			this.label = label;
			this.offset = scope.traceHeight;
			scope.traceHeight += label.height + 10;
			scope.traceWidth = Math.max(scope.traceWidth, label.width);
		}
	}

	private static final class Builder {
		final AutofacModel model;
		final ISkinParam skin;
		final StringBounder bounder;
		final Map<String, Draft> drafts = new LinkedHashMap<String, Draft>();
		final Map<String, ScopeBox> boxes = new LinkedHashMap<String, ScopeBox>();
		final Map<String, EventBox> eventBoxes = new LinkedHashMap<String, EventBox>();
		final Map<String, EventDraft> eventDrafts = new LinkedHashMap<String, EventDraft>();
		final Map<String, Double> xs = new LinkedHashMap<String, Double>();
		final Map<String, Double> extents = new LinkedHashMap<String, Double>();
		final Map<SName, FontConfiguration> fonts = new EnumMap<SName, FontConfiguration>(SName.class);
		final Map<String, ConstructionRow> registrationRows = new LinkedHashMap<String, ConstructionRow>();
		final Map<String, Draft> registrationScopes = new LinkedHashMap<String, Draft>();
		final Map<String, TraceLane> traceLanes = new LinkedHashMap<String, TraceLane>();
		final List<Connector> connectors = new ArrayList<Connector>();
		final List<Panel> panels = new ArrayList<Panel>();
		final List<UnorderedBand> bands = new ArrayList<UnorderedBand>();
		final List<Draft> roots = new ArrayList<Draft>();
		final Map<String, List<Node>> parallelGroups = new LinkedHashMap<String, List<Node>>();
		final Map<String, Integer> parallelDepths = new LinkedHashMap<String, Integer>();
		final Map<String, Label> parallelLabels = new LinkedHashMap<String, Label>();
		double horizon, scopeGap = GAP;
		int maxDepth, parallelNesting;

		Builder(AutofacModel model, ISkinParam skin, StringBounder bounder) {
			this.model = model;
			this.skin = skin;
			this.bounder = bounder;
		}

		Label label(String text, SName role, double wrap) {
			FontConfiguration font = fonts.get(role);
			if (font == null) {
				font = style(skin, role).getFontConfiguration(skin.getIHtmlColorSet());
				if (role == SName.autofacMuted)
					font = font.changeSize((float) Math.max(9, font.getSize2D() * 0.86));
				fonts.put(role, font);
			}
			return new Label(text, font, bounder, wrap);
		}

		AutofacLayout compute() {
			parallelNesting = parallelDepth(model.statements);
			if (parallelNesting > 0)
				scopeGap = GAP + parallelNesting * 16;
			for (Node scope : model.scopes.values()) {
				final String id = scope.id.toUpperCase(Locale.ROOT);
				String subtitle = scope.get("parent") == null ? "" : "child of " + scope.get("parent");
				if (scope.get("tag") != null)
					subtitle += (subtitle.length() == 0 ? "" : " · ") + "tag \"" + scope.get("tag") + "\"";
				if (scope.get("ownedBy") != null)
					subtitle += " · owned by " + scope.get("ownedBy");
				final Draft d = new Draft(scope, label(id + " LIFETIME", SName.autofacHeading, 420),
						label(subtitle, SName.autofacMuted, 420), label("Construction", SName.autofacHeading, 300),
						label("sealed before active events", SName.autofacMuted, 300),
						label("ACTIVE " + id, SName.autofacHeading, 420));
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
			measureEvents();
			placeChronology();
			boolean lanes = false;
			for (Draft d : drafts.values()) {
				if (d.open)
					d.end = horizon + 48 + (maxDepth - d.depth) * PADDING;
				final Draft parent = drafts.get(d.node.get("parent"));
				if (parent != null && (d.x < parent.x || (parent.open == false && (d.open || d.end > parent.end))))
					lanes = true;
				for (EventDraft e : d.events)
					if (xs.get(e.node.id) < d.x || (d.open == false && xs.get(e.node.id) > d.end))
						lanes = true;
				d.status = label(d.failed ? "creation failed" : d.open ? "remains active" : "disposed", SName.autofacMuted, 240);
				d.headerWidth = d.header.width + d.status.width + 3 * PADDING;
				d.right = Math.max(d.end + PADDING, Math.max(d.x + d.headerWidth, d.x + d.subtitle.width + 2 * PADDING));
				for (EventDraft e : d.events)
					if (xs.get(e.node.id) >= d.x && (d.open || xs.get(e.node.id) <= d.end))
						d.right = Math.max(d.right, xs.get(e.node.id) + e.width / 2 + PADDING);
				d.right = Math.max(d.right, d.x + PADDING + d.constructionWidth
						+ (d.failed ? PADDING : Math.max(d.activeTitle.width, d.traceWidth) + 2 * PADDING));
			}
			double bottom = PADDING;
			for (Draft root : roots)
				bottom = (lanes ? placeLanes(root, bottom) : placeNested(root, bottom)) + GAP;
			placeEvents();
			placeConnectors();
			placeBands();
			double width = PADDING;
			for (ScopeBox box : boxes.values())
				width = Math.max(width, box.right() + PADDING);
			for (EventBox event : eventBoxes.values()) {
				width = Math.max(width, event.right() + PADDING);
				bottom = Math.max(bottom, event.bottom() + GAP);
			}
			for (Connector c : connectors)
				width = Math.max(width, c.labelX + c.label.width + PADDING);
			for (UnorderedBand band : bands) {
				width = Math.max(width, band.x + band.width + PADDING);
				bottom = Math.max(bottom, band.y + band.height + GAP);
			}
			final Label legend = label("□ registration   ○ unspecified   ● success   ↶ reused   × failure   // disposal   ↩ authored cycle\nDotted: binding lookup", SName.autofacMuted, 1000);
			final Label order = label("Logical event order →", SName.autofacMuted, 600);
			width = Math.max(width, legend.width + 2 * PADDING);
			final List<Card> cards = cards();
			for (Card card : cards)
				width = Math.max(width, card.width + 2 * PADDING);
			bottom = placePanels(cards, bottom, width);
			return new AutofacLayout(this, lanes, width, bottom + legend.height + order.height + 22, bottom, legend, order);
		}

		void depth(Draft d, int value) {
			d.depth = value;
			maxDepth = Math.max(maxDepth, value);
			for (Draft child : d.children)
				depth(child, value + 1);
		}

		void construction(Draft d) {
			d.cardTop = PADDING + d.header.height + (d.subtitle.height == 0 ? 12 : 3 + d.subtitle.height + 12);
			d.constructionWidth = Math.max(d.constructionTitle.width, d.footer.width) + 24;
			double y = d.cardTop + 10 + d.constructionTitle.height + 12;
			for (Node node : d.node.children)
				if ("install".equals(node.kind)) {
					final String alias = node.get("sourceEvent", node.get("target", node.id));
					y = addRow(d, node, "install " + alias, "", "af-install-" + d.node.id + "-" + alias, y, true);
					final Node module = model.modules.get(node.get("resolvedModule"));
					if (module != null)
						for (Node contribution : module.children)
							y = constructionRow(d, contribution, d.node.id + "." + alias + ".", y);
				} else
					y = constructionRow(d, node, d.node.id + ".", y);
			if (d.rows.isEmpty())
				y = addRow(d, d.node, "No registrations", "", "af-empty-" + d.node.id, y, false);
			d.constructionHeight = y - d.cardTop + 6 + d.footer.height + 10;
		}

		double constructionRow(Draft d, Node n, String prefix, double y) {
			if ("register".equals(n.kind)) {
				String text = service(n);
				if ("delegate".equals(n.get("activator")))
					text += " → delegate " + n.get("target", "{ declared calls }");
				else if ("instance".equals(n.get("activator")))
					text += " → instance " + n.get("target", "unspecified");
				else
					text += " → " + n.get("type", "unspecified");
				String metadata = n.get("sharing", "");
				if (n.get("ownership") != null)
					metadata += (metadata.length() == 0 ? "" : " · ") + "owner " + n.get("ownership");
				return addRow(d, n, text, metadata, "af-reg-" + prefix + n.id, y, false);
			}
			if ("decorate".equals(n.kind))
				return addRow(d, n, "Decorator: " + n.get("type", "unspecified"), service(n), "af-decorator-" + prefix + d.rows.size(), y, false);
			if ("depends".equals(n.kind)) {
				final StringBuilder text = new StringBuilder("Dependencies: " + n.get("type", "unspecified"));
				for (Node dependency : n.children)
					text.append('\n').append(dependency.id).append(": ").append(service(dependency)).append(" · ").append(dependency.get("mode", "unspecified"));
				return addRow(d, n, text.toString(), "", "af-dependency-" + prefix + d.rows.size(), y, false);
			}
			return y;
		}

		double addRow(Draft d, Node node, String text, String metadata, String uid, double y, boolean module) {
			final Label main = label(text, module ? SName.autofacModule : SName.autofacConstruction, 300);
			final Label meta = label(metadata, SName.autofacMuted, 300);
			final ConstructionRow row = new ConstructionRow(node, uid, main, meta, y, module);
			d.rows.add(row);
			d.constructionWidth = Math.max(d.constructionWidth, Math.max(main.width, meta.width) + 40);
			if ("register".equals(node.kind)) {
				registrationRows.put(uid, row);
				registrationScopes.put(uid, d);
			}
			return row.metadataY + meta.height + 7;
		}

		void measureEvents() {
			for (Node n : model.timeline) {
				if ("birth".equals(n.kind))
					continue;
				final Outcome o = model.outcomes.get(n.id);
				final SName role = eventRole(n, o);
				final String action = "dispose".equals(n.kind) ? "Dispose" : isFailure(o) ? "failed" : "call".equals(n.kind) ? "call" : n.kind;
				final String service = "dispose".equals(n.kind) ? "" : "invoke".equals(n.kind) ? n.get("target", "") + "()"
						: "evaluate".equals(n.kind) ? n.get("target", "") + ".Value" : service(n);
				final EventDraft e = new EventDraft(n, o, label(service, SName.autofacLifetime, 150),
						label(action, role, 150), label(eventDetail(n, o), SName.autofacMuted, 160));
				eventDrafts.put(n.id, e);
				final Draft d = drafts.get(n.get("scope"));
				if (d != null) {
					d.events.add(e);
					d.above = Math.max(d.above, e.above);
					d.below = Math.max(d.below, e.below);
				}
			}
			// Reserve only local, measured strips; view projections must not move chronology.
			for (EventDraft event : eventDrafts.values()) {
				if (event.outcome != null && event.outcome.get("selected") != null) {
					final Draft selected = registrationScopes.get("af-reg-" + event.outcome.get("selected"));
					if (selected != null && selected.lookup == false) {
						selected.lookup = true;
						selected.traceHeight += 12;
					}
				}
			}
			for (EventDraft event : eventDrafts.values()) {
				final EventDraft during = eventDrafts.get(event.node.get("during"));
				if (during != null)
					traceLanes.put("af-call-" + event.node.id, new TraceLane(drafts.get(during.node.get("scope")),
							label("explicit call", SName.autofacModule, 250)));
			}
			for (Draft d : drafts.values())
				for (ConstructionRow row : d.rows)
					if ("register".equals(row.node.kind))
						for (int i = 0; i < row.node.children.size(); i++)
							traceLanes.put(row.uid + "-call-" + i, new TraceLane(d,
									label("declared call: " + service(row.node.children.get(i)), SName.autofacModule, 250)));
		}

		void placeChronology() {
			// A column is a causal rank, not a source-order edge. Incomparables share its center.
			final Map<String, Integer> ranks = new LinkedHashMap<String, Integer>();
			final List<Double> leftSpans = new ArrayList<Double>();
			final List<Double> rightSpans = new ArrayList<Double>();
			double parallelWidth = 0;
			for (Draft d : drafts.values())
				parallelWidth = Math.max(parallelWidth, Math.max(Math.max(d.header.width, d.subtitle.width),
						Math.max(d.activeTitle.width, d.constructionTitle.width)));
			parallelWidth += 2 * PADDING + parallelNesting * 8;
			for (Node n : model.timeline) {
				int rank = 0;
				final Set<String> predecessors = model.predecessors.get(n.id);
				if (predecessors != null)
					for (String predecessor : predecessors) {
						final Integer before = ranks.get(predecessor);
						if (before != null)
							rank = Math.max(rank, before + 1);
					}
				ranks.put(n.id, rank);
				while (leftSpans.size() <= rank) {
					leftSpans.add(0.0);
					rightSpans.add(0.0);
				}
				final Draft d = drafts.get(n.get("scope"));
				final EventDraft e = eventDrafts.get(n.id);
				final double eventWidth = e == null ? 0 : n.get("parallel") == null ? e.width : Math.max(e.width, parallelWidth);
				final double left = e == null ? 0 : eventWidth / 2;
				double right = e == null ? PADDING + d.constructionWidth : eventWidth / 2;
				final Draft owned = drafts.get(n.get("ownedScope"));
				if (owned != null)
					right = Math.max(right, PADDING + owned.constructionWidth);
				leftSpans.set(rank, Math.max(leftSpans.get(rank), left));
				rightSpans.set(rank, Math.max(rightSpans.get(rank), right));
			}
			final double[] centers = new double[leftSpans.size()];
			final double[] ends = new double[leftSpans.size()];
			double cursor = PADDING;
			for (int i = 0; i < centers.length; i++) {
				centers[i] = cursor + leftSpans.get(i);
				ends[i] = centers[i] + rightSpans.get(i);
				cursor = ends[i] + GAP;
			}
			for (Node n : model.timeline) {
				final int rank = ranks.get(n.id);
				final Draft d = drafts.get(n.get("scope"));
				final EventDraft e = eventDrafts.get(n.id);
				final double x = centers[rank];
				xs.put(n.id, x);
				extents.put(n.id, ends[rank]);
				horizon = Math.max(horizon, ends[rank]);
				final Draft owned = drafts.get(n.get("ownedScope"));
				if (owned != null) {
					owned.x = x;
					xs.put("birth-" + owned.node.id, x);
					extents.put("birth-" + owned.node.id, x + PADDING + owned.constructionWidth);
					if (owned.failed) {
						owned.open = false;
						owned.end = x + PADDING + owned.constructionWidth;
					}
				}
				if (e == null) {
					d.x = x;
					if (d.failed) {
						d.open = false;
						d.end = x + PADDING + d.constructionWidth;
					}
				} else if ("dispose".equals(n.kind) && isFailure(e.outcome) == false) {
					d.open = false;
					d.end = x;
				}
			}
			measureBands();
			for (Draft d : drafts.values()) {
				final Map<Double, Double> slots = new LinkedHashMap<Double, Double>();
				final double step = d.above + d.below + GAP;
				double lastOffset = 0;
				for (EventDraft e : d.events) {
					final Double x = xs.get(e.node.id);
					final Double previous = slots.get(x);
					e.stackOffset = previous == null ? 0 : previous + step + e.gutter;
					slots.put(x, e.stackOffset);
					lastOffset = Math.max(lastOffset, e.stackOffset);
				}
				d.trackOffset = d.cardTop + 10 + d.activeTitle.height + 12 + d.traceHeight + d.above
						+ (parallelNesting == 0 ? 0 : parallelNesting * 20);
				d.ownHeight = Math.max(d.cardTop + d.constructionHeight,
						d.trackOffset + d.below + lastOffset) + d.bandBottomPadding;
			}
		}

		double placeNested(Draft d, double y) {
			double bottom = y + d.ownHeight;
			double right = d.right;
			for (Draft child : d.children) {
				bottom = placeNested(child, bottom + localGap(child));
				right = Math.max(right, boxes.get(child.node.id).right() + PADDING);
			}
			if (d.children.isEmpty() == false)
				bottom += PADDING;
			boxes.put(d.node.id, new ScopeBox(d, y, bottom - y, right));
			return bottom;
		}

		double localGap(Draft child) {
			int provenance = child.node.get("ownedBy") == null ? 0 : 1;
			for (ConstructionRow row : child.rows)
				if (row.module)
					provenance++;
			return scopeGap + provenance * (label("module provenance", SName.autofacModule, 260).height + 8);
		}

		double placeLanes(Draft d, double y) {
			boxes.put(d.node.id, new ScopeBox(d, y, d.ownHeight, d.right));
			y += d.ownHeight;
			for (Draft child : d.children)
				y = placeLanes(child, y + localGap(child));
			return y;
		}

		void placeEvents() {
			for (EventDraft e : eventDrafts.values()) {
				final ScopeBox scope = boxes.get(e.node.get("scope"));
				if (scope != null)
					eventBoxes.put(e.node.id, new EventBox(e, xs.get(e.node.id),
							scope.trackY + e.stackOffset));
			}
		}

		void connector(String uid, String kind, Node node, String text, double x1, double y1,
				double x2, double y2, double channel, boolean local) {
			final Label label = label(text, "lookup".equals(kind) ? SName.autofacMuted : SName.autofacModule, 250);
			final double lx = Math.max(PADDING, (x1 + x2 - label.width) / 2);
			connectors.add(new Connector(uid, kind, node, label, x1, y1, x2, y2, channel,
					lx, channel - label.height - 3, local));
		}
		void traceConnector(String uid, Node source, double x1, double y1, double x2, double y2) {
			final TraceLane lane = traceLanes.get(uid);
			final ScopeBox scope = boxes.get(lane.scope.node.id);
			final double labelY = scope.activeTitleY + scope.activeTitle.height + 4 + lane.offset;
			connectors.add(new Connector(uid, "call", source, lane.label, x1, y1, x2, y2,
					labelY + lane.label.height + 3, scope.activeStartX + 8, labelY, false));
		}


		void placeConnectors() {
			for (Draft d : drafts.values()) {
				final ScopeBox scope = boxes.get(d.node.id);
				final ScopeBox parent = boxes.get(d.node.get("parent"));
				final double gapTop = parent == null ? scope.y : parent.y + drafts.get(parent.node.id).ownHeight;
				double channel = gapTop + GAP / 2 + label("module provenance", SName.autofacModule, 250).height;
				final EventBox acquisition = eventBoxes.get(d.node.get("ownedBy"));
				if (acquisition != null) {
					connector("af-owned-" + d.node.id, "provenance", d.node, "Owned acquisition",
							acquisition.x, acquisition.bottom() + 3, scope.constructionX + scope.constructionWidth / 2, scope.y,
							channel, true);
					channel += label("Owned acquisition", SName.autofacModule, 250).height + 8;
				}
				for (ConstructionRow row : scope.rows) {
					if (row.module) {
						final String alias = row.node.get("sourceEvent", row.node.get("target", row.node.id));
						final EventBox source = eventBoxes.get(alias);
						if (source != null) {
							connector("af-provenance-" + scope.node.id + "-" + alias, "provenance", row.node, "module provenance",
									source.x, source.bottom() + 3, scope.constructionX + scope.constructionWidth / 2, scope.y,
									channel, true);
							channel += label("module provenance", SName.autofacModule, 250).height + 8;
						}
					}
					if ("register".equals(row.node.kind))
						for (int i = 0; i < row.node.children.size(); i++) {
							final Node call = row.node.children.get(i);
							final ScopeBox target = boxes.get(call.get("scope"));
							if (target != null)
								traceConnector(row.uid + "-call-" + i, call,
										scope.activeStartX, scope.y + row.y + row.label.height / 2,
										target.activeStartX, target.trackY);
						}
				}
			}
			for (EventBox e : eventBoxes.values()) {
				final EventBox during = eventBoxes.get(e.node.get("during"));
				if (during != null)
					traceConnector("af-call-" + e.node.id, e.node, during.x, during.y, e.x, e.y);
				if (e.outcome == null || e.outcome.get("selected") == null
						|| (model.expanded.contains(e.node.id) == false && "diagnostics".equals(model.view) == false))
					continue;
				final String uid = "af-reg-" + e.outcome.get("selected");
				final ConstructionRow row = registrationRows.get(uid);
				final Draft selected = registrationScopes.get(uid);
				if (row != null) {
					final ScopeBox scope = boxes.get(selected.node.id);
					connector("af-lookup-" + e.node.id, "lookup", e.node, "", e.x, e.top,
							scope.activeStartX, scope.y + row.y + row.label.height / 2,
							scope.activeTitleY + scope.activeTitle.height + 6, false);
				}
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
							if (groups.get(id).contains(runtime) == false)
								groups.get(id).add(runtime);
				collectParallel(node.children, active, groups, depths);
			}
		}

		void measureBands() {
			collectParallel(model.statements, Collections.<String>emptyList(), parallelGroups, parallelDepths);
			for (Node n : model.timeline) {
				final String id = n.get("parallel");
				if (id != null) {
					if (parallelGroups.containsKey(id) == false)
						parallelGroups.put(id, new ArrayList<Node>());
					if (parallelGroups.get(id).contains(n) == false)
						parallelGroups.get(id).add(n);
				}
			}
			for (Map.Entry<String, List<Node>> entry : parallelGroups.entrySet()) {
				final Set<Double> columns = new LinkedHashSet<Double>();
				final Set<String> firstScopes = new LinkedHashSet<String>();
				final int depth = parallelDepths.containsKey(entry.getKey()) ? parallelDepths.get(entry.getKey()) : 0;
				final double inset = (parallelNesting - depth) * 10;
				String owner = null;
				boolean singleScope = true;
				for (Node n : entry.getValue()) {
					columns.add(xs.get(n.id));
					if ("birth".equals(n.kind) || (owner != null && owner.equals(n.get("scope")) == false))
						singleScope = false;
					owner = n.get("scope");
				}
				final Label label = label(columns.size() > 1 ? "unordered except shown prerequisites" : "unordered",
						SName.autofacModule, 300);
				parallelLabels.put(entry.getKey(), label);
				if (singleScope && owner != null) {
					final Draft scope = drafts.get(owner);
					scope.bandBottomPadding = Math.max(scope.bandBottomPadding, inset + 6);
				}
				if (depth > 0)
					for (Node n : entry.getValue()) {
						final EventDraft event = eventDrafts.get(n.id);
						if (event != null && firstScopes.add(n.get("scope")))
							event.gutter = Math.max(event.gutter, Math.max(0, label.height + inset + 10 - GAP));
					}
			}
		}

		void placeBands() {
			for (Map.Entry<String, List<Node>> entry : parallelGroups.entrySet()) {
				double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = 0, maxY = 0;
				for (Node n : entry.getValue()) {
					final EventBox e = eventBoxes.get(n.id);
					final ScopeBox s = boxes.get(n.get("scope"));
					if (s == null)
						continue;
					minX = Math.min(minX, e == null ? s.x : e.left);
					maxX = Math.max(maxX, e == null ? s.activeStartX : e.right());
					minY = Math.min(minY, e == null ? s.y : e.top);
					maxY = Math.max(maxY, e == null ? s.constructionY + s.constructionHeight : e.bottom());
				}
				if (minX == Double.MAX_VALUE)
					continue;
				final Label label = parallelLabels.get(entry.getKey());
				final int level = parallelDepths.containsKey(entry.getKey()) ? parallelDepths.get(entry.getKey()) : 0;
				final double inset = (parallelNesting - level) * 10;
				final double left = Math.max(4, minX - inset);
				final BandBounds bounds = new BandBounds(left, Math.max(2, minY - label.height - 6 - inset),
						Math.max(maxX + inset, left + label.width + 16), maxY + inset);
				boolean changed;
				do {
					final double oldLeft = bounds.left, oldTop = bounds.top, oldRight = bounds.right, oldBottom = bounds.bottom;
					for (ScopeBox scope : boxes.values()) {
						clearBandEdges(bounds, label, minY, level, scope.header, scope.headerX, scope.headerY);
						clearBandEdges(bounds, label, minY, level, scope.subtitle, scope.subtitleX, scope.subtitleY);
						clearBandEdges(bounds, label, minY, level, scope.activeTitle, scope.activeTitleX, scope.activeTitleY);
						clearBandEdges(bounds, label, minY, level, scope.constructionTitle, scope.constructionTitleX, scope.constructionTitleY);
					}
					changed = oldLeft != bounds.left || oldTop != bounds.top || oldRight != bounds.right || oldBottom != bounds.bottom;
				} while (changed);
				bands.add(new UnorderedBand(entry.getKey(), entry.getValue().get(0), label, bounds.left, bounds.top,
						bounds.right - bounds.left, bounds.bottom - bounds.top));
			}
		}

		double clearBandHeader(double top, double left, double right, Label band, double eventTop,
				int depth, Label header, double headerX, double headerY) {
			if (header.width == 0 || header.height == 0 || right <= headerX || left >= headerX + header.width
					|| top >= headerY + header.height + 6 || top + band.height + 2 <= headerY - 6)
				return top;
			// Use existing space before the first event; never stretch the scenario for a border.
			final double below = headerY + header.height + 6 + depth * 4;
			if (below + band.height + 8 <= eventTop)
				return below;
			return Math.max(2, Math.min(top, headerY - band.height - 8 - depth * 4));
		}

		void clearBandEdges(BandBounds bounds, Label band, double eventTop, int depth,
				Label header, double x, double y) {
			if (header.width == 0 || header.height == 0)
				return;
			final double margin = 6 + Math.max(0, parallelNesting - depth - 1) * 4;
			final double right = x + header.width, bottom = y + header.height;
			if (bounds.top < bottom + margin && bounds.bottom > y - margin) {
				if (bounds.left > x - margin && bounds.left < right + margin)
					bounds.left = Math.max(2, x - margin);
				if (bounds.right > x - margin && bounds.right < right + margin)
					bounds.right = right + margin;
			}
			bounds.top = clearBandHeader(bounds.top, bounds.left, bounds.right, band, eventTop, depth, header, x, y);
			if (bounds.left < right + margin && bounds.right > x - margin
					&& bounds.bottom > y - margin && bounds.bottom < bottom + margin)
				bounds.bottom = bottom + margin;
		}

		List<Card> cards() {
			final List<Card> cards = new ArrayList<Card>();
			final Set<String> targets = new LinkedHashSet<String>(model.expanded);
			if ("diagnostics".equals(model.view))
				for (Map.Entry<String, Outcome> e : model.outcomes.entrySet())
					if (isFailure(e.getValue()) || "recursion".equals(e.getValue().kind) || e.getValue().category != null || e.getValue().paths.containsKey("cycle"))
						targets.add(e.getKey());
			for (String id : targets) {
				final Node n = model.events.containsKey(id) ? model.events.get(id) : model.scopes.get(id);
				final Outcome o = model.outcomes.get(id);
				final StringBuilder body = new StringBuilder(o == null ? "Outcome unspecified" : humanize(o.kind)
						+ (o.category == null ? "" : " · " + humanize(o.category)));
				if (o != null) {
					for (String key : new String[] { "selected", "activation", "sharing", "owner", "instance", "state" })
						if (o.get(key) != null)
							body.append('\n').append(factTitle(key)).append(": ").append(o.get(key));
					final Node registration = model.registrations.get(o.get("selected"));
					if (registration != null)
						body.append("\nActivator: ").append(registration.get("activator", "unspecified")).append(' ')
								.append(registration.get("type", registration.get("target", "")));
					for (String key : new String[] { "cause", "risk", "exception", "message", "tag" })
						if (o.get(key) != null)
							body.append('\n').append(humanize(key)).append(": ").append(o.get(key));
					if (o.get("empty") != null)
						body.append("\nEmpty collection");
					for (String key : new String[] { "path", "cycle" })
						if (o.paths.containsKey(key))
							body.append('\n').append(humanize(key)).append(": ").append(join(o.paths.get(key), " → "));
					body.append("\nConfidence: ").append(o.get("confidence", "declared"));
				}
				body.append("\nOther facts unspecified");
				final List<Label> chain = new ArrayList<Label>();
				if (o != null && o.paths.containsKey("chain"))
					for (String object : o.paths.get("chain"))
						chain.add(label(object, SName.autofacResolve, 280));
				final SName role = isFailure(o) || (o != null && "recursion".equals(o.kind)) ? SName.autofacFailure : SName.autofacLifetime;
				cards.add(card(id, n, o, id + (n == null ? "" : " · " + ("lifetime".equals(n.kind) ? n.id : service(n))),
						body.toString(), chain, role));
			}
			for (Node object : model.objects.values())
				if ("instance".equals(object.kind) && object.attrs.containsKey("introducedBy") == false)
					cards.add(card("instance-" + object.id, object, null, "Instance " + object.id,
							object.get("type", "unspecified") + "\nOwner: " + object.get("owner", "unspecified"),
							Collections.<Label>emptyList(), SName.autofacLifetime));
			for (Node definition : model.statements)
				if ("depends".equals(definition.kind)) {
					final StringBuilder body = new StringBuilder();
					for (Node dependency : definition.children)
						body.append(body.length() == 0 ? "" : "\n").append(dependency.id).append(": ").append(service(dependency))
								.append(" · ").append(dependency.get("mode", "unspecified"));
					cards.add(card("dependencies-" + cards.size(), definition, null, "Dependencies · " + definition.get("type", "unspecified"),
							body.toString(), Collections.<Label>emptyList(), SName.autofacConstruction));
				}
			Collections.sort(cards, new Comparator<Card>() {
				public int compare(Card a, Card b) { return Double.compare(a.targetX, b.targetX); }
			});
			return cards;
		}

		Card card(String id, Node node, Outcome outcome, String title, String body, List<Label> chain, SName role) {
			final EventBox event = node == null ? null : eventBoxes.get(node.id);
			final ScopeBox scope = node == null ? null : boxes.get(node.id);
			final double target = event != null ? event.x : scope != null ? scope.activeStartX : PADDING;
			return new Card(id, node, outcome, label(title, SName.autofacHeading, 350),
					label(body, SName.autofacMuted, 350), chain, role, target);
		}

		double placePanels(List<Card> cards, double top, double width) {
			double y = top, x = PADDING, rowHeight = 0;
			for (Card card : cards) {
				if (x > PADDING && x + card.width + PADDING > width) {
					y += rowHeight + GAP;
					x = PADDING;
					rowHeight = 0;
				}
				// Prefer the target's neighborhood without wasting a whole shelf for a right-hand target.
				final double ideal = Math.max(x, Math.min(card.targetX - card.width / 2, width - card.width - PADDING));
				final double px = cards.size() == 1 ? ideal : x;
				final boolean leader = y == top && card.targetX >= px + 8 && card.targetX <= px + card.width - 8;
				panels.add(new Panel(card, px, y, top - GAP / 2, leader));
				x = px + card.width + GAP;
				rowHeight = Math.max(rowHeight, card.height);
			}
			return cards.isEmpty() ? top : y + rowHeight + GAP;
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
		return type + " " + n.get("keyKind", "key") + " " + ("true".equals(n.get("keyWildcard")) ? "*" : "\"" + n.get("key") + "\"");
	}

	private static String eventDetail(Node n, Outcome o) {
		if (o == null)
			return "dispose".equals(n.kind) ? "" : "unspecified";
		final StringBuilder text = new StringBuilder();
		if (o.category != null)
			text.append("missingRegistration".equals(o.category) && n.get("key") != null ? "Missing key" : humanize(o.category));
		if (o.get("instance") != null)
			appendDetail(text, o.get("instance") + (o.get("state") == null ? "" : " · " + o.get("state")));
		else if (o.get("state") != null)
			appendDetail(text, o.get("state"));
		final String selected = o.get("selected");
		if (selected != null) {
			final String binding = selected.substring(0, selected.indexOf('.'));
			if (binding.equals(n.get("scope")) == false)
				appendDetail(text, "inherited · binding: " + binding);
		}
		if (o.get("empty") != null)
			appendDetail(text, "empty collection");
		if ("recursion".equals(o.kind))
			appendDetail(text, o.get("confidence", "declared") + " · risk " + o.get("risk", "unspecified"));
		if ("dispose".equals(n.kind) && isFailure(o))
			appendDetail(text, "lifecycle unspecified");
		return text.toString();
	}

	private static void appendDetail(StringBuilder text, String value) {
		if (text.length() > 0)
			text.append('\n');
		text.append(value);
	}

	private static String factTitle(String key) {
		if ("selected".equals(key))
			return "Selected registration";
		if ("sharing".equals(key))
			return "Sharing scope";
		if ("activation".equals(key))
			return "Activation scope";
		return humanize(key);
	}

	private static String humanize(String text) {
		if (text.length() == 0)
			return text;
		final StringBuilder result = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			final char c = text.charAt(i);
			if (i > 0 && Character.isUpperCase(c))
				result.append(' ');
			result.append(i == 0 ? Character.toUpperCase(c) : Character.toLowerCase(c));
		}
		return result.toString();
	}

	private static String join(List<String> values, String separator) {
		final StringBuilder result = new StringBuilder();
		for (String value : values) {
			if (result.length() > 0)
				result.append(separator);
			result.append(value);
		}
		return result.toString();
	}
}
