package net.sourceforge.plantuml.autofac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sourceforge.plantuml.ErrorUml;
import net.sourceforge.plantuml.ErrorUmlType;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.TitledDiagram;
import net.sourceforge.plantuml.abel.DisplayPositioned;
import net.sourceforge.plantuml.command.CommandExecutionResult;
import net.sourceforge.plantuml.core.DiagramDescription;
import net.sourceforge.plantuml.core.DiagramType;
import net.sourceforge.plantuml.core.UmlSource;
import net.sourceforge.plantuml.error.PSystemErrorUtils;
import net.sourceforge.plantuml.klimt.creole.Display;
import net.sourceforge.plantuml.klimt.geom.HorizontalAlignment;
import net.sourceforge.plantuml.klimt.geom.VerticalAlignment;
import net.sourceforge.plantuml.klimt.shape.TextBlock;
import net.sourceforge.plantuml.preproc.PreprocessingArtifact;
import net.sourceforge.plantuml.text.StringLocated;
import net.sourceforge.plantuml.version.IteratorCounter2;
import net.sourceforge.plantuml.warning.Warning;

/** Native, author-declared Autofac lifetime composition. No container execution. */
public final class AutofacDiagram extends TitledDiagram {
	private final AutofacParser parser = new AutofacParser();
	private AutofacModel model;
	private StringLocated opener;
	private TextBlock textBlock;

	public AutofacDiagram(UmlSource source, PreprocessingArtifact preprocessing) {
		super(source, DiagramType.AUTOFAC, null, preprocessing);
	}

	public CommandExecutionResult acceptLine(StringLocated line) {
		try {
			if (opener == null) {
				final String text = line.getString();
				int first = 0;
				while (first < text.length() && Character.isWhitespace(text.charAt(first)))
					first++;
				final int keywordEnd = first + "composition".length();
				if (text.startsWith("composition", first)
						&& (text.length() == keywordEnd || Character.isWhitespace(text.charAt(keywordEnd))
								|| text.charAt(keywordEnd) == '"'))
					opener = line;
			}
			parser.appendLine(line);
			if (model == null && parser.isCompositionClosed()) {
				model = AutofacValidator.validate(parser.parse());
				for (AutofacModel.StructuralWarning warning : model.warnings)
					addWarning(new Warning(warning.code + ": " + warning.message));
			}
			return CommandExecutionResult.ok();
		} catch (AutofacParseException error) {
			final StringLocated offending = error.getSource();
			final List<StringLocated> trace = new ArrayList<>();
			final IteratorCounter2 it = getSource().iterator2();
			while (it.hasNext()) {
				final StringLocated original = it.next();
				trace.add(original);
				if (original == offending || original.getLocation().equals(offending.getLocation()))
					break;
			}
			if (trace.isEmpty() || trace.get(trace.size() - 1).getLocation().equals(offending.getLocation()) == false)
				trace.add(offending);
			final ErrorUml syntax = new ErrorUml(ErrorUmlType.SYNTAX_ERROR, error.getMessage(), 0, offending,
					DiagramType.AUTOFAC);
			return CommandExecutionResult.newDiagram(PSystemErrorUtils.buildV2(getSource(), syntax,
					Collections.<String>emptyList(), trace, getPreprocessingArtifact()));
		}
	}

	@Override
	public String checkFinalError() {
		if (model == null) {
			if (opener == null)
				return "Missing composition block";
			return "line " + (opener.getLocation().getPosition() + 1)
					+ ": unclosed composition block (missing '}')";
		}
		if (getTitle().isNull())
			setTitle(DisplayPositioned.single(Display.getWithNewlines(getPragma(), model.title),
					HorizontalAlignment.CENTER, VerticalAlignment.TOP));
		return super.checkFinalError();
	}

	public AutofacModel getModel() {
		return model;
	}

	@Override
	public DiagramDescription getDescription() {
		return new DiagramDescription("(Autofac lifetime)");
	}

	@Override
	public TextBlock getTextBlock(int num, FileFormatOption fileFormatOption) {
		if (textBlock == null)
			textBlock = new AutofacTextBlock(model, getSkinParam());
		return textBlock;
	}
}
