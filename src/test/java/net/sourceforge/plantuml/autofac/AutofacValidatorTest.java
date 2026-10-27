package net.sourceforge.plantuml.autofac;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class AutofacValidatorTest {
	@Test
	void constructorTextNeverBecomesARuntimeCall() {
		final AutofacModel model = AutofacParserTest.model("lifetime system {\n"
				+ "register risky: IFoo via delegate \"ctx => request.Resolve<IFoo>()\" sharing single\n"
				+ "}\ncreate system\nlifetime request from system\ncreate request");
		assertThat(model.registrations.get("system.risky").get("label"))
				.isEqualTo("ctx => request.Resolve<IFoo>()");
		assertThat(model.timeline.stream().filter(node -> node.kind.equals("message"))) .isEmpty();
		assertThat(model.relations).isEmpty();
	}

	@Test
	void failedAttemptDoesNotTerminateTheLifetime() {
		final AutofacModel model = AutofacParserTest.model("lifetime system\ncreate system\n"
				+ "event failed on system marker cross : Dispose failed: custom release hook\n"
				+ "event continuing on system : Cleanup remains in progress\ndestroy system");
		assertThat(targets(model, "destroy")).containsExactly("system");
		assertThat(model.predecessors.get("continuing")).contains("failed");
	}

	@Test
	void constructionCanFailWithoutEverCreatingAnActiveScope() {
		final AutofacModel model = AutofacParserTest.model("lifetime system\ncreate system\n"
				+ "lifetime request from system {\nregister foo: IFoo -> Foo\n}\n"
				+ "event aborted on request marker cross : Configuration callback throws\n");
		assertThat(model.scopes).containsKey("request");
		assertThat(targets(model, "create")).containsExactly("system");
		assertThat(model.events.get("aborted").get("scope")).isEqualTo("request");
	}

	@Test
	void destroyingParentDoesNotDestroyItsChildOrOwnedObjectsImplicitly() {
		final AutofacModel model = AutofacParserTest.model("lifetime system\ncreate system\n"
				+ "lifetime request from system\ncreate request\n"
				+ "object \"worker : Worker\" as worker ownedBy request\ncreate worker\n"
				+ "destroy system\nevent later on request : Still running\n"
				+ "destroy worker\ndestroy request");
		assertThat(targets(model, "destroy")).containsExactly("system", "worker", "request");
		assertThat(model.events.get("later").get("scope")).isEqualTo("request");
	}

	@Test
	void installationsKeepSeparateRegistrationIdentitiesAndTheSameSourceObject() {
		final AutofacModel model = AutofacParserTest.model("module M {\nregister foo: IFoo -> Foo\n}\n"
				+ "lifetime system\ncreate system\n"
				+ "object \"module1 : M\" as module1 ownedBy system\ncreate module1\n"
				+ "lifetime left from system {\ninstall module1 as M\n}\ncreate left\n"
				+ "lifetime right from system {\ninstall module1 as M\n}\ncreate right");
		assertThat(model.registrations).containsKeys("left.module1.foo", "right.module1.foo");
		for (String id : new String[] { "left.module1.foo", "right.module1.foo" }) {
			assertThat(model.registrations.get(id).get("module")).isEqualTo("M");
			assertThat(model.registrations.get(id).get("installation")).isEqualTo("module1");
		}
		assertThat(model.objects.keySet()).containsExactly("module1");
	}

	@Test
	void parallelBranchesDoNotAcquireAnInventedRelativeOrder() {
		final AutofacModel model = AutofacParserTest.model("lifetime system\ncreate system\n"
				+ "lifetime left from system\ncreate left\nlifetime right from system\ncreate right\n"
				+ "par Left request\nevent a on left : First branch\n"
				+ "else Right request\nevent b on right : Second branch\nend\n"
				+ "event joined on system : After both branches");
		assertThat(model.predecessors.get("a")).doesNotContain("b");
		assertThat(model.predecessors.get("b")).doesNotContain("a");
		assertThat(model.predecessors.get("joined")).contains("a", "b");
	}

	@Test
	void structuralAliasAndAncestryErrorsRemainErrors() {
		AutofacParserTest.error("lifetime a from b\nlifetime b from a");
		AutofacParserTest.error("lifetime system\nobject \"other\" as system");
		AutofacParserTest.error("lifetime system\ncreate system\ndestroy system\ndestroy system");
	}

	@Test
	void explicitlyDifferentSharingAndOwnershipAreNotCollapsed() {
		final AutofacModel model = AutofacParserTest.model("lifetime system\ncreate system\n"
				+ "lifetime request from system\ncreate request\n"
				+ "object \"worker : Worker\" as worker ownedBy system\ncreate worker\n"
				+ "worker ..> request : shared within\n");
		assertThat(model.objects.get("worker").get("owner")).isEqualTo("system");
		assertThat(model.relations.get(0).get("from")).isEqualTo("worker");
		assertThat(model.relations.get(0).get("to")).isEqualTo("request");
	}

	private static List<String> targets(AutofacModel model, String kind) {
		return model.timeline.stream().filter(node -> node.kind.equals(kind))
				.map(node -> node.get("target")).collect(Collectors.toList());
	}
}
