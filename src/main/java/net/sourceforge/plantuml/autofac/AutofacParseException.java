package net.sourceforge.plantuml.autofac;

import net.sourceforge.plantuml.text.StringLocated;

public final class AutofacParseException extends Exception {
	private static final long serialVersionUID = 1L;
	private final StringLocated source;
	private final int offset;

	public AutofacParseException(String message, StringLocated source, int offset) {
		super(message);
		this.source = source;
		this.offset = offset;
	}

	public StringLocated getSource() {
		return source;
	}

	public int getOffset() {
		return offset;
	}
}
