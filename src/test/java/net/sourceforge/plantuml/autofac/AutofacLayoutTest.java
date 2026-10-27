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
import net.sourceforge.plantuml.autofac.AutofacLayout.Connector;
import net.sourceforge.plantuml.autofac.AutofacLayout.EventBox;
import net.sourceforge.plantuml.autofac.AutofacLayout.Label;
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
	void headersAndSeparateConstructionClearCenteredRequests() throws Exception {
		final AutofacLayout layout = layout("lifetime system root { register foo: IFoo -> Foo }\n"
				+ "resolve first: IFoo from system\n"
				+ "lifetime request from system { register bar: IBar -> Bar }\n"
				+ "resolve second: IBar from request\ndispose request");
		final ScopeBox system = layout.scopes.get("system");
		final ScopeBox request = layout.scopes.get("request");
		assertThat(layout.lanes).isFalse();
		for (ScopeBox scope : layout.scopes.values()) {
			assertThat(scope.headerY + scope.header.height).isLessThan(scope.constructionY);
			assertThat(scope.constructionTitleY + scope.constructionTitle.height).isLessThan(scope.y + scope.rows.get(0).y);
			assertThat(scope.constructionFooterY).isGreaterThan(scope.y + scope.rows.get(scope.rows.size() - 1).y);
			assertThat(scope.activeTitleX).isGreaterThan(scope.activeStartX);
		}
		for (EventBox event : layout.events.values()) {
			final ScopeBox scope = layout.scopes.get(event.node.get("scope"));
			assertThat(event.left).isGreaterThanOrEqualTo(scope.activeStartX + AutofacLayout.GAP);
			assertThat(event.actionY).isGreaterThan(scope.activeTitleY + scope.activeTitle.height);
			assertThat(event.actionY + event.action.height).isLessThan(event.y);
			assertThat(event.labelY).isGreaterThan(event.y);
			assertThat(event.labelX + event.label.width / 2).isEqualTo(event.x);
			assertThat(event.actionX + event.action.width / 2).isEqualTo(event.x);
			assertThat(event.bottom()).isLessThan(scope.bottom());
		}
		assertThat(request.x).isGreaterThanOrEqualTo(system.activeStartX + AutofacLayout.GAP);
		assertThat(request.y).isGreaterThan(system.trackY);
		assertThat(request.bottom()).isLessThan(system.bottom());
		assertThat(request.right()).isLessThan(system.right());
		assertThat(system.open).isTrue();
		assertThat(request.open).isFalse();
		assertThat(request.trackEndX).isEqualTo(layout.events.get("dispose-request-1").x);
		assertThat(request.right()).isGreaterThan(request.trackEndX);
		assertBounds(layout);
	}

	@Test
	void simpleTwoLevelScenarioHasCompactNaturalBounds() throws Exception {
		final AutofacLayout layout = layout("lifetime system root { register bar: IBar -> Bar }\n"
				+ "lifetime request from system { register foo: IFoo -> Foo }\n"
				+ "resolve first: IFoo from request\nresolve second: IBar from request\ndispose request");
		assertThat(layout.width).isLessThanOrEqualTo(1200);
		assertThat(layout.height).isLessThanOrEqualTo(520);
		assertThat(layout.scopes.get("system").y).isEqualTo(AutofacLayout.PADDING);
		assertBounds(layout);
	}

	@Test
	void siblingsHaveSeparateBandsAndAreStrictlyInsideTheirParent() throws Exception {
		final AutofacLayout layout = layout("lifetime system root { }\nparallel {\n"
				+ "lifetime one from system { }\nlifetime two from system { }\n}\n"
				+ "resolve first: IFoo from one\nresolve second: IFoo from two");
		final ScopeBox parent = layout.scopes.get("system");
		final ScopeBox one = layout.scopes.get("one");
		final ScopeBox two = layout.scopes.get("two");
		assertThat(one.bottom() + AutofacLayout.GAP).isLessThanOrEqualTo(two.y);
		assertThat(one.x).isEqualTo(two.x);
		for (ScopeBox child : new ScopeBox[] { one, two }) {
			assertThat(child.x).isGreaterThan(parent.x);
			assertThat(child.right()).isLessThan(parent.right());
			assertThat(child.bottom()).isLessThan(parent.bottom());
		}
		assertThat(layout.unordered).hasSize(1);
	}

	@Test
	void parallelSiblingBirthsThenRequestsKeepBandBordersClearOfPhaseHeaders() throws Exception {
		final AutofacModel model = model("lifetime system root { register worker: IWorker -> Worker sharing perScope }\n"
				+ "parallel {\nlifetime left from system { }\nlifetime right from system { }\n}\n"
				+ "parallel {\nresolve leftWork: IWorker from left\nresolve rightWork: IWorker from right\n}\n"
				+ "expect leftWork success { selected system.worker; instance leftWorker; state new; activation left; sharing left; owner left }\n"
				+ "expect rightWork success { selected system.worker; instance rightWorker; state new; activation right; sharing right; owner right }\n"
				+ "dispose left\ndispose right\ndispose system");
		final SkinParam large = skin();
		large.setParam("defaultfontsize", "28");
		for (SkinParam skin : new SkinParam[] { skin(), large }) {
			final AutofacLayout layout = AutofacLayout.compute(model, skin, bounder);
			assertThat(layout.unordered).hasSize(2);
			assertThat(layout.scopes.get("left").x).isEqualTo(layout.scopes.get("right").x);
			assertThat(layout.events.get("leftWork").x).isEqualTo(layout.events.get("rightWork").x);
			for (AutofacLayout.UnorderedBand band : layout.unordered) {
				for (ScopeBox scope : layout.scopes.values()) {
					assertBandHeaderClear(band, scope.header, scope.headerX, scope.headerY);
					assertBandHeaderClear(band, scope.subtitle, scope.subtitleX, scope.subtitleY);
					assertBandHeaderClear(band, scope.activeTitle, scope.activeTitleX, scope.activeTitleY);
					assertBandHeaderClear(band, scope.constructionTitle, scope.constructionTitleX, scope.constructionTitleY);
				}
			}
			final AutofacLayout.UnorderedBand requests = layout.unordered.get(1);
			assertThat(requests.y + 2 + requests.label.height + 6).isLessThanOrEqualTo(layout.events.get("leftWork").top);
			assertBounds(layout);
		}
	}

	@Test
	void orphanParallelBandsClearEveryHeaderEdgeWithoutEnclosingLaterRequests() throws Exception {
		final AutofacModel model = model("lifetime system root { }\n"
				+ "parallel {\nlifetime requestA from system { }\nlifetime requestB from system { }\n}\n"
				+ "parallel {\nresolve a: IService from requestA\nresolve b: IService from requestB\nresolve c: IClock from requestA\n}\n"
				+ "dispose system\nresolve later: IService from requestA\n"
				+ "expect later success { instance surviving; state new; owner requestA }\n"
				+ "dispose requestA\ndispose requestB\nexpand later");
		final SkinParam large = skin();
		large.setParam("defaultfontsize", "28");
		for (SkinParam skin : new SkinParam[] { skin(), large }) {
			final AutofacLayout layout = AutofacLayout.compute(model, skin, bounder);
			assertThat(layout.lanes).isTrue();
			assertThat(layout.unordered).hasSize(2);
			final AutofacLayout.UnorderedBand requests = layout.unordered.get(1);
			for (AutofacLayout.UnorderedBand band : layout.unordered)
				for (ScopeBox scope : layout.scopes.values()) {
					assertBandHeaderClear(band, scope.header, scope.headerX, scope.headerY);
					assertBandHeaderClear(band, scope.subtitle, scope.subtitleX, scope.subtitleY);
					assertBandHeaderClear(band, scope.activeTitle, scope.activeTitleX, scope.activeTitleY);
					assertBandHeaderClear(band, scope.constructionTitle, scope.constructionTitleX, scope.constructionTitleY);
				}
			for (String id : new String[] { "a", "b", "c" }) {
				final EventBox event = layout.events.get(id);
				assertThat(event.left).isGreaterThan(requests.x);
				assertThat(event.right()).isLessThan(requests.x + requests.width);
				assertThat(event.top).isGreaterThan(requests.y);
				assertThat(event.bottom()).isLessThan(requests.y + requests.height);
			}
			assertThat(layout.events.get("a").x).isEqualTo(layout.events.get("b").x);
			assertThat(layout.events.get("b").x).isEqualTo(layout.events.get("c").x);
			assertThat(layout.events.get("later").left).isGreaterThanOrEqualTo(requests.x + requests.width + 6);
			assertThat(layout.scopes.get("requestA").trackEndX).isGreaterThan(layout.scopes.get("system").trackEndX);
			assertBounds(layout);
		}
	}

	@Test
	void nestedUnorderedBandsContainTheirIncomparableDescendants() throws Exception {
		final AutofacLayout layout = layout("lifetime system root { }\nparallel {\nresolve one: IFoo from system\n"
				+ "parallel {\nresolve two: IBar from system\nresolve three: IBaz from system\n}\n}");
		assertThat(layout.unordered).hasSize(2);
		final AutofacLayout.UnorderedBand outer = layout.unordered.get(0);
		final AutofacLayout.UnorderedBand inner = layout.unordered.get(1);
		assertThat(outer.x).isLessThan(inner.x);
		assertThat(outer.y).isLessThan(inner.y);
		assertThat(outer.y + outer.height).isGreaterThan(inner.y + inner.height);
		assertThat(outer.source.source.getLocation()).isNotNull();
		assertThat(layout.events.get("one").x).isEqualTo(layout.events.get("three").x);
		assertBounds(layout);
	}

	@Test
	void nestedTitlesHaveLocalSubtrackGuttersAndBandsStayInsideTheirLifetime() throws Exception {
		final AutofacModel model = model("lifetime system root { }\nparallel {\nresolve a: IFoo from system\n"
				+ "parallel {\nresolve b: IBar from system\nresolve c: IBaz from system\n}\n}\n"
				+ "resolve later: IClock from system");
		final SkinParam large = skin();
		large.setParam("defaultfontsize", "28");
		for (SkinParam skin : new SkinParam[] { skin(), large }) {
			final AutofacLayout layout = AutofacLayout.compute(model, skin, bounder);
			final ScopeBox scope = layout.scopes.get("system");
			final EventBox a = layout.events.get("a");
			final EventBox b = layout.events.get("b");
			final AutofacLayout.UnorderedBand outer = layout.unordered.get(0);
			final AutofacLayout.UnorderedBand inner = layout.unordered.get(1);
			assertThat(inner.y + 2).isGreaterThanOrEqualTo(a.detailY + a.detail.height + 6);
			assertThat(inner.y + 2 + inner.label.height + 6).isLessThanOrEqualTo(b.top);
			assertThat(outer.y + outer.height + 6).isLessThanOrEqualTo(scope.bottom());
			assertThat(inner.y + inner.height + 6).isLessThanOrEqualTo(scope.bottom());
			assertThat(outer.x).isLessThan(inner.x);
			assertThat(outer.y).isLessThan(inner.y);
			assertThat(outer.y + outer.height).isGreaterThan(inner.y + inner.height);
			assertThat(a.x).isEqualTo(b.x);
			assertThat(b.x).isEqualTo(layout.events.get("c").x);
			assertThat(layout.events.get("later").left).isGreaterThanOrEqualTo(outer.x + outer.width + 6);
			assertBounds(layout);
		}
	}

	@Test
	void installedContributionsUseLocalProvenanceAndMeasuredDecoratorCards() throws Exception {
		final AutofacLayout layout = layout("module RequestModule {\nregister foo: IFoo -> Foo\n"
				+ "decorate IFoo with LoggingFoo\ndecorate IFoo with MetricsFoo\n}\n"
				+ "lifetime system root { }\nresolve module1: RequestModule from system\n"
				+ "lifetime request from system { install module1 }\nresolve foo1: IFoo from request\n"
				+ "expect foo1 success { selected request.module1.foo; chain MetricsFoo -> LoggingFoo -> Foo }\nexpand foo1 trace");
		final ScopeBox request = layout.scopes.get("request");
		assertThat(request.rows).hasSize(4);
		assertThat(request.rows.get(0).module).isTrue();
		assertThat(request.rows.get(1).uid).isEqualTo("af-reg-request.module1.foo");
		final Connector provenance = layout.connectors.get(0);
		assertThat(provenance.uid).isEqualTo("af-provenance-request-module1");
		assertThat(provenance.local).isTrue();
		assertThat(provenance.x1).isEqualTo(layout.events.get("module1").x);
		assertThat(provenance.y1).isGreaterThan(layout.events.get("module1").y);
		assertThat(provenance.y2).isEqualTo(request.y);
		assertThat(provenance.x2).isBetween(request.constructionX, request.activeStartX);
		assertThat(provenance.channelY).isBetween(layout.events.get("module1").y, request.y);
		assertThat(provenance.labelY).isGreaterThan(layout.scopes.get("system").trackY);
		final Panel panel = layout.panels.get(0);
		assertThat(panel.chain).hasSize(3);
		assertThat(panel.chainY).isGreaterThan(panel.labelY + panel.label.height);
		double chainBottom = panel.chainY;
		for (Label label : panel.chain)
			chainBottom += label.height + 2 * AutofacLayout.PADDING;
		assertThat(chainBottom).isLessThanOrEqualTo(panel.y + panel.height);
		assertBounds(layout);
		assertConnectorHeadersClear(layout);
	}

	@Test
	void callLabelsUseDistinctMeasuredLocalStripsBelowPhaseHeaders() throws Exception {
		final AutofacLayout layout = layout("lifetime system root {\n"
				+ "register risky: IFoo via delegate { call resolve IBar from system; call resolve IFoo from request }\n}\n"
				+ "resolve trigger: IFoo from system\n"
				+ "lifetime request from system { }\n"
				+ "call resolve again: IFoo from request during trigger\n"
				+ "expect trigger success { selected system.risky }\nexpand trigger trace");
		final ScopeBox system = layout.scopes.get("system");
		final EventBox trigger = layout.events.get("trigger");
		int calls = 0;
		for (Connector connector : layout.connectors) {
			if ("call".equals(connector.kind)) {
				calls++;
				assertThat(connector.labelX).isGreaterThanOrEqualTo(system.activeStartX);
				assertThat(connector.labelY).isGreaterThan(system.activeTitleY + system.activeTitle.height);
				assertThat(connector.labelY + connector.label.height).isLessThan(trigger.top);
				for (Connector other : layout.connectors)
					if (other != connector && "call".equals(other.kind))
						assertThat(overlap(connector.label, connector.labelX, connector.labelY,
								other.label, other.labelX, other.labelY)).isFalse();
			} else if ("lookup".equals(connector.kind)) {
				assertThat(connector.label.lines).isEmpty();
				assertThat(connector.channelY).isGreaterThan(system.activeTitleY + system.activeTitle.height);
				assertThat(connector.channelY).isLessThan(trigger.top);
				assertThat(connector.x2).isEqualTo(system.activeStartX);
			}
		}
		assertThat(calls).isEqualTo(3);
		assertConnectorHeadersClear(layout);
		assertBounds(layout);
	}

	@Test
	void largeFontProvenanceAndTraceLabelsStayOutsideEveryHeader() throws Exception {
		final AutofacModel model = model("module M { register foo: IFoo -> Foo }\n"
				+ "lifetime system root { register module: M -> M }\nresolve module1: M from system\n"
				+ "expect module1 success { selected system.module }\n"
				+ "lifetime request from system tag \"処理 🧭\" { install module1 }\n"
				+ "resolve foo: IFoo from request\nexpect foo success { selected request.module1.foo }\n"
				+ "expand foo trace\nview diagnostics");
		final SkinParam large = skin();
		large.setParam("defaultfontsize", "28");
		final AutofacLayout layout = AutofacLayout.compute(model, large, bounder);
		assertConnectorHeadersClear(layout);
		for (Connector connector : layout.connectors)
			if (connector.local) {
				final ScopeBox request = layout.scopes.get("request");
				assertThat(connector.y2).isEqualTo(request.y);
				assertThat(connector.channelY).isLessThan(request.y);
				assertThat(connector.labelY + connector.label.height).isLessThan(request.y);
			}
		assertBounds(layout);
	}

	@Test
	void wildcardAndLiteralStarRemainDistinctWithoutDisplayingRegistrationIds() throws Exception {
		final AutofacLayout layout = layout("lifetime system root {\nregister wildcard: IService key * -> AnyService\n"
				+ "register literal: IService key \"*\" -> LiteralService\n}");
		final ConstructionRow wildcard = layout.scopes.get("system").rows.get(0);
		final ConstructionRow literal = layout.scopes.get("system").rows.get(1);
		assertThat(wildcard.label.lines).isNotEqualTo(literal.label.lines);
		assertThat(wildcard.uid).isNotEqualTo(literal.uid);
		assertThat(wildcard.label.lines.toString()).doesNotContain("wildcard:");
		assertThat(literal.label.lines.toString()).doesNotContain("literal:");
	}

	@Test
	void longUnicodeLabelsAreMeasuredWithoutTruncationOrBrokenSurrogates() throws Exception {
		final String type = "Namespace.IExtremelyLongGenericService<Namespace.FirstExtremelyLongArgument,Namespace.SecondExtremelyLongArgument>";
		final AutofacLayout layout = layout("lifetime system root { register longRegistration: " + type + " -> " + type + " }\n"
				+ "resolve veryLongRequest: " + type + " key \"Unicode 🧭 処理 & < >\" from system\nexpand veryLongRequest trace");
		final ScopeBox scope = layout.scopes.get("system");
		for (ConstructionRow row : scope.rows) {
			assertThat(scope.x + row.labelX + row.label.width).isLessThan(scope.activeStartX);
			assertThat(scope.y + row.metadataY + row.metadata.height).isLessThan(scope.constructionFooterY);
		}
		assertThat(String.join("", scope.rows.get(0).label.lines)).contains(type);
		for (String line : layout.events.get("veryLongRequest").label.lines)
			if (line.length() > 0) {
				assertThat(Character.isLowSurrogate(line.charAt(0))).isFalse();
				assertThat(Character.isHighSurrogate(line.charAt(line.length() - 1))).isFalse();
			}
		assertBounds(layout);
	}

	@Test
	void incomparableDifferentWidthRequestsShareCenterWithoutOverlappingSubtracks() throws Exception {
		final AutofacModel model = model("lifetime system root { }\nparallel {\n"
				+ "resolve one: IFoo from system\nresolve two: Namespace.ILongerServiceName from system\n}");
		final AutofacLayout layout = AutofacLayout.compute(model, skin(), bounder);
		assertThat(model.predecessors.get("one")).doesNotContain("two");
		assertThat(model.predecessors.get("two")).doesNotContain("one");
		final EventBox one = layout.events.get("one");
		final EventBox two = layout.events.get("two");
		assertThat(one.x).isEqualTo(two.x);
		assertThat(two.top).isGreaterThan(one.bottom());
		assertThat(layout.unordered).hasSize(1);
		assertThat(layout.scopes.get("system").bottom()).isGreaterThan(two.bottom());
	}

	@Test
	void causallyOrderedCenteredLabelsDoNotCollide() throws Exception {
		final AutofacLayout layout = layout("lifetime system root { }\n"
				+ "resolve one: A from system\nresolve two: Namespace.ILongerServiceName from system\n"
				+ "resolve three: B from system");
		final EventBox one = layout.events.get("one");
		final EventBox two = layout.events.get("two");
		final EventBox three = layout.events.get("three");
		assertThat(two.left).isGreaterThanOrEqualTo(one.right() + AutofacLayout.GAP);
		assertThat(three.left).isGreaterThanOrEqualTo(two.right() + AutofacLayout.GAP);
	}

	@Test
	void orphanFallbackPreservesActualEndsIndependentOfClosedEnvelopePadding() throws Exception {
		final AutofacLayout layout = layout("lifetime system root { }\nlifetime request from system { }\n"
				+ "dispose stopSystem: system\nresolve later: IFoo from request\ndispose stopRequest: request");
		final ScopeBox system = layout.scopes.get("system");
		final ScopeBox request = layout.scopes.get("request");
		assertThat(layout.lanes).isTrue();
		assertThat(system.trackEndX).isEqualTo(layout.events.get("stopSystem").x);
		assertThat(request.trackEndX).isEqualTo(layout.events.get("stopRequest").x);
		assertThat(request.trackEndX).isGreaterThan(system.trackEndX);
		assertThat(request.y).isGreaterThan(system.bottom());
		assertThat(layout.events.get("later").x).isGreaterThan(system.trackEndX);
		assertBounds(layout);
	}

	@Test
	void openOrphanRemainsOpenAfterParentDisposal() throws Exception {
		final AutofacLayout layout = layout("lifetime system root { }\nlifetime child from system { }\ndispose stop: system");
		assertThat(layout.lanes).isTrue();
		assertThat(layout.scopes.get("system").open).isFalse();
		assertThat(layout.scopes.get("child").open).isTrue();
		assertThat(layout.scopes.get("child").trackEndX).isGreaterThan(layout.scopes.get("system").trackEndX);
		assertThat(layout.scopes.get("child").status.height).isGreaterThan(0);
	}

	@Test
	void finiteCycleArcHasItsOwnMeasuredStripAboveRequestLabels() throws Exception {
		final AutofacLayout layout = layout("lifetime system root {\n"
				+ "register risky: IFoo via delegate { call resolve IFoo from system }\n}\n"
				+ "resolve trigger: IFoo from system\n"
				+ "expect trigger recursion { cycle risky -> IFoo -> risky; risk stackOverflow }\nview diagnostics");
		assertThat(layout.events).hasSize(1);
		assertThat(layout.connectors).hasSize(1);
		assertThat(layout.panels).hasSize(1);
		final EventBox event = layout.events.get("trigger");
		assertThat(event.feedbackWidth).isGreaterThan(0);
		assertThat(event.feedbackY + event.feedbackHeight).isLessThan(event.actionY);
		assertThat(event.feedbackY).isGreaterThanOrEqualTo(event.top);
		assertThat(layout.width).isBetween(100.0, 8192.0);
		assertThat(layout.height).isBetween(100.0, 8192.0);
		assertBounds(layout);
	}

	@Test
	void ownedScopeSharesAcquisitionAndItsConstructionClearsTheNextRequest() throws Exception {
		final AutofacLayout layout = layout("lifetime system root { }\nresolve owned: Owned<IWorker> from system\n"
				+ "lifetime workerScope from system ownedBy owned { }\nresolve worker: IWorker from workerScope\ndispose owned");
		final ScopeBox owned = layout.scopes.get("workerScope");
		assertThat(owned.x).isEqualTo(layout.events.get("owned").x);
		assertThat(owned.open).isFalse();
		assertThat(layout.events.get("worker").left).isGreaterThanOrEqualTo(owned.activeStartX + AutofacLayout.GAP);
		assertThat(owned.trackEndX).isEqualTo(layout.events.get("dispose-workerScope-1").x);
		assertBounds(layout);
	}

	@Test
	void diagnosticProjectionDoesNotMoveLifetimeOrRequestGeometry() throws Exception {
		final String body = "lifetime system root { register foo: IFoo -> Foo }\n"
				+ "resolve first: IFoo from system\nexpect first success { selected system.foo }\n"
				+ "resolve bad: IBar from system\nexpect bad failure missingRegistration\n";
		final AutofacLayout normal = layout(body);
		final AutofacLayout diagnostics = layout(body + "view diagnostics");
		for (String id : normal.scopes.keySet()) {
			assertThat(normal.scopes.get(id).x).isEqualTo(diagnostics.scopes.get(id).x);
			assertThat(normal.scopes.get(id).y).isEqualTo(diagnostics.scopes.get(id).y);
			assertThat(normal.scopes.get(id).trackEndX).isEqualTo(diagnostics.scopes.get(id).trackEndX);
		}
		for (String id : normal.events.keySet()) {
			assertThat(normal.events.get(id).x).isEqualTo(diagnostics.events.get(id).x);
			assertThat(normal.events.get(id).y).isEqualTo(diagnostics.events.get(id).y);
		}
		assertThat(diagnostics.panels).hasSize(1);
		assertThat(diagnostics.connectors).hasSize(1);
	}

	@Test
	void compactFactCardsUseCollisionFreeShelvesInsteadOfAVerticalTower() throws Exception {
		final AutofacLayout layout = layout("lifetime system root { register foo: IFoo -> Foo }\n"
				+ "resolve one: IFoo from system\nexpect one success { selected system.foo; owner system }\nexpand one\n"
				+ "resolve two: IBar from system\nexpect two failure missingRegistration\n"
				+ "resolve three: IBaz from system\nexpect three failure missingRegistration\nview diagnostics");
		assertThat(layout.panels).hasSize(3);
		assertThat(layout.panels.get(0).y).isEqualTo(layout.panels.get(1).y);
		for (int i = 0; i < layout.panels.size(); i++) {
			final Panel a = layout.panels.get(i);
			assertThat(a.titleY + a.title.height).isLessThan(a.labelY);
			assertThat(a.labelY + a.label.height).isLessThan(a.y + a.height);
			for (int j = i + 1; j < layout.panels.size(); j++) {
				final Panel b = layout.panels.get(j);
				assertThat(a.x + a.width <= b.x || b.x + b.width <= a.x
						|| a.y + a.height <= b.y || b.y + b.height <= a.y).isTrue();
			}
		}
		assertBounds(layout);
	}

	@Test
	void largerInheritedFontExpandsEveryMeasuredRegionWithoutCollisions() throws Exception {
		final AutofacModel model = model("lifetime system root { register foo: IFoo -> Foo }\n"
				+ "resolve first: IFoo from system\nlifetime request from system tag \"処理 🧭\" { }\n"
				+ "resolve second: IBar from request\nexpect second failure missingRegistration\nview diagnostics");
		final SkinParam large = skin();
		large.setParam("defaultfontsize", "28");
		final AutofacLayout normal = AutofacLayout.compute(model, skin(), bounder);
		final AutofacLayout bigger = AutofacLayout.compute(model, large, bounder);
		assertThat(bigger.scopes.get("system").constructionWidth).isGreaterThan(normal.scopes.get("system").constructionWidth);
		assertThat(bigger.scopes.get("system").height).isGreaterThan(normal.scopes.get("system").height);
		for (ScopeBox scope : bigger.scopes.values()) {
			assertThat(scope.headerX + scope.header.width).isLessThan(scope.statusX);
			assertThat(scope.subtitleY + scope.subtitle.height).isLessThan(scope.constructionY);
			for (ConstructionRow row : scope.rows)
				assertThat(scope.y + row.metadataY + row.metadata.height).isLessThan(scope.constructionFooterY);
		}
		assertBounds(bigger);
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

	private void assertBounds(AutofacLayout layout) {
		for (ScopeBox scope : layout.scopes.values()) {
			assertThat(scope.x).isGreaterThanOrEqualTo(0);
			assertThat(scope.right()).isLessThanOrEqualTo(layout.width);
			assertThat(scope.bottom()).isLessThan(layout.height);
			assertLabel(scope.header, scope.headerX, scope.headerY, layout);
			assertLabel(scope.subtitle, scope.subtitleX, scope.subtitleY, layout);
			assertLabel(scope.status, scope.statusX, scope.statusY, layout);
			assertLabel(scope.constructionFooter, scope.constructionFooterX, scope.constructionFooterY, layout);
		}
		for (EventBox event : layout.events.values()) {
			assertLabel(event.action, event.actionX, event.actionY, layout);
			assertLabel(event.label, event.labelX, event.labelY, layout);
			assertLabel(event.detail, event.detailX, event.detailY, layout);
		}
		for (Connector connector : layout.connectors)
			assertLabel(connector.label, connector.labelX, connector.labelY, layout);
		for (Panel panel : layout.panels) {
			assertThat(panel.x).isGreaterThanOrEqualTo(0);
			assertThat(panel.x + panel.width).isLessThanOrEqualTo(layout.width);
			assertThat(panel.y + panel.height).isLessThan(layout.height);
			assertLabel(panel.title, panel.titleX, panel.titleY, layout);
			assertLabel(panel.label, panel.labelX, panel.labelY, layout);
		}
		assertLabel(layout.legend, layout.legendX, layout.legendY, layout);
		assertLabel(layout.orderLabel, layout.orderX, layout.orderY, layout);
	}

	private void assertBandHeaderClear(AutofacLayout.UnorderedBand band, Label header, double x, double y) {
		if (header.width == 0 || header.height == 0)
			return;
		final double right = band.x + band.width, bottom = band.y + band.height;
		if (right > x - 6 && band.x < x + header.width + 6) {
			assertThat(band.y >= y + header.height + 6 || band.y + band.label.height + 2 <= y - 6).isTrue();
			assertThat(bottom <= y - 6 || bottom >= y + header.height + 6).isTrue();
		}
		if (bottom > y - 6 && band.y < y + header.height + 6) {
			assertThat(band.x <= x - 6 || band.x >= x + header.width + 6).isTrue();
			assertThat(right <= x - 6 || right >= x + header.width + 6).isTrue();
		}
	}

	private void assertConnectorHeadersClear(AutofacLayout layout) {
		for (Connector connector : layout.connectors)
			for (ScopeBox scope : layout.scopes.values()) {
				assertThat(overlap(connector.label, connector.labelX, connector.labelY,
						scope.header, scope.headerX, scope.headerY)).isFalse();
				assertThat(overlap(connector.label, connector.labelX, connector.labelY,
						scope.subtitle, scope.subtitleX, scope.subtitleY)).isFalse();
				assertThat(overlap(connector.label, connector.labelX, connector.labelY,
						scope.activeTitle, scope.activeTitleX, scope.activeTitleY)).isFalse();
				assertThat(overlap(connector.label, connector.labelX, connector.labelY,
						scope.constructionTitle, scope.constructionTitleX, scope.constructionTitleY)).isFalse();
			}
	}

	private boolean overlap(Label a, double ax, double ay, Label b, double bx, double by) {
		return a.width > 0 && a.height > 0 && b.width > 0 && b.height > 0
				&& ax < bx + b.width && bx < ax + a.width && ay < by + b.height && by < ay + a.height;
	}

	private void assertLabel(Label label, double x, double y, AutofacLayout layout) {
		assertThat(x).isGreaterThanOrEqualTo(0);
		assertThat(y).isGreaterThanOrEqualTo(0);
		assertThat(x + label.width).isLessThanOrEqualTo(layout.width);
		assertThat(y + label.height).isLessThanOrEqualTo(layout.height);
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
