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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

//import com.google.auto.service.AutoService;

/**
 * Process annotations for creating a weak EventBus subscriber.
 */
@SupportedAnnotationTypes({"com.raelity.lib.eventbus.WeakSubscribe",
    "com.raelity.lib.eventbus.WeakAllowConcurrentEvents"
})
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@SuppressWarnings("ObsoleteAnnotationSupportedSource")
public class WeakEventBusProcessor extends AbstractProcessor // implements Processor
{
// For debug output
@SuppressWarnings("UseOfSystemOutOrSystemErr")
private static void P(String fmt, Object... args) {
    @SuppressWarnings("unused")
    String s = args.length == 0 ? fmt : String.format(fmt, args);
    System.out.printf(s);
}

private record MethodInfo(Element method, boolean isSubscribe, boolean isConcurrent){}
// Key is classElement.
private Map<Element, List<MethodInfo>> clazzes = new HashMap<>();

boolean has_error;

@Override
public boolean process(Set<? extends TypeElement> annotations,
                       RoundEnvironment roundEnv)
{
    P("PROCESSOR: %s\n", annotations);
    if (annotations.isEmpty())
        return false;

    // Scan the annotations and build per class method information.
    for (TypeElement annotation : annotations) {
        Set<? extends Element> annotatedElements
                = roundEnv.getElementsAnnotatedWith(annotation);

        P("\nPROCESSOR element %s: %s\n", annotation, annotatedElements);
        for(Element element : annotatedElements) {
            // Process the element's class
            checkClassFor(element);
        }
    }
    if (has_error)
        return true;
    
    // Now build the weak event bus receiver files.
    P("\nGenerating source files\n\n");
    for(Entry<Element, List<MethodInfo>> entry : clazzes.entrySet()) {
        try {
            generateSourceFile(entry);
        } catch(IOException ex) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Error generating builder: " + ex);
        }
    }
    return true;
}

