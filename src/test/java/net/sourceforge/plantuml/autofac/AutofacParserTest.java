package net.sourceforge.plantuml.autofac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import net.sourceforge.plantuml.SourceStringReader;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.text.StringLocated;
import net.sourceforge.plantuml.error.PSystemError;

class AutofacParserTest {
	static AutofacModel parse(String source) throws Exception {
		AutofacParser parser=new AutofacParser();
		for(String line:source.split("\n")) parser.appendLine(new StringLocated(line,null));
		return AutofacValidator.validate(parser.parse());
	}
	@Test void inlineAndNestedDelegate() throws Exception {
		AutofacModel m=parse("composition \"x\" {\nlifetime system root { register f: Func<Owned<IWorker>> via delegate { call resolve IFoo from request } sharing single }\nlifetime request from system { }\n}");
		assertEquals("Func<Owned<IWorker>>",m.registrations.get("system.f").get("service"));
		assertEquals("single",m.registrations.get("system.f").get("sharing"));
	}
	@Test void quotedCommentAndBraceCharacters() throws Exception {
		AutofacModel m=parse("composition \"x\" {\nlifetime system root { register f: IFoo key \"a} // b\\\"c\" -> Foo } // comment\n}");
		assertEquals("a} // b\"c",m.registrations.get("system.f").get("key"));
	}
	@Test void escapedUnicodeAndGenericWhitespaceAreRetained() throws Exception {
		AutofacModel model = parse("composition \"Ω\\\\path\\nline\" {\n"
				+ "lifetime system root { register f: IEnumerable < Owned < IWorker > > -> Worker }\n"
				+ "resolve r: IFoo from system\n"
				+ "expect r success { message \"a\\tb <&>\" }\n}");
		assertEquals("IEnumerable<Owned<IWorker>>", model.registrations.get("system.f").get("service"));
		assertEquals("Ω\\path\nline", model.title);
		assertEquals("a\tb <&>", model.outcomes.get("r").get("message"));
	}

