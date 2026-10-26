package net.sourceforge.plantuml.autofac;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.autofac.AutofacLayout.ConstructionRow;
import net.sourceforge.plantuml.autofac.AutofacLayout.EventBox;
import net.sourceforge.plantuml.autofac.AutofacLayout.Panel;
import net.sourceforge.plantuml.autofac.AutofacLayout.ScopeBox;
import net.sourceforge.plantuml.core.DiagramType;
import net.sourceforge.plantuml.klimt.drawing.font.StringBounderFromWidthTable;
import net.sourceforge.plantuml.klimt.font.StringBounder;
import net.sourceforge.plantuml.nio.PathSystem;
import net.sourceforge.plantuml.preproc.ConfigurationStore;
import net.sourceforge.plantuml.skin.Pragma;
import net.sourceforge.plantuml.skin.SkinParam;
import net.sourceforge.plantuml.text.StringLocated;
import net.sourceforge.plantuml.utils.LineLocationImpl;

class AutofacLayoutTest {
	private final StringBounder bounder = new StringBounderFromWidthTable(FileFormat.SVG);

	@Test
	void constructionClearsActiveEventsAndChildren() throws Exception {
		final AutofacLayout layout = layout("lifetime system root { register foo: IFoo -> Foo }\n"
				+ "resolve first: IFoo from system\n"
				+ "lifetime request from system { register bar: IBar -> Bar }\n"
				+ "resolve second: IBar from request\n"
				+ "dispose request");
		final ScopeBox system = layout.scopes.get("system");
		final ScopeBox request = layout.scopes.get("request");
		assertThat(layout.lanes).isFalse();
		assertThat(layout.events.get("first").x).isGreaterThanOrEqualTo(system.x + system.constructionWidth + AutofacLayout.GAP);
		assertThat(request.x).isGreaterThanOrEqualTo(system.x + system.constructionWidth + AutofacLayout.GAP);
		assertThat(layout.events.get("second").x).isGreaterThanOrEqualTo(request.x + request.constructionWidth + AutofacLayout.GAP);
		assertThat(request.y).isGreaterThan(system.trackY);
		assertThat(request.bottom()).isLessThan(system.bottom());
		assertThat(request.right()).isLessThan(system.right());
		assertThat(system.open).isTrue();
		assertThat(request.open).isFalse();
	}

	@Test
	void siblingsHaveSeparateVerticalBands() throws Exception {
		final AutofacLayout layout = layout("lifetime system root { }\n"
				+ "parallel {\n"
				+ "lifetime one from system { }\n"
				+ "lifetime two from system { }\n"
				+ "}\n"
				+ "resolve first: IFoo from one\n"
				+ "resolve second: IFoo from two");
		final ScopeBox one = layout.scopes.get("one");
		final ScopeBox two = layout.scopes.get("two");
		assertThat(one.bottom() + AutofacLayout.GAP).isLessThanOrEqualTo(two.y);
		assertThat(one.x).isEqualTo(two.x);
		assertThat(layout.unordered).hasSize(1);
	}

	@Test
	void nestedUnorderedBandsContainTheirIncomparableDescendants() throws Exception {
		final AutofacLayout layout = layout("lifetime system root { }\n"
				+ "parallel {\nresolve one: IFoo from system\n"
				+ "parallel {\nresolve two: IBar from system\nresolve three: IBaz from system\n}\n}");
		assertThat(layout.unordered).hasSize(2);
		final AutofacLayout.UnorderedBand outer = layout.unordered.get(0);
		final AutofacLayout.UnorderedBand inner = layout.unordered.get(1);
		assertThat(outer.x).isLessThan(inner.x);
		assertThat(outer.y).isLessThan(inner.y);
		assertThat(outer.y + outer.height).isGreaterThan(inner.y + inner.height);
		assertThat(outer.source.source.getLocation()).isNotNull();
		assertThat(layout.events.get("one").x).isEqualTo(layout.events.get("three").x);
	}

	@Test
	void installedContributionsAndDecoratorObjectsRetainSeparateIdentities() throws Exception {
		final AutofacLayout layout = layout("module RequestModule {\n"
				+ "register foo: IFoo -> Foo\n"
				+ "decorate IFoo with LoggingFoo\ndecorate IFoo with MetricsFoo\n}\n"
				+ "lifetime system root { }\nresolve module1: RequestModule from system\n"
				+ "lifetime request from system { install module1 }\n"
				+ "resolve foo1: IFoo from request\n"
				+ "expect foo1 success { selected request.module1.foo; chain MetricsFoo -> LoggingFoo -> Foo }\n"
				+ "expand foo1 trace");
		final ScopeBox request = layout.scopes.get("request");
		assertThat(request.rows).hasSize(4);
		assertThat(request.rows.get(0).module).isTrue();
		assertThat(request.rows.get(1).uid).isEqualTo("af-reg-request.module1.foo");
		assertThat(request.rows.get(2).label.lines.toString()).contains("LoggingFoo");
		assertThat(request.rows.get(3).label.lines.toString()).contains("MetricsFoo");
		final AutofacLayout.Connector provenance = layout.connectors.get(0);
		assertThat(provenance.uid).isEqualTo("af-provenance-request-module1");
		assertThat(provenance.x1).isEqualTo(layout.events.get("module1").x);
		assertThat(provenance.x2).isEqualTo(request.x + request.constructionWidth);
		final Panel panel = layout.panels.get(0);
		assertThat(panel.chain).hasSize(3);
		assertThat(panel.chain.get(0).lines).containsExactly("MetricsFoo");
		assertThat(panel.chain.get(1).lines).containsExactly("LoggingFoo");
		assertThat(panel.chain.get(2).lines).containsExactly("Foo");
	}

