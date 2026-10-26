package net.sourceforge.plantuml.autofac;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.sourceforge.plantuml.autofac.AutofacLayout.ConstructionRow;
import net.sourceforge.plantuml.autofac.AutofacLayout.Connector;
import net.sourceforge.plantuml.autofac.AutofacLayout.EventBox;
import net.sourceforge.plantuml.autofac.AutofacLayout.Label;
import net.sourceforge.plantuml.autofac.AutofacLayout.Panel;
import net.sourceforge.plantuml.autofac.AutofacLayout.ScopeBox;
import net.sourceforge.plantuml.autofac.AutofacLayout.UnorderedBand;
import net.sourceforge.plantuml.autofac.AutofacModel.Node;
import net.sourceforge.plantuml.autofac.AutofacModel.Outcome;
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
import net.sourceforge.plantuml.klimt.shape.UText;
import net.sourceforge.plantuml.style.ISkinParam;
import net.sourceforge.plantuml.style.PName;
import net.sourceforge.plantuml.style.SName;
import net.sourceforge.plantuml.style.Style;

/** Native vector rendering shared by JVM and TeaVM backends. */
public final class AutofacTextBlock implements TextBlock {
	private final AutofacModel model;
	private final ISkinParam skin;
	private StringBounder measuredBounder;
	private Object measuredStyles;
	private AutofacLayout measuredLayout;
	private final Map<SName, Style> styles = new EnumMap<SName, Style>(SName.class);

	public AutofacTextBlock(AutofacModel model, ISkinParam skinParam) {
		this.model = model;
		this.skin = skinParam;
	}

	private AutofacLayout layout(StringBounder bounder) {
		final Object styles = skin.getCurrentStyleBuilder();
		if (measuredLayout == null || measuredBounder != bounder || measuredStyles != styles) {
			measuredLayout = AutofacLayout.compute(model, skin, bounder);
			measuredBounder = bounder;
			this.styles.clear();
			measuredStyles = styles;
		}
		return measuredLayout;
	}

	@Override
	public XDimension2D calculateDimension(StringBounder bounder) {
		final AutofacLayout layout = layout(bounder);
		return new XDimension2D(layout.width, layout.height);
	}

	@Override
	public HColor getBackcolor() {
		return skin.getBackgroundColor();
	}

	@Override
	public void drawU(UGraphic ug) {
		final AutofacLayout layout = layout(ug.getStringBounder());
		drawLabel(ug, layout.orderLabel, 40, 8);
		final List<ScopeBox> scopes = layout.scopeOrder;
		for (ScopeBox scope : scopes)
			drawEnvelope(ug, scope, layout);
		for (ScopeBox scope : scopes)
			drawConstruction(ug, scope);
		for (UnorderedBand band : layout.unordered)
			drawUnordered(ug, band);
		for (Connector connector : layout.connectors)
			drawConnector(ug, connector);
		for (EventBox event : layout.events.values())
			drawEvent(ug, event, layout);
		for (Panel panel : layout.panels)
			drawPanel(ug, panel);
		start(ug, "af-legend", "autofac-legend", null);
		drawLabel(ug, layout.legend, 40, layout.legendY);
		ug.closeGroup();
	}

	private Style style(SName role) {
		Style value = styles.get(role);
		if (value == null) {
			value = AutofacLayout.style(skin, role);
			styles.put(role, value);
		}
		return value;
	}

	private UGraphic paint(UGraphic ug, SName role, boolean fill) {
		final Style style = style(role);
		final HColor line = style.value(PName.LineColor).asColor(skin.getIHtmlColorSet());
		final HColor background = fill ? style.value(PName.BackGroundColor).asColor(skin.getIHtmlColorSet()) : HColors.none();
		return ug.apply(line).apply(background.bg()).apply(style.getStroke());
	}

	private HColor lineColor(SName role) {
		return style(role).value(PName.LineColor).asColor(skin.getIHtmlColorSet());
	}

