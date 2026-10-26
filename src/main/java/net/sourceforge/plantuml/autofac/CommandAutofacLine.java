package net.sourceforge.plantuml.autofac;

import net.sourceforge.plantuml.command.Command;
import net.sourceforge.plantuml.command.CommandControl;
import net.sourceforge.plantuml.command.CommandExecutionResult;
import net.sourceforge.plantuml.command.ParserPass;
import net.sourceforge.plantuml.utils.BlocLines;

/** Tokenize once per line; common PlantUML commands are registered before this command. */
final class CommandAutofacLine implements Command<AutofacDiagram> {
	@Override
	public CommandExecutionResult execute(AutofacDiagram diagram, BlocLines lines, ParserPass pass) {
		return diagram.acceptLine(lines.getFirst());
	}

	@Override
	public CommandControl isValid(BlocLines lines) {
		return lines.size() == 1 ? CommandControl.OK : CommandControl.NOT_OK;
	}

	@Override
	public boolean isEligibleFor(ParserPass pass) {
		return pass == ParserPass.ONE;
	}
}
