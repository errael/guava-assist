/*
 * Portions created by Ernie Rael are
 * Copyright (C) 2024 Ernie Rael.  All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * Contributor(s): Ernie Rael <errael@raelity.com>
 */

package com.raelity.lib.eventbus;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.openide.util.lookup.ServiceProvider;

/**
 * Process annotations for creating a weak EventBus subscriber.
 */
@SupportedAnnotationTypes({"com.raelity.lib.eventbus.WeakSubscribe",
    "com.raelity.lib.eventbus.WeakAllowConcurrentEvents"})
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@SuppressWarnings("ObsoleteAnnotationSupportedSource")
@ServiceProvider(service=Processor.class)
public class WeakEventBusProcessor extends AbstractProcessor
{
// For debug output
@SuppressWarnings({"UseOfSystemOutOrSystemErr", "unused"})
private static void P(@SuppressWarnings("unused") String fmt,
                      @SuppressWarnings("unused") Object... args) {
    String s = args.length == 0 ? fmt : String.format(fmt, args);
    System.out.printf(s);
}
private enum Annotation { SUBSCRIBE, CONCURRENT }
private static final String WEAK_SUBSCRIBE = "com.raelity.lib.eventbus.WeakSubscribe";
private static final String WEAK_CONCURRENT = "com.raelity.lib.eventbus.WeakAllowConcurrentEvents";

private Map<TypeElement, Map<ExecutableElement, Set<Annotation>>> classesAndMethods = new HashMap<>();

@Override
public boolean process(Set<? extends TypeElement> annotations,
                       RoundEnvironment roundEnv)
{
    //P("PROCESSOR: %s\n", annotations);
    if (annotations.isEmpty())
        return false;

    // Scan the annotations and collect per class methods
    for (TypeElement annotation : annotations) {
        Set<? extends Element> annotatedElements
                = roundEnv.getElementsAnnotatedWith(annotation);
        String annotName = annotation.toString();
        Annotation annot = annotName.equals(WEAK_SUBSCRIBE) ? Annotation.SUBSCRIBE
                           :annotName.equals(WEAK_CONCURRENT) ? Annotation.CONCURRENT 
                            : null; // null impossible (at least for now)

        //P("\nPROCESSOR element %s, %s: %s\n", annotation, annot, annotatedElements);
        for (Element element : annotatedElements) {
            // The annotated methods for this annotatations class.
            Map<ExecutableElement, Set<Annotation>> methods
                    = classesAndMethods.computeIfAbsent(
                            (TypeElement)element.getEnclosingElement(),
                            k -> new HashMap<>());
            // Add current annotation to the method
            methods.computeIfAbsent((ExecutableElement)element,
                                    k -> EnumSet.noneOf(Annotation.class))
                    .add(annot);
            //P("Method %s: %s\n", element, methods.get((ExecutableElement)element));
        }
    }

    // Check method's signature and annotation usage.
    for(Entry<TypeElement, Map<ExecutableElement, Set<Annotation>>> classMeths
            : classesAndMethods.entrySet()) {
        TypeElement classElement = classMeths.getKey();
        for(Entry<ExecutableElement, Set<Annotation>> methAnnos
                : classMeths.getValue().entrySet()) {
            ExecutableElement methodElement = methAnnos.getKey();
            Set<Annotation> annos= methAnnos.getValue();

            // Check method parameter.
            ExecutableType methodType = (ExecutableType)methodElement.asType();
            List<? extends TypeMirror> pt = methodType.getParameterTypes();
            if (pt.size() != 1)
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        String.format("%s::%s @Subscribe method exactly one parameter",
                        classElement.toString(), methodElement.getSimpleName()));
            else if (pt.get(0).getKind().isPrimitive())
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        String.format("%s::%s @Subscribe parameter is not an object",
                        classElement.toString(), methodElement.getSimpleName()));
            if(annos.contains(Annotation.CONCURRENT)
                    && !annos.contains(Annotation.SUBSCRIBE))
                processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                        String.format("%s::%s @AllowConcurrentEvents without @Subscribe",
                        classElement, methodElement.getSimpleName()));
            if (annos.contains(Annotation.SUBSCRIBE)
                    && methodElement.getModifiers().contains(Modifier.PRIVATE))
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        String.format("%s::%s @WeakSubscriber has private access",
                        classElement, methodElement.getSimpleName()));
        }
    }

    // Now build the weak event bus receiver files (even if there were errors).
    //P("\nGenerating source files\n\n");
    for(Entry<TypeElement, Map<ExecutableElement, Set<Annotation>>> classMeths
            : classesAndMethods.entrySet()) {
        try {
            generateSourceFile(classMeths.getKey(), classMeths.getValue());
        } catch(IOException ex) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Error generating builder: " + ex.getMessage());
            break;
        }
    }
    return true;
}

