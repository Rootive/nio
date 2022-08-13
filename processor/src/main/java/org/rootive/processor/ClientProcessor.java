package org.rootive.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Set;
import java.util.TreeSet;
import org.rootive.annotation.Reference;

public class ClientProcessor extends AbstractProcessor {

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
    }
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var es = roundEnv.getElementsAnnotatedWith(Reference.class);

        return true;
    }
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> ret = new TreeSet<>();
        ret.add("org.rootive.annotation.Reference");
        return ret;
    }

    private void log(Object obj) {
        try {
            OutputStream o = new FileOutputStream("C:\\Users\\4t42xks88g\\Desktop\\out.txt", true);
            o.write(obj.toString().getBytes());
            o.write('\n');
            o.flush();
            o.close();
        } catch (IOException e) {
            e.printStackTrace();
            assert true;
        }
    }
}
