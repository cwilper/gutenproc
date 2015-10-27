package com.github.cwilper.gutenproc;

import jline.TerminalFactory;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.File;
import java.util.Optional;

public final class GutenProc
{
    private GutenProc() { }

    private static void die(String message) {
        System.out.println("Error: " + message + " (-h for help)");
        System.exit(1);
    }

    private static void printHelpAndExit(String usage, String header, Options options, String footer) {
        HelpFormatter helpFormatter = new HelpFormatter();
        helpFormatter.setWidth(getTerminalWidth());
        helpFormatter.printHelp(usage, "\n" + header + "\n\nOptions:", options, "\n" + footer);
        System.exit(0);
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            die("Must specify a processor name");
        }

        Options options = new Options();
        options.addOption(Option.builder("d")
                .longOpt("dvd-path")
                .desc("Path to the DVD (required). The PGDVD_PATH environment variable will be checked if this is unspecified.")
                .hasArg()
                .build());
        options.addOption("h", "help", false, "Shows help");

        if (args[0].equals("-h") || args[0].equals("--help")) {
            StringBuilder footer = new StringBuilder();
            footer.append("Processors:\n");
            for (Processor processor : Processor.list()) {
                footer.append(" " + processor.getName() + ": " + processor.getSynopsis() + "\n");
            }
            footer.append("\nSpecify -h after a processor name to processor-specific help");
            printHelpAndExit("gutenproc -h | processor -h | processor -d /path/to/DVD [processor-options..]",
                    "Processes the Project Gutenberg DVD from 2010",
                    options,
                    footer.toString());
        }

        Optional<Processor> oProcessor = Processor.forName(args[0]);
        if (!oProcessor.isPresent()) {
            die("No such processor: " + args[0]);
        }
        Processor processor = oProcessor.get();

        processor.addOptions(options);

        try {
            Commandline cmd = new Commandline(new DefaultParser().parse(options, args));

            if (cmd.hasOption("h")) {
                printHelpAndExit(
                        "gutenproc " + processor.getName() + " -h | -d /path/to/DVD [processor-options..]",
                        processor.getSynopsis(),
                        options,
                        processor.getHelpFooter());
            } else {
                String dvdPath = null;
                if (!cmd.hasOption("d")) {
                    dvdPath = System.getenv("PGDVD_PATH");
                    if (dvdPath == null || dvdPath.trim().length() == 0) {
                        die("Option d missing and PGDVD_PATH environment variable undefined");
                    }
                } else {
                    cmd.getOptionValue("d").get();
                }
                DVD dvd = new DVD(new File(dvdPath));
                try {
                    processor.begin(cmd);
                } catch (Exception e) {
                    if (e.getMessage() != null) {
                        die(e.getMessage());
                    } else {
                        e.printStackTrace();
                        die("See above");
                    }
                }
                try {
                    processor.process(dvd, cmd);
                } finally {
                    processor.end();
                }
            }
        } catch (ParseException e) {
            if (e.getMessage() != null) {
                die(e.getMessage());
            } else {
                e.printStackTrace();
                die("See above");
            }
        }
    }

    private static int getTerminalWidth() {
        int reportedWidth = TerminalFactory.get().getWidth();
        if (reportedWidth < 50) {
            return 50;
        }
        return reportedWidth;
    }
}
