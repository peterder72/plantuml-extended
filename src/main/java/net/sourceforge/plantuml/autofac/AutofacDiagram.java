package net.sourceforge.plantuml.autofac;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sourceforge.plantuml.ErrorUml;
import net.sourceforge.plantuml.ErrorUmlType;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.autofac.AutofacModel.Node;
import net.sourceforge.plantuml.command.Command;
import net.sourceforge.plantuml.command.CommandExecutionResult;
import net.sourceforge.plantuml.command.ParserPass;
import net.sourceforge.plantuml.core.DiagramDescription;
import net.sourceforge.plantuml.core.DiagramType;
import net.sourceforge.plantuml.core.UmlSource;
import net.sourceforge.plantuml.error.PSystemErrorUtils;
import net.sourceforge.plantuml.klimt.Fashion;
import net.sourceforge.plantuml.klimt.color.HColor;
import net.sourceforge.plantuml.klimt.color.ColorType;
import net.sourceforge.plantuml.klimt.color.Colors;
import net.sourceforge.plantuml.klimt.color.NoSuchColorException;
import net.sourceforge.plantuml.klimt.creole.Display;
import net.sourceforge.plantuml.klimt.shape.TextBlock;
import net.sourceforge.plantuml.preproc.PreprocessingArtifact;
import net.sourceforge.plantuml.sequencediagram.AbstractMessage;
import net.sourceforge.plantuml.sequencediagram.Event;
import net.sourceforge.plantuml.sequencediagram.EventWithNote;
import net.sourceforge.plantuml.sequencediagram.Grouping;
import net.sourceforge.plantuml.sequencediagram.GroupingType;
import net.sourceforge.plantuml.sequencediagram.LifeEvent;
import net.sourceforge.plantuml.sequencediagram.LifeEventType;
import net.sourceforge.plantuml.sequencediagram.Message;
import net.sourceforge.plantuml.sequencediagram.MessageExo;
import net.sourceforge.plantuml.sequencediagram.MessageExoType;
import net.sourceforge.plantuml.sequencediagram.Note;
import net.sourceforge.plantuml.sequencediagram.Notes;
import net.sourceforge.plantuml.sequencediagram.Participant;
import net.sourceforge.plantuml.sequencediagram.ParticipantType;
import net.sourceforge.plantuml.sequencediagram.SequenceDiagram;
import net.sourceforge.plantuml.sequencediagram.command.CommandReturn;
import net.sourceforge.plantuml.skin.ArrowConfiguration;
import net.sourceforge.plantuml.text.StringLocated;
import net.sourceforge.plantuml.utils.BlocLines;
import net.sourceforge.plantuml.utils.LineLocation;
import net.sourceforge.plantuml.utils.StartUtils;
import net.sourceforge.plantuml.version.IteratorCounter2;
import net.sourceforge.plantuml.warning.Warning;

/** Native sequence command receiver with explicit Autofac structure and horizontal rendering. */
public final class AutofacDiagram extends SequenceDiagram {
	private final AutofacParser parser = new AutofacParser();
	private final Set<String> nativeEntities = new LinkedHashSet<String>();
	private final Set<Note> capturedNotes = Collections.newSetFromMap(new IdentityHashMap<Note, Boolean>());
	private final Map<AbstractMessage, String> occurrences = new IdentityHashMap<AbstractMessage, String>();
	private final Map<String, Participant> nativeParticipants = new LinkedHashMap<String, Participant>();
	private final List<Participant> addedParticipants = new ArrayList<Participant>();
	private AutofacModel model;
	private TextBlock textBlock;
	private int nativeEventCursor;
	private int statementCursor;
	private final StringLocated lastBodyLine;
	private boolean finalized;
	private HColor authoredGroupBody;
	private HColor authoredGroupHeader;

