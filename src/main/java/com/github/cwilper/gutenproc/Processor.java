package com.github.cwilper.gutenproc;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.reflect.ClassPath;
import org.apache.commons.cli.Options;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface Processor
{
    String CLASS_NAME_SUFFIX = "Processor";

    default String getName() {
        final String n = getClass().getSimpleName();
        return n.substring(0, n.length() - CLASS_NAME_SUFFIX.length()).toLowerCase();
    }

    String getSynopsis();

    String getHelpFooter();

    /**
     * Adds processor-specific options.
     *
     * Implementations should NOT mark any options as required.
     * Instead, option descriptions should end with (required) if they're required,
     * and missing/invalid option errors should be detected during begin.
     */
    void addOptions(Options options);

    /**
     * Prepares this processor, throwing an exception with an informative message
     * for the user if a failure occurs.
     */
    void begin(Commandline cmd);

    /**
     * Processes books from the dvd.
     */
    void process(DVD dvd, Commandline cmd);

    /**
     * Cleans up resources acquired or allocated by the processor.
     * This is guaranteed to run after processing if begin completes without throwing an exception.
     */
    void end();

    /**
     * Dynamically gets all known processors. Any class residing in a sub-package of the base package
     * whose name ends with "Processor" will be added to this list, so it's not necessary to explicitly
     * register them.
     */
    static List<Processor> list() {
        List<Processor> list = Lists.newArrayList();
        try {
            String basePackage = Processor.class.getPackage().getName();
            Set<ClassPath.ClassInfo> infos = ClassPath.from(
                    Processor.class.getClassLoader()).getTopLevelClassesRecursive(
                    basePackage);
            for (ClassPath.ClassInfo info : infos) {
                String n = info.getName();
                if (n.endsWith("Processor") && n.substring(basePackage.length() + 1).contains(".")) {
                    list.add((Processor) info.load().newInstance());
                }
            }
        } catch (Exception e) {
            Throwables.propagate(e);
        }
        return list;
    }

    /**
     * Gets the processor with the given name, if it exists.
     */
    static Optional<Processor> forName(String name) {
        final String lcName = name.toLowerCase();
        for (Processor processor : list()) {
            if (lcName.equals(processor.getName())) {
                return Optional.of(processor);
            }
        }
        return Optional.empty();
    }
}