	private static void start(UGraphic ug, String uid, String cssClass, Node source) {
		final UGroup group = new UGroup(source == null || source.source == null ? null : source.source.getLocation());
		group.put(UGroupType.DATA_UID, uid);
		group.put(UGroupType.CLASS, cssClass);
		ug.startGroup(group);
	}

	private static void drawLabel(UGraphic ug, Label label, double x, double y) {
		double baseline = y + label.lineHeight - 3;
		for (String line : label.lines) {
			if (line.length() > 0)
				ug.apply(new UTranslate(x, baseline)).draw(UText.build(line, label.font));
			baseline += label.lineHeight;
		}
	}

	private static void line(UGraphic ug, double x1, double y1, double x2, double y2) {
		ug.apply(new UTranslate(x1, y1)).draw(new ULine(x2 - x1, y2 - y1));
	}

	private static void arrow(UGraphic ug, double x, double y, double dx, double dy) {
		final double length = Math.sqrt(dx * dx + dy * dy);
		if (length == 0)
			return;
		final double ux = dx / length, uy = dy / length;
		final UPolygon polygon = new UPolygon();
		polygon.addPoint(x, y);
		polygon.addPoint(x - ux * 8 - uy * 4, y - uy * 8 + ux * 4);
		polygon.addPoint(x - ux * 8 + uy * 4, y - uy * 8 - ux * 4);
		ug.draw(polygon);
	}

	private void drawEnvelope(UGraphic ug, ScopeBox scope, AutofacLayout layout) {
		start(ug, "af-scope-" + scope.node.id, "autofac-scope", scope.node);
		final SName role = scope.failed ? SName.autofacFailure : SName.autofacLifetime;
		final UGraphic styled = paint(ug, role, true);
		if (scope.open && !scope.failed) {
			// Intentionally no right-hand closing stroke: the scope has no declared end.
			final UPath outline = UPath.none();
			outline.moveTo(scope.right(), scope.y);
			outline.lineTo(scope.x + 12, scope.y);
			outline.cubicTo(scope.x, scope.y, scope.x, scope.y, scope.x, scope.y + 12);
			outline.lineTo(scope.x, scope.bottom() - 12);
			outline.cubicTo(scope.x, scope.bottom(), scope.x, scope.bottom(), scope.x + 12, scope.bottom());
			outline.lineTo(scope.right(), scope.bottom());
			styled.draw(outline);
		} else {
			styled.apply(new UTranslate(scope.x, scope.y)).draw(URectangle.build(scope.width, scope.height).rounded(12));
		}
		if (!scope.failed) {
			final UGraphic track = paint(ug, SName.autofacLifetime, false).apply(UStroke.withThickness(2));
			line(track, scope.x + scope.constructionWidth, scope.trackY, scope.right(), scope.trackY);
			if (scope.open)
				arrow(track.apply(lineColor(SName.autofacLifetime).bg()), scope.right(), scope.trackY, 1, 0);
		}
		if (layout.lanes && scope.node.get("parent") != null) {
			final ScopeBox parent = layout.scopes.get(scope.node.get("parent"));
			if (parent != null) {
				final UGraphic ancestry = paint(ug, SName.autofacLifetime, false).apply(new UStroke(3, 3, 1));
				final double x = 12 + scope.depth * 8;
				line(ancestry, x, parent.bottom() - 8, x, scope.y + 8);
				line(ancestry, x, scope.y + 8, scope.x, scope.y + 8);
			}
		}
		ug.closeGroup();
	}