	public AutofacDiagram(UmlSource source, PreprocessingArtifact preprocessing) {
		super(source, DiagramType.AUTOFAC, null, preprocessing);
		StringLocated last = null;
		final IteratorCounter2 iterator = source.iterator2();
		if (iterator.hasNext()) iterator.next();
		while (iterator.hasNext()) {
			final StringLocated line = iterator.next();
			if (StartUtils.isEndDirective(line.getString())) break;
			final String text = line.getString().trim();
			if (!text.isEmpty() && !text.startsWith("'") && !text.startsWith("//")) last = line;
		}
		lastBodyLine = last;
	}

	@Override
	public CommandExecutionResult executeCommand(Command command, BlocLines lines, ParserPass pass) {
		final CommandExecutionResult result = super.executeCommand(command, lines, pass);
		if (!result.isOk() || result.getNewDiagram() != null || lastBodyLine == null) return result;
		boolean finishesSource = false;
		for (int i = 0; i < lines.size(); i++)
			if (lines.getAt(i) == lastBodyLine) finishesSource = true;
		if (!finishesSource) return result;
		try {
			finishModel();
			return result;
		} catch (AutofacParseException error) {
			return syntaxError(error);
		}
	}

	public CommandExecutionResult acceptLine(StringLocated line) {
		try {
			final int before = parser.timelineSize();
			parser.appendLine(line);
			synchronizeDeclarations();
			for (int i = before; i < parser.timelineSize(); i++) {
				final Node node = parser.timelineAt(i);
				if (node.kind.equals("event")) {
					final Participant participant = getOrCreateParticipant(line.getLocation(), node.get("scope"));
					final Message marker = new Message(getSkinParam().getCurrentStyleBuilder(), participant, participant,
							Display.create(""), ArrowConfiguration.withDirectionSelf(false), null, line.getLocation());
					addMessage(marker);
					occurrences.put(marker, node.id);
					// Alias participants are command adapters, never additional visible lifetimes.
					if (!node.id.startsWith("$af-")) {
						getOrCreateParticipant(line.getLocation(), node.id);
						nativeEntities.add(node.id);
					}
				} else if (node.kind.equals("create") || node.kind.equals("destroy")) {
					final Participant participant = getOrCreateParticipant(line.getLocation(), node.get("target"));
					appendSequenceEvent(new LifeEvent(participant,
							node.kind.equals("create") ? LifeEventType.CREATE : LifeEventType.DESTROY,
							new Fashion(null, null), getSkinParam().getCurrentStyleBuilder()));
				}
			}
			nativeEventCursor = events().size();
			addedParticipants.clear();
			textBlock = null;
			return CommandExecutionResult.ok();
		} catch (AutofacParseException error) {
			return syntaxError(error);
		}
	}

	CommandExecutionResult executeNative(Command<SequenceDiagram> command, BlocLines lines, ParserPass pass)
			throws NoSuchColorException {
		final StringLocated source = lines.getFirst();
		if (parser.hasConstruction())
			return syntaxError(new AutofacParseException("Unexpected native command in " + parser.constructionKind()
					+ " construction", source, 0));
		String header = source.getString().trim();
		final boolean prefixed = header.startsWith("&") || header.startsWith("/");
		while (header.startsWith("&") || header.startsWith("/")) header = header.substring(1).trim();
		if (prefixed && (header.startsWith("note") || header.startsWith("hnote") || header.startsWith("rnote")))
			return syntaxError(new AutofacParseException("Parallel or merged Autofac notes are not supported", source, 0));
		parser.beginNative();
		try {
			final CommandExecutionResult result = command.execute(this, lines, pass);
			if (!result.isOk() || result.getNewDiagram() != null) return result;
			for (Participant participant : addedParticipants) {
				if (participant.getUrl() != null)
					throw new AutofacParseException("Autofac participant URLs are not supported", source, 0);
				if (nativeEntities.add(participant.getCode())) {
					final Map<String, String> presentation = colors(participant.getColors(), source);
					final String stereotype = stereotype(participant.getStereotype());
					if (stereotype != null) presentation.put("stereotype", stereotype);
					parser.addParticipant(participant.getCode(), display(participant.getDisplay(false)),
							participant.getType().name(), source, presentation);
				}
			}
			addedParticipants.clear();
			for (int i = nativeEventCursor; i < events().size(); i++)
				capture(events().get(i), source, command instanceof CommandReturn);
			nativeEventCursor = events().size();
			// Native notes on arrows modify the last message instead of appending an event.
			final EventWithNote last = getLastEventWithNote();
			if (last instanceof AbstractMessage) {
				final AbstractMessage message = (AbstractMessage) last;
				for (Note note : message.getNoteOnMessages()) captureNote(note, source, occurrences.get(message));
			}
			// Native merged notes may replace or extend the last event in place.
			if (!events().isEmpty()) {
				final Event event = events().get(events().size() - 1);
				if (event instanceof Notes) for (Note note : (Notes) event) captureNote(note, source, null);
			}
			statementCursor = parser.statementSize();
			textBlock = null;
			return result;
		} catch (AutofacParseException error) {
			return syntaxError(error);
		}
	}

