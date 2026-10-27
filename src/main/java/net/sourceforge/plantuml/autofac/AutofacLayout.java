package net.sourceforge.plantuml.autofac;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeSet;

import net.sourceforge.plantuml.autofac.AutofacModel.Node;
import net.sourceforge.plantuml.klimt.color.HColor;
import net.sourceforge.plantuml.klimt.creole.CreoleMode;
import net.sourceforge.plantuml.klimt.creole.Display;
import net.sourceforge.plantuml.klimt.font.FontConfiguration;
import net.sourceforge.plantuml.klimt.font.StringBounder;
import net.sourceforge.plantuml.klimt.geom.HorizontalAlignment;
import net.sourceforge.plantuml.klimt.geom.XDimension2D;
import net.sourceforge.plantuml.klimt.shape.TextBlock;
import net.sourceforge.plantuml.skin.ArrowConfiguration;
import net.sourceforge.plantuml.stereo.Stereotype;
import net.sourceforge.plantuml.style.ISkinParam;
import net.sourceforge.plantuml.style.SName;
import net.sourceforge.plantuml.style.Style;
import net.sourceforge.plantuml.style.StyleSignatureBasic;

/** Measured horizontal chronology. Geometry describes authored entities, not container execution. */
public final class AutofacLayout {
	public static final double PADDING = 16;
	public static final double GAP = 24;
	public static final double MARKER_RADIUS = 6;
	public final Map<String, ScopeBox> scopes;
	public final List<ScopeBox> scopeOrder;
	public final Map<String, TrackBox> tracks;
	public final Map<String, EventBox> events;
	public final Map<String, Port> ports;
	public final List<Connector> connectors;
	public final List<GroupBox> groups;
	public final List<NoteBox> notes;
	public final List<ActivationBox> activations;
	public final List<ModuleBox> modules;
	public final boolean lanes;
	public final double width, height;
	public final Label orderLabel;
	public final double orderX, orderY;

	private AutofacLayout(Builder b) {
		scopes = map(b.boxes);
		final List<ScopeBox> orderedScopes = new ArrayList<ScopeBox>(b.boxes.values());
		Collections.sort(orderedScopes, new Comparator<ScopeBox>() {
			public int compare(ScopeBox a, ScopeBox c) { return Integer.compare(a.depth, c.depth); }
		});
		scopeOrder = list(orderedScopes);
		tracks = map(b.tracks);
		events = map(b.events);
		ports = map(b.ports);
		connectors = list(b.connectors);
		groups = list(b.groups);
		notes = list(b.notes);
		activations = list(b.activations);
		modules = list(b.modules);
		lanes = b.lanes;
		width = b.width;
		height = b.bottom + b.order.height + PADDING * 2;
		orderLabel = b.order;
		orderX = Math.max(PADDING, width - orderLabel.width - PADDING);
		orderY = b.bottom + PADDING;
	}

	public static AutofacLayout compute(AutofacModel model, ISkinParam skin, StringBounder bounder) {
		return new Builder(model, skin, bounder).compute();
	}

	static Style style(ISkinParam skin, SName role) { return style(skin, role, null); }

	static Style style(ISkinParam skin, SName role, String stereotype) {
		return StyleSignatureBasic.of(SName.root, SName.element, SName.autofacDiagram, role)
				.withTOBECHANGED(Stereotype.build(stereotype(stereotype)))
				.getMergedStyle(skin.getCurrentStyleBuilder());
	}

	static String stereotype(String value) {
		if (value == null) return null;
		value = value.trim();
		return value.startsWith("<<") && value.endsWith(">>") ? value : "<<" + value + ">>";
	}

	static SName entityRole(Node node) {
		if ("object".equals(node.kind)) return SName.object;
		final String type = node.get("type", "participant").toLowerCase(java.util.Locale.ROOT);
		if ("actor".equals(type)) return SName.actor;
		if ("boundary".equals(type)) return SName.boundary;
		if ("control".equals(type)) return SName.control;
		if ("entity".equals(type)) return SName.entity;
		if ("database".equals(type)) return SName.database;
		if ("collections".equals(type)) return SName.collections;
		if ("queue".equals(type)) return SName.queue;
		return SName.participant;
	}

	private static <T> List<T> list(List<T> values) {
		return Collections.unmodifiableList(new ArrayList<T>(values));
	}

	private static <K, V> Map<K, V> map(Map<K, V> values) {
		return Collections.unmodifiableMap(new LinkedHashMap<K, V>(values));
	}

	public static final class Label {
		public final String text;
		public final TextBlock block;
		public final double width, height;

		private Label(String text, SName role, ISkinParam skin, StringBounder bounder, String textColor, String stereotype) {
			this.text = text;
			FontConfiguration font = style(skin, role, stereotype).getFontConfiguration(skin.getIHtmlColorSet());
			final HColor color = textColor == null ? null : skin.getIHtmlColorSet().getColorOrNull(textColor);
			if (color != null) font = font.changeColor(color);
			block = Display.getWithNewlines(skin.getPragma(), text).create8(
					font, HorizontalAlignment.LEFT, skin, CreoleMode.FULL,
					net.sourceforge.plantuml.klimt.LineBreakStrategy.NONE);
			final XDimension2D dim = text.length() == 0 ? new XDimension2D(0, 0) : block.calculateDimension(bounder);
			width = dim.getWidth();
			height = dim.getHeight();
		}
	}

	public static final class Point {
		public final double x, y;
		public Point(double x, double y) { this.x = x; this.y = y; }
	}

	/** A real entity attachment point. Registration ports are on the edge of their actual row. */
	public static final class Port {
		public final String id, kind;
		public final Node node;
		public final double x, y;
		private Port(Node node, double x, double y) {
			this.id = node.id; this.kind = node.kind; this.node = node; this.x = x; this.y = y;
		}
	}

	public static final class ConstructionRow {
		public final Node node;
		public final Label label, metadata;
		public final double x, y, width, height;
		private ConstructionRow(Node node, Label label, Label metadata, double x, double y, double width) {
			this.node = node; this.label = label; this.metadata = metadata;
			this.x = x; this.y = y; this.width = width;
			height = label.height + (metadata.height > 0 ? metadata.height + 4 : 0) + 12;
		}
	}

	public static final class ModuleBox {
		public final Node node;
		public final Label header;
		public final List<ConstructionRow> rows;
		public final double x, y, width, height, headerX, headerY;
		private ModuleBox(Node node, Label header, List<ConstructionRow> rows, double x, double y, double width, double height) {
			this.node = node; this.header = header; this.rows = list(rows);
			this.x = x; this.y = y; this.width = width; this.height = height;
			headerX = x + 12; headerY = y + 12;
		}
		public double right() { return x + width; }
		public double bottom() { return y + height; }
	}

	public static final class ScopeBox {
		public final Node node;
		public final Label header, subtitle, constructionTitle;
		public final List<ConstructionRow> rows;
		public final double x, y, width, height, constructionX, constructionY, constructionWidth, constructionHeight;
		public final double headerX, headerY, subtitleX, subtitleY, activeStartX, birthX, trackY, trackEndX;
		public final int depth;
		public final boolean open, created;
		private ScopeBox(Draft d, double right, double height) {
			node = d.node; header = d.header; subtitle = d.subtitle; constructionTitle = d.title;
			x = d.x; y = d.y; width = right - x; this.height = height; depth = d.depth;
			headerX = x + PADDING; headerY = y + PADDING;
			subtitleX = headerX; subtitleY = headerY + header.height + 4;
			constructionX = x + PADDING; constructionY = y + d.cardTop;
			constructionWidth = d.cardWidth; constructionHeight = d.cardHeight;
			activeStartX = d.start; birthX = d.start;
			trackEndX = d.end; trackY = d.trackY;
			open = d.destroy == null; created = d.create != null;
			final List<ConstructionRow> rows = new ArrayList<ConstructionRow>();
			double rowY = constructionY + 14 + constructionTitle.height + 12;
			for (RowDraft row : d.rows) {
				final ConstructionRow r = new ConstructionRow(row.node, row.label, row.meta,
						constructionX + 24, rowY, constructionWidth - 36);
				rows.add(r); rowY += r.height;
			}
			this.rows = list(rows);
		}
		public double right() { return x + width; }
		public double bottom() { return y + height; }
	}

	public static final class TrackBox {
		public final Node node;
		public final Label label, ownership;
		public final double x, y, endX, labelX, labelY, ownershipX, ownershipY, ownerBoundaryX;
		public final boolean openStart, open;
		private TrackBox(Node node, Label label, double x, double y, double end, boolean openStart, boolean open,
				Label ownership, double ownerBoundaryX) {
			this.node = node; this.label = label; this.x = x; this.y = y; endX = end;
			labelX = x + 8; labelY = y - label.height - 12; this.openStart = openStart; this.open = open;
			this.ownership = ownership; ownershipX = x + 8; ownershipY = y + 28; this.ownerBoundaryX = ownerBoundaryX;
		}
	}