private void generateSourceFile(TypeElement classElement,
                                Map<ExecutableElement, Set<Annotation>> methAnnos)
        throws IOException
{
    String pkg = processingEnv.getElementUtils()
            .getPackageOf(classElement).getQualifiedName().toString();
    String strongClassName = classElement.asType().toString();
    String weakClassName = nameWeakBR(strongClassName, pkg);

    JavaFileObject of = processingEnv.getFiler().createSourceFile(
            pkg + "."+ weakClassName);
    try (PrintWriter out = new PrintWriter(of.openWriter())) {
        // First, the stuff that's common; declares the weak receiver and a few methods.
        out.write(classTemplate
                .replace("{WeakBusReceiver}", weakClassName)
                .replace("{StrongBusReceiver}", strongClassName)
                .replace("{package}", pkg));
        // The trampoline methods to the strong/real event bus.
        for(Entry<ExecutableElement, Set<Annotation>> entry : methAnnos.entrySet()) {
            ExecutableElement method = entry.getKey();
            Set<Annotation> annos= entry.getValue();
            if (!annos.contains(Annotation.SUBSCRIBE))
                continue;

            ExecutableType methodType = (ExecutableType)method.asType();
            TypeMirror paramType = methodType.getParameterTypes().get(0);
            out.write(methodTemplate
                    .replace("{method}", method.getSimpleName())
                    .replace("{eventType}", paramType.toString())
                    .replace("{subscribe}", annos.contains(Annotation.SUBSCRIBE)
                                              ? "\n    @Subscribe" : "")
                    .replace("{allowConcurrent}", annos.contains(Annotation.CONCURRENT)
                                              ? "\n    @AllowConcurrentEvents" : ""));
        }
        out.write("}\n");
    }
}

public static String nameWeakBR(String strongEBName, String pkg)
{
    if (!strongEBName.startsWith(pkg))
        throw new IllegalArgumentException("EBName must start with package name");
    String n = "WeakEB_" + strongEBName.substring(pkg.length()+1).replaceAll("[\\.\\$]", "_");
    // P("WeakEventBus::register: %s::%s\n", pkg, n);
    return n;
}

String classTemplate = """
package {package};
import com.google.common.eventbus.Subscribe;
import com.google.common.eventbus.AllowConcurrentEvents;
import java.lang.ref.WeakReference;
import java.util.function.Consumer;
public class {WeakBusReceiver} {
    private final WeakReference<{StrongBusReceiver}> ref;

    public {WeakBusReceiver}({StrongBusReceiver} realBR)
    {
        this.ref = new WeakReference<>(realBR);
    }

    private void doit(Consumer<{StrongBusReceiver}> doit)
    {
        {StrongBusReceiver} br = ref.get();
        if(br != null)
            doit.accept(br);
    }
""";

String methodTemplate = """
    {subscribe}{allowConcurrent}
    public void {method}({eventType} ev)
    {
        doit((br) -> br.{method}(ev));
    }
""";
}