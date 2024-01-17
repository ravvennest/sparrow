package io.papermc.generator.types;

import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import io.papermc.generator.Main;
import io.papermc.generator.utils.Annotations;
import io.papermc.generator.utils.Formatting;
import io.papermc.generator.utils.Javadocs;
import io.papermc.generator.utils.RegistryUtils;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.key.Key;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.bukkit.MinecraftExperimental;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;

import static com.squareup.javapoet.TypeSpec.classBuilder;
import static io.papermc.generator.utils.Annotations.EXPERIMENTAL_API_ANNOTATION;
import static io.papermc.generator.utils.Annotations.NOT_NULL;
import static io.papermc.generator.utils.Annotations.experimentalAnnotations;
import static java.util.Objects.requireNonNull;
import static javax.lang.model.element.Modifier.FINAL;
import static javax.lang.model.element.Modifier.PRIVATE;
import static javax.lang.model.element.Modifier.PUBLIC;
import static javax.lang.model.element.Modifier.STATIC;

@DefaultQualifier(NonNull.class)
public class GeneratedKeyType<T, A> extends SimpleGenerator {

    private static final Map<RegistryKey<?>, String> REGISTRY_KEY_FIELD_NAMES;
    static {
        final Map<RegistryKey<?>, String> map = new HashMap<>();
        try {
            for (final Field field : RegistryKey.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !Modifier.isFinal(field.getModifiers()) || field.getType() != RegistryKey.class) {
                    continue;
                }
                map.put((RegistryKey<?>) field.get(null), field.getName());
            }
            REGISTRY_KEY_FIELD_NAMES = Map.copyOf(map);
        } catch (final ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static final String CREATE_JAVADOC = """
        Creates a key for {@link $T} in a registry.
        
        @param key the value's key in the registry
        @return a new typed key
        """;

    private final Class<A> apiType;
    private final ResourceKey<? extends Registry<T>> registryKey;
    private final RegistryKey<A> apiRegistryKey;
    private final boolean publicCreateKeyMethod;

    public GeneratedKeyType(final String keysClassName, final Class<A> apiType, final String pkg, final ResourceKey<? extends Registry<T>> registryKey, final RegistryKey<A> apiRegistryKey, final boolean publicCreateKeyMethod) {
        super(keysClassName, pkg);
        this.apiType = apiType;
        this.registryKey = registryKey;
        this.apiRegistryKey = apiRegistryKey;
        this.publicCreateKeyMethod = publicCreateKeyMethod;
    }

    private MethodSpec.Builder createMethod(final TypeName returnType) {
        final TypeName keyType = TypeName.get(Key.class).annotated(NOT_NULL);

        final ParameterSpec keyParam = ParameterSpec.builder(keyType, "key", FINAL).build();
        final MethodSpec.Builder create = MethodSpec.methodBuilder("create")
            .addModifiers(this.publicCreateKeyMethod ? PUBLIC : PRIVATE, STATIC)
            .addParameter(keyParam)
            .addCode("return $T.create($T.$L, $N);", TypedKey.class, RegistryKey.class, requireNonNull(REGISTRY_KEY_FIELD_NAMES.get(this.apiRegistryKey), "Missing field for " + this.apiRegistryKey), keyParam)
            .returns(returnType.annotated(NOT_NULL));
        if (this.publicCreateKeyMethod) {
            create.addAnnotation(EXPERIMENTAL_API_ANNOTATION); // TODO remove once not experimental
            create.addJavadoc(CREATE_JAVADOC, this.apiType);
        }
        return create;
    }

    private TypeSpec.Builder keyHolderType() {
        return classBuilder(this.className)
            .addModifiers(PUBLIC, FINAL)
            .addJavadoc(Javadocs.getVersionDependentClassHeader("{@link $T#$L}"), RegistryKey.class, REGISTRY_KEY_FIELD_NAMES.get(this.apiRegistryKey))
            .addAnnotations(Annotations.CLASS_HEADER)
            .addMethod(MethodSpec.constructorBuilder()
                .addModifiers(PRIVATE)
                .build()
            );
    }

    @Override
    protected TypeSpec getTypeSpec() {
        final TypeName typedKey = ParameterizedTypeName.get(TypedKey.class, this.apiType);

        final TypeSpec.Builder typeBuilder = this.keyHolderType();
        final MethodSpec.Builder createMethod = this.createMethod(typedKey);

        final Registry<T> registry = Main.REGISTRY_ACCESS.registryOrThrow(this.registryKey);
        final Set<ResourceKey<T>> experimental = RegistryUtils.collectExperimentalDataDrivenKeys(registry);

        boolean allExperimental = true;
        for (final Holder.Reference<T> reference : registry.holders().sorted(Formatting.alphabeticKeyOrder(reference -> reference.key().location().getPath())).toList()) {
            final ResourceKey<T> key = reference.key();
            final String keyPath = key.location().getPath();
            final String fieldName = Formatting.formatKeyAsField(keyPath);
            final FieldSpec.Builder fieldBuilder = FieldSpec.builder(typedKey, fieldName, PUBLIC, STATIC, FINAL)
                .initializer("$N(key($S))", createMethod.build(), keyPath)
                .addJavadoc(Javadocs.getVersionDependentField("{@code $L}"), key.location().toString());
            if (experimental.contains(key)) {
                fieldBuilder.addAnnotations(experimentalAnnotations(MinecraftExperimental.Requires.UPDATE_1_21));
            } else {
                allExperimental = false;
            }
            typeBuilder.addField(fieldBuilder.build());
        }
        if (allExperimental) {
            typeBuilder.addAnnotations(experimentalAnnotations(MinecraftExperimental.Requires.UPDATE_1_21));
            createMethod.addAnnotations(experimentalAnnotations(MinecraftExperimental.Requires.UPDATE_1_21));
        } else {
            typeBuilder.addAnnotation(EXPERIMENTAL_API_ANNOTATION); // TODO experimental API
        }
        return typeBuilder.addMethod(createMethod.build()).build();
    }

    @Override
    protected JavaFile.Builder file(final JavaFile.Builder builder) {
        return builder
            .skipJavaLangImports(true)
            .addStaticImport(Key.class, "key")
            .indent("    ");
    }
}
