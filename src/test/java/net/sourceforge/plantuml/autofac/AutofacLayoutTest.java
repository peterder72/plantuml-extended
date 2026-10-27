package net.sourceforge.plantuml.autofac;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.autofac.AutofacLayout.Connector;
import net.sourceforge.plantuml.autofac.AutofacLayout.EventBox;
import net.sourceforge.plantuml.klimt.drawing.font.StringBounderFromWidthTable;

class AutofacLayoutTest {
	@Test
	void invocationPhysicallyStartsAtTheResolveMarker() {
		final AutofacLayout layout = layout("lifetime system\ncreate system\n"
				+ "lifetime request from system\ncreate request\n"
				+ "event pending on request : Resolve IFoo\nrequest -> system : Invoke factory");
		final EventBox marker = layout.events.get("pending");
		final Connector call = connector(layout, "pending", "system");
		assertThat(call.x1).isEqualTo(marker.x);
		assertThat(call.y1).isEqualTo(marker.y);
		assertThat(call.y2).isEqualTo(layout.scopes.get("system").trackY);
		assertThat(call.y2).isLessThan(call.y1);
	}

	@Test
	void returningAnObjectGoesFromItsOwnerTrackToTheRequestTrack() {
		final AutofacLayout layout = layout("lifetime system\ncreate system\n"
				+ "object \"clock1 : Clock\" as clock1 ownedBy system\ncreate clock1\n"
				+ "lifetime request from system\ncreate request\n"
				+ "event first on request : Resolve IClock\nclock1 --> request : clock1\n"
				+ "event second on request : Resolve IClock\nclock1 --> request : same clock1");
		final List<Connector> returns = layout.connectors.stream()
				.filter(edge -> edge.from.equals("clock1") && edge.to.equals("request"))
				.collect(Collectors.toList());
		assertThat(returns).hasSize(2);
		for (Connector edge : returns) {
			assertThat(edge.y1).isLessThan(edge.y2);
			assertThat(edge.y2).isEqualTo(layout.scopes.get("request").trackY);
		}
		assertThat(returns.get(0).y1).isEqualTo(returns.get(1).y1);
		assertThat(layout.tracks.keySet()).containsExactly("clock1");
	}

	@Test
	void referenceDirectionIsNotReversedToSuitLayout() {
		final AutofacLayout layout = layout("lifetime system\ncreate system\n"
				+ "object \"clock1 : Clock\" as clock1 ownedBy system\ncreate clock1\n"
				+ "lifetime request from system\ncreate request\n"
				+ "event first on request : Resolve IClock\n"
				+ "first ..> clock1 : references\nclock1 ..> first : returned object");
		final EventBox marker = layout.events.get("first");
		final Connector up = connector(layout, "first", "clock1");
		final Connector down = connector(layout, "clock1", "first");
		assertThat(up.x1).isEqualTo(marker.x);
		assertThat(up.y1).isEqualTo(marker.y);
		assertThat(up.y2).isLessThan(up.y1);
		assertThat(down.x2).isEqualTo(marker.x);
		assertThat(down.y2).isEqualTo(marker.y);
		assertThat(down.y1).isLessThan(down.y2);
	}

	@Test
	void aChildCanContinueAfterItsParentsExplicitEnd() {
		final AutofacLayout layout = layout("lifetime system\ncreate system\n"
				+ "lifetime request from system\ncreate request\n"
				+ "destroy system\nevent continuing on request : Work continues\ndestroy request");
		assertThat(layout.scopes.get("request").trackEndX)
				.isGreaterThan(layout.scopes.get("system").trackEndX);
		assertThat(layout.events.get("continuing").x)
				.isGreaterThan(layout.scopes.get("system").trackEndX);
	}

	@Test
	void anInstanceCanExistBeforeItsDisposalOwnerIsBuilt() {
		final AutofacLayout layout = layout("object \"clock : Clock\" as clock ownedBy system\ncreate clock\n"
				+ "lifetime system {\nregister binding: IClock instance clock\n}\ncreate system\n"
				+ "event on system : Resolve IClock\nclock --> system : clock");
		assertThat(layout.tracks.get("clock").x).isLessThan(layout.scopes.get("system").x);
		assertThat(layout.tracks.get("clock").x).isLessThan(layout.scopes.get("system").activeStartX);
	}

	@Test
	void parallelBranchesDoNotAcquireAVisualLeftToRightOrder() {
		final AutofacLayout layout = layout("lifetime system\ncreate system\n"
				+ "lifetime left from system\ncreate left\nlifetime right from system\ncreate right\n"
				+ "par Left branch\nevent a on left : Work A\nelse Right branch\n"
				+ "event b on right : Work B\nend\nevent joined on system : Continue");
		assertThat(layout.events.get("a").x).isEqualTo(layout.events.get("b").x);
		assertThat(layout.events.get("joined").x).isGreaterThan(layout.events.get("a").x);
	}

	@Test
	void alternativesOnOneLifetimeHaveDistinctRowsNotSequentialColumns() {
		final AutofacLayout layout = layout("lifetime system\ncreate system\n"
				+ "alt Accepted\nevent yes on system marker diamond : Accept\n"
				+ "else Cancelled\nevent no on system marker cross : Cancel\nend");
		assertThat(layout.events.get("yes").x).isEqualTo(layout.events.get("no").x);
		assertThat(layout.events.get("yes").y).isNotEqualTo(layout.events.get("no").y);
	}

	@Test
	void explicitCreationIsTheActiveTrackBoundaryEvenAfterOtherWork() {
		final AutofacLayout layout = layout("lifetime system\nparticipant Builder as builder\n"
				+ "event prebuild on builder : Prepare resources\ncreate system\n"
				+ "event ready on system : Ready");
		assertThat(layout.scopes.get("system").activeStartX).isGreaterThan(layout.events.get("prebuild").x);
		assertThat(layout.scopes.get("system").activeStartX).isEqualTo(layout.scopes.get("system").birthX);
	}

	private static Connector connector(AutofacLayout layout, String from, String to) {
		return layout.connectors.stream().filter(edge -> edge.from.equals(from) && edge.to.equals(to))
				.findFirst().orElseThrow(() -> new AssertionError("Missing edge " + from + " -> " + to));
	}

	private static AutofacLayout layout(String body) {
		final AutofacDiagram diagram = AutofacParserTest.diagram(body);
		return AutofacLayout.compute(diagram.getModel(), diagram.getSkinParam(),
				new StringBounderFromWidthTable(FileFormat.SVG));
	}
}