private void generateSourceFile(Entry<Element, List<MethodInfo>> entry)
        throws IOException {
    String pkg = processingEnv.getElementUtils()
            .getPackageOf(entry.getKey()).getQualifiedName().toString();
    //DeclaredType classType = (DeclaredType)classElement.asType();
    String strongClassName = entry.getKey().asType().toString();
    String weakClassName = nameWeakBR(strongClassName, pkg);

    JavaFileObject of = processingEnv.getFiler().createSourceFile(
            pkg + "."+ weakClassName);
    try (PrintWriter out = new PrintWriter(of.openWriter())) {
        // First, the stuff that's common; declares the class and a few methods.
        out.write(classTemplate
                .replace("{WeakBusReceiver}", weakClassName)
                .replace("{StrongBusReceiver}", strongClassName)
                .replace("{package}", pkg));
        // The trampoline methods to the strong/real event bus.
        for(MethodInfo methodInfo : entry.getValue()) {
            ExecutableType methodType = (ExecutableType)methodInfo.method().asType();
            TypeMirror paramType = methodType.getParameterTypes().get(0);
            out.write(methodTemplate
                    .replace("{method}", methodInfo.method().getSimpleName())
                    .replace("{eventType}", paramType.toString())
                    .replace("{allowConcurrent}", methodInfo.isConcurrent
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

private static final String GUAVA_EB = "@com.google.common.eventbus";
private static final String WEAK_SUBSCRIBE = "@com.raelity.lib.eventbus.WeakSubscribe";
private static final String WEAK_CONCURRENT = "@com.raelity.lib.eventbus.WeakAllowConcurrentEvents";

private Element checkClassFor(Element element) {
    TypeElement classElement = (TypeElement)element.getEnclosingElement();
    DeclaredType classType = (DeclaredType)classElement.asType();
    List<MethodInfo> l = clazzes.computeIfAbsent(classElement, k -> new ArrayList<>());
    if (!l.isEmpty())
        return classElement;

    // String pkg = processingEnv.getElementUtils().getPackageOf(element)
    //         .getQualifiedName().toString();
    // P("\nenclKind %s, name %s::%s type %s\n    %s\n",
    //   classType.getKind(), pkg, nameWeakBR(classType.toString(), pkg),
    //   classType.getClass().getSimpleName(), classType);

    // Examine the annotations of each method in this class.
    for(Element el : classElement.getEnclosedElements()) {
        P("ELEM %s %s\n", el, el.getKind());
        if (el.getKind() != ElementKind.METHOD)
            continue;

        // Determine the kinds of EventBus receiver annotions on the method.
        boolean hasGuavaAnnotation = false;
        boolean hasSubscribe = false;
        boolean hasConcurrent = false;
        for(AnnotationMirror am : el.getAnnotationMirrors()) {
            String stringAnno = am.toString();
            if (stringAnno.startsWith(GUAVA_EB)) {
                hasGuavaAnnotation = true;
                break;
            } else if(stringAnno.startsWith(WEAK_SUBSCRIBE))
                hasSubscribe = true;
            else if(stringAnno.startsWith(WEAK_CONCURRENT))
                hasConcurrent = true;

        }

        // This class has WeakEventBus receiver annotions,
        // also having guava EventBus receiver annotations is an error.
        if (hasGuavaAnnotation) {
            has_error = true;
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    String.format("%s can not mix annotations for regualar EventBus with WeakEventBus",
                    classElement));
        }

        P("ANNO %s %s\n", hasGuavaAnnotation, el.getAnnotationMirrors().get(0));

        if (hasSubscribe || hasConcurrent) {
            if (hasSubscribe)
                l.add(new MethodInfo(el, hasSubscribe, hasConcurrent));
            else {
                // Concurrent without subscribe is an error.
                has_error = true;
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        String.format("%s::%s concurrent without subscribe",
                        classElement, element.getSimpleName()));
                
            }
        }
    }

    return classElement;
}

String classTemplate = """
//private static final Cleaner cleaner = Cleaner.create();
package {package};
//import com.raelity.lib.eventbus.WeakEventBus;
//import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.common.eventbus.AllowConcurrentEvents;
import java.lang.ref.WeakReference;
import java.util.function.Consumer;
public class {WeakBusReceiver} {
    private final WeakReference<{StrongBusReceiver}> ref;

    public {WeakBusReceiver}({StrongBusReceiver} realBR)
    {
        this.ref = new WeakReference<>(realBR);
        //WeakEventBus.cleaner.register(realBR, () -> evBus.unregister(this));
    }

    private void doit(Consumer<{StrongBusReceiver}> doit)
    {
        {StrongBusReceiver} br = ref.get();
        if(br != null)
            doit.accept(br);
    }
""";
// "{method}" --> "methodName(RowSetModificationEvent ev)"
String methodTemplate = """

    @Subscribe{allowConcurrent}
    public void {method}({eventType} ev)
    {
        doit((br) -> br.{method}(ev));
    }
""";

/*

This is from a previous version of checkClassFor

            //boolean hasGuavaAnnotation = el.getAnnotationMirrors().stream()
            //        .filter(a -> a.toString().startsWith(GUAVA_EB))
            //        .findFirst()
            //        .isPresent();



This is the "first" implementation.
Mostly careful about types...
Replaced by examining all the methods in the class,
checking for anomolies, recording which annotations.

@Override
public boolean process(Set<? extends TypeElement> annotations,
                       RoundEnvironment roundEnv)
{
    P("PROCESSOR: %s\n", annotations);
    if (annotations.isEmpty())
        return false;

    TypeElement allowConcurrentElem = annotations.stream()
            .filter(a -> a.toString().contains("Concurrent")).findFirst()
            .orElse(null);
    // TypeMirror allowConcurrentType = annotations.stream()
    //         .map(a -> a.asType())
    //         .filter(a -> a.toString().contains("Concurrent")).findFirst()
    //         .orElse(null);
    // P("    CONCUR TYPE: %s", allowConcurrentType);

    // Scan the annotations and build per class method information.
    for (TypeElement annotation : annotations) {
        Set<? extends Element> annotatedElements
                = roundEnv.getElementsAnnotatedWith(annotation);
        // Skip AllowConcurrentEvents, handled manually
        if(annotation.equals(allowConcurrentElem))
            continue;
        //if(annotation.asType().equals(allowConcurrentType))
        //    continue;

// Map<Boolean, List<Element>> annotatedMethods = annotatedElements.stream().collect(
//   Collectors.partitioningBy(element ->
//     ((ExecutableType) element.asType()).getParameterTypes().size() == 1
//     && element.getSimpleName().toString().startsWith("set")));
// 
// List<Element> setters = annotatedMethods.get(true);
// List<Element> otherMethods = annotatedMethods.get(false);

        P("\nPROCESSOR element %s: %s\n", annotation, annotatedElements);
        for(Element element : annotatedElements) {
            // Get the class that has this method.
            //DeclaredType classType = (DeclaredType)element.getEnclosingElement().asType();
            //if (!handledClasses.contains(classType)) {
            //    createClassFor(classType);
            //    handledClasses.add(classType);
            //}

            Element classElement = checkClassFor(element);

            ExecutableType methodType = (ExecutableType)element.asType();
            List<? extends TypeMirror> pt = methodType.getParameterTypes();
            if (pt.size() != 1) {
                has_error = true;
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        String.format("%s::%s Subscriber takes exactly one parameter",
                                      classElement.toString(), element.getSimpleName()));
            } else if (pt.get(0).getKind() != TypeKind.DECLARED) {
                has_error = true;
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        String.format("%s::%s Subscribe parameter is not an object",
                                      classElement.toString(), element.getSimpleName()));
            }
            P("el %s, elKind: %s, type %s\n",
              element, element.getKind(), methodType);
            P("    name %s::%s, argType: %s, kind %s\n",
              classElement.toString(), element.getSimpleName(),
              pt.get(0).toString(), pt.get(0).getKind());
            P("    all annotations %s\n", element.getAnnotationMirrors());
            boolean isConcurrent = element.getAnnotationMirrors().stream()
                    .filter(a -> a.getAnnotationType()
                            .equals(allowConcurrentElem.asType()))
                    .findFirst()
                    .isPresent();
            P("    concurant %s\n", isConcurrent);
            //AnnotationMirror am = element.getAnnotationMirrors().get(0);
            //P( "    conc mirror %s, am %s\n", allowConcurrentElem.asType(), am);
            //boolean isConcurrent = false;


            //clazzes.get(classElement).add(new MethodInfo(element, isConcurrent));


        }
    }
    if (has_error)
        return true;
    
    // Now build the weak event bus files.
    P("\nGenerating source files\n\n");
    for(Entry<Element, List<MethodInfo>> entry : clazzes.entrySet()) {
        try {
            // String pkg = processingEnv.getElementUtils()
            //         .getPackageOf(entry.getKey()).getQualifiedName().toString();
            // //DeclaredType classType = (DeclaredType)classElement.asType();
            // String className = entry.getKey().asType().toString();
            generateSourceFile(entry);
        } catch(IOException ex) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Error generating builder: " + ex);
        }
    }
    return true;
}
*/

}