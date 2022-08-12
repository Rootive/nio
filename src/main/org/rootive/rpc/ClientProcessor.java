package org.rootive.rpc;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;
import java.util.TreeSet;

public class ClientProcessor extends AbstractProcessor {
    private Messager messager;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        messager = env.getMessager();
    }
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (var annotation : annotations) {
            log(annotation);
        }
        log(roundEnv);
        return true;
    }
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> ret = new TreeSet<>();
        ret.add("org.rootive.rpc.Reference");
        return ret;
    }

    private void log(Object obj) {
        messager.printMessage(Diagnostic.Kind.NOTE, obj.toString());
    }
}
