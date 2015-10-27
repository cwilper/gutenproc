package com.github.cwilper.gutenproc.list;

import com.github.cwilper.gutenproc.BaseProcessor;
import com.github.cwilper.gutenproc.Book;
import com.github.cwilper.gutenproc.Commandline;
import com.github.cwilper.gutenproc.Field;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

@SuppressWarnings("unused")
public class ListProcessor extends BaseProcessor
{
    private boolean abbreviated;

    private boolean printMatches;

    private boolean showComputed;

    @Override
    public String getSynopsis() {
        return "Prints book metadata";
    }

    @Override
    public void addOptions(Options options) {
        super.addOptions(options);
        options.addOption(Option.builder("pm")
                .longOpt("print-matches")
                .desc("Print all matching metadata or text lines")
                .build());
        options.addOption(Option.builder("sc")
                .longOpt("show-computed")
                .desc("Show computed book metadata (will take longer)")
                .build());
        options.addOption(Option.builder("a")
                .longOpt("abbreviated")
                .desc("Only print EText numbers and titles (overrides pm and sc)")
                .build());
    }

    @Override
    public void begin(final Commandline cmd) {
        super.begin(cmd);
        if (cmd.hasOption("a")) {
            abbreviated = true;
        }
        if (cmd.hasOption("pm")) {
            captureMatchInfo = true;
            printMatches = true;
        }
        if (cmd.hasOption("sc")) {
            showComputed = true;
        }
    }

    @Override
    public boolean test(final Book book) {
        if (abbreviated) {
            System.out.println(
                    book.getFirst(Field.ETEXT_NO).get() + ": "
                    + book.getFirst(Field.TITLE).get());
        } else {
            System.out.println("Match #" + matchCount + " of " + scanCount + " scanned");
            System.out.println(book.getPlaintextMetadata(showComputed));
            if (printMatches) {
                String s = matchInfo.toString();
                System.out.println(s.substring(0, s.length() - 1));
            }
            System.out.println();
        }
        return true;
    }

    @Override
    public void end() {
        int percent = (matchCount * 100) / scanCount;
        System.out.println("Matched " + matchCount + " of " + scanCount + " (" + percent + "%)");
    }
}
