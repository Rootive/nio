package org.rootive.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.function.Supplier;

public class StorageList<T> {
    private ArrayList<T> v;
    private final String path;

    public StorageList(String path) {
        this.path = path;
    }

    public void init(Class<T> cls) throws IOException {
        var in = new FileInputStream(path);
        var data = in.readAllBytes();
        in.close();

        var mapper = new ObjectMapper();
        v = mapper.readValue(data, mapper.getTypeFactory().constructParametricType(ArrayList.class, cls));
    }
    public void init(ArrayList<T> v) throws IOException {
        set(v);
    }
    public void init(Class<T> cls, Supplier<ArrayList<T>> s) throws IOException {
        if (new File(path).isFile()) {
            init(cls);
        } else {
            init(s.get());
        }
    }

    public ArrayList<T> get() {
        assert v != null;
        return v;
    }
    public void set() throws IOException {
        set(v);
    }
    public void set(ArrayList<T> v) throws IOException {
        var out = new FileOutputStream(path);
        new ObjectMapper().writeValue(out, v);
        this.v = v;
        out.close();
    }
}