	@Override
	public Participant getOrCreateParticipant(LineLocation location, String code) {
		final Participant existing = nativeParticipants.get(code);
		return existing == null
				? getOrCreateParticipant(location, code, Display.getWithNewlines(getPragma(), code)) : existing;
	}

	@Override
	public Participant getOrCreateParticipant(LineLocation location, String code, Display display) {
		Participant participant = nativeParticipants.get(code);
		if (participant == null) {
			participant = super.getOrCreateParticipant(location, code, display);
			nativeParticipants.put(code, participant);
			addedParticipants.add(participant);
		}
		return participant;
	}

	@Override
	public Participant createNewParticipant(LineLocation location, ParticipantType type, String code,
			Display display, int order) {
		final Participant participant = super.createNewParticipant(location, type, code, display, order);
		nativeParticipants.put(code, participant);
		addedParticipants.add(participant);
		return participant;
	}

	/** Explicit Autofac creation is not sequence's pending-create/next-reception protocol. */
	@Override
	public String activate(Participant participant, LifeEventType type, HColor background, HColor line) {
		if (type == LifeEventType.CREATE || type == LifeEventType.DESTROY) {
			appendSequenceEvent(new LifeEvent(participant, type, new Fashion(background, line),
					getSkinParam().getCurrentStyleBuilder()));
			return null;
		}
		return super.activate(participant, type, background, line);
	}

	@Override
	public boolean grouping(String title, String comment, GroupingType type, HColor background,
			HColor header, boolean parallel) {
		if (parallel) return false;
		authoredGroupBody = background;
		authoredGroupHeader = header;
		return super.grouping(title, comment, type, background, header, parallel);
	}

	private void synchronizeDeclarations() {
		while (statementCursor < parser.statementSize()) {
			final Node declaration = parser.statementAt(statementCursor++);
			if (declaration.kind.equals("lifetime") || declaration.kind.equals("object")) {
				getOrCreateParticipant(declaration.source.getLocation(), declaration.id,
						Display.getWithNewlines(getPragma(), declaration.get("label", declaration.id)));
				nativeEntities.add(declaration.id);
			}
		}
	}

