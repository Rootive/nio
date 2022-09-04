package org.rootive.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.util.function.Supplier;

public class Storage<T> {
    private T v;
    private final String path;

    public Storage(String path) {
        this.path = path;
    }

    public void init() throws IOException {
        var in = new FileInputStream(path);
        var data = in.readAllBytes();
        in.close();

        v = (T) new ObjectMapper().readValue(data, v.getClass());
    }
    public void init(T v) throws IOException {
        set(v);
    }
    public void init(Supplier<T> s) throws IOException {
        if (new File(path).isFile()) {
            init();
        } else {
            init(s.get());
        }
    }

    public T get() {
        return v;
    }
    public void set() throws IOException {
        set(v);
    }
    public void set(T v) throws IOException {
        new ObjectMapper().writeValue(new FileOutputStream(path), v);
        this.v = v;
    }
}
