package com.besmartmobile.vmfactory.processor;


import com.besmartmobile.vmfactory.annotations.VmFactory;
import com.besmartmobile.vmfactory.annotations.VmProviderPackage;
import com.google.auto.service.AutoService;
import com.google.common.collect.Sets;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;


import static javax.lang.model.SourceVersion.latestSupported;

@AutoService(Processor.class)
public class VmFactoryProcessor  extends AbstractProcessor {

    private static final String METHOD_PREFIX_GET = "get";
    private static final String PARAM_NAME_FRAGMENT = "fragment";
    private static final String PARAM_NAME_FRAGMENT_ACTIVITY = "fragmentActivity";
    private static final String CLASS_NAME_VM_PROVIDER = "VmProvider";
    private static final String FACTORY_CREATE_METHOD_NAME = "create";
    private static final ClassName supportFragmentClass = ClassName.get("android.support.v4.app", "Fragment");
    private static final ClassName supportFragmentActivityClass = ClassName.get("android.support.v4.app", "FragmentActivity");
    private static final ClassName viewModelClass = ClassName.get("android.arch.lifecycle", "ViewModel");
    private static final ClassName viewModelProvidersClass = ClassName.get("android.arch.lifecycle", "ViewModelProviders");
    private static final ClassName viewModelProviderNewInstanceFactoryClass = ClassName.get("android.arch.lifecycle", "ViewModelProvider", "NewInstanceFactory");
    private final List<MethodSpec> getMethodSpecs = new ArrayList<>();
    private String vmProviderPackage;
    private int round = -1;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
    }

    @Override
    public Set getSupportedAnnotationTypes() {
        return Sets.newHashSet(VmFactory.class.getCanonicalName(),
                VmProviderPackage.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        round++;

        if (round == 0) {
            EnvironmentUtil.init(processingEnv);
            if (!processVmProviderPackage(roundEnvironment)) {
                return false;
            }
        }

        if (!processAnnotations(roundEnvironment)) {
            return false;
        }

        if (roundEnvironment.processingOver()) {
            try {
                createVmProvider();
                return true;
            } catch (Exception ex) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, ex.toString());
            }
        }

        return false;
    }

    private boolean processAnnotations(RoundEnvironment roundEnv) {
        return processViewModels(roundEnv);
    }

    private boolean processVmProviderPackage(RoundEnvironment roundEnv) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING,
                "processVmProviderPackage");
        final Set<? extends Element> elements = roundEnv
                .getElementsAnnotatedWith(VmProviderPackage.class);

        if (elements == null || elements.size() != 1) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Should have single package annotated with @VmProviderPackage");
            return false;
        }

        Element element = elements.iterator().next();

        if (element.getKind() != ElementKind.PACKAGE) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "Only package annotated with @VmProviderPackage, not " + element.getKind());
            return false;
        }


        Name packageName = ((PackageElement) element).getQualifiedName();

        this.vmProviderPackage = packageName.toString();

        return true;
    }

    private boolean processViewModels(RoundEnvironment roundEnv) {
        final Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(VmFactory.class);

        if (elements == null || elements.isEmpty()) {
            return true;
        }

        for (Element element : elements) {
            if (element.getKind() != ElementKind.CLASS) {
                EnvironmentUtil.logError("@VmFactory can only be used for classes.");
                return false;
            }

            if (!generateGetMethodAndFactory((TypeElement) element)) {
                return false;
            }
        }

        return true;
    }

    private boolean generateGetMethodAndFactory(TypeElement element) {
        final TypeMirror viewModelTypeMirror = element.asType();
        final ClassName viewModelTypeName = (ClassName) ClassName.get(viewModelTypeMirror);

        List<ExecutableElement> constructors = ElementFilter.constructorsIn(element.getEnclosedElements());
        if (constructors.size() != 1) {
            EnvironmentUtil.logError("ViewModel " + element.getSimpleName() + " should have only one constructor.");
            return false;
        }

        ExecutableElement constructor = constructors.get(0);
        List<? extends VariableElement> parameters = constructor.getParameters();

        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "" + viewModelTypeName);
        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "" + viewModelTypeName.packageName());

        ArrayList<ParameterSpec> parameterSpecs = new ArrayList<>();
        for (VariableElement parameter : parameters) {
            TypeName parameterTypeName = ClassName.get(parameter.asType());
            Name parameterName = parameter.getSimpleName();

            processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, parameterName + " " + parameterTypeName.toString());
            ParameterSpec parameterSpec = ParameterSpec.builder(parameterTypeName, parameterName.toString(), Modifier.FINAL)
                    .build();
            parameterSpecs.add(parameterSpec);
        }
        String parametersCallBlock = getParametersCallBlock(parameterSpecs);




        String factorySimpleName = viewModelTypeName.simpleName() + "$$" + "Factory";
        ClassName factoryClassName = ClassName.get(viewModelTypeName.packageName(), factorySimpleName);
        final TypeSpec.Builder factoryBuilder = TypeSpec.classBuilder(factoryClassName);
        factoryBuilder.addModifiers(Modifier.PUBLIC, Modifier.FINAL);


        ArrayList<FieldSpec> fieldSpecs = new ArrayList<>();
        for (ParameterSpec parameterSpec : parameterSpecs) {
            FieldSpec fieldSpec = FieldSpec
                    .builder(parameterSpec.type, parameterSpec.name, Modifier.FINAL, Modifier.PRIVATE)
                    .build();
            fieldSpecs.add(fieldSpec);
        }

        factoryBuilder.addFields(fieldSpecs);


        MethodSpec.Builder factoryConstructorMethodSpecBuilder = MethodSpec.constructorBuilder()
                .addParameters(parameterSpecs)
                .addModifiers(Modifier.PUBLIC);

        for (int i = 0; i < parameterSpecs.size(); i++) {
            ParameterSpec parameterSpec = parameterSpecs.get(i);
            FieldSpec fieldSpec = fieldSpecs.get(i);
            factoryConstructorMethodSpecBuilder.addCode("this.$N = $N;\n", fieldSpec, parameterSpec);
        }
        MethodSpec factoryConstructorMethodSpec = factoryConstructorMethodSpecBuilder.build();

        TypeVariableName createMethodTypeVariable = TypeVariableName.get("T", viewModelClass);
        ParameterizedTypeName modelClassTypeName = ParameterizedTypeName.get(ClassName.get(Class.class), createMethodTypeVariable);
        ParameterSpec modelClassParameterSpec = ParameterSpec.builder(modelClassTypeName, "modelClass")
                .build();
        MethodSpec.Builder factoryCreateMethodSpecBuilder = MethodSpec.methodBuilder(FACTORY_CREATE_METHOD_NAME)
                .addParameter(modelClassParameterSpec)
                .addTypeVariable(createMethodTypeVariable)
                .returns(createMethodTypeVariable)
                .addStatement("return ($T) new $T($L)", createMethodTypeVariable, viewModelTypeName, parametersCallBlock)
                .addModifiers(Modifier.PUBLIC);

        MethodSpec factoryCreateMethodSpec = factoryCreateMethodSpecBuilder.build();

        factoryBuilder.addMethod(factoryConstructorMethodSpec);
        factoryBuilder.addMethod(factoryCreateMethodSpec);

        factoryBuilder.superclass(viewModelProviderNewInstanceFactoryClass);


        TypeSpec factoryTypeSpec = factoryBuilder.build();


        JavaFile javaFile = JavaFile.builder(viewModelTypeName.packageName(), factoryTypeSpec)
                .build();

        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "before generateFile");

        try {
            javaFile.writeTo(processingEnv.getFiler());
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        }


        MethodSpec fragmentMethodSpec = getGetMethodSpecForFragment(element, viewModelTypeName, parameterSpecs, parametersCallBlock, factoryClassName);
        getMethodSpecs.add(fragmentMethodSpec);

        MethodSpec activityMethodSpec = getGetMethodSpecForActivity(element, viewModelTypeName, parameterSpecs, parametersCallBlock, factoryClassName);
        getMethodSpecs.add(activityMethodSpec);


        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "SUCCESS");

        return true;
    }

    private MethodSpec getGetMethodSpecForFragment(TypeElement element,
                                                   ClassName viewModelTypeName,
                                                   List<ParameterSpec> parameterSpecs,
                                                   String parametersCallBlock,
                                                   ClassName factoryClassName) {
        final MethodSpec.Builder getMethodSpecBuilder = MethodSpec
                .methodBuilder(METHOD_PREFIX_GET + element.getSimpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(viewModelTypeName)
                .addParameter(supportFragmentClass, PARAM_NAME_FRAGMENT);

        for (ParameterSpec parameterSpec : parameterSpecs) {
            getMethodSpecBuilder.addParameter(parameterSpec);
        }


        return getMethodSpecBuilder
                .addCode("$T factory = new $T($L);\n", factoryClassName, factoryClassName, parametersCallBlock)
                .addCode("return $T.of($N, factory).get($T.class);\n", viewModelProvidersClass, PARAM_NAME_FRAGMENT, viewModelTypeName)
                .build();
    }

    private MethodSpec getGetMethodSpecForActivity(TypeElement element,
                                                   ClassName viewModelTypeName,
                                                   List<ParameterSpec> parameterSpecs,
                                                   String parametersCallBlock,
                                                   ClassName factoryClassName) {
        final MethodSpec.Builder getMethodSpecBuilder = MethodSpec
                .methodBuilder(METHOD_PREFIX_GET + element.getSimpleName())
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(viewModelTypeName)
                .addParameter(supportFragmentActivityClass, PARAM_NAME_FRAGMENT_ACTIVITY);

        for (ParameterSpec parameterSpec : parameterSpecs) {
            getMethodSpecBuilder.addParameter(parameterSpec);
        }


        return getMethodSpecBuilder
                .addCode("$T factory = new $T($L);\n", factoryClassName, factoryClassName, parametersCallBlock)
                .addCode("return $T.of($N, factory).get($T.class);\n", viewModelProvidersClass, PARAM_NAME_FRAGMENT_ACTIVITY, viewModelTypeName)
                .build();
    }

    private String getParametersCallBlock(ArrayList<ParameterSpec> parameterSpecs) {
        StringBuilder parametersCallBlockStringBuilder = new StringBuilder();
        for (int i = 0; i < parameterSpecs.size(); i++) {
            ParameterSpec parameterSpec = parameterSpecs.get(i);
            if (i == parameterSpecs.size() - 1) {
                parametersCallBlockStringBuilder.append(parameterSpec.name);
            } else {
                parametersCallBlockStringBuilder.append(parameterSpec.name);
                parametersCallBlockStringBuilder.append(", ");
            }
        }
        return parametersCallBlockStringBuilder.toString();
    }

    private void createVmProvider() throws IOException {
        final TypeSpec.Builder builder = TypeSpec.classBuilder(CLASS_NAME_VM_PROVIDER);
        builder.addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        for (MethodSpec methodSpec : getMethodSpecs) {
            builder.addMethod(methodSpec);
        }

        TypeSpec typeSpec = builder.build();


        JavaFile javaFile = JavaFile.builder(vmProviderPackage, typeSpec)
                .build();

        processingEnv.getMessager().printMessage(Diagnostic.Kind.WARNING, "before generateFile");

        javaFile.writeTo(processingEnv.getFiler());
    }
}
