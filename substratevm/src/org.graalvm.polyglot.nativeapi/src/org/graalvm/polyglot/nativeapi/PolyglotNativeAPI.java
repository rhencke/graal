/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.polyglot.nativeapi;

import static org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus.poly_array_expected;
import static org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus.poly_generic_failure;
import static org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus.poly_number_expected;
import static org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus.poly_ok;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CEntryPointContext;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CDoublePointer;
import org.graalvm.nativeimage.c.type.CFloatPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;
import org.graalvm.nativeimage.c.type.VoidPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.nativeapi.types.CBoolPointer;
import org.graalvm.polyglot.nativeapi.types.CInt16Pointer;
import org.graalvm.polyglot.nativeapi.types.CInt32Pointer;
import org.graalvm.polyglot.nativeapi.types.CInt64Pointer;
import org.graalvm.polyglot.nativeapi.types.CInt8Pointer;
import org.graalvm.polyglot.nativeapi.types.CUnsignedBytePointer;
import org.graalvm.polyglot.nativeapi.types.CUnsignedIntPointer;
import org.graalvm.polyglot.nativeapi.types.CUnsignedShortPointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotCallback;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotCallbackInfo;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotContext;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotContextBuilder;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotContextBuilderPointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotContextPointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotEngine;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotEnginePointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotExtendedErrorInfo;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotExtendedErrorInfoPointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotHandle;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotIsolateThread;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotLanguage;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotLanguagePointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotStatus;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotValue;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.PolyglotValuePointer;
import org.graalvm.polyglot.nativeapi.types.PolyglotNativeAPITypes.SizeTPointer;
import org.graalvm.polyglot.proxy.ProxyArray;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.graalvm.polyglot.proxy.ProxyObject;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.c.CConst;
import com.oracle.svm.core.c.CHeader;
import com.oracle.svm.core.c.CUnsigned;

@SuppressWarnings("unused")
@CHeader(value = PolyglotAPIHeader.class)
public final class PolyglotNativeAPI {

    private static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    private static final int MAX_UNSIGNED_BYTE = (1 << 8) - 1;
    private static final int MAX_UNSIGNED_SHORT = (1 << 16) - 1;
    private static final long MAX_UNSIGNED_INT = (1L << 32) - 1;
    private static final UnsignedWord POLY_AUTO_LENGTH = WordFactory.unsigned(0xFFFFFFFFFFFFFFFFL);

    private static ThreadLocal<ErrorInfoHolder> errorInfo = new ThreadLocal<>();
    private static ThreadLocal<CallbackException> exceptionsTL = new ThreadLocal<>();

    private static class ErrorInfoHolder {
        PolyglotExtendedErrorInfo info;
        CCharPointerHolder messageHolder;

        ErrorInfoHolder(PolyglotExtendedErrorInfo info, CCharPointerHolder messageHolder) {
            this.info = info;
            this.messageHolder = messageHolder;
        }
    }

