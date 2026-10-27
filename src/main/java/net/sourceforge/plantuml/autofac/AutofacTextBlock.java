package net.sourceforge.plantuml.autofac;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.sourceforge.plantuml.autofac.AutofacLayout.ActivationBox;
import net.sourceforge.plantuml.autofac.AutofacLayout.Branch;
import net.sourceforge.plantuml.autofac.AutofacLayout.Connector;
import net.sourceforge.plantuml.autofac.AutofacLayout.ConstructionRow;
import net.sourceforge.plantuml.autofac.AutofacLayout.EventBox;
import net.sourceforge.plantuml.autofac.AutofacLayout.GroupBox;
import net.sourceforge.plantuml.autofac.AutofacLayout.Label;
import net.sourceforge.plantuml.autofac.AutofacLayout.ModuleBox;
import net.sourceforge.plantuml.autofac.AutofacLayout.NoteBox;
import net.sourceforge.plantuml.autofac.AutofacLayout.Point;
import net.sourceforge.plantuml.autofac.AutofacLayout.ScopeBox;
import net.sourceforge.plantuml.autofac.AutofacLayout.TrackBox;
import net.sourceforge.plantuml.autofac.AutofacModel.Node;
import net.sourceforge.plantuml.klimt.UGroup;
import net.sourceforge.plantuml.klimt.UGroupType;
import net.sourceforge.plantuml.klimt.UPath;
import net.sourceforge.plantuml.klimt.UStroke;
import net.sourceforge.plantuml.klimt.UTranslate;
import net.sourceforge.plantuml.klimt.color.HColor;
import net.sourceforge.plantuml.klimt.color.HColors;
import net.sourceforge.plantuml.klimt.drawing.UGraphic;
import net.sourceforge.plantuml.klimt.font.StringBounder;
import net.sourceforge.plantuml.klimt.geom.XDimension2D;
import net.sourceforge.plantuml.klimt.shape.TextBlock;
import net.sourceforge.plantuml.klimt.shape.UEllipse;
import net.sourceforge.plantuml.klimt.shape.ULine;
import net.sourceforge.plantuml.klimt.shape.UPolygon;
import net.sourceforge.plantuml.klimt.shape.URectangle;
import net.sourceforge.plantuml.skin.ArrowConfiguration;
import net.sourceforge.plantuml.skin.ArrowDecoration;
import net.sourceforge.plantuml.skin.ArrowDressing;
import net.sourceforge.plantuml.skin.ArrowHead;
import net.sourceforge.plantuml.skin.ArrowPart;
import net.sourceforge.plantuml.style.ISkinParam;
import net.sourceforge.plantuml.style.PName;
import net.sourceforge.plantuml.style.SName;
import net.sourceforge.plantuml.style.Style;

/** Native vector drawing, including actual native sequence arrow configurations. */
public final class AutofacTextBlock implements TextBlock {
	private final AutofacModel model;
	private final ISkinParam skin;
	private final Map<SName, Style> styles = new EnumMap<SName, Style>(SName.class);
	private StringBounder measuredBounder;
	private Object measuredStyles;
	private AutofacLayout measuredLayout;

	public AutofacTextBlock(AutofacModel model, ISkinParam skinParam) {
		this.model = model;
		this.skin = skinParam;
	}

	private AutofacLayout layout(StringBounder bounder) {
		final Object currentStyles = skin.getCurrentStyleBuilder();
		if (measuredLayout == null || measuredBounder != bounder || measuredStyles != currentStyles) {
			measuredLayout = AutofacLayout.compute(model, skin, bounder);
			measuredBounder = bounder;
			measuredStyles = currentStyles;
			styles.clear();
		}
		return measuredLayout;
	}

	@Override
	public XDimension2D calculateDimension(StringBounder bounder) {
		final AutofacLayout layout = layout(bounder);
		return new XDimension2D(layout.width, layout.height);
	}

	@Override
	public HColor getBackcolor() { return skin.getBackgroundColor(); }

