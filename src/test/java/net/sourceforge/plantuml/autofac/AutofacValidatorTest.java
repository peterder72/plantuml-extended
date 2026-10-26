package net.sourceforge.plantuml.autofac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AutofacValidatorTest {
	private AutofacModel bind(String body) throws Exception { return AutofacParserTest.parse("composition \"test\" {\nlifetime system root { }\n"+body+"\n}"); }
	@Test void unspecifiedRequestDoesNotInventAnOutcome() throws Exception {
		AutofacModel m=bind("resolve r: IUnregistered from system");
		assertTrue(m.outcomes.isEmpty()); assertTrue(m.objects.isEmpty());
	}
	@Test void incomparableReuseIsNotCreationOrder() {
		assertThrows(AutofacParseException.class,()->bind("parallel {\nresolve a: IFoo from system\nresolve b: IFoo from system\n}\nexpect a success { instance foo; state new }\nexpect b success { instance foo; state reused }"));
	}
	@Test void causalReuseAndUnknownHistory() throws Exception {
		AutofacModel m=bind("resolve a: IFoo from system\nresolve b: IFoo from system\nexpect a success { instance foo; state new }\nexpect b success { instance foo; state reused }");
		assertTrue(m.predecessors.get("b").contains("a")); assertEquals(1,m.objects.size());
	}
	@Test void reuseBeforeCreationIsRejected() {
		assertThrows(AutofacParseException.class,()->bind("resolve a: IFoo from system\nresolve b: IFoo from system\nexpect a success { instance foo; state reused }\nexpect b success { instance foo; state new }"));
	}
	@Test void failedAttemptAllowsLaterTerminalDisposal() throws Exception {
		AutofacModel m=bind("dispose failed: system\nexpect failed failure disposalError\ndispose system");
		assertEquals("dispose-system-2",m.timeline.get(m.timeline.size()-1).id);
	}
	@Test void duplicateTerminalDisposalIsRejected() {
		assertThrows(AutofacParseException.class,()->bind("dispose system\ndispose system"));
	}
	@Test void parentDisposalDoesNotDisposeChild() throws Exception {
		AutofacModel m=bind("lifetime request from system { }\ndispose system\nresolve later: IFoo from request\ndispose request");
		assertTrue(m.warnings.stream().anyMatch(w->w.code.equals("undisposedChild")));
		assertFalse(m.warnings.stream().anyMatch(w->w.code.equals("disposedScope") && w.target.equals("later")));
	}
	@Test void failuresDoNotEndScope() throws Exception {
		AutofacModel m=bind("resolve r: IFoo from system\nexpect r failure missingRegistration\nresolve again: IFoo from system");
		assertTrue(m.warnings.isEmpty());
	}
	@Test void modulesHaveInstallationLocalIdentities() throws Exception {
		AutofacModel m=bind("module M { register foo: IFoo -> Foo }\nresolve m: M from system\nlifetime a from system { install m }\nlifetime b from system { install m }");
		assertTrue(m.registrations.containsKey("a.m.foo")); assertTrue(m.registrations.containsKey("b.m.foo"));
		assertEquals("M",m.scopes.get("a").children.get(0).get("resolvedModule"));
	}
	@Test void installBeforeResolutionIsRejected() {
		assertThrows(AutofacParseException.class,()->bind("module M { register foo: IFoo -> Foo }\nlifetime a from system { install m }\nresolve m: M from system"));
	}
	@Test void parentCycleAndUnknownScopesAreRejected() {
		assertThrows(AutofacParseException.class,()->bind("lifetime a from b { }\nlifetime b from a { }"));
		AutofacParseException e=assertThrows(AutofacParseException.class,()->bind("resolve r: IFoo from missing"));
		assertEquals("Unknown lifetime 'missing'",e.getMessage());
	}
	@Test void ownedChildRequiresExplicitDeclaration() throws Exception {
		AutofacModel m=bind("resolve owned: Owned<IWorker> from system\nlifetime worker from system ownedBy owned { }\ndispose owned");
		assertEquals("worker",m.timeline.get(m.timeline.size()-1).get("scope"));
		assertEquals(3, m.timeline.size(), "Owned acquisition is the child birth, not an extra event");
		assertEquals("worker", m.events.get("owned").get("ownedScope"));
		assertTrue(m.predecessors.get("dispose-worker-1").contains("owned"));
	}
	@Test void deferredEventsRemainSeparateAndInheritOrigin() throws Exception {
		AutofacModel m=bind("resolve f: Func<IWorker> from system\ninvoke one: f()\ninvoke two: f()\nresolve l: Lazy<IWorker> from system\nevaluate value: l.Value");
		assertEquals("system",m.events.get("two").get("scope")); assertEquals(6,m.timeline.size());
	}
	@Test void lateConstructionAndAmbiguousExpectationAreRejected() {
		assertThrows(AutofacParseException.class,()->bind("register late: IFoo -> Foo"));
		assertThrows(AutofacParseException.class,()->bind("resolve system: IFoo from system\nexpect system success"));
	}
	@Test void uninstalledModulesStillHaveLocalNamespaces() {
		assertThrows(AutofacParseException.class, () -> bind(
				"module M { register x: IFoo -> Foo; register x: IBar -> Bar }"));
		assertThrows(AutofacParseException.class, () -> bind(
				"depends Foo { x: IFoo; x: IBar }"));
	}

	@Test void conflictingCategoryAndReservedFactsAreRejected() {
		assertThrows(AutofacParseException.class, () -> bind(
				"resolve r: IFoo from system\nexpect r failure missingKey { category custom }"));
		assertThrows(AutofacParseException.class, () -> bind(
				"resolve r: IFoo from system\nexpect r success { owner none }"));
		assertThrows(AutofacParseException.class, () -> bind(
				"resolve r: IFoo from system\nexpect r success { activation external }"));
	}

	@Test void instanceAliasesCanBeDeferredTargetsWithAuthoredOrigin() throws Exception {
		AutofacModel m = bind("resolve r: Lazy<IWorker> from system\n"
				+ "expect r success { instance lazyObject; state new }\n"
				+ "evaluate v: lazyObject.Value");
		assertEquals("system", m.events.get("v").get("scope"));
		assertTrue(m.predecessors.get("v").contains("r"));
	}

	@Test void finiteSupportedCyclesRemainAuthoredSuccess() throws Exception {
		AutofacModel m = bind("depends A { b: B via property }\n"
				+ "depends B { a: A via property }\nresolve r: A from system\n"
				+ "expect r success { category propertyCycle; cycle A -> B -> A; empty }");
		assertEquals("success", m.outcomes.get("r").kind);
		assertEquals(3, m.outcomes.get("r").paths.get("cycle").size());
		assertEquals(2, m.timeline.size(), "Descriptions do not manufacture runtime events");
	}
}
