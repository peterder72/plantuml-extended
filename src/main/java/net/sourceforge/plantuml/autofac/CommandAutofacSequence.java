package net.sourceforge.plantuml.autofac;

import net.sourceforge.plantuml.command.Command;
import net.sourceforge.plantuml.command.CommandControl;
import net.sourceforge.plantuml.command.CommandExecutionResult;
import net.sourceforge.plantuml.command.ParserPass;
import net.sourceforge.plantuml.klimt.color.NoSuchColorException;
import net.sourceforge.plantuml.sequencediagram.SequenceDiagram;
import net.sourceforge.plantuml.utils.BlocLines;

/** Delegates syntax and execution to the real sequence command, then binds its occurrences. */
final class CommandAutofacSequence implements Command<AutofacDiagram> {
	private final Command<SequenceDiagram> nativeCommand;

	CommandAutofacSequence(Command<SequenceDiagram> nativeCommand) {
		this.nativeCommand = nativeCommand;
	}

	@Override
	public CommandExecutionResult execute(AutofacDiagram diagram, BlocLines lines, ParserPass pass)
			throws NoSuchColorException {
		return diagram.executeNative(nativeCommand, lines, pass);
	}

	@Override
	public CommandControl isValid(BlocLines lines) {
		return nativeCommand.isValid(lines);
	}

	@Override
	public boolean isEligibleFor(ParserPass pass) {
		return nativeCommand.isEligibleFor(pass);
	}

	@Override
	public boolean isCommandForbidden(BlocLines lines) {
		return nativeCommand.isCommandForbidden(lines);
	}

	@Override
	public String explain(BlocLines lines) {
		return nativeCommand.explain(lines);
	}
}
