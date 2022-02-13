package tyger.binder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tyger.ast.AstNode;

public class ErrorReporter {

    private static final Logger logger = LoggerFactory.getLogger(ErrorReporter.class);

    private static final String COLOR_BRIGHT_RED = "\u001b[31;1m";
    private static final String COLOR_RESET = "\u001b[0m";
    private static final int COMPILER_ERROR_LINES_AROUND = 7;

    private String filename;
    private String[] source_code;

    public ErrorReporter(final String filename, final String source_code) {
        this.filename = filename;
        this.source_code = source_code.split("\n");
    }

    public <T> T compiler_error(AstNode.Loc loc, String format, Object... args) {
        int errorStartLine = loc.start_line();
        int errorStartLineStartChar = loc.start_line_char();
        int errorStopLine = loc.stop_line();
        int errorStopLineStopChar = loc.stop_line_char();

        int outputStartLine = Math.max(1, errorStartLine - COMPILER_ERROR_LINES_AROUND);
        int outputStopLine = Math.min(source_code.length, errorStopLine + COMPILER_ERROR_LINES_AROUND);

        int lineNumberDigits = outputStopLine / 10;

        StringBuilder errorMessage = new StringBuilder("\n")
                .append(COLOR_BRIGHT_RED)
                .append("Compilation error [")
                .append(filename)
                .append(":")
                .append(errorStartLine)
                .append("]: ")
                .append(COLOR_RESET)
                .append(String.format(format, args))
                .append("\n\n");



        for (int lineNumber = outputStartLine; lineNumber <= outputStopLine; lineNumber++) {
            int lineNumberWhitespacePadding = lineNumberDigits - lineNumber / 10;
            errorMessage.append(" ".repeat(Math.max(0, lineNumberWhitespacePadding)));

            errorMessage.append(lineNumber)
                    .append(": ");

            String line = source_code[lineNumber - 1];

            if (lineNumber >= errorStartLine && lineNumber <= errorStopLine) {
                if (lineNumber > errorStartLine) {
                    errorMessage.append(COLOR_BRIGHT_RED);
                }

                for (int charIndex = 0; charIndex < line.length(); charIndex++) {
                    if (lineNumber == errorStartLine && charIndex == errorStartLineStartChar) {
                        errorMessage.append(COLOR_BRIGHT_RED);
                    }

                    errorMessage.append(line.charAt(charIndex));

                    if (lineNumber == errorStopLine && charIndex == errorStopLineStopChar - 1) {
                        // Reset color
                        errorMessage.append(COLOR_RESET);
                    }
                }
                errorMessage.append(COLOR_RESET);
                errorMessage.append('\n');
            } else {
                errorMessage.append(line).append('\n');
            }
        }

        logger.error("{}", errorMessage);

        throw new RuntimeException(String.format(format, args));
    }

    public static String ordinal(int i) {
        String[] suffixes = new String[] { "th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th" };
        return switch (i % 100) {
            case 11, 12, 13 -> i + "th";
            default -> i + suffixes[i % 10];
        };
    }

}
