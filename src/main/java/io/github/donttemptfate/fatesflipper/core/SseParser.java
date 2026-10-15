package io.github.donttemptfate.fatesflipper.core;

/**
 * Line-by-line parser for Server-Sent Events (the viewer's {@code /mod/flips} stream).
 *
 * <p>Feed it each line without the line break. A blank line ends an event, which is returned.
 * Comment lines (starting with ':') are keep-alives and return nothing.
 */
public final class SseParser {
	/** One complete event. {@code event} defaults to "message". */
	public record Event(String event, String data, String id) {}

	/** Events larger than this are dropped (a sane upper bound for one flip). */
	static final int MAX_DATA_CHARS = 512 * 1024;

	private String event;
	private final StringBuilder data = new StringBuilder();
	private boolean hasData;
	private boolean overflow;
	private String lastId;

	/** Returns a finished event, or {@code null} if the line didn't complete one. */
	public Event feed(String line) {
		if (line == null) return null;
		if (line.isEmpty()) return dispatch();
		if (line.charAt(0) == ':') return null;
		int colon = line.indexOf(':');
		String field;
		String value;
		if (colon < 0) {
			field = line;
			value = "";
		} else {
			field = line.substring(0, colon);
			value = line.substring(colon + 1);
			if (value.startsWith(" ")) value = value.substring(1);
		}
		switch (field) {
			case "event" -> event = value;
			case "data" -> {
				if (overflow) break;
				if (data.length() + value.length() + 1 > MAX_DATA_CHARS) {
					overflow = true;
					data.setLength(0);
					break;
				}
				if (hasData) data.append('\n');
				data.append(value);
				hasData = true;
			}
			case "id" -> {
				if (value.indexOf('\0') < 0) lastId = value;
			}
			default -> {
				// "retry" and unknown fields are ignored
			}
		}
		return null;
	}

	private Event dispatch() {
		try {
			if (!hasData || overflow) return null;
			return new Event(event == null || event.isEmpty() ? "message" : event, data.toString(), lastId);
		} finally {
			event = null;
			data.setLength(0);
			hasData = false;
			overflow = false;
		}
	}

	public String lastEventId() {
		return lastId;
	}
}
