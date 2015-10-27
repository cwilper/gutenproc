package com.github.cwilper.gutenproc.dspace;

import com.github.cwilper.gutenproc.BaseProcessor;
import com.github.cwilper.gutenproc.Book;
import com.github.cwilper.gutenproc.Commandline;
import com.github.cwilper.gutenproc.Field;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.xml.XmlEscapers;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.pdfbox.io.IOUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@SuppressWarnings("unused")
public class DSpaceProcessor extends BaseProcessor
{
    private File outputDir;
    private boolean noOrig;
    private boolean explodeAll;
    private boolean explodeOne;
    private boolean generateStrippedText;
    private boolean generatePdf;
    private boolean generateStrippedPdf;

    @Override
    public String getSynopsis() {
        return "Creates a directory of items that can be ingested into DSpace";
    }

    @Override
    public void addOptions(Options options) {
        super.addOptions(options);
        options.addOption(Option.builder("o")
                .longOpt("output-dir")
                .desc("Send output to the given directory (required)")
                .hasArg()
                .build());
        options.addOption(Option.builder("no")
                .longOpt("no-orig")
                .desc("Don't store the original file as a bitstream")
                .build());
        options.addOption(Option.builder("ea")
                .longOpt("explode-all")
                .desc("If the original file is a zip, save all files within as bitstreams")
                .build());
        options.addOption(Option.builder("eo")
                .longOpt("explode-one")
                .desc("If the original file is a zip with one file inside, save it as a bitstream")
                .build());
        options.addOption(Option.builder("gst")
                .longOpt("generate-stripped-text")
                .desc("If the original file is plaintext or is a single-file zip with plaintext,"
                        + " save a stripped version of it as a bitstream")
                .build());
        options.addOption(Option.builder("gp")
                .longOpt("generate-pdf")
                .desc("If the original file is plaintext or is a single-file zip with plaintext,"
                        + " save a PDF version of it as a bitstream")
                .build());
        options.addOption(Option.builder("gsp")
                .longOpt("generate-stripped-pdf")
                .desc("If the original file is plaintext or is a single-file zip with plaintext,"
                        + " save a stripped PDF version of it as a bitstream.")
                .build());
    }

    @Override
    public void begin(Commandline cmd) {
        super.begin(cmd);
        Preconditions.checkArgument(cmd.hasOption("o"), "Missing required option: o");
        outputDir = new File(cmd.getOptionValue("o").get());
        Preconditions.checkArgument(!outputDir.exists(), "Output directory already exists");
        Preconditions.checkArgument(outputDir.mkdir(), "Unable to create output directory");
        noOrig = cmd.hasOption("no");
        explodeAll = cmd.hasOption("ea");
        explodeOne = cmd.hasOption("eo");
        generateStrippedText = cmd.hasOption("gst");
        generatePdf = cmd.hasOption("gp");
        generateStrippedPdf = cmd.hasOption("gsp");
    }

    @Override
    public boolean test(final Book book) {
        try {
            System.out.print("Creating package for book #" + matchCount + " of " + scanCount + " scanned. ");
            final File itemDir = new File(outputDir, "book_" + book.getFirst(Field.ETEXT_NO).get());
            Preconditions.checkState(itemDir.mkdir());

            // add bitstreams, returning early if none are eligible
            final List<String> bitstreams = addBitstreams(book, itemDir);
            if (bitstreams.isEmpty()) {
                itemDir.delete();
                System.out.println("SKIPPED; No eligible bitstreams");
                return false;
            }

            // add content file with list of bitstreams
            final String contents = getContents(bitstreams);
            final File contentsFile = new File(itemDir, "contents");
            com.google.common.io.Files.write(contents, contentsFile, StandardCharsets.UTF_8);

            // add dublin_core.xml
            final String dc = getDcXml(book);
            final File dcFile = new File(itemDir, "dublin_core.xml");
            com.google.common.io.Files.write(dc, dcFile, StandardCharsets.UTF_8);

            final String suffix = bitstreams.size() == 1 ? "" : "s";
            System.out.println("Added " + bitstreams.size() + " bitstream" + suffix);
        } catch (Exception e) {
            System.out.println("FAILED");
            Throwables.propagate(e);
        }
        return true;
    }

    private String getContents(List<String> bitstreams) {
        StringBuilder s = new StringBuilder();
        for (String bitstream : bitstreams) {
            if (s.length() > 0) {
                s.append("\n");
            }
            s.append(bitstream);
        }
        s.append("\tprimary:true");
        return s.toString();
    }

    private List<String> addBitstreams(Book book, File itemDir) {
        final List<String> bitstreams = Lists.newArrayList();

        final int zipEntries = book.zipEntryCount();

        if (zipEntries > 1 && explodeAll) {
            bitstreams.addAll(explode(book, itemDir));
        }

        if (!noOrig) {
            bitstreams.add(addOrig(book, itemDir));
        }

        if (zipEntries == 1 && (explodeOne || explodeAll)) {
            bitstreams.addAll(explode(book, itemDir));
        } else if (zipEntries == -1) {
            // zipfile is corrupt; derivatives are not possible
            return bitstreams;
        }

        if (generateStrippedText || generatePdf || generateStrippedPdf) {
            if (generateStrippedText) {
                final Optional<List<String>> strippedText = book.getPlaintextContent(true);
                if (strippedText.isPresent()) {
                    final String name = book.getBaseFilename() + "-gens.txt";
                    try {
                        Files.write(new File(itemDir, name).toPath(), strippedText.get(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        Throwables.propagate(e);
                    }
                    bitstreams.add(name);
                }
            }
            if (generatePdf) {
                final String name = book.getBaseFilename() + "-gen.pdf";
                if (book.writePdf(new File(itemDir, name), false)) {
                    bitstreams.add(name);
                }
            }
            if (generateStrippedPdf) {
                final String name = book.getBaseFilename() + "-gens.pdf";
                if (book.writePdf(new File(itemDir, name), true)) {
                    bitstreams.add(name);
                }
            }
        }

        return bitstreams;
    }

    private String addOrig(Book book, File itemDir) {
        final File outputFile = new File(itemDir, book.getFile().getName());
        try {
            com.google.common.io.Files.copy(book.getFile(), outputFile);
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        return outputFile.getName();
    }

    private List<String> explode(Book book, File itemDir) {
        final List<String> bitstreams = Lists.newArrayList();
        try {
            final ZipFile zipFile = new ZipFile(book.getFile());
            final Enumeration entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = (ZipEntry) entries.nextElement();
                final String name = entry.getName().replaceAll("/", "_");
                bitstreams.add(name);
                final File outFile = new File(itemDir, name);
                try (InputStream in = zipFile.getInputStream(entry);
                     OutputStream out = new FileOutputStream(outFile)) {
                    IOUtils.copy(in, out);
                }
            }
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        return bitstreams;
    }

    private String getDcXml(final Book book) {
        StringBuilder builder = new StringBuilder();
        builder.append("<dublin_core>\n");
        for (Field field : book.fields()) {
            for (String value : book.get(field).get()) {
                if (field.dcElement() != null) {
                    builder.append("  <dcvalue element=\"");
                    builder.append(field.dcElement());
                    builder.append("\" qualifier=\"");
                    builder.append(field.dcQualifier());
                    builder.append("\">");
                    builder.append(XmlEscapers.xmlContentEscaper().escape(value));
                    builder.append("</dcvalue>\n");
                }
            }
        }
        builder.append("</dublin_core>");
        return builder.toString();
    }

    @Override
    public void end() {
        System.out.println("Added " + matchCount + " of " + scanCount + " scanned");
    }
}
