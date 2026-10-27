package net.sourceforge.plantuml.autofac;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.autofac.AutofacModel.Node;
import net.sourceforge.plantuml.error.PSystemError;

class AutofacParserTest {
	static AutofacDiagram diagram(String body) {
		final SourceStringReader reader = new SourceStringReader("@startautofac\n" + body + "\n@endautofac",
				StandardCharsets.UTF_8);
		final Object result = reader.getBlocks().get(0).getDiagram();
		assertThat(result).as("Autofac source parses without an error diagram").isInstanceOf(AutofacDiagram.class);
		return (AutofacDiagram) result;
	}

	static AutofacModel model(String body) {
		return diagram(body).getModel();
	}

	static PSystemError error(String body) {
		final SourceStringReader reader = new SourceStringReader("@startautofac\n" + body + "\n@endautofac",
				StandardCharsets.UTF_8);
		final Object result = reader.getBlocks().get(0).getDiagram();
		assertThat(result).isInstanceOf(PSystemError.class);
		return (PSystemError) result;
	}

	@Test
	void arbitraryOperationsHaveNoCaseVocabularyOrTwoRequestLimit() {
		final StringBuilder source = new StringBuilder("lifetime system\ncreate system\n");
		for (int i = 0; i < 17; i++)
			source.append("event step").append(i).append(" on system : Custom operation ").append(i).append('\n');
		final AutofacModel model = model(source.toString());
		for (int i = 0; i < 17; i++) {
			final Node event = model.events.get("step" + i);
			assertThat(event.get("label")).isEqualTo("Custom operation " + i);
			assertThat(event.get("scope")).isEqualTo("system");
			if (i > 0)
				assertThat(model.predecessors.get(event.id)).contains("step" + (i - 1));
		}
	}

	@Test
	void followingCallStartsAtItsResolveOccurrence() {
		final AutofacModel model = model("lifetime system\ncreate system\n"
				+ "lifetime request from system\ncreate request\n"
				+ "event pending on request : Resolve IFoo\nrequest -> system : Invoke factory");
		final Node call = messages(model).get(0);
		assertThat(call.get("from")).isEqualTo("pending");
		assertThat(call.get("to")).isEqualTo("system");
	}

	@Test
	void explicitOccurrenceAliasStillAnchorsAfterOtherWork() {
		final AutofacModel model = model("lifetime system\ncreate system\n"
				+ "lifetime request from system\ncreate request\n"
				+ "event pending on request : Resolve IFoo\n"
				+ "event on system : Other work\npending -> system : Continue this operation");
		assertThat(messages(model).get(0).get("from")).isEqualTo("pending");
	}

	@Test
	void reverseAndReturnArrowsPreserveAuthoredDirection() {
		final AutofacModel model = model("lifetime system\ncreate system\n"
				+ "object \"clock1 : Clock\" as clock1 ownedBy system\ncreate clock1\n"
				+ "lifetime request from system\ncreate request\n"
				+ "request <-- clock1 : returned clock\nsystem <- request : Explicit call");
		final List<Node> messages = messages(model);
		assertThat(messages.get(0).get("from")).isEqualTo("clock1");
		assertThat(messages.get(0).get("to")).isEqualTo("request");
		assertThat(messages.get(1).get("from")).isEqualTo("request");
		assertThat(messages.get(1).get("to")).isEqualTo("system");
	}

	@Test
	void policyAndOperationWordsNeverManufactureObjectsOrOutcomes() {
		final AutofacModel model = model("lifetime system {\n"
				+ "register clock: IClock -> Clock sharing single\n}\ncreate system\n"
				+ "event on system : Resolve IClock - reused success recursion timeout\n");
		assertThat(model.objects).isEmpty();
		assertThat(model.relations).isEmpty();
		assertThat(messages(model)).isEmpty();
	}

	@Test
	void unknownReferenceIsReportedAtTheReferenceNotTheEndDirective() {
		for (String suffix : new String[] { "", "\n\n", "\n' Trailing comment\n" }) {
			final PSystemError error = error("lifetime system\ncreate system\n"
					+ "event first on system : Resolve IFoo\nfirst ..> system.missing : selected" + suffix);
			assertThat(error.getFirstError().getPosition()).isEqualTo(4);
		}
	}

	private static List<Node> messages(AutofacModel model) {
		return model.timeline.stream().filter(node -> node.kind.equals("message")).collect(Collectors.toList());
	}
}