	@Override
	public void drawU(UGraphic ug) {
		final AutofacLayout layout = layout(ug.getStringBounder());
		for (ModuleBox module : layout.modules) drawModule(ug, module);
		for (ScopeBox scope : layout.scopeOrder) drawEnvelope(ug, scope, layout);
		for (GroupBox group : layout.groups) drawGroup(ug, group);
		for (ScopeBox scope : layout.scopeOrder) drawConstruction(ug, scope);
		for (TrackBox track : layout.tracks.values()) drawTrack(ug, track);
		for (ActivationBox activation : layout.activations) drawActivation(ug, activation);
		for (Connector connector : layout.connectors) drawConnector(ug, connector);
		for (EventBox event : layout.events.values()) drawEvent(ug, event);
		for (NoteBox note : layout.notes) drawNote(ug, note);
		start(ug, "af-order", "autofac-order", null);
		text(ug, layout.orderLabel, layout.orderX, layout.orderY);
		ug.closeGroup();
	}

	private Style style(SName role) {
		Style result = styles.get(role);
		if (result == null) {
			result = AutofacLayout.style(skin, role);
			styles.put(role, result);
		}
		return result;
	}

	private Style style(SName role, Node node) {
		return node.get("stereotype") == null ? style(role) : AutofacLayout.style(skin, role, node.get("stereotype"));
	}

	private HColor lineColor(SName role) {
		return style(role).value(PName.LineColor).asColor(skin.getIHtmlColorSet());
	}

	private UGraphic paint(UGraphic ug, SName role, boolean fill) {
		HColor background = fill ? style(role).value(PName.BackGroundColor).asColor(skin.getIHtmlColorSet()) : HColors.none();
		if (background == null) background = HColors.none();
		return ug.apply(lineColor(role)).apply(background.bg()).apply(style(role).getStroke());
	}

	private UGraphic paint(UGraphic ug, SName role, boolean fill, Node node) {
		final Style resolved = style(role, node);
		final HColor fillColor = fill ? resolved.value(PName.BackGroundColor).asColor(skin.getIHtmlColorSet()) : HColors.none();
		UGraphic styled = ug.apply(resolved.value(PName.LineColor).asColor(skin.getIHtmlColorSet()))
				.apply(fillColor.bg()).apply(resolved.getStroke());
		final HColor line = color(node, "lineColor");
		final HColor background = color(node, "backgroundColor");
		if (line != null) styled = styled.apply(line);
		if (background != null && fill) styled = styled.apply(background.bg());
		return styled;
	}

	private HColor color(Node node, String attribute) {
		return node.get(attribute) == null ? null : skin.getIHtmlColorSet().getColorOrNull(node.get(attribute));
	}

	private static void start(UGraphic ug, String uid, String cssClass, Node source) {
		final UGroup group = new UGroup(source == null || source.source == null ? null : source.source.getLocation());
		group.put(UGroupType.DATA_UID, uid);
		group.put(UGroupType.CLASS, cssClass);
		ug.startGroup(group);
	}

	private static void text(UGraphic ug, Label label, double x, double y) {
		if (label.width > 0 || label.height > 0) label.block.drawU(ug.apply(new UTranslate(x, y)));
	}

	private static void line(UGraphic ug, double x1, double y1, double x2, double y2) {
		ug.apply(new UTranslate(x1, y1)).draw(new ULine(x2 - x1, y2 - y1));
	}

	private void drawModule(UGraphic ug, ModuleBox module) {
		start(ug, "af-module-" + module.node.id, "autofac-module", module.node);
		final UGraphic styled = paint(ug, SName.autofacModule, true, module.node);
		styled.apply(new UTranslate(module.x, module.y))
				.draw(URectangle.build(module.width, module.height).rounded(8));
		text(ug, module.header, module.headerX, module.headerY);
		final double divider = module.y + module.header.height + 20;
		line(styled, module.x + 12, divider, module.x + module.width - 12, divider);
		for (ConstructionRow row : module.rows) {
			start(ug, "af-module-reg-" + row.node.id, "autofac-registration", row.node);
			if ("register".equals(row.node.kind))
				paint(ug, SName.autofacModule, false).apply(new UTranslate(row.x - 12, row.y + 5))
						.draw(URectangle.build(6, 6));
			text(ug, row.label, row.x, row.y);
			text(ug, row.metadata, row.x, row.y + row.label.height + 4);
			ug.closeGroup();
		}
		ug.closeGroup();
	}