	@Test
	void wildcardAndLiteralStarKeysHaveDistinctVisibleSpellings() throws Exception {
		final AutofacLayout layout = layout("lifetime system root {\n"
				+ "register wildcard: IService key * -> AnyService\n"
				+ "register literal: IService key \"*\" -> LiteralService\n}");
		assertThat(layout.scopes.get("system").rows.get(0).label.lines.toString()).contains("key *");
		assertThat(layout.scopes.get("system").rows.get(1).label.lines.toString()).contains("key \"*\"");
	}

	@Test
	void longLabelsStayInsideMeasuredBoundsWithoutTruncation() throws Exception {
		final String type = "Namespace.IExtremelyLongGenericService<Namespace.FirstExtremelyLongArgument,Namespace.SecondExtremelyLongArgument>";
		final AutofacLayout layout = layout("lifetime system root { register longRegistration: " + type + " -> " + type + " }\n"
				+ "resolve veryLongRequest: " + type + " from system\n"
				+ "expand veryLongRequest trace");
		final ScopeBox scope = layout.scopes.get("system");
		for (ConstructionRow row : scope.rows) {
			assertThat(scope.x + 28 + row.label.width).isLessThan(scope.x + scope.constructionWidth);
			assertThat(scope.y + row.y + row.label.height).isLessThan(scope.y + scope.constructionHeight);
		}
		for (EventBox event : layout.events.values()) {
			assertThat(event.labelX + event.label.width).isLessThan(layout.width);
			assertThat(event.labelY).isGreaterThanOrEqualTo(scope.y);
		}
		for (Panel panel : layout.panels) {
			assertThat(panel.x + panel.width).isLessThan(layout.width);
			assertThat(panel.y + panel.height).isLessThan(layout.height);
		}
		assertThat(String.join("", scope.rows.get(0).label.lines)).contains(type);
	}

	@Test
	void incomparableRequestsShareColumnAndUseSeparateSubtracks() throws Exception {
		final AutofacModel model = model("lifetime system root { }\nparallel {\n"
				+ "resolve one: IFoo from system\nresolve two: IBar from system\n}");
		final AutofacLayout layout = AutofacLayout.compute(model, skin(), bounder);
		assertThat(model.predecessors.get("one")).doesNotContain("two");
		assertThat(model.predecessors.get("two")).doesNotContain("one");
		final EventBox one = layout.events.get("one");
		final EventBox two = layout.events.get("two");
		assertThat(one.x).isEqualTo(two.x);
		assertThat(two.labelY).isGreaterThan(one.y);
		assertThat(layout.unordered).hasSize(1);
		assertThat(layout.scopes.get("system").bottom()).isGreaterThan(two.y);
	}

	@Test
	void orphanFallbackDoesNotExtendClosedParentOrCascadeDisposal() throws Exception {
		final AutofacLayout layout = layout("lifetime system root { }\n"
				+ "lifetime request from system { }\n"
				+ "dispose stopSystem: system\n"
				+ "resolve later: IFoo from request\n"
				+ "dispose stopRequest: request");
		final ScopeBox system = layout.scopes.get("system");
		final ScopeBox request = layout.scopes.get("request");
		assertThat(layout.lanes).isTrue();
		assertThat(system.right()).isEqualTo(layout.events.get("stopSystem").x);
		assertThat(request.right()).isEqualTo(layout.events.get("stopRequest").x);
		assertThat(request.right()).isGreaterThan(system.right());
		assertThat(request.y).isGreaterThan(system.bottom());
		assertThat(layout.events.get("later").x).isGreaterThan(system.right());
	}

	@Test
	void openOrphanRemainsOpenAfterParentDisposal() throws Exception {
		final AutofacLayout layout = layout("lifetime system root { }\n"
				+ "lifetime child from system { }\n"
				+ "dispose stop: system");
		assertThat(layout.lanes).isTrue();
		assertThat(layout.scopes.get("system").open).isFalse();
		assertThat(layout.scopes.get("child").open).isTrue();
		assertThat(layout.scopes.get("child").right()).isGreaterThan(layout.scopes.get("system").right());
	}