	public static final class EventBox {
		public final Node node;
		public final Label label;
		public final double x, y, baseY, labelX, labelY, width, height;
		private EventBox(Node node, Label label, double x, double y) { this(node, label, x, y, y); }
		private EventBox(Node node, Label label, double x, double y, double baseY) {
			this.node = node; this.label = label; this.x = x; this.y = y; this.baseY = baseY;
			labelX = x - label.width / 2; labelY = y + 14;
			width = Math.max(12, label.width); height = 14 + label.height;
		}
	}

	/** Points are in source-to-target order, independent of vertical direction and arrowhead style. */
	public static final class Connector {
		public final Node node;
		public final String kind, from, to;
		public final Label label;
		public final List<Point> points;
		public final double x1, y1, x2, y2, labelX, labelY;
		public final ArrowConfiguration arrow;
		private Connector(Node node, String kind, String from, String to, Label label, List<Point> points,
				double labelX, double labelY, ArrowConfiguration arrow) {
			this.node = node; this.kind = kind; this.from = from; this.to = to; this.label = label;
			this.points = list(points); this.labelX = labelX; this.labelY = labelY; this.arrow = arrow;
			x1 = points.get(0).x; y1 = points.get(0).y;
			x2 = points.get(points.size() - 1).x; y2 = points.get(points.size() - 1).y;
		}
	}

	public static final class GroupBox {
		public final Node node;
		public final String uid, logicalId;
		public final Label label;
		public final double x, y, width, height, continuationY;
		public final boolean continuation;
		public final List<Branch> branches;
		private GroupBox(Node node, String uid, String logicalId, Label label, double x, double y, double width,
				double height, List<Branch> branches, boolean continuation, double continuationY) {
			this.node = node; this.uid = uid; this.logicalId = logicalId; this.label = label; this.x = x; this.y = y;
			this.width = width; this.height = height; this.branches = list(branches);
			this.continuation = continuation; this.continuationY = continuationY;
		}
	}

	public static final class Branch {
		public final Node node;
		public final Label label;
		public final double x;
		private Branch(Node node, Label label, double x) { this.node = node; this.label = label; this.x = x; }
	}

	public static final class NoteBox {
		public final Node node;
		public final Label label;
		public final double x, y, width, height, labelX, labelY;
		private NoteBox(Node node, Label label, double x, double y, double span) {
			this.node = node; this.label = label; this.x = x; this.y = y;
			width = label.width + 24; height = Math.max(label.height + 20, span);
			labelX = x + 12; labelY = y + (height - label.height) / 2;
		}
	}

	public static final class ActivationBox {
		public final Node node;
		public final String target;
		public final double x1, x2, y;
		public final boolean open;
		private ActivationBox(Node node, String target, double x1, double x2, double y, boolean open) {
			this.node = node; this.target = target; this.x1 = x1; this.x2 = x2; this.y = y; this.open = open;
		}
	}

	private static final class RowDraft {
		final Node node;
		final Label label, meta;
		RowDraft(Node node, Label label, Label meta) { this.node = node; this.label = label; this.meta = meta; }
	}

	private static final class Draft {
		final Node node;
		final Label header, subtitle, title;
		final List<RowDraft> rows = new ArrayList<RowDraft>();
		final int depth;
		double x, y, cardTop, cardWidth, cardHeight, ownHeight, subtreeHeight, trackY, start, end;
		Node create, destroy;
		Draft(Node node, Label header, Label subtitle, Label title, int depth) {
			this.node = node; this.header = header; this.subtitle = subtitle; this.title = title; this.depth = depth;
		}
	}

	private static final class Obstacle {
		final double x, y, right, bottom;
		Obstacle(double x, double y, double width, double height) {
			this.x = x; this.y = y; right = x + width; bottom = y + height;
		}
		boolean intersects(double left, double top, double width, double height) {
			return left < right + 4 && left + width > x - 4 && top < bottom + 4 && top + height > y - 4;
		}
		boolean crosses(Point a, Point b) { return crosses(a.x, a.y, b.x, b.y); }
		boolean crosses(double x1, double y1, double x2, double y2) {
			if (x1 == x2) return x1 > x && x1 < right && Math.max(y1, y2) > y && Math.min(y1, y2) < bottom;
			if (y1 == y2) return y1 > y && y1 < bottom && Math.max(x1, x2) > x && Math.min(x1, x2) < right;
			return false;
		}
		boolean contains(Point p) { return p.x > x && p.x < right && p.y > y && p.y < bottom; }
	}

	private static final class RouteStep implements Comparable<RouteStep> {
		final int index;
		final double cost, estimate;
		RouteStep(int index, double cost, double estimate) {
			this.index = index; this.cost = cost; this.estimate = estimate;
		}
		public int compareTo(RouteStep other) {
			final int result = Double.compare(estimate, other.estimate);
			return result == 0 ? Integer.compare(index, other.index) : result;
		}
	}

	private static final class ChronologyGroup {
		final Node start;
		final double entry;
		final boolean unordered;
		final List<Node> branches = new ArrayList<Node>();
		final List<int[]> ranges = new ArrayList<int[]>();
		int begin;
		double end;
		double eventHalfWidth;
		ChronologyGroup(Node start, double entry, int begin) {
			this.start = start; this.entry = entry; this.begin = begin; end = entry;
			unordered = "par".equals(start.get("mode")) || "alt".equals(start.get("mode"));
			branches.add(start);
		}
	}

	private static final class Builder {
		final AutofacModel model;
		final ISkinParam skin;
		final StringBounder bounder;
		final Map<String, Draft> drafts = new LinkedHashMap<String, Draft>();
		final Map<String, ScopeBox> boxes = new LinkedHashMap<String, ScopeBox>();
		final Map<String, TrackBox> tracks = new LinkedHashMap<String, TrackBox>();
		final Map<String, EventBox> events = new LinkedHashMap<String, EventBox>();
		final Map<String, Port> ports = new LinkedHashMap<String, Port>();
		final Map<String, Double> columns = new LinkedHashMap<String, Double>();
		final Map<String, Double> births = new LinkedHashMap<String, Double>();
		final Map<String, Double> deaths = new LinkedHashMap<String, Double>();
		final Map<String, Label> labels = new LinkedHashMap<String, Label>();
		final Map<String, Label> entityLabels = new LinkedHashMap<String, Label>();
		final Map<String, Double> entityY = new LinkedHashMap<String, Double>();
		final List<Connector> connectors = new ArrayList<Connector>();
		final List<GroupBox> groups = new ArrayList<GroupBox>();
		final List<NoteBox> notes = new ArrayList<NoteBox>();
		final List<ActivationBox> activations = new ArrayList<ActivationBox>();
		final List<ModuleBox> modules = new ArrayList<ModuleBox>();
		final List<Obstacle> obstacles = new ArrayList<Obstacle>();
		final List<Obstacle> noteObstacles = new ArrayList<Obstacle>();
		final List<Obstacle> labelBoundaries = new ArrayList<Obstacle>();
		final Map<String, Double> useEnds = new LinkedHashMap<String, Double>();
		final Map<String, Double> extentEnds = new LinkedHashMap<String, Double>();
		final Map<String, Double> relationColumns = new LinkedHashMap<String, Double>();
		final Map<String, double[]> groupRanges = new LinkedHashMap<String, double[]>();
		final Map<String, String> logicalGroups = new LinkedHashMap<String, String>();
		final Map<String, Map<String, Double>> rowOffsets = new LinkedHashMap<String, Map<String, Double>>();
		final Map<String, Double> maximumRowOffsets = new LinkedHashMap<String, Double>();
		final Label order;
		double horizon, width, bottom, top;
		boolean lanes;

		Builder(AutofacModel model, ISkinParam skin, StringBounder bounder) {
			this.model = model; this.skin = skin; this.bounder = bounder;
			order = label("Logical event order →", SName.autofacMuted);
		}

		Label label(String text, SName role) { return new Label(text, role, skin, bounder, null, null); }
		Label label(Node node, String text, SName role) {
			return new Label(text, role, skin, bounder, node.get("textColor"), node.get("stereotype"));
		}
		Label entityLabel(Node node) {
			Label label = entityLabels.get(node.id);
			if (label == null) {
				label = label(node, node.get("label", node.id), entityRole(node));
				entityLabels.put(node.id, label);
			}
			return label;
		}
		double column(Node node) { return columns.get(node.id); }

