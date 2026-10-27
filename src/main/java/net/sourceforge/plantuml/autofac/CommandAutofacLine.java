package net.sourceforge.plantuml.autofac;

import net.sourceforge.plantuml.command.Command;
import net.sourceforge.plantuml.command.CommandControl;
import net.sourceforge.plantuml.command.CommandExecutionResult;
import net.sourceforge.plantuml.command.ParserPass;
import net.sourceforge.plantuml.utils.BlocLines;

/** Structural commands precede native arrows; the final fallback reports unknown DSL statements. */
final class CommandAutofacLine implements Command<AutofacDiagram> {
	private static final String[] KEYWORDS = { "lifetime", "module", "object", "register", "decorate",
			"install", "event", "create", "destroy" };
	private final boolean fallback;

	CommandAutofacLine(boolean fallback) {
		this.fallback = fallback;
	}

	@Override
	public CommandExecutionResult execute(AutofacDiagram diagram, BlocLines lines, ParserPass pass) {
		return diagram.acceptLine(lines.getFirst());
	}

	@Override
	public CommandControl isValid(BlocLines lines) {
		if (lines.size() != 1) return CommandControl.NOT_OK;
		if (fallback) return CommandControl.OK;
		final String text = lines.getFirst().getString().trim();
		if (text.isEmpty() || text.startsWith("}") || text.startsWith("'")) return CommandControl.OK;
		for (String keyword : KEYWORDS)
			if (text.equals(keyword) || text.startsWith(keyword + " ") || text.startsWith(keyword + "\t"))
				return CommandControl.OK;
		// Static references are deliberately not temporal sequence arrows.
		final int colon = text.indexOf(':');
		final String endpoints = colon < 0 ? text : text.substring(0, colon);
		return endpoints.contains("..>") || endpoints.contains("<..") ? CommandControl.OK : CommandControl.NOT_OK;
	}

	@Override
	public boolean isEligibleFor(ParserPass pass) {
		return pass == ParserPass.ONE;
	}
}
