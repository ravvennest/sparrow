package io.papermc.generator.utils;

import com.google.common.base.CaseFormat;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import javax.lang.model.SourceVersion;

public class NamingManager {

    private final AccessKeyword accessKeyword;
    private final String baseName;
    private final String lowerCamelName, upperCamelName;

    public NamingManager(@Nullable NamingManager.AccessKeyword accessKeyword, CaseFormat format, String baseName) {
        this.accessKeyword = accessKeyword; // this is a little bit too restrictive for extra data hmm
        this.baseName = baseName;
        this.upperCamelName = format.to(CaseFormat.UPPER_CAMEL, baseName);
        this.lowerCamelName = format.to(CaseFormat.LOWER_CAMEL, baseName);
    }

    public String getVariableName() {
        return this.lowerCamelName;
    }

    public String getMethodBaseName() {
        return this.upperCamelName;
    }

    public NameWrapper getMethodNameWrapper() {
        return NameWrapper.wrap("get", this.upperCamelName);
    }

    public NameWrapper getVariableNameWrapper() {
        return NameWrapper.wrap("", this.lowerCamelName);
    }

    public NameWrapper getterName(Predicate<String> keywordPredicate) {
        return accessName(keywordPredicate, AccessKeyword::get, "get");
    }

    public NameWrapper setterName(Predicate<String> keywordPredicate) {
        return accessName(keywordPredicate, AccessKeyword::set, "set");
    }

    public String simpleGetterName(Predicate<String> keywordPredicate) {
        return getterName(keywordPredicate).concat();
    }

    public String simpleSetterName(Predicate<String> keywordPredicate) {
        return setterName(keywordPredicate).concat();
    }

    private NameWrapper accessName(Predicate<String> keywordPredicate, KeywordFetcher keywordFetcher, String fallbackKeyword) {
        final String name;
        String accessKeyword;
        if (keywordPredicate.test(this.baseName)) {
            accessKeyword = Optional.ofNullable(this.accessKeyword).flatMap(keywordFetcher::fetch).orElse(fallbackKeyword);
            name = this.upperCamelName;
        } else {
            accessKeyword = "";
            name = this.lowerCamelName;
        }
        return NameWrapper.wrap(accessKeyword, name);
    }

    public String paramName(Class<?> type) {
        final String paramName;
        if (type.isPrimitive()) {
            paramName = this.lowerCamelName;
        } else {
            paramName = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_CAMEL, type.getSimpleName());
        }
        return ensureValidName(paramName);
    }

    public static String ensureValidName(String name) {
        if (!SourceVersion.isName(name)) {
            return "_" + name;
        }
        return name;
    }

    public static final Pattern FULLY_QUALIFIED_NAME_SEPARATOR = Pattern.compile(".", Pattern.LITERAL);

    public static boolean isValidName(String typeName) {
        return isValidName(typeName, keyword -> false);
    }

    // check only syntax error and keyword but if each id are valid identifier
    public static boolean isValidName(String typeName, Predicate<String> validKeyword) {
        for (String part : FULLY_QUALIFIED_NAME_SEPARATOR.split(typeName)) {
            if (part.isEmpty()) {
                return false;
            }
            if (SourceVersion.isKeyword(part)) {
                return validKeyword.test(part);
            }
        }
        return true;
    }

    public static class NameWrapper {

        private final String keyword;
        private final String name;
        private String preValue = "", postValue = "";

        private NameWrapper(String keyword, String name) {
            this.keyword = keyword;
            this.name = name;
        }

        public static NameWrapper wrap(String keyword, String name) {
            return new NameWrapper(keyword, name);
        }

        @Contract(value = "_ -> this", mutates = "this")
        public NameWrapper pre(String value) {
            this.preValue = value;
            return this;
        }

        @Contract(value = "_ -> this", mutates = "this")
        public NameWrapper post(String value) {
            this.postValue = value;
            return this;
        }

        public String concat() {
            String finalName = this.keyword + this.preValue + this.name + this.postValue;
            this.preValue = this.postValue = ""; // reset
            return ensureValidName(finalName);
        }
    }

    @FunctionalInterface
    private interface KeywordFetcher {

        Optional<String> fetch(AccessKeyword accessKeyword);
    }

    public static AccessKeyword keywordGet(String keyword) {
        return new AccessKeyword(Optional.of(keyword), Optional.empty());
    }

    public static AccessKeyword keywordSet(String keyword) {
        return new AccessKeyword(Optional.empty(), Optional.of(keyword));
    }

    public static AccessKeyword keywordGetSet(String getter, String setter) {
        return new AccessKeyword(Optional.of(getter), Optional.of(setter));
    }

    public record AccessKeyword(Optional<String> get, Optional<String> set) {
    }
}