		AutofacLayout compute() {
			measure();
			chronology();
			planRelations();
			intervals();
			double moduleY = top;
			for (Node module : model.modules.values()) {
				final ModuleBox box = moduleBox(module, moduleY);
				modules.add(box);
				width = Math.max(width, box.right() + PADDING);
				ports.put(module.id, new Port(module, box.right(), box.y + box.height / 2));
				entityY.put(module.id, box.y + box.height / 2);
				moduleY = box.bottom() + GAP;
			}
			top = moduleY;
			double y = top;
			for (Draft d : drafts.values())
				if (d.node.get("parent") == null) y = placeScope(d, y) + GAP;
			for (Node node : model.objects.values())
				if (drafts.containsKey(node.get("owner")) == false) y = placeTrack(node, y) + GAP;
			for (Node node : model.participants.values())
				if (drafts.containsKey(node.get("owner")) == false) y = placeTrack(node, y) + GAP;
			bottom = Math.max(top + 80, y);
			for (Draft d : drafts.values()) {
				final double h = lanes ? d.ownHeight : d.subtreeHeight;
				final ScopeBox box = new ScopeBox(d, Math.max(d.x + d.cardWidth + PADDING * 2, d.end + PADDING), h);
				boxes.put(d.node.id, box);
				ports.put(d.node.id, new Port(d.node, d.start, d.trackY));
				for (ConstructionRow row : box.rows)
					ports.put(row.node.id, new Port(row.node, box.constructionX + box.constructionWidth, row.y + row.label.height / 2));
				width = Math.max(width, box.right() + PADDING);
			}
			occurrences();
			frames();
			activities();
			obstacles();
			userNotes();
			messages();
			references();
			width = Math.max(width, Math.max(horizon + PADDING * 2, order.width + PADDING * 2));
			return new AutofacLayout(this);
		}

		int depth(Node node) {
			final Node parent = model.scopes.get(node.get("parent"));
			return parent == null ? 0 : depth(parent) + 1;
		}

		void measure() {
			top = PADDING;
			for (Node relation : model.relations)
				if (endpointEntity(relation.get("from")).equals(endpointEntity(relation.get("to"))))
					top += label(relation, relation.get("label", ""), SName.reference).height * 2 + 24;
			for (Node node : model.timeline) {
				String text = node.get("label", "");
				if ("groupStart".equals(node.kind)) text = node.get("mode", "group") + (text.length() > 0 ? "  " + text : "");
				if (("event".equals(node.kind) || "message".equals(node.kind) || "note".equals(node.kind))
						&& node.get("stereotype") != null)
					text = stereotype(node.get("stereotype")) + "\n" + text;
				final SName role = "message".equals(node.kind) ? SName.arrow : "note".equals(node.kind)
						? SName.note : node.kind.startsWith("group") ? SName.group : SName.circle;
				labels.put(node.id, label(node, text, role));
			}
			for (Node node : model.scopes.values()) {
				String subtitle = "";
				if (node.get("parent") != null) subtitle = "child of " + display(node.get("parent"));
				if (node.get("tag") != null) subtitle += (subtitle.length() > 0 ? " · " : "") + "tag \"" + node.get("tag") + "\"";
				final Draft d = new Draft(node, label(node.get("label", node.id), SName.autofacHeading),
						label(subtitle, SName.autofacMuted), label("Construction", SName.autofacHeading), depth(node));
				d.cardWidth = Math.max(112, Math.max(Math.max(d.header.width, d.subtitle.width), d.title.width) + 32);
				rows(d, node.children);
				d.cardTop = PADDING + d.header.height + (d.subtitle.height > 0 ? d.subtitle.height + 4 : 0) + 12;
				double contentHeight = 14 + d.title.height + 12;
				for (RowDraft row : d.rows) contentHeight += row.label.height + (row.meta.height > 0 ? row.meta.height + 4 : 0) + 12;
				d.cardHeight = Math.max(64, contentHeight + 12);
				d.ownHeight = d.cardTop + d.cardHeight + PADDING;
				drafts.put(node.id, d);
			}
		}

		void rows(Draft d, List<Node> nodes) {
			for (Node node : nodes) {
				String text;
				String meta = "";
				if ("register".equals(node.kind)) {
					final int dot = node.id.lastIndexOf('.');
					text = node.id.substring(dot + 1) + ": " + ("delegate".equals(node.get("activator")) ? "ƒ " : "")
							+ node.get("service", "");
					if (node.get("key") != null) text += " [\"" + node.get("key") + "\"]";
					else if (node.get("name") != null) text += " [\"" + node.get("name") + "\"]";
					text += " → " + ("delegate".equals(node.get("activator")) ? node.get("label", "delegate")
							: "instance".equals(node.get("activator")) ? "instance " + display(node.get("target")) : node.get("type", ""));
					if (node.get("sharing") != null) meta = node.get("sharing");
					if (node.get("ownership") != null) meta += (meta.length() > 0 ? " · " : "") + node.get("ownership");
				} else if ("install".equals(node.kind)) {
					text = "install " + node.get("target") + " (" + display(node.get("module")) + ")";
				} else if ("decorate".equals(node.kind)) {
					text = "decorate " + node.get("service", "") + " with " + node.get("type", "");
				} else continue;
				final RowDraft row = new RowDraft(node,
						label(text, d.depth == 0 ? SName.autofacConstruction : SName.autofacChildConstruction),
						label(meta, SName.autofacMuted));
				d.rows.add(row);
				d.cardWidth = Math.max(d.cardWidth, Math.max(row.label.width, row.meta.width) + 48);
				if ("install".equals(node.kind)) rows(d, node.children);
			}
		}

		String display(String id) {
			final Node node = model.entities.get(id);
			return node == null ? id : node.get("label", id);
		}

		ModuleBox moduleBox(Node node, double y) {
			final Label header = label(node.get("label", node.id), SName.autofacHeading);
			final Draft d = new Draft(node, header, label("", SName.autofacMuted),
					label("module", SName.autofacHeading), 0);
			rows(d, node.children);
			d.cardWidth = Math.max(112, Math.max(d.header.width, d.title.width) + 32);
			for (RowDraft row : d.rows)
				d.cardWidth = Math.max(d.cardWidth, Math.max(row.label.width, row.meta.width) + 48);
			final List<ConstructionRow> measured = new ArrayList<ConstructionRow>();
			double rowY = y + 12 + header.height + 12;
			for (RowDraft row : d.rows) {
				final ConstructionRow built = new ConstructionRow(row.node, row.label, row.meta,
						PADDING + 24, rowY, d.cardWidth - 36);
				measured.add(built);
				rowY += built.height;
			}
			final double height = Math.max(64, rowY - y + 12);
			return new ModuleBox(node, header, measured, PADDING, y, d.cardWidth, height);
		}

		void chronology() {
			double cursor = PADDING * 2;
			Node previous = null;
			final List<ChronologyGroup> frameStack = new ArrayList<ChronologyGroup>();
			for (int timelineIndex = 0; timelineIndex < model.timeline.size(); timelineIndex++) {
				final Node node = model.timeline.get(timelineIndex);
				final Label text = labels.get(node.id);
				double x = cursor;
				if ("configure".equals(node.kind)) {
					final Draft d = drafts.get(node.get("target"));
					if (d != null) {
						d.x = cursor + d.depth * PADDING;
						cursor = d.x + d.cardWidth + PADDING * 2 + GAP;
						x = d.x;
					}
				} else if ("create".equals(node.kind)) {
					births.put(node.get("target"), x);
					final Draft d = drafts.get(node.get("target"));
					if (d != null) d.create = node;
					final Node entity = model.entities.get(node.get("target"));
					double reserved = d == null && entity != null ? entityLabel(entity).width : 0;
					final Draft owner = entity == null ? null : drafts.get(entity.get("owner"));
					if (d == null && owner != null && owner.x == 0)
						reserved = Math.max(reserved, label("owned by " + display(owner.node.id), SName.autofacMuted).width);
					cursor += reserved + GAP;
				} else if ("destroy".equals(node.kind)) {
					x += 12; deaths.put(node.get("target"), x); cursor = x + GAP;
					final Draft d = drafts.get(node.get("target"));
					if (d != null) d.destroy = node;
				} else if ("activate".equals(node.kind) || "deactivate".equals(node.kind)) {
					x = previous == null ? cursor : column(previous);
				} else if ("message".equals(node.kind)) {
					final Double anchor = columns.get(node.get("from"));
					final Double receiverBirth = births.get(endpointEntity(node.get("to")));
					if (previous != null && previous.id.equals(node.get("from")) && anchor != null
							&& (receiverBirth == null || receiverBirth <= anchor)) x = anchor;
					if ("true".equals(node.get("parallel")) && previous != null && "message".equals(previous.kind))
						x = column(previous);
					if (node.arrow != null && node.arrow.isSelfArrow() && node.arrow.isReverseDefine()
							&& anchor == null) x += text.width + 24;
					cursor = Math.max(cursor, x + text.width + GAP + 16);
				} else if ("event".equals(node.kind)) {
					double half = Math.max(MARKER_RADIUS, text.width / 2);
					for (ChronologyGroup group : frameStack)
						if (group.unordered) half = Math.max(half, group.eventHalfWidth);
					x = cursor + half;
					cursor = x + half + GAP;
				} else if ("groupStart".equals(node.kind)) {
					final ChronologyGroup group = new ChronologyGroup(node, cursor, timelineIndex + 1);
					if (group.unordered) group.eventHalfWidth = unorderedEventHalfWidth(timelineIndex, node.id);
					frameStack.add(group);
					cursor += 12;
				} else if ("groupElse".equals(node.kind) && frameStack.isEmpty() == false) {
					final ChronologyGroup group = frameStack.get(frameStack.size() - 1);
					group.end = Math.max(group.end, cursor);
					group.ranges.add(new int[] { group.begin, timelineIndex });
					group.begin = timelineIndex + 1;
					group.branches.add(node);
					if (group.unordered) {
						x = group.entry;
						cursor = group.entry + 12;
					} else cursor += 12;
				} else if ("groupEnd".equals(node.kind)) {
					if (frameStack.isEmpty() == false) {
						final ChronologyGroup group = frameStack.remove(frameStack.size() - 1);
						group.ranges.add(new int[] { group.begin, timelineIndex });
						x = Math.max(group.end, cursor);
						for (Node branch : group.branches)
							x = Math.max(x, group.entry + labels.get(branch.id).width + 32);
						for (Node branch : group.branches) {
							groupRanges.put(branch.id, new double[] { group.entry - 10, x + 10 });
							logicalGroups.put(branch.id, group.start.id);
						}
						if (group.unordered) stackBranchRows(group);
					}
					cursor = x + 12;
				} else if ("note".equals(node.kind)) {
					final String targets = "," + node.get("targets", node.get("target", "")).replace(" ", "") + ",";
					for (Node relation : model.relations)
						if (model.objects.containsKey(relation.get("from")) && model.objects.containsKey(relation.get("to"))
								&& targets.contains("," + relation.get("from") + ",") && targets.contains("," + relation.get("to") + ","))
							x += label(relation, relation.get("label", ""), SName.reference).width + GAP;
					cursor = x + text.width + GAP + 32;
				} else cursor += GAP;
				columns.put(node.id, x);
				previous = node;
			}
			horizon = Math.max(cursor + 48, 320);
		}

