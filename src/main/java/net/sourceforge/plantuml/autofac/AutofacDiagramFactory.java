package net.sourceforge.plantuml.autofac;

import java.util.List;

import net.sourceforge.plantuml.Previous;
import net.sourceforge.plantuml.command.Command;
import net.sourceforge.plantuml.command.CommonCommands;
import net.sourceforge.plantuml.command.PSystemCommandFactory;
import net.sourceforge.plantuml.core.DiagramType;
import net.sourceforge.plantuml.core.UmlSource;
import net.sourceforge.plantuml.nio.PathSystem;
import net.sourceforge.plantuml.preproc.PreprocessingArtifact;

public final class AutofacDiagramFactory extends PSystemCommandFactory {
	public AutofacDiagramFactory() {
		super(DiagramType.AUTOFAC);
	}

	@Override
	protected void initCommandsList(List<Command> commands) {
		CommonCommands.addCommonCommands1(commands);
		commands.add(new CommandAutofacLine());
	}

	@Override
	public AutofacDiagram createEmptyDiagram(PathSystem pathSystem, UmlSource source, Previous previous,
			PreprocessingArtifact preprocessing) {
		return new AutofacDiagram(source, preprocessing);
	}
}
