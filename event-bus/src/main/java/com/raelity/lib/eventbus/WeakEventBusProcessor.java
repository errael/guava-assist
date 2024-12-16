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
 * The Original Code is jvi - vi editor clone.
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
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

//import com.google.auto.service.AutoService;

/**
 */
// TODO: don't have WeakEventBus, it can be inferred.
// @SupportedAnnotationTypes({"com.raelity.lib.eventbus.WeakSubscribe",
//     "com.raelity.lib.eventbus.WeakAllowConcurrentEvents"
//     //     ,
//     // "com.raelity.lib.eventbus.WeakEventBus"
// })
//@AutoService(Processor.class)
@SupportedAnnotationTypes("com.raelity.lib.eventbus.WeakSubscribe")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
public class WeakEventBusProcessor extends AbstractProcessor // implements Processor
{
// List of methods in order
private record ClassStuff(List<Element> methods){}
// Key is classElement.
private Map<Element, ClassStuff> clazzes = new HashMap<>();

@Override
public boolean process(Set<? extends TypeElement> annotations,
                       RoundEnvironment roundEnv)
{
    boolean has_error = false;
    System.out.println("PROCESSOR-1: " + annotations);
    if (annotations.isEmpty())
        return false;

    // Scan the annotations and build per class method information.
    for (TypeElement annotation : annotations) {
        Set<? extends Element> annotatedElements
                = roundEnv.getElementsAnnotatedWith(annotation);

// Map<Boolean, List<Element>> annotatedMethods = annotatedElements.stream().collect(
//   Collectors.partitioningBy(element ->
//     ((ExecutableType) element.asType()).getParameterTypes().size() == 1
//     && element.getSimpleName().toString().startsWith("set")));
// 
// List<Element> setters = annotatedMethods.get(true);
// List<Element> otherMethods = annotatedMethods.get(false);

        System.err.println("PROCESSOR element: " + annotatedElements);
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
                // has_error = true;
                // processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                //         String.format("%s::%s parameter is not an object",
                //                       classElement.toString(), element.getSimpleName()));
            }
            System.err.printf("el %s, elKind: %s, type %s\n",
                              element, element.getKind(), methodType);
            System.err.printf("name %s::%s, argType: %s, kind %s\n",
                              classElement.toString(), element.getSimpleName(),
                              pt.get(0).toString(), pt.get(0).getKind());
            clazzes.get(classElement).methods().add(element);
            //stuff.methods().add(element);
        }
        for(Entry<Element, ClassStuff> entry : clazzes.entrySet()) {
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

        // Now build the weak event bus classes.
        
        //System.err.println("PROCESSOR: "+annotatedElements);
    }
    return true;
}

private void generateSourceFile(Entry<Element, ClassStuff> entry)
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
        for(Element method : entry.getValue().methods()) {
            ExecutableType methodType = (ExecutableType)method.asType();
            TypeMirror paramType = methodType.getParameterTypes().get(0);
            out.write(methodTemplate
                    .replace("{method}", method.getSimpleName())
                    .replace("{eventType}", paramType.toString())
                    .replace("{allowConcurrent}", ""));
        }
        out.write("}\n");
    }
}

public static String nameWeakBR(String strongEBName, String pkg)
{
    // StringBuilder sb = new StringBuilder(strongEBName.replaceAll("[\\.\\$]", "_"));
    if (!strongEBName.startsWith(pkg))
        throw new IllegalArgumentException("EBName must start with package name");
    String n = "WeakEB_" + strongEBName.substring(pkg.length()+1).replaceAll("[\\.\\$]", "_");
    System.err.printf("WeakEventBus::register: %s::%s\n", pkg, n);
    return n;
}

private static final String GUAVA_EB = "com.google.common.eventbus";

//private void createClass(DeclaredType classType) {
private Element checkClassFor(Element element) {
    // TODO: check for guava eventbus annotations; if any then error.
    Element classElement = element.getEnclosingElement();
    DeclaredType classType = (DeclaredType)classElement.asType();
    if (clazzes.containsKey(classElement))
        return classElement;

    String pkg = processingEnv.getElementUtils().getPackageOf(element)
            .getQualifiedName().toString();
    clazzes.put(classElement, new ClassStuff(new ArrayList<>()));
    System.err.printf("el %s, enclKind %s, name %s::%s classType:\n    %s\n    %s\n",
                      element, classType.getKind(),
                      pkg, nameWeakBR(classType.toString(), pkg),
                      classType.getClass().getName(), classType);

    return classElement;

    // JavaFileObject of = null;
    // try {
    //     of = processingEnv.getFiler().createSourceFile("com.raelity.lib.Foo");
    //     try (PrintWriter out = new PrintWriter(of.openWriter())) {
    //         //out.println("random garbage PROCESSOR-1: " + annotations);
    //     }
    // } catch(IOException ex) {
    //     ex.printStackTrace();
    // }
    // return classType;
}

private void createMethod(TypeElement classElement) {
}

//public static void main(String[] args) {
//    System.out.println("Hello World!");
//}

String classTemplate = """
//private static final Cleaner cleaner = Cleaner.create();
package {package};
//import com.raelity.lib.eventbus.WeakEventBus;
import com.google.common.eventbus.EventBus;
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

    @Subscribe {allowConcurrent}
    public void {method}({eventType} ev)
    {
        doit((br) -> br.{method}(ev));
    }
""";
}