	private void drawEnvelope(UGraphic ug, ScopeBox scope, AutofacLayout layout) {
		start(ug, "af-scope-" + scope.node.id, "autofac-scope", scope.node);
		final SName role = scope.depth == 0 ? SName.autofacLifetime : SName.autofacChildLifetime;
		paint(ug, role, true).apply(new UTranslate(scope.x, scope.y))
				.draw(URectangle.build(scope.width, scope.height).rounded(12));
		text(ug, scope.header, scope.headerX, scope.headerY);
		text(ug, scope.subtitle, scope.subtitleX, scope.subtitleY);
		if (scope.created) {
			final double constructionEnd = scope.constructionX + scope.constructionWidth;
			if (constructionEnd < scope.birthX)
				line(paint(ug, scope.depth == 0 ? SName.autofacConstruction : SName.autofacChildConstruction, false)
						.apply(new UStroke(3, 4, 1.5)), constructionEnd, scope.trackY, scope.birthX, scope.trackY);
			final UGraphic track = paint(ug, SName.lifeLine, false);
			line(track, scope.activeStartX, scope.trackY, scope.trackEndX, scope.trackY);
			if (scope.open) continuation(track, scope.trackEndX, scope.trackY);
		}
		if (layout.lanes && scope.node.get("parent") != null) {
			final ScopeBox parent = layout.scopes.get(scope.node.get("parent"));
			if (parent != null) {
				final double x = 8 + scope.depth * 6;
				final UGraphic ancestry = paint(ug, SName.autofacMuted, false).apply(new UStroke(3, 3, 1));
				line(ancestry, parent.x, parent.y + 8, x, parent.y + 8);
				line(ancestry, x, parent.y + 8, x, scope.y + 8);
				line(ancestry, x, scope.y + 8, scope.x, scope.y + 8);
			}
		}
		ug.closeGroup();
	}

	private void drawConstruction(UGraphic ug, ScopeBox scope) {
		start(ug, "af-construction-" + scope.node.id, "autofac-construction", scope.node);
		final SName role = scope.depth == 0 ? SName.autofacConstruction : SName.autofacChildConstruction;
		final UGraphic styled = paint(ug, role, true);
		styled.apply(new UTranslate(scope.constructionX, scope.constructionY))
				.draw(URectangle.build(scope.constructionWidth, scope.constructionHeight).rounded(8));
		text(ug, scope.constructionTitle, scope.constructionX + 12, scope.constructionY + 12);
		final double divider = scope.constructionY + scope.constructionTitle.height + 20;
		line(styled, scope.constructionX + 12, divider, scope.constructionX + scope.constructionWidth - 12, divider);
		for (ConstructionRow row : scope.rows) {
			start(ug, "af-reg-" + row.node.id, "register".equals(row.node.kind)
					? "autofac-registration" : "install".equals(row.node.kind) ? "autofac-install" : "autofac-decoration", row.node);
			if ("register".equals(row.node.kind))
				paint(ug, role, false).apply(new UTranslate(row.x - 12, row.y + 5)).draw(URectangle.build(6, 6));
			text(ug, row.label, row.x, row.y);
			text(ug, row.metadata, row.x, row.y + row.label.height + 4);
			ug.closeGroup();
		}
		ug.closeGroup();
	}

