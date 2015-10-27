package com.github.cwilper.gutenproc;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;

public class DVD
{
    private final Path dvdPath;

    public DVD(final File file) {
        Preconditions.checkArgument(file.isDirectory(), "No such directory: " + file);
        this.dvdPath = file.toPath();
    }

    public Stream<Book> books() {
        try {
            return Files.list(dvdPath.resolve("ETEXT"))
                    .map(path -> lines(path, UTF_8))
                    .map(lines -> book(lines));
                           // lines.collect(Collectors.toList())));
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private Book book(final Stream<String> lines) {
        final Map<Field, List<String>> metadata = Maps.newHashMap();
        List<String> formats = Lists.newArrayList();
        List<String> paths = Lists.newArrayList();
        Field field = null;
        boolean inFilesSection = false;
        for (String line : lines.collect(Collectors.toList())) {
            if (line.startsWith("<th")) {
                field = Field.forString(stripTagsAndTrim(line));
            } else if (line.startsWith("<td")) {
                String value = stripTagsAndTrim(line);
                if (!value.isEmpty()) {
                    List<String> values = metadata.get(field);
                    if (values == null) {
                        values = Lists.newArrayList();
                        metadata.put(field, values);
                    }
                    values.add(value);
                }
            } else if (line.startsWith("<table><caption>")) {
                addFileInfo(line.substring(line.indexOf("</tr><tr>") + 5), formats, paths);
            } else if (inFilesSection) {
                if (line.startsWith("</table>")) {
                    inFilesSection = false;
                } else {
                    addFileInfo(line, formats, paths);
                }
            }
        }
        lines.close();

        // add formats and paths
        if (!formats.isEmpty()) {
            metadata.put(Field.FORMAT, formats);
            metadata.put(Field.PATH, paths);
        }

        // add url
        List<String> urls = Lists.newArrayList();
        urls.add("https://www.gutenberg.org/ebooks/" + metadata.get(Field.ETEXT_NO).get(0));
        metadata.put(Field.URL, urls);

        return new Book(metadata);
    }

    private void addFileInfo(String tableRow, List<String> formats, List<String> paths) {
        String stripped = stripTagsAndTrim(tableRow.replace("<td><a", "<td> <a"));
        int i = stripped.lastIndexOf(' ');
        formats.add(stripped.substring(0, i));
        paths.add(dvdPath.resolve(stripped.substring(i + 2)).toString());
    }

    private static String stripTagsAndTrim(String input) {
        return input.replaceAll("<[^>]*>", "");
    }

    private static Stream<String> lines(final Path path, final Charset charset) {
        try {
            return Files.lines(path, charset);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }
}
