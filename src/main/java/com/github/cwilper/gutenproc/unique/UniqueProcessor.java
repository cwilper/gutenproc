package com.github.cwilper.gutenproc.unique;

import com.github.cwilper.gutenproc.BaseProcessor;
import com.github.cwilper.gutenproc.Book;
import com.github.cwilper.gutenproc.Commandline;
import com.github.cwilper.gutenproc.Field;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class UniqueProcessor extends BaseProcessor
{
    private final Map<Field, Map<String, Integer>> fieldMap = Maps.newHashMap();

    private final List<Field> fields = Lists.newArrayList();

    private boolean showCounts;

    private int showTop = Integer.MAX_VALUE;

    @Override
    public String getSynopsis() {
        return "Prints unique metadata values";
    }

    @Override
    public void addOptions(final Options options) {
        super.addOptions(options);
        options.addOption(Option.builder("f")
                .longOpt("field")
                .desc("Show unique values for a given field")
                .hasArg()
                .build());
        options.addOption(Option.builder("c")
                .longOpt("show-counts")
                .desc("Show counts for each unique field value (only applicable if -f is specified). This also has the effect of ordering the unique values by frequency rather than alphabetically.")
                .build());
        options.addOption(Option.builder("t")
                .longOpt("show-top")
                .desc("Limit the number of unique values shown")
                .hasArg()
                .build());
    }

    @Override
    public void begin(final Commandline cmd) {
        super.begin(cmd);
        if (cmd.hasOption("f")) {
            for (String value : cmd.getOptionValues("f")) {
                fields.add(Field.forString(value));
            }
        }
        if (cmd.hasOption("c")) {
            showCounts = true;
        }
        if (cmd.hasOption("t")) {
            showTop = cmd.getOptionIntValue("t").get();
        }
    }

    @Override
    public void accept(final Book book) {
        for (Field field : book.fields()) {
            Map<String, Integer> valueMap = fieldMap.get(field);
            if (valueMap == null) {
                valueMap = Maps.newHashMap();
                fieldMap.put(field, valueMap);
            }
            for (String value : book.get(field).get()) {
                Integer count = valueMap.get(value);
                if (count == null) {
                    count = 0;
                }
                valueMap.put(value, count + 1);
            }
        }
    }

    @Override
    public void end() {
        if (fields.isEmpty()) {
            for (Map.Entry<Field, Map<String, Integer>> entry : fieldMap.entrySet()) {
                System.out.println(entry.getKey().label() + " values: " + entry.getValue().size());
            }
        } else if (fields.size() == 1) {
            printValues(fieldMap.get(fields.get(0)));
        } else {
            for (Field field : fields) {
                System.out.println(field.label() + " values:");
                printValues(fieldMap.get(field));
                System.out.println();
            }
        }
    }

    private void printValues(final Map<String, Integer> valueMap) {
        if (valueMap == null) return;
        int i = 0;
        for (Map.Entry<String, Integer> entry : sortedEntryList(valueMap)) {
            if (showCounts) {
                System.out.print(entry.getValue() + ": ");
            }
            System.out.println(entry.getKey());
            i++;
            if (i > showTop) {
                break;
            }
        }
    }

    private List<Map.Entry<String, Integer>> sortedEntryList(Map<String, Integer> map) {
        List<Map.Entry<String, Integer>> list = Lists.newArrayList(map.entrySet());
        Collections.sort(list, (o1, o2) -> {
            if (showCounts) {
                return o2.getValue().compareTo(o1.getValue());
            } else {
                return o1.getKey().compareTo(o2.getKey());
            }
        });
        return list;
    }
}
