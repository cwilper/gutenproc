package com.github.cwilper.gutenproc;

import com.google.common.base.Preconditions;

public enum Field
{
    AUTHOR("Author", "ma", "contributor", "author"),
    BASE_DIR("Base Directory", "mb"),
    CONTRIBUTOR("Contributor", "mo", "contributor", "other"),
    COPYRIGHT_STATUS("Copyright Status", "my", "rights"),
    ETEXT_NO("EText-No.", "me", "identifier"),
    FORMAT("Format", "mf", "format", "mimetype"),
    LANGUAGE("Language", "ml", "language"),
    LOC_CLASS("LoC Class", "mc", "subject", "lcsh"),
    NOTE("Note", "mn", "description"),
    PATH("Path", "mp"),
    RELEASE_DATE("Release Date", "mr", "date", "issued"),
    SUBJECT("Subject", "ms", "subject"),
    TITLE("Title", "mt", "title"),
    URL("URL", "mu", "identifier", "uri");

    private final String label;
    private final String opt;
    private final String dcElement;
    private final String dcQualifier;

    Field(String label, String opt, String... dc) {
        Preconditions.checkArgument(dc.length < 3);
        this.label = label;
        this.opt = opt;
        if (dc.length == 0) {
            this.dcElement = null;
        } else {
            this.dcElement = dc[0];
        }
        if (dc.length == 2) {
            this.dcQualifier = dc[1];
        } else {
            this.dcQualifier = "none";
        }
    }

    public String dcElement() {
        return dcElement;
    }

    public String dcQualifier() {
        return dcQualifier;
    }

    public String label() {
        return label;
    }

    public String opt() {
        return opt;
    }

    public String longOpt() {
        return "match-" + shortLabel();
    }

    protected String shortLabel() {
        return label.replace(".", "").replace(" ", "-").toLowerCase();
    }

    public static Field forString(String string) {
        for (Field field : values()) {
            if (field.label().equalsIgnoreCase(string) || field.shortLabel().equalsIgnoreCase(string)) {
                return field;
            }
        }
        throw new IllegalArgumentException("Unrecognized field: " + string);
    }
}
