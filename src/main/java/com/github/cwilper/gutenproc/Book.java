package com.github.cwilper.gutenproc;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.edit.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Book
{
    private final Map<Field, List<String>> metadata;

    private Optional<List<String>> plaintextContent = null;

    private Optional<List<String>> plaintextContentNormalized = null;

    private Integer lineCount;

    protected Book(Map<Field, List<String>> metadata) {
        this.metadata = metadata;
    }

    public boolean has(Field field) {
        return metadata.containsKey(field);
    }

    public Optional<List<String>> get(Field field) {
        if (has(field)) {
            return Optional.of(metadata.get(field));
        }
        return Optional.empty();
    }

    public Optional<String> getFirst(Field field) {
        if (has(field)) {
            return Optional.of(metadata.get(field).get(0));
        }
        return Optional.empty();
    }

    public Iterable<Field> fields() {
        return metadata.keySet();
    }

    public String getPlaintextMetadata(boolean includeComputed) {
        String computed = "";
        if (includeComputed) {
            computed = "Zip Entries: " + zipEntryCount() + "\n"
                    + "Text Lines: " + lineCount() + "\n";
        }
        final String value = getPlaintextMetadata(
                Field.TITLE,
                Field.AUTHOR,
                Field.CONTRIBUTOR,
                Field.LANGUAGE,
                Field.SUBJECT,
                Field.LOC_CLASS,
                Field.NOTE,
                Field.RELEASE_DATE,
                Field.COPYRIGHT_STATUS,
                Field.ETEXT_NO,
                Field.BASE_DIR,
                Field.FORMAT,
                Field.PATH,
                Field.URL) + computed;
        return value.substring(0, value.length() - 1);
    }

    private String getPlaintextMetadata(Field...fields) {
        StringBuilder builder = new StringBuilder();
        for (Field field : fields) {
            addValuesIfPresent(field, builder);
        }
        return builder.toString();
    }

    private void addValuesIfPresent(Field field, StringBuilder builder) {
        if (has(field)) {
            for (String value : get(field).get()) {
                builder.append(field.label() + ": " + value + "\n");
            }
        }
    }

    public synchronized Optional<List<String>> getPlaintextContent(boolean normalize) {
        if (plaintextContent == null) {
            plaintextContent = getPlaintextContent();
        }
        if (!plaintextContent.isPresent()) {
            return Optional.empty();
        }
        if (normalize) {
            if (plaintextContentNormalized == null) {
                plaintextContentNormalized = Optional.ofNullable(normalizeText(plaintextContent.get()));
            }
            return plaintextContentNormalized;
        } else {
            return plaintextContent;
        }
    }

    public String getBaseFilename() {
        final String path = getFirst(Field.PATH).get();
        final String pathFilename = path.substring(path.lastIndexOf('/') + 1);
        return pathFilename.substring(0, pathFilename.lastIndexOf('.'));
    }

    public boolean writePdf(File file, boolean normalize) {
        Optional<List<String>> content = getPlaintextContent(normalize);
        if (!content.isPresent()) {
            return false;
        }

        PDDocument document = new PDDocument();
        PDFont font = PDType1Font.COURIER;
        try {
            PDPage page = null;
            PDPageContentStream contentStream = null;
            int i = 0;
            for (String line : content.get()) {
                if (page == null) {
                    page = new PDPage(PDPage.PAGE_SIZE_LETTER);
                    contentStream = new PDPageContentStream(document, page);
                    contentStream.setFont(font, 11);
                    contentStream.beginText();
                    contentStream.moveTextPositionByAmount(65, 735);
                }
                contentStream.moveTextPositionByAmount(0, -13);
                contentStream.drawString(line);
                i++;
                if (i == 51) {
                    contentStream.endText();
                    contentStream.close();
                    document.addPage(page);
                    page = null;
                    contentStream = null;
                    i = 0;
                }
            }
            if (page != null) {
                contentStream.endText();
                contentStream.close();
                document.addPage(page);
            }

            document.save(file);
            document.close();
        } catch (Exception e) {
            Throwables.propagate(e);
        }
        return true;
    }

    public File getFile() {
        return GutenProc.getFileCaseInsensitive(getFirst(Field.PATH).get()).get();
    }

    public boolean isZipped() {
        return getFile().getName().endsWith(".zip");
    }

    public int zipEntryCount() {
        if (isZipped()) {
            try {
                return new ZipFile(getFile()).size();
            } catch (IOException e) {
                return -1; // zip not parsable
            }
        }
        return 0;
    }

    public synchronized int lineCount() {
        if (lineCount == null) {
            Optional<List<String>> lines = getPlaintextContent(true);
            if (lines.isPresent()) {
                lineCount = 0;
                for (String line : lines.get()) {
                    if (!line.isEmpty()) {
                        lineCount++;
                    }
                }
            } else {
                return -1;
            }
        }
        return lineCount;
    }

    private Optional<List<String>> getPlaintextContent() {
        final String format = getFirst(Field.FORMAT).get();
        try {
            final Optional<Charset> charset = getCharset(format);
            if (charset.isPresent()) {
                if (getFile().getName().endsWith(".txt")) {
                    return Optional.of(Files.readAllLines(getFile().toPath(), charset.get()));
                } else if (zipEntryCount() == 1 && format.startsWith("text/plain")) {
                    final Optional<Stream<String>> text = getPlaintextContentFromZip(new ZipFile(getFile()), charset.get());
                    if (text.isPresent()) {
                        final List<String> lines = text.get().collect(Collectors.toList());
                        text.get().close();
                        return Optional.of(lines);
                    }
                }
            }
        } catch (IOException e) {
            Throwables.propagate(e);
        }
        return Optional.empty();
    }

    private static Optional<Charset> getCharset(String format) {
        int i = format.indexOf("charset=\"");
        if (i != -1) {
            String remainder = format.substring(i + 9);
            int j = remainder.indexOf("\"");
            String name = remainder.substring(0, j);
            if (name.equals("macintosh")) {
                name = "MacRoman";
            }
            try {
                return Optional.of(Charset.forName(name));
            } catch (UnsupportedCharsetException e) {
                return Optional.empty();
            }
        } else if (format.startsWith("text/plain")) {
            return Optional.of(StandardCharsets.US_ASCII);
        }
        return Optional.empty();
    }

    private static Optional<Stream<String>> getPlaintextContentFromZip(ZipFile zipFile, Charset charset) {
        try {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            ZipEntry textEntry = null;
            int textEntryCount = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".txt")) {
                    textEntry = entry;
                    textEntryCount++;
                }
            }
            if (textEntryCount == 1) {
                InputStream inputStream = zipFile.getInputStream(textEntry);
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, charset));
                return Optional.of(reader.lines());
            }
            return Optional.empty(); // no text entry, or more than one (ambiguous); skip
        } catch (IOException e) {
            return Optional.empty(); // bad zip or bad text file within; skip
        }
    }

    private static List<String> normalizeText(List<String> input) {
        List<String> lines = Lists.newArrayList();
        boolean skipNextIfBlank = true;
        for (String line : input) {
            String lc = line.toLowerCase();
            if (lc.contains("project gutenberg") || lc.contains("public domain") || lc.contains(" etext") || (lc.contains("gutenberg") && lc.contains("http"))) {
                if (lines.size() > 90) {
                    // must be near the end. stop adding text to our list
                    // and ensure the last line is not blank
                    if (lines.get(lines.size() - 1).length() == 0) {
                        lines.remove(lines.size() - 1);
                    }
                    break;
                } else {
                    // must be near beginning; ignore everything we've saved up to now,
                    // since we haven't reached the actual text yet
                    lines.clear();
                    skipNextIfBlank = true;
                }
            } else if (line.trim().length() == 0) {
                if (!skipNextIfBlank) {
                    // only add blank line if previous existed and wasn't blank, else skip
                    lines.add("");
                    skipNextIfBlank = true;
                }
            } else {
                // add non-blank line, stripping any trailing whitespace
                lines.add(line.replaceFirst("\\s+$", ""));
                skipNextIfBlank = false;
            }
        }
        if (lines.size() < 80) { // didn't see much non-gutenberg text; looks like a descriptor, not a text
            return null;
        }
        return lines;
    }
}
