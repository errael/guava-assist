package com.raelity.play.event.bus.user;

import javax.annotation.processing.Processor;
import java.util.ServiceLoader;

public class ListProcessors {
    @SuppressWarnings("UseOfSystemOutOrSystemErr")
    public static void main(String[] args) {
        ServiceLoader<Processor> loader = ServiceLoader.load(Processor.class);
        System.out.println("loader: " + loader);
        for (Processor processor : loader) {
            System.out.println(processor.getClass().getName());
        }
    }
}
