package io.dreamz.lunar.bootstrap.cli;

import java.util.Map;

public final class FlagReader {
    private Map<String, String> flags;

    public FlagReader(Map<String, String> flags) {
        this.flags = flags;
    }

    public String read(String flag) {
        return this.flags.get(flag);
    }

    public boolean hasFlag(String flag) {
        return this.flags.containsKey(flag);
    }
}