	private void drawTrack(UGraphic ug, TrackBox track) {
		start(ug, "af-" + ("object".equals(track.node.kind) ? "instance-" : "participant-") + track.node.id,
				"object".equals(track.node.kind) ? "autofac-instance" : "autofac-participant", track.node);
		final SName role = AutofacLayout.entityRole(track.node);
		final UGraphic styled = paint(ug, role, false, track.node);
		text(ug, track.label, track.labelX, track.labelY);
		if ("object".equals(track.node.kind))
			line(styled, track.labelX, track.labelY + track.label.height + 2,
					track.labelX + track.label.width, track.labelY + track.label.height + 2);
		else drawParticipantSymbol(paint(ug, role, false, track.node), track);
		if (track.openStart) {
			final double uncertainEnd = Math.min(track.endX, track.x + 24);
			line(styled.apply(new UStroke(2, 3, 1.5)), track.x, track.y, uncertainEnd, track.y);
			if (uncertainEnd < track.endX) line(styled, uncertainEnd, track.y, track.endX, track.y);
		} else line(styled, track.x, track.y, track.endX, track.y);
		if (track.open) continuation(styled, track.endX, track.y);
		if (track.ownership != null) {
			start(ug, "af-ownership-" + track.node.id, "autofac-ownership", track.node);
			final UGraphic leader = paint(ug, SName.autofacMuted, false).apply(new UStroke(2, 4, 1));
			line(leader, track.x, track.y + 6, track.x, track.y + 20);
			line(leader, track.x, track.y + 20, track.ownerBoundaryX, track.y + 20);
			text(ug, track.ownership, track.ownershipX, track.ownershipY);
			ug.closeGroup();
		}
		ug.closeGroup();
	}

	private static void drawParticipantSymbol(UGraphic ug, TrackBox track) {
		final String type = track.node.get("type", "participant").toLowerCase(java.util.Locale.ROOT);
		final double x = track.x, y = track.y;
		if ("actor".equals(type)) {
			ug.apply(new UTranslate(x - 6, y - 24)).draw(UEllipse.build(12, 12));
			line(ug, x, y - 12, x, y + 7); line(ug, x - 9, y - 5, x + 9, y - 5);
			line(ug, x, y + 7, x - 8, y + 17); line(ug, x, y + 7, x + 8, y + 17);
		} else if ("database".equals(type)) {
			ug.apply(new UTranslate(x - 9, y - 14)).draw(URectangle.build(18, 28));
			ug.apply(new UTranslate(x - 9, y - 18)).draw(UEllipse.build(18, 8));
			ug.apply(new UTranslate(x - 9, y + 10)).draw(UEllipse.build(18, 8));
		} else if ("control".equals(type) || "boundary".equals(type) || "entity".equals(type)) {
			ug.apply(new UTranslate(x - 9, y - 9)).draw(UEllipse.build(18, 18));
			if ("entity".equals(type)) line(ug, x - 10, y + 14, x + 10, y + 14);
			if ("boundary".equals(type)) { line(ug, x - 15, y, x - 9, y); line(ug, x - 15, y - 12, x - 15, y + 12); }
			if ("control".equals(type)) { line(ug, x, y - 9, x + 6, y - 15); line(ug, x, y - 9, x + 8, y - 7); }
		} else {
			ug.apply(new UTranslate(x - 7, y - 10)).draw(URectangle.build(14, 20).rounded(3));
			if ("collections".equals(type)) ug.apply(new UTranslate(x - 11, y - 14)).draw(URectangle.build(14, 20).rounded(3));
			if ("queue".equals(type)) ug.apply(new UTranslate(x - 10, y - 10)).draw(UEllipse.build(6, 20));
		}
	}