	@Test void unknownFactReferenceReportsItsOwnLine() throws Exception {
		AutofacParser parser = new AutofacParser();
		parser.appendLine(new StringLocated("composition \"x\" {", null));
		parser.appendLine(new StringLocated("lifetime system root { }", null));
		parser.appendLine(new StringLocated("resolve r: IFoo from system", null));
		parser.appendLine(new StringLocated("expect r success {", null));
		StringLocated selected = new StringLocated(" selected system.missing", null);
		parser.appendLine(selected);
		parser.appendLine(new StringLocated("}", null));
		parser.appendLine(new StringLocated("}", null));
		AutofacParseException error = assertThrows(AutofacParseException.class,
				() -> AutofacValidator.validate(parser.parse()));
		assertEquals(selected, error.getSource());
		assertEquals("Unknown registration 'system.missing'", error.getMessage());
	}
	@Test void duplicateFactKeepsOffendingSource() throws Exception {
		AutofacParser p=new AutofacParser();
		p.appendLine(new StringLocated("composition \"x\" {",null));
		StringLocated bad=new StringLocated("expect r success { owner system; owner system }",null);
		p.appendLine(bad); p.appendLine(new StringLocated("}",null));
		AutofacParseException e=assertThrows(AutofacParseException.class,()->p.parse());
		assertEquals(bad,e.getSource()); assertEquals("Conflicting fact 'owner'",e.getMessage());
	}
	@Test void closedParserRejectsMoreDsl() throws Exception {
		AutofacParser p=new AutofacParser(); p.appendLine(new StringLocated("composition \"x\" { }",null));
		assertThrows(AutofacParseException.class,()->p.appendLine(new StringLocated("resolve r: IFoo from system",null)));
	}
	@Test void nativeSourceReaderReportsUnknownLifetimeAtItsLine() throws Exception {
		String source="@startautofac\ncomposition \"Unknown scope\" {\n lifetime system root { }\n resolve r: IFoo from missing\n}\n@endautofac";
		SourceStringReader reader=new SourceStringReader(source,StandardCharsets.UTF_8);
		ByteArrayOutputStream out=new ByteArrayOutputStream();
		reader.outputImage(out,0,new FileFormatOption(FileFormat.SVG_DETERMINISTIC));
		assertTrue(reader.getBlocks().get(0).getDiagram() instanceof net.sourceforge.plantuml.error.PSystemError);
		net.sourceforge.plantuml.error.PSystemError error =
				(net.sourceforge.plantuml.error.PSystemError) reader.getBlocks().get(0).getDiagram();
		assertEquals(3, error.getFirstError().getPosition());
		assertTrue(error.getFirstError().getError().contains("Unknown lifetime 'missing'"));
	}
	@Test void commonTitleStyleAndIndependentDiagrams() throws Exception {
		String source="@startautofac\ntitle Explicit title\n<style>\nautofacDiagram {\n FontSize 18\n}\n</style>\ncomposition \"Not the title\" {\n lifetime system root { }\n}\n@endautofac";
		SourceStringReader reader=new SourceStringReader(source,StandardCharsets.UTF_8);
		ByteArrayOutputStream out=new ByteArrayOutputStream(); reader.outputImage(out,0,new FileFormatOption(FileFormat.SVG_DETERMINISTIC));
		assertTrue(new String(out.toByteArray(),StandardCharsets.UTF_8).contains("Explicit title"));
		ByteArrayOutputStream larger = new ByteArrayOutputStream();
		new SourceStringReader(source.replace("FontSize 18", "FontSize 36"), StandardCharsets.UTF_8)
				.outputImage(larger, 0, new FileFormatOption(FileFormat.SVG_DETERMINISTIC));
		assertTrue(svgWidth(larger) > svgWidth(out));
		SourceStringReader next=new SourceStringReader("@startautofac\ncomposition \"Second\" {\n lifetime other root { }\n}\n@endautofac",StandardCharsets.UTF_8);
		AutofacDiagram diagram = (AutofacDiagram) next.getBlocks().get(0).getDiagram();
		assertTrue(diagram.getModel().scopes.containsKey("other"));
		assertEquals(1, diagram.getModel().scopes.size());
		assertEquals("Second", diagram.getModel().title);
	}

	@Test
	void missingCompositionHasDistinctDiagnostic() throws Exception {
		final PSystemError error = eofError("resolve r: IFoo from system");
		assertTrue(error.getFirstError().getError().contains("Missing composition block"));
	}

	@Test
	void unclosedCompositionIdentifiesItsOpeningLine() throws Exception {
		final PSystemError error = eofError("composition \"Open\" {\n lifetime system root { }");
		assertTrue(error.getFirstError().getError().contains("line 2: unclosed composition block"));
	}

	@Test
	void leadingCommentDoesNotReplaceCompositionOpeningLine() throws Exception {
		final PSystemError error = eofError("// Context for this composition\ncomposition \"Open\" {\n"
				+ " lifetime system root { }");
		assertTrue(error.getFirstError().getError().contains("line 3: unclosed composition block"));
	}

	private PSystemError eofError(String body) throws Exception {
		final SourceStringReader reader = new SourceStringReader("@startautofac\n" + body + "\n@endautofac",
				StandardCharsets.UTF_8);
		assertTrue(reader.getBlocks().get(0).getDiagram() instanceof PSystemError);
		return (PSystemError) reader.getBlocks().get(0).getDiagram();
	}

	private double svgWidth(ByteArrayOutputStream output) {
		java.util.regex.Matcher width = java.util.regex.Pattern.compile("<svg[^>]*width=\"([0-9.]+)(?:px)?\"")
				.matcher(new String(output.toByteArray(), StandardCharsets.UTF_8));
		assertTrue(width.find(), "SVG root has measured width");
		return Double.parseDouble(width.group(1));
	}
}