	@Test
	void recursionIsFiniteAndDoesNotExpandDependencyTemplates() throws Exception {
		final AutofacLayout layout = layout("lifetime system root {\n"
				+ "register risky: IFoo via delegate { call resolve IFoo from system }\n}\n"
				+ "resolve trigger: IFoo from system\n"
				+ "expect trigger recursion { cycle risky -> IFoo -> risky; risk stackOverflow }\n"
				+ "view diagnostics");
		assertThat(layout.events).hasSize(1);
		assertThat(layout.connectors).hasSize(1);
		assertThat(layout.panels).hasSize(1);
		assertThat(layout.width).isBetween(100.0, 8192.0);
		assertThat(layout.height).isBetween(100.0, 8192.0);
		assertThat(layout.events.get("trigger").label.lines.toString()).contains("declared");
	}

	@Test
	void ownedScopeBirthSharesAcquisitionCoordinate() throws Exception {
		final AutofacLayout layout = layout("lifetime system root { }\n"
				+ "resolve owned: Owned<IWorker> from system\n"
				+ "lifetime workerScope from system ownedBy owned { }\n"
				+ "resolve worker: IWorker from workerScope\n"
				+ "dispose owned");
		assertThat(layout.scopes.get("workerScope").x).isEqualTo(layout.events.get("owned").x);
		assertThat(layout.scopes.get("workerScope").open).isFalse();
		assertThat(layout.events.get("worker").x).isGreaterThanOrEqualTo(layout.scopes.get("workerScope").x
				+ layout.scopes.get("workerScope").constructionWidth + AutofacLayout.GAP);
	}

	@Test
	void diagnosticProjectionKeepsTimelineGeometry() throws Exception {
		final String body = "lifetime system root { register foo: IFoo -> Foo }\n"
				+ "resolve first: IFoo from system\nexpect first success { selected system.foo }\n"
				+ "resolve bad: IBar from system\nexpect bad failure missingRegistration\n";
		final AutofacLayout normal = layout(body);
		final AutofacLayout diagnostics = layout(body + "view diagnostics");
		assertThat(normal.scopes.get("system").x).isEqualTo(diagnostics.scopes.get("system").x);
		assertThat(normal.scopes.get("system").y).isEqualTo(diagnostics.scopes.get("system").y);
		assertThat(normal.events.get("first").x).isEqualTo(diagnostics.events.get("first").x);
		assertThat(normal.events.get("first").y).isEqualTo(diagnostics.events.get("first").y);
		assertThat(diagnostics.panels).hasSize(1);
		assertThat(diagnostics.connectors).hasSize(1);
	}

	@Test
	void largerInheritedFontExpandsMeasuredGeometry() throws Exception {
		final AutofacModel model = model("lifetime system root { register foo: IFoo -> Foo }\nresolve first: IFoo from system");
		final SkinParam large = skin();
		large.setParam("defaultfontsize", "28");
		final AutofacLayout normal = AutofacLayout.compute(model, skin(), bounder);
		final AutofacLayout bigger = AutofacLayout.compute(model, large, bounder);
		assertThat(bigger.scopes.get("system").constructionWidth).isGreaterThan(normal.scopes.get("system").constructionWidth);
		assertThat(bigger.scopes.get("system").height).isGreaterThan(normal.scopes.get("system").height);
	}

	@Test
	void actualSourceReaderExportsNativeSemanticGroups() throws Exception {
		final String source = "@startautofac\ncomposition \"Native rendering\" {\n"
				+ "lifetime system root { register foo: IFoo -> Foo }\n"
				+ "resolve r: IFoo from system\nexpect r success { selected system.foo; instance obj; state new }\n"
				+ "expand r trace\n}\n@endautofac\n";
		final SourceStringReader reader = new SourceStringReader(source, StandardCharsets.UTF_8);
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		reader.outputImage(output, 0, new FileFormatOption(FileFormat.SVG_DETERMINISTIC));
		final String svg = new String(output.toByteArray(), StandardCharsets.UTF_8);
		assertThat(svg).contains("af-scope-system", "af-reg-system.foo", "af-event-r", "af-instance-obj-r",
				"af-diagnostic-r", "autofac-lookup", "data-source-line");
		assertThat(svg).doesNotContain("Syntax Error", "Unsupported diagram type");
	}

	private AutofacLayout layout(String body) throws Exception {
		return AutofacLayout.compute(model(body), skin(), bounder);
	}

	private AutofacModel model(String body) throws Exception {
		final AutofacParser parser = new AutofacParser();
		LineLocationImpl location = new LineLocationImpl("layout-test", null);
		for (String line : ("composition \"Layout\" {\n" + body + "\n}").split("\n")) {
			location = location.oneLineRead();
			parser.appendLine(new StringLocated(line, location));
		}
		return AutofacValidator.validate(parser.parse());
	}

	private SkinParam skin() {
		return SkinParam.create(Collections.<String, String>emptyMap(), PathSystem.fetch(), DiagramType.AUTOFAC,
				Pragma.createEmpty(), ConfigurationStore.createEmpty());
	}
}