	private void drawEvent(UGraphic ug, EventBox event) {
		final String kind = event.node.kind;
		if ("event".equals(kind) == false && "create".equals(kind) == false && "destroy".equals(kind) == false) return;
		start(ug, "af-event-" + event.node.id, "autofac-event autofac-" + kind, event.node);
		final boolean objectLifecycle = ("create".equals(kind) || "destroy".equals(kind))
				&& layout(ug.getStringBounder()).scopes.containsKey(event.node.get("target")) == false;
		final UGraphic styled = paint(ug, objectLifecycle ? SName.object : SName.circle, true, event.node);
		final ScopeBox scope = layout(ug.getStringBounder()).scopes.get(event.node.get("target"));
		if ("destroy".equals(kind) && scope != null) {
			final UGraphic terminal = paint(ug, SName.destroy, false, event.node);
			line(terminal, event.x - 6, event.y + 9, event.x, event.y - 9);
			line(terminal, event.x + 1, event.y + 9, event.x + 7, event.y - 9);
		} else if ("destroy".equals(kind)) cross(styled, event.x, event.y, 8);
		else if ("create".equals(kind)) {
			if (scope == null) {
				line(styled, event.x, event.y - 9, event.x, event.y + 9);
				styled.apply(new UTranslate(event.x - 3, event.y - 3)).draw(UEllipse.build(6, 6));
			}
		} else {
			if (event.y != event.baseY) {
				final UGraphic row = paint(ug, SName.lifeLine, false).apply(UStroke.withThickness(1));
				final double left = event.labelX - 12;
				line(row.apply(new UStroke(2, 4, 1)), left, event.baseY, left, event.y);
				line(row, left, event.y, event.x + event.width / 2 + 12, event.y);
			}
			final String marker = event.node.get("marker", "circle");
			final double r = AutofacLayout.MARKER_RADIUS;
			if ("cross".equals(marker)) cross(styled, event.x, event.y, r);
			else if ("square".equals(marker)) styled.apply(new UTranslate(event.x - r, event.y - r)).draw(URectangle.build(r * 2, r * 2));
			else if ("diamond".equals(marker)) {
				final UPolygon polygon = new UPolygon();
				polygon.addPoint(event.x, event.y - r - 2); polygon.addPoint(event.x + r + 2, event.y);
				polygon.addPoint(event.x, event.y + r + 2); polygon.addPoint(event.x - r - 2, event.y);
				styled.draw(polygon);
			} else styled.apply(new UTranslate(event.x - r, event.y - r)).draw(UEllipse.build(r * 2, r * 2));
			text(ug, event.label, event.labelX, event.labelY);
		}
		ug.closeGroup();
	}

	private void drawConnector(UGraphic ug, Connector connector) {
		if (connector.arrow.isHidden()) return;
		start(ug, "af-" + connector.kind + "-" + connector.node.id, "autofac-" + connector.kind, connector.node);
		if ("message".equals(connector.kind)) {
			drawOccurrenceRow(ug, connector.from, connector.x1, connector.y1);
			drawOccurrenceRow(ug, connector.to, connector.x2, connector.y2);
		}
		final SName role = "reference".equals(connector.kind) ? SName.reference
				: "provenance".equals(connector.kind) ? SName.autofacModule : SName.arrow;
		final Style resolved = style(role, connector.node);
		final HColor color = connector.arrow.getColor() == null
				? resolved.value(PName.LineColor).asColor(skin.getIHtmlColorSet()) : connector.arrow.getColor();
		UGraphic styled = paint(ug, role, false, connector.node).apply(color);
		final UGraphic configured = connector.arrow.applyStroke(styled);
		// Native inline bold/thickness wins; otherwise use the inherited role/stereotype stroke.
		styled = configured.getParam().getStroke().getThickness() == 1
				? connector.arrow.applyStroke(styled, resolved) : configured;
		final double thickness = styled.getParam().getStroke().getThickness();
		if ("reference".equals(connector.kind)) styled = styled.apply(new UStroke(1, 4, thickness));
		if ("provenance".equals(connector.kind)) styled = styled.apply(new UStroke(6, 4, thickness));
		final UPath path = UPath.none();
		final List<Point> points = connector.points;
		path.moveTo(points.get(0).x, points.get(0).y);
		for (int i = 1; i < points.size(); i++) path.lineTo(points.get(i).x, points.get(i).y);
		styled.draw(path);
		final Point a = points.get(0), z = points.get(points.size() - 1);
		int first = 1, last = points.size() - 2;
		while (first < points.size() - 1 && points.get(first).x == a.x && points.get(first).y == a.y) first++;
		while (last > 0 && points.get(last).x == z.x && points.get(last).y == z.y) last--;
		final Point b = points.get(first), before = points.get(last);
		final UGraphic heads = paint(ug, role, false, connector.node).apply(color).apply(color.bg())
				.apply(UStroke.withThickness(thickness));
		dressing(heads, a, b, connector.arrow.getDressing1(), connector.arrow.getDecoration1());
		dressing(heads, z, before, connector.arrow.getDressing2(), connector.arrow.getDecoration2());
		if (connector.label.width > 0) {
			final HColor background = style(SName.autofacLifetime).value(PName.BackGroundColor).asColor(skin.getIHtmlColorSet());
			ug.apply(background).apply(background.bg()).apply(UStroke.withThickness(0))
					.apply(new UTranslate(connector.labelX - 3, connector.labelY - 2))
					.draw(URectangle.build(connector.label.width + 6, connector.label.height + 4).rounded(3));
			text(ug, connector.label, connector.labelX, connector.labelY);
		}
		ug.closeGroup();
	}

