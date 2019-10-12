package io.dreamz.lunar.bootstrap.cli;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.function.Function;

public final class Commander {
    private LinkedList<String> arguments;
    private FlagReader flagReader;


    private Map<String, String> defaults = new HashMap<>();
    private Map<Class<?>, Function<String, ?>> transformers = new HashMap<>();

    {
        // Files
        transformers.put(File.class, File::new);


        // Long
        transformers.put(Long.class, Long::valueOf);
        transformers.put(Long.TYPE, Long::valueOf);

        // Integers
        transformers.put(Integer.TYPE, Integer::valueOf);
        transformers.put(Integer.class, Integer::valueOf);

        // Double
        transformers.put(Double.TYPE, Double::valueOf);
        transformers.put(Double.class, Double::valueOf);

        transformers.put(String.class, (s) -> s);
    }

    public Commander(String[] arguments) {
        this.arguments = new LinkedList<>(Arrays.asList(arguments));
    }


    public <T> Commander withTransformer(Class<? extends T> clazz, Function<String, ? extends T> transformer) {
        this.transformers.put(clazz, transformer);
        return this;
    }


    public Commander withDefault(String flag, String value) {
        this.defaults.put(flag, value);
        return this;
    }


    public void execute(Class<?> template) throws IllegalAccessException, InstantiationException {
        Object instance = template.newInstance();

        for (Method m : template.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Command.class)) {
                m.setAccessible(true);
                if (flagReader.hasFlag(m.getName())) {
                    List<Object> parameters = new ArrayList<>();

                    for (Parameter parameter : m.getParameters()) {
                        if (parameter.isAnnotationPresent(Flag.class) || parameter.isNamePresent()) {
                            String key = (parameter.isAnnotationPresent(Flag.class) ?
                                    parameter.getAnnotation(Flag.class).value() :
                                    (parameter.isNamePresent()) ? parameter.getName() : "");

                            if (!key.isEmpty() && flagReader.hasFlag(key)) {
                                parameters.add(this.transformers.get(parameter.getType()).apply(flagReader.read(key)));
                            }
                        }
                    }

                    try {
                        m.invoke(instance, parameters.toArray());
                    } catch (InvocationTargetException ignored) {
                    }
                }
            }
        }
    }


    public String getValue(String flag) {
        return flagReader.read(flag);
    }


    public boolean hasFlag(String flag) {
        return flagReader.hasFlag(flag);
    }

    public void parse() {
        this.flagReader = this.readFlags();
    }

    private FlagReader readFlags() {

        Map<String, String> flags = new HashMap<>(defaults);

        for (int i = 0; i < arguments.size(); i++) {
            String s = arguments.get(i);

            if (s.charAt(0) == '-') {
                s = s.substring((s.lastIndexOf('-')) + 1);

                String[] x;
                if ((x = s.split("=")).length == 2) {
                    flags.put(x[0].toLowerCase(), x[1].trim());
                } else {
                    if (i <= arguments.size() - 1) {
                        flags.put(s, arguments.get(i + 1).trim());
                    }
                    // ignore the flag otherwise. no value
                }
            }
        }

        return new FlagReader(flags);
    }


}