		double unorderedEventHalfWidth(int begin, String groupId) {
			double half = MARKER_RADIUS;
			for (int i = begin + 1; i < model.timeline.size(); i++) {
				final Node node = model.timeline.get(i);
				if ("groupEnd".equals(node.kind) && groupId.equals(node.get("group"))) break;
				if ("event".equals(node.kind)) half = Math.max(half, labels.get(node.id).width / 2);
			}
			return half;
		}

		List<String> occurrenceRails(Node node) {
			final List<String> result = new ArrayList<String>();
			if ("message".equals(node.kind)) {
				addBranchRail(result, endpointEntity(node.get("from")));
				addBranchRail(result, endpointEntity(node.get("to")));
			} else if ("event".equals(node.kind)) addBranchRail(result, node.get("scope"));
			else if ("create".equals(node.kind) || "destroy".equals(node.kind)
					|| "activate".equals(node.kind) || "deactivate".equals(node.kind))
				addBranchRail(result, node.get("target"));
			return result;
		}

		void addBranchRail(List<String> rails, String id) {
			if ((model.scopes.containsKey(id) || model.participants.containsKey(id)) && rails.contains(id) == false)
				rails.add(id);
		}

		void stackBranchRows(ChronologyGroup group) {
			if (group.ranges.size() < 2) return;
			final Map<String, Integer> seen = new LinkedHashMap<String, Integer>();
			double rowHeight = 144;
			for (int[] range : group.ranges)
				for (int i = range[0]; i < range[1]; i++) {
					final Node node = model.timeline.get(i);
					rowHeight = Math.max(rowHeight, labels.get(node.id).height * 2 + labels.get(group.start.id).height + 96);
					if (node.arrow != null && node.arrow.isSelfArrow())
						rowHeight = Math.max(rowHeight, labels.get(node.id).height * 2 + 64);
				}
			for (int[] range : group.ranges) {
				final List<String> rails = new ArrayList<String>();
				for (int i = range[0]; i < range[1]; i++)
					for (String rail : occurrenceRails(model.timeline.get(i)))
						if (rails.contains(rail) == false) rails.add(rail);
				for (String rail : rails) {
					final int lane = seen.containsKey(rail) ? seen.get(rail) : 0;
					seen.put(rail, lane + 1);
					if (lane == 0) continue;
					for (int i = range[0]; i < range[1]; i++) {
						final Node node = model.timeline.get(i);
						if (occurrenceRails(node).contains(rail) == false) continue;
						Map<String, Double> offsets = rowOffsets.get(node.id);
						if (offsets == null) {
							offsets = new LinkedHashMap<String, Double>();
							rowOffsets.put(node.id, offsets);
						}
						final double offset = (offsets.containsKey(rail) ? offsets.get(rail) : 0) + lane * rowHeight;
						offsets.put(rail, offset);
						maximumRowOffsets.put(rail, Math.max(maximumRowOffsets.containsKey(rail)
								? maximumRowOffsets.get(rail) : 0, offset));
					}
				}
			}
		}

		double rowOffset(Node node, String rail) {
			final Map<String, Double> offsets = rowOffsets.get(node.id);
			return offsets == null || offsets.containsKey(rail) == false ? 0 : offsets.get(rail);
		}

		double branchBelow(String rail) {
			if (maximumRowOffsets.containsKey(rail) == false) return 0;
			double labelHeight = 0;
			for (Node node : model.timeline)
				if (rowOffset(node, rail) > 0) labelHeight = Math.max(labelHeight, labels.get(node.id).height);
			return maximumRowOffsets.get(rail) + labelHeight + 40;
		}

		void planRelations() {
			final Map<String, Double> objectChannels = new LinkedHashMap<String, Double>();
			int index = 0;
			for (Node node : model.relations) {
				final String from = node.get("from"), to = node.get("to");
				final Label label = label(node, node.get("label", ""), SName.reference);
				double x;
				if (isRail(from) && isRail(to)) {
					final Label first = railLabel(from), second = railLabel(to);
					final String pair = from.compareTo(to) < 0 ? from + ":" + to : to + ":" + from;
					x = Math.max(railStart(from) + 8 + first.width, railStart(to) + 8 + second.width) + GAP;
					for (Node occurrence : model.timeline)
						if ("message".equals(occurrence.kind)
								&& (from.equals(endpointEntity(occurrence.get("from"))) && to.equals(endpointEntity(occurrence.get("to")))
										|| to.equals(endpointEntity(occurrence.get("from"))) && from.equals(endpointEntity(occurrence.get("to")))))
							x = Math.max(x, column(occurrence) + labels.get(occurrence.id).width / 2 + GAP);
					if (objectChannels.containsKey(pair)) x = Math.max(x, objectChannels.get(pair));
					objectChannels.put(pair, x + label.width + GAP);
				} else {
					final Double fromColumn = columns.get(from), toColumn = columns.get(to);
					x = Math.max(fromColumn == null ? top : fromColumn, toColumn == null ? top : toColumn) + 16 + index * 12;
				}
				relationColumns.put(node.id, x);
				index++;
			}
		}

		boolean isRail(String id) {
			return model.scopes.containsKey(id) || model.objects.containsKey(id) || model.participants.containsKey(id);
		}

		Label railLabel(String id) {
			final Draft scope = drafts.get(id);
			return scope != null ? scope.header : entityLabel(model.entities.get(id));
		}

		double railStart(String id) {
			if (births.containsKey(id)) return births.get(id);
			final Draft scope = drafts.get(id);
			if (scope != null) return scope.x + PADDING + scope.cardWidth;
			final Node entity = model.entities.get(id);
			final String owner = entity == null ? null : entity.get("owner");
			if (owner != null) {
				if (births.containsKey(owner)) return births.get(owner);
				final Draft ownerDraft = drafts.get(owner);
				if (ownerDraft != null) return ownerDraft.x + PADDING + ownerDraft.cardWidth;
			}
			return PADDING * 2;
		}