	private void drawOccurrenceRow(UGraphic ug, String id, double x, double y) {
		final AutofacLayout.Port port = layout(ug.getStringBounder()).ports.get(id);
		if (port == null || port.y == y || ("lifetime".equals(port.kind) == false && "participant".equals(port.kind) == false))
			return;
		final UGraphic row = paint(ug, SName.lifeLine, false).apply(UStroke.withThickness(1));
		line(row.apply(new UStroke(2, 4, 1)), x - 12, port.y, x - 12, y);
		line(row, x - 12, y, x + 12, y);
	}

	private static void dressing(UGraphic ug, Point tip, Point previous, ArrowDressing dressing, ArrowDecoration decoration) {
		final double dx = tip.x - previous.x, dy = tip.y - previous.y;
		final double length = Math.sqrt(dx * dx + dy * dy);
		if (length == 0) return;
		final double ux = dx / length, uy = dy / length;
		if (decoration == ArrowDecoration.CIRCLE)
			ug.apply(HColors.none().bg()).apply(new UTranslate(tip.x - 4, tip.y - 4)).draw(UEllipse.build(8, 8));
		if (dressing.getHead() == ArrowHead.NONE) return;
		if (dressing.getHead() == ArrowHead.CROSSX) { cross(ug, tip.x, tip.y, 5); return; }
		final double ax = tip.x - ux * 9 - uy * 4, ay = tip.y - uy * 9 + ux * 4;
		final double bx = tip.x - ux * 9 + uy * 4, by = tip.y - uy * 9 - ux * 4;
		final ArrowPart part = dressing.getPart();
		if (dressing.getHead() == ArrowHead.ASYNC || part != ArrowPart.FULL) {
			if (part != ArrowPart.BOTTOM_PART) line(ug, tip.x, tip.y, ax, ay);
			if (part != ArrowPart.TOP_PART) line(ug, tip.x, tip.y, bx, by);
		} else {
			final UPolygon triangle = new UPolygon();
			triangle.addPoint(tip.x, tip.y); triangle.addPoint(ax, ay); triangle.addPoint(bx, by); ug.draw(triangle);
		}
	}

	private static void continuation(UGraphic ug, double x, double y) {
		line(ug, x, y, x - 8, y - 4); line(ug, x, y, x - 8, y + 4);
	}

	private static void cross(UGraphic ug, double x, double y, double r) {
		line(ug, x - r, y - r, x + r, y + r); line(ug, x - r, y + r, x + r, y - r);
	}

