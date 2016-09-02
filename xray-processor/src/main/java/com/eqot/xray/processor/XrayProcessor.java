package com.eqot.xray.processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

@AutoService(Processor.class)
@SupportedAnnotationTypes("com.eqot.xray.processor.Xray")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class XrayProcessor extends AbstractProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Xray.class)) {
            processElement(element);
        }

        return true;
    }

    private void processElement(Element element) {
        final Xray xray = element.getAnnotation(Xray.class);
        if (xray == null) {
            return;
        }

        String className = "";
        try {
            xray.value();
        } catch (MirroredTypeException mte) {
            className = mte.getTypeMirror().toString();
        }
        if (className.equals("") || className.equals("java.lang.Object")) {
            return;
        }

        generateCode(className);
    }

    private void generateCode(String target) {
        final ClassDef classDef = new ClassDef(target);

        final ClassName targetClass = ClassName.get(classDef.packageName, classDef.className);
        final ClassName generatedClass = ClassName.get(
                classDef.packageName, classDef.className + "$Fiberscope");

        final TypeSpec.Builder classBuilder = TypeSpec.classBuilder(generatedClass.simpleName())
                .addModifiers(Modifier.PUBLIC);

        classBuilder
                .addField(targetClass, "mInstance", Modifier.PRIVATE, Modifier.FINAL);

        final MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addStatement("mInstance = new $T()", targetClass);

        for (ClassDef.MethodDef methodDef : classDef.methods) {
            String combinedMethodName = "_" + methodDef.name;
            String argTypes = "";
            String argNames = "";
            for (ClassDef.ParameterDef parameterDef : methodDef.parameters) {
                combinedMethodName += "_" + parameterDef.type.getName();

                if (!argTypes.equals("")) {
                    argTypes += ", ";
                    argNames += ", ";
                }
                argTypes += parameterDef.type.getName() + ".class";
                argNames += parameterDef.name;
            }

            constructorBuilder
                    .beginControlFlow("try")
                    .addStatement("$N = $T.class.getDeclaredMethod($S, $N)",
                            combinedMethodName, targetClass, methodDef.name, argTypes)
                    .addStatement("$N.setAccessible(true)", combinedMethodName)
                    .endControlFlow("catch (Exception e) {}");

            MethodSpec.Builder methodSpecBuilder = MethodSpec.methodBuilder(methodDef.name)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(methodDef.returnType);

            for (ClassDef.ParameterDef parameterDef : methodDef.parameters) {
                methodSpecBuilder
                        .addParameter(parameterDef.type, parameterDef.name);
            }

            methodSpecBuilder
                    .addStatement("$N result = 0", methodDef.returnType.getName())
                    .beginControlFlow("try")
                    .addStatement("result = ($N) $N.invoke(mInstance, $N)",
                            methodDef.returnType.getName(), combinedMethodName, argNames)
                    .endControlFlow("catch (Exception e) {}")
                    .addStatement("return result")
                    .build();

            classBuilder
                    .addField(Method.class, combinedMethodName, Modifier.PRIVATE)
                    .addMethod(methodSpecBuilder.build());
        }

        classBuilder
                .addMethod(constructorBuilder.build());

        try {
            JavaFileObject source = processingEnv.getFiler().createSourceFile(generatedClass.toString());
            Writer writer = source.openWriter();

            JavaFile.builder(classDef.packageName, classBuilder.build())
                    .build()
                    .writeTo(writer);
//                    .writeTo(System.out);

            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void log(String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
    }
}