		void intervals() {
			useEnds.clear();
			extentEnds.clear();
			for (Node node : model.timeline) {
				if ("event".equals(node.kind))
					use(node.get("scope"), column(node) + 16, column(node) + labels.get(node.id).width + 8);
				else if ("message".equals(node.kind)) {
					use(endpointEntity(node.get("from")), column(node) + 16, column(node) + labels.get(node.id).width + 12);
					use(endpointEntity(node.get("to")), column(node) + 16, column(node) + labels.get(node.id).width + 12);
				} else if ("activate".equals(node.kind) || "deactivate".equals(node.kind))
					use(node.get("target"), column(node), column(node));
				else if ("note".equals(node.kind)) {
					final String targets = node.get("targets", node.get("target", ""));
					for (String target : targets.split(","))
						use(endpointEntity(target.trim()), column(node) + 8, column(node) + labels.get(node.id).width + 12);
				} else if ("create".equals(node.kind) || "destroy".equals(node.kind))
					use(node.get("target"), column(node), column(node));
			}
			for (Node relation : model.relations) {
				final Double x = relationColumns.get(relation.id);
				if (x == null) continue;
				final double extent = x + label(relation, relation.get("label", ""), SName.reference).width + 8;
				useEndpoint(relation.get("from"), x + 8, extent);
				useEndpoint(relation.get("to"), x + 8, extent);
			}
			for (Draft d : drafts.values()) {
				d.start = births.containsKey(d.node.id) ? births.get(d.node.id) : d.x + PADDING + d.cardWidth;
				final double cardRight = d.x + d.cardWidth + PADDING * 2;
				if (d.create == null) d.end = cardRight;
				else if (deaths.containsKey(d.node.id)) d.end = deaths.get(d.node.id);
				else d.end = Math.max(cardRight, Math.max(d.start + 48,
						extentEnds.containsKey(d.node.id) ? extentEnds.get(d.node.id) + 48 : 0));
				final Draft parentDraft = drafts.get(d.node.get("parent"));
				if (parentDraft != null && parentDraft.create == null && d.create != null) lanes = true;
				final String parent = d.node.get("parent");
				if (parent != null && deaths.containsKey(parent) && (d.end > deaths.get(parent))) lanes = true;
			}
			for (Node node : model.timeline) {
				if ("event".equals(node.kind)) {
					final Draft d = drafts.get(node.get("scope"));
					if (d != null && (column(node) < d.x || d.create != null && column(node) > d.end)) lanes = true;
				}
			}
			for (Node object : model.objects.values())
				if (deaths.containsKey(object.get("owner")) && (deaths.containsKey(object.id) == false
						|| deaths.get(object.id) > deaths.get(object.get("owner")))) lanes = true;
		}

		void useEndpoint(String id, double x, double extent) {
			use(endpointEntity(id), x, extent);
		}

		void use(String id, double x, double extent) {
			if (id == null) return;
			final Double previous = useEnds.get(id), previousExtent = extentEnds.get(id);
			if (previous == null || x > previous) useEnds.put(id, x);
			if (previousExtent == null || extent > previousExtent) extentEnds.put(id, extent);
			final Node entity = model.entities.get(id);
			if (entity != null && entity.get("owner") != null) use(entity.get("owner"), x, extent);
		}

		double placeScope(Draft d, double y) {
			d.y = y;
			double above = Math.max(18, selfAbove(d.node.id)), below = 18;
			for (Node node : model.timeline)
				if (d.node.id.equals(node.get("scope"))) below = Math.max(below, labels.get(node.id).height + 24);
			below = Math.max(below, branchBelow(d.node.id));
			d.trackY = y + d.cardTop + Math.max(above, d.cardHeight / 2);
			d.ownHeight = Math.max(d.ownHeight, d.trackY - y + below + PADDING);
			entityY.put(d.node.id, d.trackY);
			double next = y + d.ownHeight;
			for (Node node : model.objects.values())
				if (d.node.id.equals(node.get("owner"))) next = placeTrack(node, next + GAP);
			for (Node node : model.participants.values())
				if (d.node.id.equals(node.get("owner"))) next = placeTrack(node, next + GAP);
			for (Node node : model.objects.values())
				if (d.node.id.equals(node.get("owner")) && d.destroy == null)
					d.end = Math.max(d.end, tracks.get(node.id).endX + 8);
			for (Node node : model.participants.values())
				if (d.node.id.equals(node.get("owner")) && d.destroy == null)
					d.end = Math.max(d.end, tracks.get(node.id).endX + 8);
			d.ownHeight = next - y + PADDING;
			if (lanes == false) next += PADDING;
			for (Draft child : drafts.values())
				if (d.node.id.equals(child.node.get("parent"))) {
					next = placeScope(child, next + GAP);
					if (d.destroy == null)
						d.end = Math.max(d.end, Math.max(child.x + child.cardWidth + PADDING * 2, child.end + PADDING));
				}
			d.subtreeHeight = next - y + PADDING;
			return y + d.subtreeHeight;
		}

		double selfAbove(String id) {
			double above = 0;
			for (Node node : model.timeline)
				if ("message".equals(node.kind) && id.equals(endpointEntity(node.get("from")))
						&& id.equals(endpointEntity(node.get("to"))))
					above = Math.max(above, labels.get(node.id).height + 40);
			return above;
		}

		double placeTrack(Node node, double y) {
			final Label label = entityLabel(node);
			final Draft owner = drafts.get(node.get("owner"));
			final boolean unknown = births.containsKey(node.id) == false;
			final double birth = births.containsKey(node.id) ? births.get(node.id)
					: owner == null ? PADDING * 2 : owner.start;
			double end;
			if (deaths.containsKey(node.id)) end = deaths.get(node.id) + 8;
			else {
				end = Math.max(birth, useEnds.containsKey(node.id) ? useEnds.get(node.id) : birth) + 28;
			}
			final double start = Math.min(birth, end);
			final double trackY = y + Math.max(label.height + 20, selfAbove(node.id));
			final boolean beforeOwner = owner != null && start < owner.x;
			final Label ownership = beforeOwner ? label("owned by " + display(owner.node.id), SName.autofacMuted) : null;
			tracks.put(node.id, new TrackBox(node, label, start, trackY, end, unknown, deaths.containsKey(node.id) == false,
					ownership, beforeOwner ? owner.x : start));
			entityY.put(node.id, trackY);
			ports.put(node.id, new Port(node, start, trackY));
			width = Math.max(width, start + label.width + 24);
			width = Math.max(width, end + 8);
			if (ownership != null) width = Math.max(width, start + ownership.width + 24);
			double below = ownership == null ? 28 : ownership.height + 44;
			for (Node event : model.timeline)
				if (node.id.equals(event.get("scope"))) below = Math.max(below, labels.get(event.id).height + 24);
			below = Math.max(below, branchBelow(node.id));
			return trackY + below;
		}

		String endpointEntity(String id) {
			final Node event = model.events.get(id);
			if (event == null) return id;
			if ("event".equals(event.kind)) return event.get("scope");
			if ("message".equals(event.kind)) return endpointEntity(event.get("to"));
			return event.get("target", id);
		}

		double y(String id) {
			final Double y = entityY.get(endpointEntity(id));
			return y == null ? top : y;
		}

		double occurrenceY(Node node, String id) {
			final EventBox anchor = events.get(id);
			if (anchor != null) return anchor.y;
			return y(id) + rowOffset(node, endpointEntity(id));
		}

		void occurrences() {
			for (Node node : model.timeline) {
				final String target = "event".equals(node.kind) ? node.get("scope")
						: "message".equals(node.kind) ? node.get("to") : node.get("target");
				final double y = occurrenceY(node, target);
				final EventBox box = new EventBox(node, labels.get(node.id), column(node), y, y(target));
				events.put(node.id, box);
				ports.put(node.id, new Port(node, box.x, box.y));
			}
		}

		Point endpoint(String id, double x, double otherY, Node occurrence) {
			if ("@left".equals(id)) return new Point(PADDING, otherY);
			if ("@right".equals(id)) return new Point(horizon, otherY);
			final EventBox event = events.get(id);
			if (event != null) return new Point(event.x, event.y);
			final Node node = model.registrations.get(id);
			if (node != null && ports.containsKey(id)) return new Point(ports.get(id).x, ports.get(id).y);
			return new Point(x, occurrenceY(occurrence, id));
		}

		void messages() {
			for (Node node : model.timeline) {
				if ("message".equals(node.kind) == false) continue;
				final String from = node.get("from"), to = node.get("to");
				Point a = endpoint(from, column(node), occurrenceY(node, to), node);
				Point b = endpoint(to, column(node), a.y, node);
				if (node.arrow != null) {
					if (model.events.containsKey(from) == false && node.arrow.getInclination1() != 0)
						a = new Point(a.x + node.arrow.getInclination1(), a.y);
					if (model.events.containsKey(to) == false && node.arrow.getInclination2() != 0)
						b = new Point(b.x + node.arrow.getInclination2(), b.y);
				}
				final Label label = labels.get(node.id);
				final List<Point> route = new ArrayList<Point>();
				route.add(a);
				final double lx, ly;
				if (a.y == b.y && a.x == b.x) {
					final boolean reverse = node.arrow != null && node.arrow.isReverseDefine();
					final double out = a.x + (reverse ? -1 : 1) * Math.max(32, label.width + 16);
					final double loopY = a.y - label.height - 32;
					route.add(new Point(out, a.y)); route.add(new Point(out, loopY));
					route.add(new Point(a.x, loopY)); route.add(b);
					lx = Math.min(a.x, out) + 8; ly = loopY + 8;
				} else if (a.y == b.y) {
					route.add(b);
					lx = Math.min(a.x, b.x) + 8; ly = a.y - label.height - 12;
				} else {
					if (a.x != b.x && a.y != b.y && (node.arrow == null
							|| node.arrow.getInclination1() == 0 && node.arrow.getInclination2() == 0))
						route.add(new Point(b.x, a.y));
					route.add(b);
					lx = Math.max(a.x, b.x) + 10;
					ly = Math.min(a.y, b.y) + Math.max(10, (Math.abs(b.y - a.y) - label.height) / 2);
				}
				add(new Connector(node, "message", from, to, label, route, lx, ly,
						node.arrow == null ? ArrowConfiguration.withDirectionNormal() : node.arrow));
				events.put(node.id, new EventBox(node, label, b.x, b.y));
				ports.put(node.id, new Port(node, b.x, b.y));
			}
		}