	private void drawConstruction(UGraphic ug, ScopeBox scope) {
		start(ug, "af-construction-" + scope.node.id, "autofac-construction", scope.node);
		final UGraphic styled = paint(ug, scope.failed ? SName.autofacFailure : SName.autofacConstruction, true);
		styled.apply(new UTranslate(scope.x, scope.y)).draw(URectangle.build(scope.constructionWidth, scope.constructionHeight).rounded(12));
		start(ug, "af-event-birth-" + scope.node.id, "autofac-event", scope.node);
		drawLabel(ug, scope.label, scope.x + 16, scope.y + 16);
		ug.closeGroup();
		if (scope.failed)
			cross(paint(ug, SName.autofacFailure, false), scope.x + scope.constructionWidth - 12, scope.y + 12);
		for (ConstructionRow row : scope.rows) {
			start(ug, row.uid, "register".equals(row.node.kind) ? "autofac-registration" : "autofac-construction", row.node);
			if ("register".equals(row.node.kind))
				paint(ug, SName.autofacConstruction, false).apply(UStroke.withThickness(2))
						.apply(new UTranslate(scope.x + 12, scope.y + row.y + 5))
						.draw(URectangle.build(8, 8));
			drawLabel(ug, row.label, scope.x + 28, scope.y + row.y);
			ug.closeGroup();
		}
		ug.closeGroup();
	}

	private void drawUnordered(UGraphic ug, UnorderedBand band) {
		start(ug, "af-unordered-" + band.id, "autofac-unordered", band.source);
		paint(ug, SName.autofacModule, false).apply(new UStroke(5, 4, 1.5))
				.apply(new UTranslate(band.x, band.y)).draw(URectangle.build(band.width, band.height).rounded(12));
		drawLabel(ug, band.label, band.x + 8, band.y + 2);
		ug.closeGroup();
	}

	private void drawConnector(UGraphic ug, Connector connector) {
		start(ug, connector.uid, "autofac-" + connector.kind, connector.source);
		final SName role = "lookup".equals(connector.kind) ? SName.autofacLifetime : SName.autofacModule;
		final UGraphic styled = paint(ug, role, false).apply("lookup".equals(connector.kind)
				? new UStroke(1, 4, 1) : new UStroke(5, 4, 1.5));
		final UPath path = UPath.none();
		path.moveTo(connector.x1, connector.y1);
		path.lineTo(connector.x1, connector.channelY + 8);
		path.cubicTo(connector.x1, connector.channelY, connector.x1, connector.channelY, connector.x1 + 8, connector.channelY);
		path.lineTo(connector.x2 + 8, connector.channelY);
		path.cubicTo(connector.x2, connector.channelY, connector.x2, connector.channelY, connector.x2, connector.channelY + 8);
		path.lineTo(connector.x2, connector.y2);
		styled.draw(path);
		arrow(paint(ug, role, false).apply(lineColor(role).bg()), connector.x2, connector.y2, 0, 1);
		drawLabel(ug, connector.label, Math.min(connector.x1, connector.x2) + 12,
				connector.channelY - connector.label.height - 2);
		ug.closeGroup();
	}