    @CEntryPoint(name = "poly_create_engine", documentation = {
                    "Creates a polyglot engine: An execution engine for Graal guest languages that allows to inspect the ",
                    "installed languages and can have multiple execution contexts.",
                    "",
                    "Engine is a unit that holds configuration, instruments, and compiled code for all contexts assigned ",
                    "to this engine.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_engine(PolyglotIsolateThread thread, PolyglotEnginePointer result) {
        return withHandledErrors(() -> {
            ObjectHandle handle = createHandle(Engine.create());
            result.write(handle);
        });
    }

    @CEntryPoint(name = "poly_engine_get_languages", documentation = {
                    "Returns an array of size returned by {@link poly_engine_get_languages_size} where each element is a <code>poly_language<code> handle.",
                    "",
                    " @param engine for which languages are returned.",
                    " @param language_array array to write <code>poly_language</code>s to or NULL.",
                    " @param size the number of languages in the engine.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_engine_get_languages(PolyglotIsolateThread thread, PolyglotEngine engine, PolyglotLanguagePointer language_array, SizeTPointer size) {
        return withHandledErrors(() -> {
            Engine jEngine = fetchHandle(engine);
            UnsignedWord languagesSize = WordFactory.unsigned(jEngine.getLanguages().size());
            if (language_array.isNull()) {
                size.write(languagesSize);
            } else {
                size.write(languagesSize);
                List<Language> sortedLanguages = sortedLangs(fetchHandle(engine));
                for (int i = 0; i < sortedLanguages.size(); i++) {
                    language_array.write(i, createHandle(sortedLanguages.get(i)));
                }
            }
        });
    }

    @CEntryPoint(name = "poly_create_context_builder", documentation = {
                    "Creates a context with a new engine polyglot engine with a list ",
                    "",
                    "A context holds all of the program data. Each context is by default isolated from all other contexts",
                    "with respect to program data and evaluation semantics.",
                    "",
                    " @param permittedLanguages array of 0 terminated language identifiers in UTF-8 that are permitted.",
                    " @param length of the array of language identifiers.",
                    " @param result the created context.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_context_builder(PolyglotIsolateThread thread, @CConst CCharPointerPointer permitted_languages, UnsignedWord length, PolyglotContextBuilderPointer result) {
        return withHandledErrors(() -> {
            List<String> jPermittedLangs = new ArrayList<>();
            for (int i = 0; length.aboveThan(i); i++) {
                jPermittedLangs.add(CTypeConversion.toJavaString(permitted_languages.read(i)));
            }
            Context.Builder c = Context.newBuilder(jPermittedLangs.toArray(new String[jPermittedLangs.size()]));
            result.write(createHandle(c));
        });
    }

    @CEntryPoint(name = "poly_context_builder_engine", documentation = {
                    "Sets an engine for the context builder.",
                    "",
                    " @param context_builder that is assigned an engine.",
                    " @param engine to assign to this builder.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_context_builder_engine(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, PolyglotEngine engine) {
        return withHandledErrors(() -> {
            Context.Builder contextBuilder = fetchHandle(context_builder);
            Engine jEngine = fetchHandle(engine);
            contextBuilder.engine(jEngine);
        });
    }

    @CEntryPoint(name = "poly_context_builder_option", documentation = {
                    "Sets an option on a <code>poly_context_builder</code>.",
                    "",
                    " @param context_builder that is assigned an option.",
                    " @param key_utf8 0 terminated and UTF-8 encoded key for the option.",
                    " @param value_utf8 0 terminated and UTF-8 encoded value for the option.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_context_builder_option(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, @CConst CCharPointer key_utf8, @CConst CCharPointer value_utf8) {
        return withHandledErrors(() -> {
            Context.Builder contextBuilder = fetchHandle(context_builder);
            contextBuilder.option(CTypeConversion.toJavaString(key_utf8), CTypeConversion.toJavaString(value_utf8));
        });
    }

    @CEntryPoint(name = "poly_context_builder_allow_all_access", documentation = {
                    "Allows or disallows all access for a <code>poly_context_builder</code>.",
                    "",
                    " @param context_builder that is modified.",
                    " @param allow_all_access bool value that defines all access.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_context_builder_allow_all_access(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, boolean allow_all_access) {
        return withHandledErrors(() -> {
            Context.Builder contextBuilder = fetchHandle(context_builder);
            contextBuilder.allowAllAccess(allow_all_access);
        });
    }

    @CEntryPoint(name = "poly_context_builder_allow_io", documentation = {
                    "Allows or disallows IO for a <code>poly_context_builder</code>.",
                    "",
                    " @param context_builder that is modified.",
                    " @param allow_IO bool value that is passed to the builder.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_context_builder_allow_io(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, boolean allow_IO) {
        return withHandledErrors(() -> {
            Context.Builder contextBuilder = fetchHandle(context_builder);
            contextBuilder.allowIO(allow_IO);
        });
    }

    @CEntryPoint(name = "poly_context_builder_allow_native_access", documentation = {
                    "Allows or disallows native access for a <code>poly_context_builder</code>.",
                    "",
                    " @param context_builder that is modified.",
                    " @param allow_native_access bool value that is passed to the builder.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_context_builder_allow_native_access(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, boolean allow_native_access) {
        return withHandledErrors(() -> {
            Context.Builder contextBuilder = fetchHandle(context_builder);
            contextBuilder.allowNativeAccess(allow_native_access);
        });
    }

    @CEntryPoint(name = "poly_context_builder_allow_create_thread", documentation = {
                    "Allows or disallows thread creation or a <code>poly_context_builder</code>.",
                    "",
                    " @param context_builder that is modified.",
                    " @param allow_create_thread bool value that is passed to the builder.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_context_builder_allow_create_thread(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, boolean allow_create_thread) {
        return withHandledErrors(() -> {
            Context.Builder contextBuilder = fetchHandle(context_builder);
            contextBuilder.allowNativeAccess(allow_create_thread);
        });
    }

    @CEntryPoint(name = "poly_context_builder_build", documentation = {
                    "Builds a <code>context</code> from a <code>context_builder</code>. The same builder can be used to ",
                    "produce multiple <code>poly_context</code> instances.",
                    "",
                    " @param context_builder that is allowed all access.",
                    " @param result the created context.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_context_builder_build(PolyglotIsolateThread thread, PolyglotContextBuilder context_builder, PolyglotContextPointer result) {
        return withHandledErrors(() -> {
            Context.Builder contextBuilder = fetchHandle(context_builder);
            result.write(createHandle(contextBuilder.build()));
        });
    }

    @CEntryPoint(name = "poly_create_context", documentation = {
                    "Creates a context with default configuration.",
                    "",
                    "A context holds all of the program data. Each context is by default isolated from all other contexts",
                    "with respect to program data and evaluation semantics.",
                    "",
                    " @param permitted_languages array of 0 terminated language identifiers in UTF-8 that are permitted, or NULL for ",
                    "        supporting all available languages.",
                    " @param length of the array of language identifiers.",
                    " @param result the created context.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_context(PolyglotIsolateThread thread, @CConst CCharPointerPointer permitted_languages, UnsignedWord length, PolyglotContextPointer result) {
        return withHandledErrors(() -> {
            Context c;
            if (permitted_languages.isNull()) {
                c = Context.create();
            } else {
                List<String> jPermittedLangs = new ArrayList<>();
                for (int i = 0; length.aboveThan(i); i++) {
                    jPermittedLangs.add(CTypeConversion.toJavaString(permitted_languages.read(i)));
                }
                c = Context.create(jPermittedLangs.toArray(new String[jPermittedLangs.size()]));
            }
            result.write(createHandle(c));
        });
    }

    @CEntryPoint(name = "poly_context_eval", documentation = {
                    "Evaluate a source of guest languages inside a context.",
                    "",
                    " @param context in which we evaluate source code.",
                    " @param language_id the language identifier.",
                    " @param name_utf8 given to the evaluate source code.",
                    " @param source_utf8 the source code to be evaluated.",
                    " @param result <code>poly_value</code> that is the result of the evaluation.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @see org::graalvm::polyglot::Context::eval",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_context_eval(PolyglotIsolateThread thread, PolyglotContext context, @CConst CCharPointer language_id, @CConst CCharPointer name_utf8,
                    @CConst CCharPointer source_utf8, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context c = fetchHandle(context);
            String languageName = CTypeConversion.toJavaString(language_id);
            String jName = CTypeConversion.toJavaString(name_utf8);
            String jCode = CTypeConversion.toJavaString(source_utf8);

            Source sourceCode = Source.newBuilder(languageName, jCode, jName).build();
            result.write(createHandle(c.eval(sourceCode)));
        });
    }

    @CEntryPoint(name = "poly_context_get_engine", documentation = {
                    "Returns the engine this context belongs to.",
                    "",
                    " @param context for which we extract the bindings.",
                    " @param result a value whose members correspond to the symbols in the top scope of the `language_id`.",
                    " @return poly_ok if everything is fine, poly_generic_failure if there is an error.",
                    " @see org::graalvm::polyglot::Context::getEngine",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_context_get_engine(PolyglotIsolateThread thread, PolyglotContext context, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context jContext = fetchHandle(context);
            result.write(createHandle(jContext.getEngine()));
        });
    }

    @CEntryPoint(name = "poly_context_get_bindings", documentation = {
                    "Returns a value that represents the top-most bindings of a language. The top-most bindings of",
                    "the language are a value whose members correspond to each symbol in the top scope.",
                    "",
                    "Languages may allow modifications of members of the returned bindings object at the",
                    "language's discretion. If the language was not yet initialized it",
                    "will be initialized when the bindings are requested.",
                    "",
                    " @param context for which we extract the bindings.",
                    " @param language_id the language identifier.",
                    " @param result a value whose members correspond to the symbols in the top scope of the `language_id`.",
                    " @return poly_generic_failure if the language does not exist, if context is already closed, ",
                    "        in case the lazy initialization failed due to a guest language error.",
                    " @see org::graalvm::polyglot::Context::getBindings",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_context_get_bindings(PolyglotIsolateThread thread, PolyglotContext context, @CConst CCharPointer language_id, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context jContext = fetchHandle(context);
            String jLanguage = CTypeConversion.toJavaString(language_id);
            Value languageBindings = jContext.getBindings(jLanguage);
            result.write(createHandle(languageBindings));
        });
    }

    @CEntryPoint(name = "poly_context_get_polyglot_bindings", documentation = {
                    "Returns polyglot bindings that may be used to exchange symbols between the host and ",
                    "guest languages. All languages have unrestricted access to the polyglot bindings. ",
                    "The returned bindings object always has members and its members are readable, writable and removable.",
                    "",
                    "Guest languages may put and get members through language specific APIs. For example, ",
                    "in JavaScript symbols of the polyglot bindings can be accessed using ",
                    "`Polyglot.import(\"name\")` and set using `Polyglot.export(\"name\", value)`. Please see ",
                    "the individual language reference on how to access these symbols.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is already closed.",
                    " @see org::graalvm::polyglot::Context::getPolyglotBindings",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_context_get_polyglot_bindings(PolyglotIsolateThread thread, PolyglotContext context, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context jContext = fetchHandle(context);
            result.write(createHandle(jContext.getPolyglotBindings()));
        });
    }

    @CEntryPoint(name = "poly_value_can_execute", documentation = {
                    "Checks whether a polyglot value can be executed.",
                    "",
                    " @param value a polyglot value.",
                    " @param result true if the value can be executed, false otherwise.",
                    " @return poly_ok if all works, poly_generic_error if there is a failure.",
                    " @see org::graalvm::polyglot::Value::canExecute",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_can_execute(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(jValue.canExecute()));
        });
    }

    @CEntryPoint(name = "poly_value_execute", documentation = {
                    "Executes a value if it can be executed and returns its result. All arguments passed ",
                    "must be polyglot values.",
                    "",
                    " @param value to be executed.",
                    " @param args array of poly_value.",
                    " @param args_size length of the args array.",
                    " @return poly_ok if all works, poly_generic_error if the underlying context was closed, if a wrong ",
                    "         number of arguments was provided or one of the arguments was not applicable, if this value cannot be executed,",
                    " and if a guest language error occurred during execution.",
                    " @see org::graalvm::polyglot::Value::execute",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_execute(PolyglotIsolateThread thread, PolyglotValue value, PolyglotValuePointer args, int args_size,
                    PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Value function = fetchHandle(value);
            Object[] jArgs = new Object[args_size];
            for (int i = 0; i < args_size; i++) {
                PolyglotValue handle = args.read(i);
                jArgs[i] = fetchHandle(handle);
            }

            Value resultValue = function.execute(jArgs);
            result.write(createHandle(resultValue));
        });
    }

    @CEntryPoint(name = "poly_value_get_member", documentation = {
                    "Returns the member with a given `utf8_identifier` or `null` if the member does not exist.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the value has no members, the given identifier exists ",
                    "        but is not readable, if a guest language error occurred during execution.",
                    " @see org::graalvm::polyglot::Value::getMember",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_get_member(PolyglotIsolateThread thread, PolyglotValue value, @CConst CCharPointer utf8_identifier, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Value jObject = fetchHandle(value);
            result.write(createHandle(jObject.getMember(CTypeConversion.toJavaString(utf8_identifier))));
        });
    }

    @CEntryPoint(name = "poly_value_put_member", documentation = {
                    "Sets the value of a member with the `utf8_identifier`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the context is already closed, if the value does ",
                    "         not have any members, the key does not exist and new members cannot be added, or the existing ",
                    "         member is not modifiable.",
                    " @see org::graalvm::polyglot::Value::putMember",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_put_member(PolyglotIsolateThread thread, PolyglotValue value, @CConst CCharPointer utf8_identifier, PolyglotValue member) {
        return withHandledErrors(() -> {
            Value jObject = fetchHandle(value);
            Value jMember = fetchHandle(member);
            jObject.putMember(CTypeConversion.toJavaString(utf8_identifier), jMember);
        });
    }

    @CEntryPoint(name = "poly_value_has_member", documentation = {
                    "Returns `true` if such a member exists for the given `utf8_identifier`. If the value has no members ",
                    "then it returns `false`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "         during execution.",
                    " @see org::graalvm::polyglot::Value::putMember",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_has_member(PolyglotIsolateThread thread, PolyglotValue value, @CConst CCharPointer utf8_identifier, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value jObject = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(jObject.hasMember(CTypeConversion.toJavaString(utf8_identifier))));
        });
    }

    @CEntryPoint(name = "poly_create_boolean", documentation = {
                    "Creates a polyglot boolean value from a C boolean.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_boolean(PolyglotIsolateThread thread, PolyglotContext context, boolean value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = fetchHandle(context);
            result.write(createHandle(ctx.asValue(value)));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int8", documentation = {
                    "Creates a polyglot integer number from `int8_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_int8(PolyglotIsolateThread thread, PolyglotContext context, byte value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = fetchHandle(context);
            result.write(createHandle(ctx.asValue(Byte.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int16", documentation = {
                    "Creates a polyglot integer number from `int16_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_int16(PolyglotIsolateThread thread, PolyglotContext context, short value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = fetchHandle(context);
            result.write(createHandle(ctx.asValue(Short.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int32", documentation = {
                    "Creates a polyglot integer number from `int32_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_int32(PolyglotIsolateThread thread, PolyglotContext context, int value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = fetchHandle(context);
            result.write(createHandle(ctx.asValue(Integer.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_int64", documentation = {
                    "Creates a polyglot integer number from `int64_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_int64(PolyglotIsolateThread thread, PolyglotContext context, long value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = fetchHandle(context);
            result.write(createHandle(ctx.asValue(Long.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_uint8", documentation = {
                    "Creates a polyglot integer number from `uint8_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_uint8(PolyglotIsolateThread thread, PolyglotContext context, @CUnsigned byte value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = fetchHandle(context);
            result.write(createHandle(ctx.asValue(Byte.toUnsignedInt(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_uint16", documentation = {
                    "Creates a polyglot integer number from `uint16_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_uint16(PolyglotIsolateThread thread, PolyglotContext context, @CUnsigned short value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = fetchHandle(context);
            result.write(createHandle(ctx.asValue(Short.toUnsignedInt(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_uint32", documentation = {
                    "Creates a polyglot integer number from `uint32_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_uint32(PolyglotIsolateThread thread, PolyglotContext context, @CUnsigned int value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = fetchHandle(context);
            result.write(createHandle(ctx.asValue(Integer.toUnsignedLong(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_float", documentation = {
                    "Creates a polyglot floating point number from C `float`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_float(PolyglotIsolateThread thread, PolyglotContext context, float value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = fetchHandle(context);
            result.write(createHandle(ctx.asValue(Float.valueOf(value))));
        });
    }

    @SuppressWarnings("UnnecessaryBoxing")
    @CEntryPoint(name = "poly_create_double", documentation = {
                    "Creates a polyglot floating point number from C `double`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_double(PolyglotIsolateThread thread, PolyglotContext context, double value, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = fetchHandle(context);
            result.write(createHandle(ctx.asValue(Double.valueOf(value))));
        });
    }

    @CEntryPoint(name = "poly_create_character", documentation = {
                    "Creates a polyglot character from C `char`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_character(PolyglotIsolateThread thread, PolyglotContext context, char character, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = fetchHandle(context);
            result.write(createHandle(ctx.asValue(character)));
        });
    }

    @CEntryPoint(name = "poly_create_string_utf8", documentation = {
                    "Creates a polyglot string from an UTF-8 encoded string. Only the `length` of the string in bytes is used unless",
                    "`POLY_AUTO_LENGTH` is passed as the `length` argument.",
                    "",
                    " @param string the C string, null terminated or not.",
                    " @param length the length of C string, or POLY_AUTO_LENGTH if the string is null terminated.",
                    " @return the polyglot string value.",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_string_utf8(PolyglotIsolateThread thread, PolyglotContext context, @CConst CCharPointer string, UnsignedWord length, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = fetchHandle(context);
            result.write(createHandle(ctx.asValue(length.equal(POLY_AUTO_LENGTH) ? CTypeConversion.toJavaString(string) : CTypeConversion.toJavaString(string, length, UTF8_CHARSET))));
        });
    }

    @CEntryPoint(name = "poly_create_null", documentation = {
                    "Creates the polyglot `null` value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::Context::asValue",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_null(PolyglotIsolateThread thread, PolyglotContext context, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = fetchHandle(context);
            result.write(createHandle(ctx.asValue(null)));
        });
    }

    @CEntryPoint(name = "poly_create_object", documentation = {
                    "Creates a polyglot object with no members.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed.",
                    " @see org::graalvm::polyglot::ProxyObject::fromMap",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_object(PolyglotIsolateThread thread, PolyglotContext context, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context c = fetchHandle(context);
            ProxyObject proxy = ProxyObject.fromMap(new HashMap<>());
            result.write(createHandle(c.asValue(proxy)));
        });
    }

    @CEntryPoint(name = "poly_create_array", documentation = {
                    "Creates a polyglot array from the C array of polyglot values.",
                    "",
                    " @param value_array array containing polyglot values",
                    " @param array_length the number of elements in the value_array",
                    " @return poly_ok if all works, poly_generic_failure if context is null, if the underlying context was closed, ",
                    "         if the array does not contain polyglot values.",
                    " @see org::graalvm::polyglot::ProxyArray::fromList",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_array(PolyglotIsolateThread thread, PolyglotContext context, @CConst PolyglotValuePointer value_array, long array_length,
                    PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Context ctx = fetchHandle(context);
            List<Object> values = new LinkedList<>();
            for (long i = 0; i < array_length; i++) {
                values.add(fetchHandle(value_array.read(i)));
            }
            result.write(createHandle(ctx.asValue(ProxyArray.fromList(values))));
        });
    }

    @CEntryPoint(name = "poly_value_has_array_elements", documentation = {
                    "Check whether a polyglot value has array elements. ",
                    "",
                    "If yes, array elements can be accessed using {@link poly_value_get_array_element}, ",
                    "{@link poly_value_set_array_element}, {@link poly_value_remove_array_element} and the array size ",
                    "can be queried using {@link poly_value_get_array_size}.",
                    "",
                    " @param value value that we are checking.",
                    " @return true if the value has array elements.",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::hasArrayElements",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_has_array_elements(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(jValue.hasArrayElements()));
        });
    }

    @CEntryPoint(name = "poly_value_get_array_element", documentation = {
                    "Returns an array element from the specified index. ",
                    "",
                    "Polyglot arrays start with index `0`, independent of the guest language. The given array index must ",
                    "be greater or equal 0.",
                    "",
                    " @param value value that has array elements.",
                    " @param index index of the element starting from 0.",
                    " @return the array element.",
                    " @return poly_ok if all works, poly_generic_failure if the array index does not exist, if index is not readable, if the ",
                    "         underlying context was closed, if guest language error occurred during execution, poly_array_expected if the ",
                    "         value has no array elements.",
                    " @see org::graalvm::polyglot::Value::getArrayElement",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_get_array_element(PolyglotIsolateThread thread, PolyglotValue value, long index, PolyglotValuePointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            if (!jValue.hasArrayElements()) {
                throw reportError("Array expected but got " + jValue.getMetaObject().toString(), poly_array_expected);
            }
            result.write(createHandle(jValue.getArrayElement(index)));
        });
    }

    @CEntryPoint(name = "poly_value_set_array_element", documentation = {
                    "Sets the value at a given index.",
                    "",
                    "Polyglot arrays start with index `0`, independent of the guest language. The given array index must ",
                    "be greater or equal 0.",
                    "",
                    " @param value value that we are checking.",
                    " @param index index of the element starting from 0.",
                    " @param element to be written into the array.",
                    " @param result true if the value has array elements.",
                    " @return poly_ok if all works, poly_generic_failure if the array index does not exist, if index is not writeable, if the ",
                    "         underlying context was closed, if guest language error occurred during execution, poly_array_expected if the value has no array elements..",
                    " @see org::graalvm::polyglot::Value::setArrayElement",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_set_array_element(PolyglotIsolateThread thread, PolyglotValue value, long index, PolyglotValue element) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            if (!jValue.hasArrayElements()) {
                throw reportError("Array expected but got " + jValue.getMetaObject().toString(), poly_array_expected);
            }
            Value jElement = fetchHandle(element);
            jValue.setArrayElement(index, jElement);
        });
    }

    @CEntryPoint(name = "poly_value_remove_array_element", documentation = {
                    "Sets the value at a given index.",
                    "",
                    "Polyglot arrays start with index `0`, independent of the guest language. The given array index must ",
                    "be greater or equal 0.",
                    "",
                    " @param value value that we are checking.",
                    " @param index index of the element starting from 0.",
                    " @param element to be written into the array.",
                    " @return true if the value has array elements.",
                    " @return poly_ok if all works, poly_generic_failure if the array index does not exist, if index is not removable, if the ",
                    "         underlying context was closed, if guest language error occurred during execution, poly_array_expected if the ",
                    "         value has no array elements.",
                    " @see org::graalvm::polyglot::Value::removeArrayElement",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_remove_array_element(PolyglotIsolateThread thread, PolyglotValue value, long index, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            if (!jValue.hasArrayElements()) {
                throw reportError("Array expected but got " + jValue.getMetaObject().toString(), poly_array_expected);
            }
            result.write(CTypeConversion.toCBoolean(jValue.removeArrayElement(index)));
        });
    }

    @CEntryPoint(name = "poly_value_get_array_size", documentation = {
                    "Gets the size of the polyglot value that has array elements.",
                    "",
                    " @param value value that has array elements.",
                    " @param result number of elements in the value.",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "         during execution, poly_array_expected if the value has no array elements.",
                    " @see org::graalvm::polyglot::Value::removeArrayElement",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_get_array_size(PolyglotIsolateThread thread, PolyglotValue value, CInt64Pointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            if (!jValue.hasArrayElements()) {
                throw reportError("Array expected but got " + jValue.getMetaObject().toString(), poly_array_expected);
            }
            result.write(jValue.getArraySize());
        });
    }

    @CEntryPoint(name = "poly_value_is_null", documentation = {
                    "Returns `true` if this value is `null` like.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::isNull",
                    " @since 1.0"
    })
    public static PolyglotStatus poly_value_is_null(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(jValue.isNull()));
        });
    }

    @CEntryPoint(name = "poly_value_is_boolean", documentation = {
                    "Returns `true` if this value represents a boolean value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "        if the underlying context was closed, if value could not be converted. ",
                    " @see org::graalvm::polyglot::Value::isBoolean",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_is_boolean(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(jValue.isBoolean()));
        });
    }

    @CEntryPoint(name = "poly_value_is_string", documentation = {
                    "Returns `true` if this value represents a string.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::isString",
                    " @since 1.0"
    })
    public static PolyglotStatus poly_value_is_string(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(jValue.isString()));
        });
    }

    @CEntryPoint(name = "poly_value_is_number", documentation = {
                    "Returns `true` if this value represents a number.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::isNumber",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_is_number(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(jValue.isNumber()));
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_float", documentation = {
                    "Returns `true` if this value is a number and can fit into a C float.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::fitsInFloat",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_fits_in_float(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(dataObject.fitsInFloat()));
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_double", documentation = {
                    "Returns `true` if this value is a number and can fit into a C double.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::fitsInDouble",
                    " @since 1.0"
    })
    public static PolyglotStatus poly_value_fits_in_double(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(dataObject.fitsInDouble()));
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_int8", documentation = {
                    "Returns `true` if this value is a number and can fit into `int8_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::fitsInByte",
                    " @since 1.0"
    })
    public static PolyglotStatus poly_value_fits_in_int8(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(dataObject.fitsInByte()));
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_int16", documentation = {
                    "Returns `true` if this value is a number and can fit into `int16_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @since 1.0"
    })
    public static PolyglotStatus poly_value_fits_in_int16(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            boolean jResult = jValue.fitsInInt();
            if (jResult) {
                int intValue = jValue.asInt();
                jResult = intValue >= Short.MIN_VALUE && intValue <= Short.MAX_VALUE;
            }
            result.write(CTypeConversion.toCBoolean(jResult));
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_int32", documentation = {
                    "Returns `true` if this value is a number and can fit into `int32_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::fitsInInt",
                    " @since 1.0"
    })
    public static PolyglotStatus poly_value_fits_in_int32(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(dataObject.fitsInInt()));
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_int64", documentation = {
                    "Returns `true` if this value is a number and can fit into `int64_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @see org::graalvm::polyglot::Value::fitsInLong",
                    " @since 1.0"
    })
    public static PolyglotStatus poly_value_fits_in_int64(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(CTypeConversion.toCBoolean(dataObject.fitsInLong()));
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_uint8", documentation = {
                    "Returns `true` if this value is a number and can fit into `uint8_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @since 1.0"
    })
    public static PolyglotStatus poly_value_fits_in_uint8(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            boolean jResult = jValue.fitsInInt();
            if (jResult) {
                int intValue = jValue.asInt();
                jResult = intValue >= 0 && intValue <= MAX_UNSIGNED_BYTE;
            }
            result.write(CTypeConversion.toCBoolean(jResult));
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_uint16", documentation = {
                    "Returns `true` if this value is a number and can fit into `uint16_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @since 1.0"
    })
    public static PolyglotStatus poly_value_fits_in_uint16(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            boolean jResult = jValue.fitsInInt();
            if (jResult) {
                int intValue = jValue.asInt();
                jResult = intValue >= 0 && intValue <= MAX_UNSIGNED_SHORT;
            }
            result.write(CTypeConversion.toCBoolean(jResult));
        });
    }

    @CEntryPoint(name = "poly_value_fits_in_uint32", documentation = {
                    "Returns `true` if this value is a number and can fit into `uint32_t`.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if the underlying context was closed, if guest language error occurred ",
                    "        during execution.",
                    " @since 1.0"
    })
    public static PolyglotStatus poly_value_fits_in_uint32(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            boolean jResult = jValue.fitsInLong();
            if (jResult) {
                long intValue = jValue.asLong();
                jResult = intValue >= 0 && intValue <= MAX_UNSIGNED_INT;
            }
            result.write(CTypeConversion.toCBoolean(jResult));
        });
    }

    @CEntryPoint(name = "poly_value_as_string_utf8", documentation = {
                    "Converts a string value to a C string by filling the <code>buffer</code> with with a string encoded in UTF-8 and ",
                    "storing the number of written bytes to <code>result</code>. If the the buffer is <code>NULL</code> writes the required",
                    "size to <code>result</code>.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if a guest language error occurred during execution ",
                    "         poly_string_expected if the value is not a string.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_as_string_utf8(PolyglotIsolateThread thread, PolyglotValue value, CCharPointer buffer, UnsignedWord buffer_size, SizeTPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            if (jValue.isString()) {
                writeString(jValue.asString(), buffer, buffer_size, result, UTF8_CHARSET);
            } else {
                throw reportError("Expected type String but got " + jValue.getMetaObject().toString(), PolyglotStatus.poly_string_expected);
            }
        });
    }

    @CEntryPoint(name = "poly_value_to_string_utf8", documentation = {
                    "Returns a <code>toString</code> representation of a <code>poly_value</code> by filling the <code>buffer</code> with with a string encoded ",
                    "in UTF-8 and stores the number of written bytes to <code>result</code>. If the the buffer is <code>NULL</code> writes the ",
                    "required size to <code>result</code>.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if a guest language error occurred during execution ",
                    "         poly_string_expected if the value is not a string.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_to_string_utf8(PolyglotIsolateThread thread, PolyglotValue value, CCharPointer buffer, UnsignedWord buffer_size, SizeTPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            writeString(jValue.toString(), buffer, buffer_size, result, UTF8_CHARSET);
        });
    }

    @CEntryPoint(name = "poly_value_as_boolean", documentation = {
                    "Returns a boolean representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted. ",
                    " @see org::graalvm::polyglot::Value::asBoolean",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_as_bool(PolyglotIsolateThread thread, PolyglotValue value, CBoolPointer result) {
        return withHandledErrors(() -> {
            Value jValue = fetchHandle(value);
            if (jValue.isBoolean()) {
                result.write(CTypeConversion.toCBoolean(jValue.asBoolean()));
            } else {
                throw reportError("Expected type Boolean but got " + jValue.getMetaObject().toString(), PolyglotStatus.poly_boolean_expected);
            }
        });
    }

    @CEntryPoint(name = "poly_value_as_int8", documentation = {
                    "Returns a int8_t representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted. ",
                    " @see org::graalvm::polyglot::Value::asByte",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_as_int8(PolyglotIsolateThread thread, PolyglotValue value, CInt8Pointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            result.write(valueObject.asByte());
        });
    }

    @CEntryPoint(name = "poly_value_as_int16", documentation = {
                    "Returns a int32_t representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted.",
                    " @see org::graalvm::polyglot::Value::asInt",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_as_int16(PolyglotIsolateThread thread, PolyglotValue value, CInt16Pointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            int intValue = valueObject.asInt();
            if (intValue < Short.MIN_VALUE || intValue > Short.MAX_VALUE) {
                throw reportError("Value " + intValue + " does not fit into int_16_t.", poly_generic_failure);
            }
            result.write((short) intValue);
        });
    }

    @CEntryPoint(name = "poly_value_as_int32", documentation = {
                    "Returns a int32_t representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted.",
                    " @see org::graalvm::polyglot::Value::asInt",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_as_int32(PolyglotIsolateThread thread, PolyglotValue value, CInt32Pointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            result.write(valueObject.asInt());
        });
    }

    @CEntryPoint(name = "poly_value_as_int64", documentation = {
                    "Returns a int64_t representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted.",
                    " @see org::graalvm::polyglot::Value::asInt",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_as_int64(PolyglotIsolateThread thread, PolyglotValue value, CInt64Pointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            result.write(valueObject.asLong());
        });
    }

    @CEntryPoint(name = "poly_value_as_uint8", documentation = {
                    "Returns a uint8_t representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted.",
                    " @see org::graalvm::polyglot::Value::asInt",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_as_uint8(PolyglotIsolateThread thread, PolyglotValue value, CUnsignedBytePointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            int intValue = valueObject.asInt();
            if (intValue < 0 || intValue > MAX_UNSIGNED_BYTE) {
                throw reportError("Value " + Integer.toUnsignedString(intValue) + "does not fit in uint8_t", poly_generic_failure);
            }
            result.write((byte) intValue);
        });
    }

    @CEntryPoint(name = "poly_value_as_uint16", documentation = {
                    "Returns a uint16_t representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "         if the underlying context was closed, if value could not be converted.",
                    " @see org::graalvm::polyglot::Value::asInt",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_as_uint16(PolyglotIsolateThread thread, PolyglotValue value, CUnsignedShortPointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            int intValue = valueObject.asInt();
            if (intValue < 0 || intValue > MAX_UNSIGNED_SHORT) {
                throw reportError("Value " + Integer.toUnsignedString(intValue) + "does not fit in uint16_t", poly_generic_failure);
            }
            result.write((short) intValue);
        });
    }

    @CEntryPoint(name = "poly_value_as_uint32", documentation = {
                    "Returns a uint32_t representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "        if the underlying context was closed, if value could not be converted.",
                    " @see org::graalvm::polyglot::Value::asLong",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_as_uint32(PolyglotIsolateThread thread, PolyglotValue value, CUnsignedIntPointer result) {
        return withHandledErrors(() -> {
            Value valueObject = fetchHandle(value);
            long longValue = valueObject.asLong();
            if (longValue < 0 || longValue > MAX_UNSIGNED_INT) {
                throw reportError("Value " + Long.toUnsignedString(longValue) + "does not fit in uint32_t", poly_generic_failure);
            }
            result.write((int) longValue);
        });
    }

    @CEntryPoint(name = "poly_value_as_float", documentation = {
                    "Returns a float representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is null, if a guest language error occurred during execution, ",
                    "        if the underlying context was closed, if value could not be converted.",
                    " @see org::graalvm::polyglot::Value::asFloat",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_as_float(PolyglotIsolateThread thread, PolyglotValue value, CFloatPointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            result.write(dataObject.asFloat());
        });
    }

    @CEntryPoint(name = "poly_value_as_double", documentation = {
                    "Returns a double representation of the value.",
                    "",
                    " @return poly_ok if all works, poly_generic_failure if value is <code>null</code>, if a guest language error occurred during execution, ",
                    "        if the underlying context was closed, if value could not be converted.",
                    " @see org::graalvm::polyglot::Value::asDouble",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_value_as_double(PolyglotIsolateThread thread, PolyglotValue value, CDoublePointer result) {
        return withHandledErrors(() -> {
            Value dataObject = fetchHandle(value);
            if (dataObject.isNumber()) {
                result.write(dataObject.asDouble());
            } else {
                throw reportError("Value is not a number.", poly_number_expected);
            }
        });
    }

    @CEntryPoint(name = "poly_language_get_id", documentation = {
                    "Gets the primary identification string of this language. The language id is",
                    "used as the primary way of identifying languages in the polyglot API. (eg. <code>js</code>)",
                    "",
                    " @return a language ID string.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_language_get_id(PolyglotIsolateThread thread, PolyglotLanguage language, CCharPointer utf8_result, UnsignedWord buffer_size, SizeTPointer length) {
        return withHandledErrors(() -> {
            Language jLanguage = fetchHandle(language);
            writeString(jLanguage.getId(), utf8_result, buffer_size, length, UTF8_CHARSET);
        });
    }

    @CEntryPoint(name = "poly_get_last_error_info", documentation = {
                    "Returns information about last error that occurred on this thread in the poly_extended_error_info structure.",
                    "",
                    "This method must be called right after a failure occurs and can be called only once.",
                    "",
                    " @return information about the last failure on this thread.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_get_last_error_info(PolyglotIsolateThread thread, @CConst PolyglotExtendedErrorInfoPointer result) {
        ErrorInfoHolder errorInfoHolder = errorInfo.get();
        if (errorInfoHolder != null) {
            result.write(errorInfoHolder.info);
            return poly_ok;
        } else {
            return poly_generic_failure;
        }
    }

    @CEntryPoint(name = "poly_create_function", documentation = {
                    "Creates a polyglot function that calls back into native code.",
                    "",
                    " @param data user defined data to be passed into the function.",
                    " @param callback function that is called from the polyglot engine.",
                    " @return information about the last failure on this thread.",
                    " @see org::graalvm::polyglot::ProxyExecutable",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_create_function(PolyglotIsolateThread thread, PolyglotContext context, PolyglotCallback callback, VoidPointer data,
                    PolyglotValuePointer value) {
        return withHandledErrors(() -> {
            Context c = fetchHandle(context);
            ProxyExecutable executable = (Value... arguments) -> {
                ObjectHandle[] handleArgs = new ObjectHandle[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    handleArgs[i] = createHandle(arguments[i]);
                }
                PolyglotCallbackInfo cbInfo = (PolyglotCallbackInfo) createHandle(new PolyglotCallbackInfoInternal(handleArgs, data));
                try {
                    PolyglotValue result = callback.invoke((PolyglotIsolateThread) CEntryPointContext.getCurrentIsolateThread(), cbInfo);
                    CallbackException ce = exceptionsTL.get();
                    if (ce != null) {
                        exceptionsTL.remove();
                        throw ce;
                    } else {
                        return PolyglotNativeAPI.fetchHandle(result);
                    }
                } finally {
                    PolyglotCallbackInfoInternal info = fetchHandle(cbInfo);
                    for (ObjectHandle arg : info.arguments) {
                        destroyHandle(arg);
                    }
                    destroyHandle(cbInfo);
                }
            };
            value.write(createHandle(c.asValue(executable)));
        });
    }

    @CEntryPoint(name = "poly_get_callback_info", documentation = {
                    "Retrieves details about the call within a callback (e.g., the arguments from a given callback info).",
                    "",
                    " @param callback_info from the callback.",
                    " @param argc number of arguments to the callback.",
                    " @param argv poly_value array of arguments for the callback.",
                    " @param the data pointer for the callback.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_get_callback_info(PolyglotIsolateThread thread, PolyglotCallbackInfo callback_info, SizeTPointer argc, PolyglotValuePointer argv, WordPointer data) {
        return withHandledErrors(() -> {
            PolyglotCallbackInfoInternal callbackInfo = fetchHandle(callback_info);
            UnsignedWord numberOfArguments = WordFactory.unsigned(callbackInfo.arguments.length);
            UnsignedWord bufferSize = argc.read();
            UnsignedWord size = bufferSize.belowThan(numberOfArguments) ? bufferSize : numberOfArguments;
            argc.write(size);
            for (UnsignedWord i = WordFactory.zero(); i.belowThan(size); i = i.add(1)) {
                int index = (int) i.rawValue();
                ObjectHandle argument = callbackInfo.arguments[index];
                argv.write(index, argument);
            }
            data.write(callbackInfo.data);
        });
    }

    @CEntryPoint(name = "poly_throw_exception", documentation = {
                    "Raises an exception in a C callback.",
                    "",
                    "Invocation of this method does not interrupt control-flow so it is neccesarry to return from a function after ",
                    "the exception has been raised. If this method is called multiple times only the last exception will be thrown in",
                    "in the guest language.",
                    "",
                    " @param utf8_message 0 terminated error message.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_throw_exception(PolyglotIsolateThread thread, @CConst CCharPointer utf8_message) {
        return withHandledErrors(() -> exceptionsTL.set(new CallbackException(CTypeConversion.toJavaString(utf8_message))));
    }

    @CEntryPoint(name = "poly_destroy_handle", documentation = {
                    "Destroys a poly_handle. After this point, the handle must not be used anymore. ",
                    "",
                    "Handles are: poly_engine, poly_context, poly_context_builder, poly_language, poly_value, and poly_callback_info.",
                    " @since 1.0",
    })
    public static PolyglotStatus poly_destroy_handle(PolyglotIsolateThread thread, PolyglotHandle handle) {
        return withHandledErrors(() -> destroyHandle(handle));
    }

    private static class PolyglotCallbackInfoInternal {
        ObjectHandle[] arguments;
        VoidPointer data;

        PolyglotCallbackInfoInternal(ObjectHandle[] arguments, VoidPointer data) {
            this.arguments = arguments;
            this.data = data;
        }
    }

    private static void writeString(String valueString, CCharPointer buffer, UnsignedWord length, SizeTPointer result, Charset charset) {
        UnsignedWord stringLength = WordFactory.unsigned(valueString.getBytes(charset).length);
        if (buffer.isNull()) {
            result.write(stringLength);
        } else {
            result.write(CTypeConversion.toCString(valueString, charset, buffer, length));
        }
    }

    private static List<Language> sortedLangs(Engine engine) {

        return engine.getLanguages().entrySet().stream()
                        .sorted(Comparator.comparing(Entry::getKey))
                        .map(Entry::getValue)
                        .collect(Collectors.toList());
    }

    private static void resetErrorState() {
        ErrorInfoHolder current = errorInfo.get();
        if (current != null) {
            current.messageHolder.close();
            UnmanagedMemory.free(current.info);
            errorInfo.remove();
        }
    }

    private static RuntimeException reportError(String message, PolyglotStatus errorCode) {
        throw new PolyglotNativeAPIError(errorCode, message);
    }

    private static PolyglotStatus handleThrowable(Throwable t) {
        PolyglotStatus errorCode = t instanceof PolyglotNativeAPIError ? ((PolyglotNativeAPIError) t).getCode() : poly_generic_failure;
        PolyglotExtendedErrorInfo unmanagedErrorInfo = UnmanagedMemory.malloc(SizeOf.get(PolyglotExtendedErrorInfo.class));
        unmanagedErrorInfo.setErrorCode(errorCode.getCValue());
        CCharPointerHolder holder = CTypeConversion.toCString(t.getMessage());
        CCharPointer value = holder.get();
        unmanagedErrorInfo.setErrorMessage(value);
        errorInfo.set(new ErrorInfoHolder(unmanagedErrorInfo, holder));

        return errorCode;
    }

    private interface VoidThunk {
        void apply() throws Exception;
    }

    private static PolyglotStatus withHandledErrors(VoidThunk func) {
        resetErrorState();
        try {
            func.apply();
            return poly_ok;
        } catch (Throwable t) {
            return handleThrowable(t);
        }
    }

    private static ObjectHandle createHandle(Object result) {
        return ObjectHandles.getGlobal().create(result);
    }

    private static <T> T fetchHandle(ObjectHandle object) {
        return ObjectHandles.getGlobal().get(object);
    }

    private static void destroyHandle(ObjectHandle handle) {
        ObjectHandles.getGlobal().destroy(handle);
    }

    public static class CallbackException extends RuntimeException {
        static final long serialVersionUID = 123123098097526L;

        CallbackException(String message) {
            super(message);
        }
    }
}