		void references() {
			int index = 0;
			double referenceChannel = top - 12;
			for (Node node : model.relations) {
				final String from = node.get("from"), to = node.get("to");
				final Port source = ports.get(from), target = ports.get(to);
				if (source == null || target == null) continue;
				final Label label = label(node, node.get("label", ""), SName.reference);
				final Double planned = relationColumns.get(node.id);
				final double x = planned == null ? Math.max(source.x, target.x) + 16 + index * 12 : planned;
				final Point a = entityPoint(from, x), b = entityPoint(to, x);
				final List<Point> route = new ArrayList<Point>(); route.add(a);
				final double lx, ly;
				if (model.registrations.containsKey(from) || model.registrations.containsKey(to)
						|| model.modules.containsKey(from) || model.modules.containsKey(to)) {
					final boolean targetRow = model.registrations.containsKey(to) || model.modules.containsKey(to);
					final Point row = targetRow ? b : a, occurrence = targetRow ? a : b;
					final double gutter = row.x + 12 + index * 8;
					final List<Point> lookup = new ArrayList<Point>();
					lookup.add(occurrence); lookup.add(new Point(gutter, occurrence.y));
					lookup.add(new Point(gutter, row.y)); lookup.add(row);
					if (targetRow == false) Collections.reverse(lookup);
					route.clear(); route.addAll(lookup);
					lx = Math.min(occurrence.x, gutter) + 8;
					ly = Math.min(occurrence.y, row.y) - label.height - 8;
				} else if (a.y == b.y) {
					referenceChannel -= label.height + 12;
					final double channel = referenceChannel;
					route.add(new Point(a.x, channel)); route.add(new Point(b.x, channel)); route.add(b);
					lx = Math.min(a.x, b.x) + 8; ly = channel - label.height - 4;
				} else {
					if (a.x != b.x) route.add(model.events.containsKey(to) ? new Point(a.x, b.y) : new Point(b.x, a.y));
					route.add(b); lx = Math.max(a.x, b.x) + 10; ly = (a.y + b.y - label.height) / 2;
				}
				add(new Connector(node, "reference", from, to, label, route, lx, Math.max(2, ly),
						node.arrow == null ? ArrowConfiguration.withDirectionNormal() : node.arrow));
				index++;
			}
			for (Draft d : drafts.values())
				for (ConstructionRow row : boxes.get(d.node.id).rows)
					if ("install".equals(row.node.kind)) {
						final String from = row.node.get("target");
						if (ports.containsKey(from) == false) continue;
						final double x = boxes.get(d.node.id).constructionX - 12;
						final Point a = entityPoint(from, x);
						final Point b = new Point(boxes.get(d.node.id).constructionX, row.y + row.label.height / 2);
						final List<Point> route = new ArrayList<Point>(); route.add(a);
						route.add(new Point(x, a.y)); route.add(new Point(x, b.y)); route.add(b);
						final Label label = label("", SName.autofacModule);
						add(new Connector(row.node, "provenance", from, row.node.id, label, route,
								x + 8, Math.max(a.y + 8, (a.y + b.y - label.height) / 2), ArrowConfiguration.withDirectionNormal()));
					}
		}

		Point entityPoint(String id, double x) {
			final Port port = ports.get(id);
			if (model.events.containsKey(id) || model.registrations.containsKey(id)) return new Point(port.x, port.y);
			if (model.scopes.containsKey(id) == false && model.objects.containsKey(id) == false
					&& model.participants.containsKey(id) == false) return new Point(port.x, port.y);
			final TrackBox track = tracks.get(id);
			if (track != null) x = Math.max(track.x, Math.min(track.endX, x));
			final ScopeBox scope = boxes.get(id);
			if (scope != null) x = Math.max(scope.activeStartX, Math.min(scope.trackEndX, x));
			return new Point(x, port.y);
		}

		void obstacles() {
			for (ModuleBox module : modules) {
				obstacles.add(new Obstacle(module.x, module.y, module.width, module.height));
				noteObstacles.add(new Obstacle(module.x, module.y, module.width, module.height));
			}
			for (ScopeBox scope : boxes.values()) {
				obstacles.add(new Obstacle(scope.headerX, scope.headerY, scope.header.width, scope.header.height));
				obstacles.add(new Obstacle(scope.subtitleX, scope.subtitleY, scope.subtitle.width, scope.subtitle.height));
				obstacles.add(new Obstacle(scope.constructionX, scope.constructionY, scope.constructionWidth, scope.constructionHeight));
				labelBoundaries.add(new Obstacle(scope.x, scope.y - 3, scope.width, 6));
				labelBoundaries.add(new Obstacle(scope.x, scope.bottom() - 3, scope.width, 6));
				labelBoundaries.add(new Obstacle(scope.x - 3, scope.y, 6, scope.height));
				labelBoundaries.add(new Obstacle(scope.right() - 3, scope.y, 6, scope.height));
			}
			for (TrackBox track : tracks.values()) {
				obstacles.add(new Obstacle(track.labelX, track.labelY, track.label.width, track.label.height + 2));
				if (track.ownership != null)
					obstacles.add(new Obstacle(track.ownershipX, track.ownershipY, track.ownership.width, track.ownership.height));
			}
			for (EventBox event : events.values())
				if ("event".equals(event.node.kind))
					obstacles.add(new Obstacle(event.labelX, event.labelY, event.label.width, event.label.height));
			for (GroupBox group : groups) {
				if (group.label.height > 0)
					obstacles.add(new Obstacle(group.x, group.y, group.label.width + 20, group.label.height + 12));
				for (Branch branch : group.branches)
					obstacles.add(new Obstacle(branch.x + 8, group.y + 5, branch.label.width, branch.label.height));
			}
			for (NoteBox note : notes)
				obstacles.add(new Obstacle(note.x, note.y, note.width, note.height));
			noteObstacles.clear();
			noteObstacles.addAll(obstacles);
			noteObstacles.addAll(labelBoundaries);
			for (TrackBox track : tracks.values()) {
				noteObstacles.add(new Obstacle(track.x - 2, track.y - 2, track.endX - track.x + 4, 4));
				if (track.ownership != null) noteObstacles.add(new Obstacle(
						track.ownershipX, track.ownershipY, track.ownership.width, track.ownership.height));
			}
			for (ScopeBox scope : boxes.values()) {
				if (scope.created)
					noteObstacles.add(new Obstacle(scope.activeStartX - 2, scope.trackY - 2,
							scope.trackEndX - scope.activeStartX + 4, 4));
				noteObstacles.add(new Obstacle(scope.x, scope.y - 2, scope.width, 4));
				noteObstacles.add(new Obstacle(scope.x, scope.bottom() - 2, scope.width, 4));
				noteObstacles.add(new Obstacle(scope.x - 2, scope.y, 4, scope.height));
				noteObstacles.add(new Obstacle(scope.right() - 2, scope.y, 4, scope.height));
			}
			for (GroupBox group : groups) {
				noteObstacles.add(new Obstacle(group.x, group.y - 2, group.width, 4));
				noteObstacles.add(new Obstacle(group.x, group.y + group.height - 2, group.width, 4));
				noteObstacles.add(new Obstacle(group.x - 2, group.y, 4, group.height));
				noteObstacles.add(new Obstacle(group.x + group.width - 2, group.y, 4, group.height));
			}
			for (ActivationBox activation : activations)
				noteObstacles.add(new Obstacle(activation.x1 - 3, activation.y - 7,
						activation.x2 - activation.x1 + 6, 14));
		}

		boolean clearLabel(Label label, double x, double y) {
			for (Obstacle obstacle : obstacles)
				if (obstacle.intersects(x, y, label.width, label.height)) return false;
			for (Obstacle boundary : labelBoundaries)
				if (boundary.intersects(x, y, label.width, label.height)) return false;
			return true;
		}

		boolean clearRoute(List<Point> points) {
			for (int i = 1; i < points.size(); i++)
				for (Obstacle obstacle : obstacles)
					if (obstacle.crosses(points.get(i - 1), points.get(i))) return false;
			return true;
		}

