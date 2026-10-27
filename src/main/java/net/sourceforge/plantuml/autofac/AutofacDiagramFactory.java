package net.sourceforge.plantuml.autofac;

import java.util.List;

import net.sourceforge.plantuml.Previous;
import net.sourceforge.plantuml.command.Command;
import net.sourceforge.plantuml.command.CommonCommands;
import net.sourceforge.plantuml.command.PSystemCommandFactory;
import net.sourceforge.plantuml.command.note.sequence.FactorySequenceNoteAcrossCommand;
import net.sourceforge.plantuml.command.note.sequence.FactorySequenceNoteCommand;
import net.sourceforge.plantuml.command.note.sequence.FactorySequenceNoteOnArrowCommand;
import net.sourceforge.plantuml.command.note.sequence.FactorySequenceNoteOverSeveralCommand;
import net.sourceforge.plantuml.core.DiagramType;
import net.sourceforge.plantuml.core.UmlSource;
import net.sourceforge.plantuml.nio.PathSystem;
import net.sourceforge.plantuml.preproc.PreprocessingArtifact;
import net.sourceforge.plantuml.sequencediagram.command.CommandActivate;
import net.sourceforge.plantuml.sequencediagram.command.CommandActivate2;
import net.sourceforge.plantuml.sequencediagram.command.CommandArrow;
import net.sourceforge.plantuml.sequencediagram.command.CommandDeactivateShort;
import net.sourceforge.plantuml.sequencediagram.command.CommandExoArrowLeft;
import net.sourceforge.plantuml.sequencediagram.command.CommandExoArrowRight;
import net.sourceforge.plantuml.sequencediagram.command.CommandGrouping;
import net.sourceforge.plantuml.sequencediagram.command.CommandParticipantA;
import net.sourceforge.plantuml.sequencediagram.command.CommandParticipantA2;
import net.sourceforge.plantuml.sequencediagram.command.CommandParticipantA3;
import net.sourceforge.plantuml.sequencediagram.command.CommandParticipantA4;
import net.sourceforge.plantuml.sequencediagram.command.CommandParticipantMultilines;
import net.sourceforge.plantuml.sequencediagram.command.CommandReturn;

public final class AutofacDiagramFactory extends PSystemCommandFactory {
	public AutofacDiagramFactory() {
		super(DiagramType.AUTOFAC);
	}

	@Override
	protected void initCommandsList(List<Command> commands) {
		CommonCommands.addCommonCommands1(commands);
		commands.add(new CommandAutofacLine(false));
		commands.add(new CommandAutofacSequence(new CommandParticipantA()));
		commands.add(new CommandAutofacSequence(new CommandParticipantA2()));
		commands.add(new CommandAutofacSequence(new CommandParticipantA3()));
		commands.add(new CommandAutofacSequence(new CommandParticipantA4()));
		commands.add(new CommandAutofacSequence(new CommandParticipantMultilines()));
		commands.add(new CommandAutofacSequence(new CommandArrow()));
		commands.add(new CommandAutofacSequence(new CommandExoArrowLeft()));
		commands.add(new CommandAutofacSequence(new CommandExoArrowRight()));
		commands.add(new CommandAutofacSequence(new CommandGrouping()));
		commands.add(new CommandAutofacSequence(new CommandActivate()));
		commands.add(new CommandAutofacSequence(new CommandDeactivateShort()));
		commands.add(new CommandAutofacSequence(new CommandActivate2()));
		commands.add(new CommandAutofacSequence(new CommandReturn()));
		final FactorySequenceNoteCommand note = new FactorySequenceNoteCommand();
		final FactorySequenceNoteOverSeveralCommand over = new FactorySequenceNoteOverSeveralCommand();
		final FactorySequenceNoteAcrossCommand across = new FactorySequenceNoteAcrossCommand();
		final FactorySequenceNoteOnArrowCommand arrow = new FactorySequenceNoteOnArrowCommand();
		commands.add(new CommandAutofacSequence(note.createSingleLine()));
		commands.add(new CommandAutofacSequence(over.createSingleLine()));
		commands.add(new CommandAutofacSequence(across.createSingleLine()));
		commands.add(new CommandAutofacSequence(arrow.createSingleLine()));
		commands.add(new CommandAutofacSequence(note.createMultiLine(false)));
		commands.add(new CommandAutofacSequence(over.createMultiLine(false)));
		commands.add(new CommandAutofacSequence(across.createMultiLine(false)));
		commands.add(new CommandAutofacSequence(arrow.createMultiLine(false)));
		commands.add(new CommandAutofacLine(true));
	}

	@Override
	public AutofacDiagram createEmptyDiagram(PathSystem pathSystem, UmlSource source, Previous previous,
			PreprocessingArtifact preprocessing) {
		return new AutofacDiagram(source, preprocessing);
	}
}