	private void capture(Event event, StringLocated source, boolean generatedReturn) throws AutofacParseException {
		if (event instanceof AbstractMessage) {
			final AbstractMessage message = (AbstractMessage) event;
			if (message.getUrl() != null)
				throw new AutofacParseException("Autofac message URLs are not supported", source, 0);
			if (message.getAnchor() != null || message.getPart1Anchor() != null || message.getPart2Anchor() != null)
				throw new AutofacParseException("Use event aliases for Autofac occurrence anchors", source, 0);
			if (message instanceof MessageExo) {
				final MessageExo exo = (MessageExo) message;
				final MessageExoType type = exo.getType();
				final boolean incoming = type == MessageExoType.FROM_LEFT || type == MessageExoType.FROM_RIGHT;
				final String border = type.isLeftBorder() ? "@left" : "@right";
				final String participant = returnEndpoint(exo.getParticipant().getCode(), generatedReturn);
				addMessage(incoming ? border : participant, incoming ? participant : border,
						display(message.getLabel()), message.getArrowConfiguration(), source,
						type.name(), message.isParallel(), stereotype(message.getStereotype()));
			} else {
				addMessage(returnEndpoint(message.getParticipant1().getCode(), generatedReturn),
						returnEndpoint(message.getParticipant2().getCode(), generatedReturn),
						display(message.getLabel()), message.getArrowConfiguration(), source, null,
						message.isParallel(), stereotype(message.getStereotype()));
			}
			occurrences.put(message, parser.lastTimeline().id);
			if (message instanceof Message) {
				final Node primary = parser.lastTimeline();
				for (Participant recipient : ((Message) message).getMulticast())
					if (recipient != message.getParticipant2())
						addMessage(primary.get("from"), recipient.getCode(), display(message.getLabel()),
								message.getArrowConfiguration(), source, null, true,
								stereotype(message.getStereotype()));
			}
			for (Note note : message.getNoteOnMessages()) captureNote(note, source, occurrences.get(message));
		} else if (event instanceof Grouping) {
			final Grouping grouping = (Grouping) event;
			final String mode = grouping.getType() == GroupingType.END ? "end"
					: grouping.getType() == GroupingType.ELSE ? "else" : grouping.getTitle();
			final Map<String, String> attrs = new LinkedHashMap<String, String>();
			color(attrs, "backgroundColor", authoredGroupBody, source);
			color(attrs, "headerColor", authoredGroupHeader, source);
			parser.addGroup(mode, grouping.getComment() == null ? "" : grouping.getComment(), source, attrs);
		} else if (event instanceof LifeEvent) {
			final LifeEvent life = (LifeEvent) event;
			final Map<String, String> attrs = AutofacParser.attrs("target", life.getParticipant().getCode());
			color(attrs, "backgroundColor", life.getSpecificColors().getBackColor(), source);
			color(attrs, "lineColor", life.getSpecificColors().getForeColor(), source);
			parser.addTimeline(life.getType().name().toLowerCase(java.util.Locale.ROOT), null, attrs, source, null);
		} else if (event instanceof Note) {
			captureNote((Note) event, source, null);
		} else if (event instanceof Notes) {
			for (Note note : (Notes) event) captureNote(note, source, null);
		} else {
			throw new AutofacParseException("Unsupported sequence occurrence", source, 0);
		}
	}

	private String returnEndpoint(String endpoint, boolean generatedReturn) {
		final Node occurrence = generatedReturn ? parser.event(endpoint) : null;
		return occurrence != null && occurrence.kind.equals("event") ? occurrence.get("scope") : endpoint;
	}

	private void addMessage(String from, String to, String label, ArrowConfiguration arrow, StringLocated source,
			String external, boolean parallel, String stereotype) throws AutofacParseException {
		final Map<String, String> attrs = AutofacParser.attrs("from", from, "to", to, "label", label);
		final Node previous = parser.lastTimeline();
		if (previous != null && previous.kind.equals("event") && from.equals(previous.get("scope")))
			attrs.put("from", previous.id);
		if (external != null) attrs.put("external", external);
		if (parallel) attrs.put("parallel", "true");
		if (stereotype != null) attrs.put("stereotype", stereotype);
		parser.addTimeline("message", null, attrs, source, arrow);
	}

	private static String stereotype(net.sourceforge.plantuml.stereo.Stereotype stereotype) {
		return stereotype == null ? null : stereotype.toString();
	}