		/** Only obstructed segments use the orthogonal visibility grid; ordinary calls remain straight. */
		List<Point> avoidText(List<Point> route) {
			if (clearRoute(route)) return route;
			final List<Point> result = new ArrayList<Point>();
			result.add(route.get(0));
			for (int i = 1; i < route.size(); i++) {
				final Point a = route.get(i - 1), b = route.get(i);
				boolean clear = true;
				for (Obstacle obstacle : obstacles)
					if (obstacle.crosses(a, b)) { clear = false; break; }
				if (clear) result.add(b);
				else {
					final List<Point> detour = routeBetween(a, b);
					for (int j = 1; j < detour.size(); j++) result.add(detour.get(j));
				}
			}
			return result;
		}

		List<Point> routeBetween(Point a, Point b) {
			final TreeSet<Double> horizontal = new TreeSet<Double>(), vertical = new TreeSet<Double>();
			horizontal.add(a.x); horizontal.add(b.x); vertical.add(a.y); vertical.add(b.y);
			final List<Obstacle> blocking = new ArrayList<Obstacle>();
			for (Obstacle obstacle : obstacles) {
				if (obstacle.contains(a) || obstacle.contains(b)) continue;
				blocking.add(obstacle);
				horizontal.add(Math.max(2, obstacle.x - 8)); horizontal.add(obstacle.right + 8);
				vertical.add(Math.max(2, obstacle.y - 8)); vertical.add(obstacle.bottom + 8);
			}
			final List<Double> xs = new ArrayList<Double>(horizontal), ys = new ArrayList<Double>(vertical);
			final int nx = xs.size(), ny = ys.size();
			final int start = Collections.binarySearch(ys, a.y) * nx + Collections.binarySearch(xs, a.x);
			final int finish = Collections.binarySearch(ys, b.y) * nx + Collections.binarySearch(xs, b.x);
			final double[] distance = new double[nx * ny];
			final int[] previous = new int[distance.length];
			Arrays.fill(distance, Double.POSITIVE_INFINITY); Arrays.fill(previous, -1);
			distance[start] = 0;
			final PriorityQueue<RouteStep> pending = new PriorityQueue<RouteStep>();
			pending.add(new RouteStep(start, 0, Math.abs(a.x - b.x) + Math.abs(a.y - b.y)));
			while (pending.isEmpty() == false) {
				final RouteStep step = pending.remove();
				if (step.cost != distance[step.index]) continue;
				if (step.index == finish) break;
				final int ix = step.index % nx, iy = step.index / nx;
				final double x = xs.get(ix), y = ys.get(iy);
				for (int direction = 0; direction < 4; direction++) {
					final int tx = ix + (direction == 0 ? -1 : direction == 1 ? 1 : 0);
					final int ty = iy + (direction == 2 ? -1 : direction == 3 ? 1 : 0);
					if (tx < 0 || tx >= nx || ty < 0 || ty >= ny) continue;
					final double px = xs.get(tx), py = ys.get(ty);
					boolean clear = true;
					for (Obstacle obstacle : blocking)
						if (obstacle.crosses(x, y, px, py)) { clear = false; break; }
					if (clear == false) continue;
					final int next = ty * nx + tx;
					final double cost = step.cost + Math.abs(px - x) + Math.abs(py - y);
					if (cost >= distance[next]) continue;
					distance[next] = cost; previous[next] = step.index;
					pending.add(new RouteStep(next, cost, cost + Math.abs(px - b.x) + Math.abs(py - b.y)));
				}
			}
			if (previous[finish] < 0 && finish != start)
				return new ArrayList<Point>(Arrays.asList(a, b));
			final List<Point> reversed = new ArrayList<Point>();
			for (int at = finish; at >= 0; at = previous[at])
				reversed.add(new Point(xs.get(at % nx), ys.get(at / nx)));
			Collections.reverse(reversed);
			final List<Point> result = new ArrayList<Point>();
			for (Point point : reversed) {
				if (result.size() >= 2) {
					final Point p = result.get(result.size() - 2), q = result.get(result.size() - 1);
					if (p.x == q.x && q.x == point.x || p.y == q.y && q.y == point.y)
						result.remove(result.size() - 1);
				}
				result.add(point);
			}
			return result;
		}

		Point connectorLabel(Connector connector, List<Point> route) {
			final Label label = connector.label;
			if (label.height == 0 || clearLabel(label, connector.labelX, connector.labelY))
				return new Point(connector.labelX, connector.labelY);
			for (int i = 1; i < route.size(); i++) {
				final Point a = route.get(i - 1), b = route.get(i);
				if (a.x == b.x) {
					final double low = Math.min(a.y, b.y) + 8, high = Math.max(a.y, b.y) - label.height - 8;
					for (double y = low; y <= high; y += 8) {
						if (clearLabel(label, a.x + 10, y)) return new Point(a.x + 10, y);
						if (clearLabel(label, a.x - label.width - 10, y)) return new Point(a.x - label.width - 10, y);
					}
				} else if (a.y == b.y) {
					final double low = Math.min(a.x, b.x) + 8, high = Math.max(a.x, b.x) - label.width - 8;
					for (double x = low; x <= high; x += 8) {
						if (clearLabel(label, x, a.y - label.height - 10)) return new Point(x, a.y - label.height - 10);
						if (clearLabel(label, x, a.y + 10)) return new Point(x, a.y + 10);
					}
				}
			}
			// Search the same local column, never a synthetic post-disposal/global-right channel.
			for (double delta = 8; ; delta += 8) {
				if (connector.labelY - delta >= 2 && clearLabel(label, connector.labelX, connector.labelY - delta))
					return new Point(connector.labelX, connector.labelY - delta);
				if (clearLabel(label, connector.labelX, connector.labelY + delta))
					return new Point(connector.labelX, connector.labelY + delta);
			}
		}

		void add(Connector connector) {
			final List<Point> route = avoidText(connector.points);
			final Point label = connectorLabel(connector, route);
			if (route != connector.points || label.x != connector.labelX || label.y != connector.labelY)
				connector = new Connector(connector.node, connector.kind, connector.from, connector.to,
						connector.label, route, label.x, label.y, connector.arrow);
			connectors.add(connector);
			if (connector.label.width > 0)
				obstacles.add(new Obstacle(label.x, label.y, connector.label.width, connector.label.height));
			if (connector.label.width > 0 || connector.label.height > 0) {
				width = Math.max(width, connector.labelX + connector.label.width + PADDING);
				bottom = Math.max(bottom, connector.labelY + connector.label.height + PADDING);
			}
			for (Point p : connector.points) { width = Math.max(width, p.x + PADDING); bottom = Math.max(bottom, p.y + PADDING); }
		}

		void frames() {
			final List<Node> starters = new ArrayList<Node>();
			final List<Node> branchStarts = new ArrayList<Node>();
			final List<Integer> branchBegin = new ArrayList<Integer>();
			final List<Integer> depths = new ArrayList<Integer>();
			for (int index = 0; index < model.timeline.size(); index++) {
				final Node node = model.timeline.get(index);
				if ("groupStart".equals(node.kind)) {
					starters.add(node);
					branchStarts.add(node);
					branchBegin.add(index + 1);
					depths.add(starters.size());
				} else if ("groupElse".equals(node.kind)) {
					int at = branchOf(starters, node.get("group"), branchStarts);
					if (at >= 0) {
						emitGroup(branchStarts.get(at), branchBegin.get(at), index, depths.get(at));
						branchStarts.set(at, node);
						branchBegin.set(at, index + 1);
					}
				} else if ("groupEnd".equals(node.kind) && starters.isEmpty() == false) {
					final int at = branchOf(starters, node.get("group"), branchStarts);
					final int top = starters.size() - 1;
					final int which = at >= 0 ? at : top;
					emitGroup(branchStarts.get(which), branchBegin.get(which), index, depths.get(which));
					for (int i = branchBegin.size() - 1; i >= top; i--) {
						starters.remove(i); branchStarts.remove(i); branchBegin.remove(i); depths.remove(i);
					}
				}
			}
		}

		int branchOf(List<Node> starters, String id, List<Node> branchStarts) {
			for (int i = starters.size() - 1; i >= 0; i--)
				if (starters.get(i).id.equals(id) || branchStarts.get(i).id.equals(id)) return i;
			return starters.isEmpty() ? -1 : starters.size() - 1;
		}