	private void drawGroup(UGraphic ug, GroupBox group) {
		start(ug, group.uid, group.continuation ? "autofac-group autofac-group-continuation" : "autofac-group", group.node);
		final UGraphic styled = paint(ug, SName.group, group.node.get("backgroundColor") != null, group.node);
		styled.apply(new UTranslate(group.x, group.y)).draw(URectangle.build(group.width, group.height).rounded(4));
		if (group.continuation)
			line(styled.apply(new UStroke(3, 4, 1)), group.x + 4, group.continuationY, group.x + 4, group.y);
		for (int i = 0; i < group.branches.size(); i++) {
			final Branch branch = group.branches.get(i);
			final HColor fill = color(branch.node, "backgroundColor");
			if (fill != null) {
				final double end = i + 1 < group.branches.size() ? group.branches.get(i + 1).x : group.x + group.width;
				ug.apply(fill).apply(fill.bg()).apply(UStroke.withThickness(0))
						.apply(new UTranslate(branch.x, group.y + 1))
						.draw(URectangle.build(Math.max(1, end - branch.x - 1), group.height - 2));
			}
		}
		if (group.label.height > 0) {
			final double tabWidth = Math.min(group.width, group.label.width + 20);
			final double tabHeight = group.label.height + 12;
			final UPolygon tab = new UPolygon();
			tab.addPoint(group.x, group.y); tab.addPoint(group.x + tabWidth, group.y);
			tab.addPoint(group.x + tabWidth, group.y + tabHeight - 8);
			tab.addPoint(group.x + tabWidth - 8, group.y + tabHeight); tab.addPoint(group.x, group.y + tabHeight);
			UGraphic header = paint(ug, SName.group, true, group.node);
			final HColor headerColor = color(group.node, "headerColor");
			if (headerColor != null) header = header.apply(headerColor.bg());
			header.draw(tab);
			text(ug, group.label, group.x + 8, group.y + 5);
		}
		for (Branch branch : group.branches) {
			start(ug, "af-group-branch-" + branch.node.id, "autofac-group-branch", branch.node);
			line(styled.apply(new UStroke(4, 4, 1)), branch.x, group.y, branch.x, group.y + group.height);
			final HColor branchHeader = color(branch.node, "headerColor");
			if (branchHeader != null)
				ug.apply(branchHeader).apply(branchHeader.bg()).apply(UStroke.withThickness(0))
						.apply(new UTranslate(branch.x + 1, group.y + 1))
						.draw(URectangle.build(branch.label.width + 15, branch.label.height + 10));
			text(ug, branch.label, branch.x + 8, group.y + 5);
			ug.closeGroup();
		}
		ug.closeGroup();
	}

	private void drawActivation(UGraphic ug, ActivationBox activation) {
		start(ug, "af-activation-" + activation.node.id, "autofac-activation", activation.node);
		drawOccurrenceRow(ug, activation.target, activation.x1, activation.y);
		final UGraphic styled = paint(ug, SName.activationBox, true, activation.node);
		if (activation.open) {
			line(styled, activation.x1, activation.y - 5, activation.x2, activation.y - 5);
			line(styled, activation.x1, activation.y + 5, activation.x2, activation.y + 5);
			line(styled, activation.x1, activation.y - 5, activation.x1, activation.y + 5);
			continuation(styled, activation.x2, activation.y);
		} else styled.apply(new UTranslate(activation.x1, activation.y - 5))
				.draw(URectangle.build(Math.max(1, activation.x2 - activation.x1), 10));
		ug.closeGroup();
	}

	private void drawNote(UGraphic ug, NoteBox note) {
		start(ug, "af-note-" + note.node.id, "autofac-note", note.node);
		final String shape = note.node.get("shape", "note");
		final UGraphic styled = paint(ug, SName.note, true, note.node);
		if ("rnote".equals(shape)) {
			styled.apply(new UTranslate(note.x, note.y)).draw(URectangle.build(note.width, note.height));
		} else if ("hnote".equals(shape)) {
			final UPolygon hexagon = new UPolygon();
			hexagon.addPoint(note.x + 10, note.y); hexagon.addPoint(note.x + note.width - 10, note.y);
			hexagon.addPoint(note.x + note.width, note.y + note.height / 2);
			hexagon.addPoint(note.x + note.width - 10, note.y + note.height);
			hexagon.addPoint(note.x + 10, note.y + note.height); hexagon.addPoint(note.x, note.y + note.height / 2);
			styled.draw(hexagon);
		} else {
			final UPolygon polygon = new UPolygon();
			polygon.addPoint(note.x, note.y); polygon.addPoint(note.x + note.width - 10, note.y);
			polygon.addPoint(note.x + note.width, note.y + 10); polygon.addPoint(note.x + note.width, note.y + note.height);
			polygon.addPoint(note.x, note.y + note.height);
			styled.draw(polygon);
			line(styled, note.x + note.width - 10, note.y, note.x + note.width - 10, note.y + 10);
			line(styled, note.x + note.width - 10, note.y + 10, note.x + note.width, note.y + 10);
		}
		text(ug, note.label, note.labelX, note.labelY);
		ug.closeGroup();
	}
}