	private void captureNote(Note note, StringLocated source, String occurrence) throws AutofacParseException {
		if (!capturedNotes.add(note)) return;
		if (note.getUrl() != null)
			throw new AutofacParseException("Autofac note URLs are not supported", source, 0);
		String targets = "";
		if (occurrence != null) targets = occurrence;
		else if (note.getParticipant() != null) {
			targets = note.getParticipant().getCode();
			if (note.getParticipant2() != null) targets += "," + note.getParticipant2().getCode();
		}
		final String position = note.getPosition().name().toLowerCase(java.util.Locale.ROOT);
		final Map<String, String> attrs = colors(note.getColors(), source);
		attrs.put("label", display(note.getDisplay()));
		attrs.put("position", targets.isEmpty() ? "across" : position.equals("over_several") ? "over" : position);
		attrs.put("targets", targets);
		String header = source.getString().trim();
		if (header.startsWith("&")) header = header.substring(1).trim();
		if (header.startsWith("/")) header = header.substring(1).trim();
		attrs.put("shape", header.startsWith("hnote") ? "hnote" : header.startsWith("rnote") ? "rnote" : "note");
		final String stereotype = stereotype(note.getStereotype());
		if (stereotype != null) attrs.put("stereotype", stereotype);
		parser.addTimeline("note", null, attrs, source, null);
	}

	private static Map<String, String> colors(Colors colors, StringLocated source) throws AutofacParseException {
		final Map<String, String> result = new LinkedHashMap<String, String>();
		color(result, "backgroundColor", colors.getColor(ColorType.BACK), source);
		color(result, "lineColor", colors.getColor(ColorType.LINE), source);
		color(result, "textColor", colors.getColor(ColorType.TEXT), source);
		return result;
	}

	private static void color(Map<String, String> attrs, String key, HColor color, StringLocated source)
			throws AutofacParseException {
		if (color == null) return;
		final String value = color.asString();
		if (value.startsWith("?") || color.darkSchemeTheme() != color)
			throw new AutofacParseException("Compound Autofac presentation colors are not supported", source, 0);
		attrs.put(key, value);
	}

	private static String display(Display display) {
		final StringBuilder text = new StringBuilder();
		for (CharSequence line : display) {
			if (text.length() > 0) text.append('\n');
			text.append(line);
		}
		return text.toString();
	}

	private CommandExecutionResult syntaxError(AutofacParseException error) {
		final StringLocated offending = error.getSource();
		final List<StringLocated> trace = new ArrayList<StringLocated>();
		final IteratorCounter2 iterator = getSource().iterator2();
		while (iterator.hasNext()) {
			final StringLocated original = iterator.next();
			trace.add(original);
			if (original == offending || original.getLocation().equals(offending.getLocation())) break;
		}
		if (trace.isEmpty() || !trace.get(trace.size() - 1).getLocation().equals(offending.getLocation())) trace.add(offending);
		final ErrorUml syntax = new ErrorUml(ErrorUmlType.SYNTAX_ERROR, error.getMessage(), 0, offending, DiagramType.AUTOFAC);
		return CommandExecutionResult.newDiagram(PSystemErrorUtils.buildV2(getSource(), syntax,
				Collections.<String>emptyList(), trace, getPreprocessingArtifact()));
	}

	@Override
	public String checkFinalError() {
		try {
			finishModel();
			return super.checkFinalError();
		} catch (AutofacParseException error) {
			return "line " + (error.getSource().getLocation().getPosition() + 1) + ": " + error.getMessage();
		}
	}

	private void finishModel() throws AutofacParseException {
		if (finalized) return;
		model = AutofacValidator.validate(parser.parse());
		for (AutofacModel.StructuralWarning warning : model.warnings)
			addWarning(new Warning(warning.code + ": " + warning.message));
		finalized = true;
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
		if (textBlock == null) textBlock = new AutofacTextBlock(model, getSkinParam());
		return textBlock;
	}
}