	private void drawEvent(UGraphic ug, EventBox event, AutofacLayout layout) {
		start(ug, "af-event-" + event.node.id, "autofac-event", event.node);
		final SName role = AutofacLayout.eventRole(event.node, event.outcome);
		final UGraphic styled = paint(ug, role, false).apply(UStroke.withThickness(2));
		final ScopeBox eventScope = layout.scopes.get(event.node.get("scope"));
		if (eventScope != null && event.y > eventScope.trackY)
			line(paint(ug, SName.autofacLifetime, false), event.x - 12, event.y,
					event.x + event.width, event.y);
		final Outcome outcome = event.outcome;
		if ("dispose".equals(event.node.kind)) {
			line(styled, event.x - 6, event.y + 9, event.x, event.y - 9);
			line(styled, event.x + 1, event.y + 9, event.x + 7, event.y - 9);
			if (AutofacLayout.isFailure(outcome))
				cross(styled, event.x + 11, event.y);
		} else if (AutofacLayout.isFailure(outcome)) {
			cross(styled, event.x, event.y);
		} else {
			final boolean success = outcome != null && "success".equals(outcome.kind);
			final UGraphic circle = success ? styled.apply(lineColor(role).bg()) : styled
					.apply(style(SName.autofacLifetime).value(PName.BackGroundColor).asColor(skin.getIHtmlColorSet()).bg());
			final double radius = AutofacLayout.MARKER_RADIUS;
			circle.apply(new UTranslate(event.x - radius, event.y - radius)).draw(UEllipse.build(radius * 2, radius * 2));
			if (outcome != null && "reused".equals(outcome.get("state")))
				feedback(styled, event.x - 10, event.y + 12, 34, 20, false);
		}
		if (outcome != null && (outcome.paths.containsKey("cycle") || "recursion".equals(outcome.kind)))
			feedback(styled.apply(UStroke.withThickness("recursion".equals(outcome.kind) ? 3 : 2)),
					event.x - 8, event.y + 12, Math.max(60, event.label.width), 24, true);
		if (outcome != null && outcome.get("instance") != null) {
			start(ug, "af-instance-" + outcome.get("instance") + "-" + event.node.id, "autofac-instance", event.node);
			drawLabel(ug, event.label, event.labelX, event.labelY);
			ug.closeGroup();
		} else {
			drawLabel(ug, event.label, event.labelX, event.labelY);
		}
		final ScopeBox scope = layout.scopes.get(event.node.get("scope"));
		if (scope != null && (event.x < scope.x || (!scope.open && event.x > scope.right()))) {
			final UGraphic warning = paint(ug, SName.autofacWarning, false).apply(new UStroke(3, 3, 1.5));
			line(warning, event.x, event.y + 12, event.x < scope.x ? scope.x : scope.right(), event.y + 12);
			arrow(warning.apply(lineColor(SName.autofacWarning).bg()), event.x < scope.x ? scope.x : scope.right(), event.y + 12,
					event.x < scope.x ? 1 : -1, 0);
		}
		ug.closeGroup();
	}

	private static void cross(UGraphic ug, double x, double y) {
		line(ug, x - 6, y - 6, x + 6, y + 6);
		line(ug, x - 6, y + 6, x + 6, y - 6);
	}

	private static void feedback(UGraphic ug, double x, double y, double width, double height, boolean closedCycle) {
		final UPath loop = UPath.none();
		loop.moveTo(x + width, y);
		loop.cubicTo(x + width, y + height, x, y + height, x, y);
		if (closedCycle)
			loop.cubicTo(x, y - 5, x + 6, y - 5, x + 12, y);
		ug.draw(loop);
		line(ug, x, y, x - 3, y + 7);
		line(ug, x, y, x + 6, y + 4);
	}

	private void drawPanel(UGraphic ug, Panel panel) {
		start(ug, "af-diagnostic-" + panel.target, "autofac-diagnostic", panel.node);
		if (panel.node != null && "instance".equals(panel.node.kind))
			start(ug, "af-instance-" + panel.node.id, "autofac-instance", panel.node);
		paint(ug, panel.role, true).apply(new UTranslate(panel.x, panel.y))
				.draw(URectangle.build(panel.width, panel.height).rounded(12));
		drawLabel(ug, panel.label, panel.x + 16, panel.y + 16);
		if (!panel.chain.isEmpty()) {
			double top = panel.y + panel.label.height + 32;
			double remainingHeight = 0;
			for (Label label : panel.chain)
				remainingHeight += label.height + 32;
			for (int i = 0; i < panel.chain.size(); i++) {
				final Label label = panel.chain.get(i);
				final double x = panel.x + 16 * (i + 1);
				start(ug, "af-instance-" + panel.target + "-decorator-" + i, "autofac-instance", panel.node);
				paint(ug, SName.autofacResolve, false).apply(new UTranslate(x, top))
						.draw(URectangle.build(panel.width - 32 * (i + 1), remainingHeight - 8).rounded(6));
				drawLabel(ug, label, x + 16, top + 8);
				ug.closeGroup();
				top += label.height + 32;
				remainingHeight -= label.height + 32;
			}
		}
		if (panel.node != null && "instance".equals(panel.node.kind))
			ug.closeGroup();
		ug.closeGroup();
	}
}
