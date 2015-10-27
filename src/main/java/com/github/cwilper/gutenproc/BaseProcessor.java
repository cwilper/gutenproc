package com.github.cwilper.gutenproc;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class BaseProcessor
        implements Predicate<Book>, Processor
{
    protected boolean captureMatchInfo;
    protected StringBuilder matchInfo = null;

    protected int scanCount = 0;
    protected int matchCount = 0;
    protected int processCount = 0;

    protected Long minLines;
    protected Long maxLines;

    protected long limit;

    @Override
    public String getHelpFooter() {
        return "String Matching:\nString matches are performed as case insensitive substring matches, by default. "
                + "But if you specify a string starting with 's/' and ending with '/', a regular expression "
                + "match will be performed instead. For example, -mt \"s/The.*End/\" "
                + "will only match books whose titles begin with 'The' and end with 'End'.";
    }

    @Override
    public void addOptions(final Options options) {
        for (Field field : Field.values()) {
            addFieldFilterOption(options, field);
        }
        options.addOption(Option.builder("mil")
                .longOpt("min-lines")
                .desc("Minimum lines of text")
                .hasArg()
                .build());
        options.addOption(Option.builder("mal")
                .longOpt("max-lines")
                .desc("Maximum lines of text")
                .hasArg()
                .build());
        options.addOption(Option.builder("mx")
                .longOpt("match-text")
                .desc("Limit to books available in plaintext with a matching line")
                .hasArg()
                .build());
        options.addOption(Option.builder("l")
                .longOpt("limit")
                .desc("Limit to the given number of books")
                .hasArg()
                .build());
    }

    protected void addFieldFilterOption(Options options, Field field) {
        options.addOption(Option.builder(field.opt())
                .longOpt(field.longOpt())
                .desc("Limit to books with a matching " + field.label())
                .hasArg()
                .build());
    }

    @Override
    public void begin(Commandline cmd) {
        if (cmd.hasOption("mil")) {
            minLines = cmd.getOptionLongValue("mil").get();
        }
        if (cmd.hasOption("mal")) {
            maxLines = cmd.getOptionLongValue("mal").get();
        }
        if (cmd.hasOption("l")) {
            limit = cmd.getOptionLongValue("l").get();
        } else {
            limit = Long.MAX_VALUE;
        }
    }

    @Override
    public void process(final DVD dvd, final Commandline cmd) {
        Stream<Book> books = dvd.books().filter(book -> {
            // before each book, clear state of matchInfo if needed
            if (captureMatchInfo) {
                matchInfo = new StringBuilder();
            }
            // also increment scanCount so it can be used to determine overall progress
            scanCount++;
            return true;
        });
        for (Field field : Field.values()) {
            books = filterByFieldIfNeeded(cmd, books, field);
        }
        if (cmd.hasOption("mx")) {
            for (String value : cmd.getOptionValues("mx")) {
                books = books.filter(contentLineMatches(value));
            }
        }
        if (minLines != null) {
            books = books.filter(book -> book.lineCount() >= minLines);
        }
        if (maxLines != null) {
            books = books.filter(book -> book.lineCount() <= maxLines);
        }

        books = books.filter(book -> {
            matchCount++;
            return true;
        });

        books = books.filter(this);

        books = books.limit(limit);

        books.forEach(book -> {
            processCount++;
        });
    }

    protected Stream<Book> filterByFieldIfNeeded(Commandline cmd, Stream<Book> books, Field field) {
        if (cmd.hasOption(field.opt())) {
            for (String value : cmd.getOptionValues(field.opt())) {
                books = books.filter(metadataMatches(field, value));
            }
        }
        return books;
    }

    protected Predicate<Book> metadataMatches(final Field field, final String substringOrRegex) {
        if (substringOrRegex.startsWith("s/") && substringOrRegex.endsWith("/")) {
            final String regex = substringOrRegex.substring(2, substringOrRegex.length() - 1);
            return book -> {
                boolean matched = false;
                if (book.has(field)) {
                    for (String value : book.get(field).get()) {
                        if (value.matches(regex)) {
                            addMatchInfo("Metadata regex match on " + field.label() + ": " + value);
                            if (captureMatchInfo) {
                                matched = true;
                            } else {
                                return true;
                            }
                        }
                    }
                }
                return matched;
            };
        } else {
            final String lcSubstring = substringOrRegex.toLowerCase();
            return book -> {
                boolean matched = false;
                if (book.has(field)) {
                    for (String value : book.get(field).get()) {
                        if (value.toLowerCase().contains(lcSubstring)) {
                            addMatchInfo("Metadata substring match on " + field.label() + ": " + value);
                            if (captureMatchInfo) {
                                matched = true;
                            } else {
                                return true;
                            }
                        }
                    }
                }
                return matched;
            };
        }
    }

    protected Predicate<Book> contentLineMatches(final String substringOrRegex) {
        if (substringOrRegex.startsWith("s/") && substringOrRegex.endsWith("/")) {
            final String regex = substringOrRegex.substring(2, substringOrRegex.length() - 1);
            return book -> {
                boolean matched = false;
                Optional<List<String>> lines = book.getPlaintextContent(false);
                if (lines.isPresent()) {
                    int lineNum = 0;
                    for (String line : lines.get()) {
                        lineNum++;
                        if (line.matches(regex)) {
                            addMatchInfo("Text regex match on line " + lineNum + ": " + line);
                            if (captureMatchInfo) {
                                matched = true;
                            } else {
                                return true;
                            }
                        }
                    }
                }
                return matched;
            };
        } else {
            final String lcSubstring = substringOrRegex.toLowerCase();
            return book -> {
                boolean matched = false;
                Optional<List<String>> lines = book.getPlaintextContent(false);
                if (lines.isPresent()) {
                    int lineNum = 0;
                    for (String line : lines.get()) {
                        lineNum++;
                        if (line.toLowerCase().contains(lcSubstring)) {
                            addMatchInfo("Text substring match on line " + lineNum + ": " + line);
                            if (captureMatchInfo) {
                                matched = true;
                            } else {
                                return true;
                            }
                        }
                    }
                }
                return matched;
            };
        }
    }

    protected void addMatchInfo(String string) {
        if (captureMatchInfo) {
            matchInfo.append(string + "\n");
        }
    }
}
