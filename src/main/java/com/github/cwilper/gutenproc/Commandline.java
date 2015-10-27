package com.github.cwilper.gutenproc;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Commandline
{
    private final CommandLine cmd;

    Commandline(CommandLine cmd) {
        this.cmd = cmd;
    }

    public List<String> getArguments() {
        return cmd.getArgList();
    }

    public List<Option> getOptions() {
        return Arrays.asList(cmd.getOptions());
    }

    public boolean hasOption(String opt) {
        return cmd.hasOption(opt);
    }

    public Optional<String> getOptionValue(String opt) {
        if (cmd.hasOption(opt)) {
            return Optional.of(cmd.getOptionValue(opt));
        }
        return Optional.empty();
    }

    public List<String> getOptionValues(String opt) {
        List<String> values = Lists.newArrayList();
        if (cmd.hasOption(opt)) {
            String[] oValues = cmd.getOptionValues(opt);
            values.addAll(Arrays.asList(oValues));
        }
        return values;
    }

    public String getOptionValue(String opt, String defaultValue) {
        if (cmd.hasOption(opt)) {
            return getOptionValue(opt).get();
        }
        return defaultValue;
    }

    public Optional<Integer> getOptionIntValue(String opt) {
        if (cmd.hasOption(opt)) {
            try {
                return Optional.of(Integer.parseInt(cmd.getOptionValue(opt)));
            } catch (NumberFormatException e) {
                throw Throwables.propagate(e);
            }
        }
        return Optional.empty();
    }

    public int getOptionIntValue(String opt, int defaultValue) {
        if (cmd.hasOption(opt)) {
            return getOptionIntValue(opt).get();
        }
        return defaultValue;
    }

    public Optional<Long> getOptionLongValue(String opt) {
        if (cmd.hasOption(opt)) {
            try {
                return Optional.of(Long.parseLong(cmd.getOptionValue(opt)));
            } catch (NumberFormatException e) {
                throw Throwables.propagate(e);
            }
        }
        return Optional.empty();
    }

    public long getOptionLongValue(String opt, long defaultValue) {
        if (cmd.hasOption(opt)) {
            return getOptionLongValue(opt).get();
        }
        return defaultValue;
    }
}