		void emitGroup(Node starter, int begin, int end, int depth) {
			final Map<String, double[]> regions = new LinkedHashMap<String, double[]>();
			double minX = Double.POSITIVE_INFINITY, maxX = 0;
			for (int i = begin; i < end; i++) {
				final Node node = model.timeline.get(i);
				final List<String> targets = new ArrayList<String>();
				if ("event".equals(node.kind) || "activate".equals(node.kind) || "deactivate".equals(node.kind)
						|| "create".equals(node.kind) || "destroy".equals(node.kind))
					targets.add(node.get("scope", node.get("target")));
				else if ("message".equals(node.kind)) {
					targets.add(endpointEntity(node.get("from"))); targets.add(endpointEntity(node.get("to")));
				} else if ("note".equals(node.kind)) {
					final String noteTargets = node.get("targets", node.get("target", ""));
					for (String target : noteTargets.split(",")) targets.add(endpointEntity(target.trim()));
				} else continue;
				double topPad = 12, bottomPad = 12;
				if ("event".equals(node.kind)) bottomPad = labels.get(node.id).height + 18;
				else if ("message".equals(node.kind)) { topPad = labels.get(node.id).height + 10; bottomPad = labels.get(node.id).height + 10; }
				else if ("note".equals(node.kind)) { topPad = labels.get(node.id).height + 12; bottomPad = labels.get(node.id).height + 12; }
				for (String target : targets) {
					if (target == null || ports.containsKey(target) == false) continue;
					final double targetY = occurrenceY(node, target);
					final String scope = scopeForEntity(target);
					final String key = scope == null ? "" : scope;
					double[] bounds = regions.get(key);
					if (bounds == null) { bounds = new double[] { targetY - topPad, targetY + bottomPad }; regions.put(key, bounds); }
					else { bounds[0] = Math.min(bounds[0], targetY - topPad); bounds[1] = Math.max(bounds[1], targetY + bottomPad); }
					minX = Math.min(minX, column(node) - 10);
					maxX = Math.max(maxX, column(node) + labels.get(node.id).width + 10);
				}
			}
			if (regions.isEmpty()) return;
			Label groupLabel = labels.get(starter.id);
			if ("groupElse".equals(starter.kind)) groupLabel = label("else " + groupLabel.text, SName.group);
			final List<String> regionOrder = new ArrayList<String>(regions.keySet());
			Collections.sort(regionOrder, new Comparator<String>() {
				public int compare(String first, String second) {
					return Double.compare(regions.get(first)[0], regions.get(second)[0]);
				}
			});
			final double[] range = groupRanges.get(starter.id);
			final String logicalId = logicalGroups.containsKey(starter.id) ? logicalGroups.get(starter.id) : starter.id;
			final Label emptyLabel = label("", SName.group);
			boolean continuation = false;
			double continuationY = -1;
			for (String key : regionOrder) {
				double minY = regions.get(key)[0], maxY = regions.get(key)[1];
				double top = Math.max(2, minY - groupLabel.height - 20 - Math.max(0, 3 - depth) * 12);
				double frameBottom = maxY + 8 + Math.max(0, 3 - depth) * 8;
				double x = range == null ? Math.min(column(starter) - 10, minX) : range[0];
				double frameWidth = range == null ? Math.max(groupLabel.width + 24, maxX - x) : range[1] - range[0];
				if (key.length() > 0 && boxes.containsKey(key)) {
					final ScopeBox scope = boxes.get(key);
					top = Math.max(scope.y + 6, top);
					frameBottom = Math.min(scope.bottom() - 6, Math.max(top + groupLabel.height + 18, frameBottom));
					if (range == null) {
						x = Math.max(scope.x + 6, x);
						frameWidth = Math.max(groupLabel.width + 24, Math.min(scope.right() - 6, x + frameWidth) - x);
					}
				}
				final String uid = regions.size() == 1 ? "af-group-" + starter.id
						: "af-group-" + starter.id + (key.length() == 0 ? "-external" : "-" + key);
				final GroupBox group = new GroupBox(starter, uid, logicalId, continuation ? emptyLabel : groupLabel, x, top,
						frameWidth, frameBottom - top, new ArrayList<Branch>(), continuation, continuationY);
				groups.add(group);
				width = Math.max(width, group.x + group.width + PADDING);
				bottom = Math.max(bottom, group.y + group.height + PADDING);
				continuation = true;
				continuationY = group.y + group.height;
			}
		}

		String scopeForEntity(String id) {
			if (model.scopes.containsKey(id)) return id;
			final Node entity = model.entities.get(id);
			if (entity != null && entity.get("owner") != null && model.scopes.containsKey(entity.get("owner")))
				return entity.get("owner");
			return null;
		}

		void activities() {
			final Map<String, List<Node>> active = new LinkedHashMap<String, List<Node>>();
			for (Node node : model.timeline) {
				final String target = node.get("target");
				if ("activate".equals(node.kind)) {
					if (active.containsKey(target) == false) active.put(target, new ArrayList<Node>());
					active.get(target).add(node);
				} else if ("deactivate".equals(node.kind) && active.containsKey(target) && active.get(target).isEmpty() == false) {
					final Node start = active.get(target).remove(active.get(target).size() - 1);
					activations.add(new ActivationBox(start, target, column(start), column(node), occurrenceY(start, target) + active.get(target).size() * 4, false));
				}
			}
			for (Map.Entry<String, List<Node>> entry : active.entrySet())
				for (int i = 0; i < entry.getValue().size(); i++) {
					final Node start = entry.getValue().get(i);
					activations.add(new ActivationBox(start, entry.getKey(), column(start), entityEnd(entry.getKey()),
							occurrenceY(start, entry.getKey()) + i * 4, true));
				}
		}

		double entityEnd(String id) {
			final TrackBox track = tracks.get(id);
			if (track != null) return track.endX;
			final ScopeBox scope = boxes.get(id);
			if (scope != null) return scope.trackEndX;
			return horizon;
		}

		void userNotes() {
			for (Node node : model.timeline) {
				if ("note".equals(node.kind) == false) continue;
				final Label label = labels.get(node.id);
				final String targets = node.get("targets", node.get("target", ""));
				double minY = Double.POSITIVE_INFINITY, maxY = 0, minX = Double.POSITIVE_INFINITY, maxX = 0;
				for (String target : targets.split(",")) {
					final String id = target.trim();
					if (ports.containsKey(id)) {
						final Node occurrence = model.events.get(id);
						final double targetY = occurrence != null && "message".equals(occurrence.kind)
								? (y(occurrence.get("from")) + y(occurrence.get("to"))) / 2 : y(id);
						minY = Math.min(minY, targetY); maxY = Math.max(maxY, targetY);
						minX = Math.min(minX, ports.get(id).x); maxX = Math.max(maxX, ports.get(id).x);
					}
				}
				if (minY == Double.POSITIVE_INFINITY) {
					minY = top; maxY = Math.min(bottom, top + 80);
					minX = column(node); maxX = column(node);
				}
				final NoteBox size = new NoteBox(node, label, 0, 0, 0);
				final double preferredX = Math.max(PADDING, (minX + maxX - size.width) / 2);
				final String position = node.get("position", "over");
				final boolean preferBelow = "right".equals(position) || "bottom".equals(position);
				final double above = minY - size.height - 16, below = maxY + 16;
				NoteBox chosen = null;
				for (int row = 0; row < 24 && chosen == null; row++) {
					final double firstY = preferBelow ? below + row * (size.height + 8) : above - row * (size.height + 8);
					final double secondY = preferBelow ? above - row * (size.height + 8) : below + row * (size.height + 8);
					chosen = noteShelf(node, label, size, firstY, preferredX);
					if (chosen == null) chosen = noteShelf(node, label, size, secondY, preferredX);
				}
				if (chosen == null) chosen = noteShelf(node, label, size, obstacleBottom() + 20, preferredX);
				if (chosen == null) chosen = new NoteBox(node, label, preferredX, obstacleBottom() + 20, 0);
				notes.add(chosen);
				noteObstacles.add(new Obstacle(chosen.x, chosen.y, chosen.width, chosen.height));
				obstacles.add(new Obstacle(chosen.x, chosen.y, chosen.width, chosen.height));
				width = Math.max(width, chosen.x + chosen.width + PADDING);
				bottom = Math.max(bottom, chosen.y + chosen.height + PADDING);
			}
		}

		NoteBox noteShelf(Node node, Label label, NoteBox size, double y, double preferredX) {
			if (y < PADDING) return null;
			for (int shift = 0; shift < 8; shift++) {
				final double[] positions = shift == 0 ? new double[] { preferredX }
						: new double[] { preferredX + shift * 24, preferredX - shift * 24 };
				for (double x : positions) {
					if (x < PADDING) continue;
					final NoteBox candidate = new NoteBox(node, label, x, y, 0);
					if (noteClear(candidate)) return candidate;
				}
			}
			return null;
		}

		boolean noteClear(NoteBox note) {
			for (Obstacle obstacle : noteObstacles)
				if (obstacle.intersects(note.x, note.y, note.width, note.height)) return false;
			return true;
		}

		double obstacleBottom() {
			double result = bottom;
			for (Obstacle obstacle : noteObstacles) result = Math.max(result, obstacle.bottom);
			return result;
		}
	}
}
