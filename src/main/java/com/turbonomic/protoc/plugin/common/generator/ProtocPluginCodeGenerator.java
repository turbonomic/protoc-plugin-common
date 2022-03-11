/*
 * Copyright (C) 2009 - 2018 Turbonomic, Inc.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */
package com.turbonomic.protoc.plugin.common.generator;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.api.AnnotationsProto;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse.File;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.turbonomic.protoc.plugin.common.generator.TypeNameFormatter.IdentityTypeNameFormatter;

/**
 * This is the central class that does the heavy lifting of:
 * 1) Reading and parsing the {@link CodeGeneratorRequest}
 * 2) Generating the code (extensions of this class control how this is actually done).
 * 3) Writing out the {@link CodeGeneratorResponse}.
 * <p>
 * The user of this class should implement the abstract methods and override the necessary
 * code generation methods, and then use {@link ProtocPluginCodeGenerator#generate()} from
 * their plugin's Main class to do the generation.
 */
public abstract class ProtocPluginCodeGenerator {

    private static final Logger logger = LogManager.getLogger();

    /**
     * Stores information about processed messages.
     */
    private final Registry registry = new Registry();

    /**
     * This method should return the name of the plugin.
     * This will appear in the comments of the generated files (so that it's clear which plugin
     * generated them).
     *
     * @return The name of the plugin.
     */
    @Nonnull
    public abstract String getPluginName();

    /**
     * This method should return the name of the plugin's outer Java class for a particular file,
     * derived from the name of the outer Java class generated by the regular protobuf compiler.
     *
     * All generated classes from a particular file will be inside this outer class. This is
     * identical to the behaviour of the regular protobuf compiler for Java code, where all
     * classes inside a single .proto file are wrapped in a single Java outer class.
     *
     * @param protoJavaClass The Java class generated by the regular protobuf compiler. For example
     *                       if you have a TestDTO.proto file, this will be "TestDTO".
     * @return The Java class name for the file generated by the user's plugin. For example, if
     *         your plugin is "protoc-generate-money", this might be "TestDTOMoney."
     */
    @Nonnull
    protected abstract String generatePluginJavaClass(@Nonnull final String protoJavaClass);

    /**
     * Generate the Java imports required by the generated code in your plugin.
     * @return A string, listing all the imports. e.g: "import java.util.List;import java.util.Map;"
     */
    @Nonnull
    protected abstract String generateImports();

    /**
     * Generate the Java code for a particular {@link EnumDescriptor}.
     *
     * @param enumDescriptor The {@link EnumDescriptor} for an enum defined in the .proto file.
     * @return An {@link Optional} containing a string of Java code generated from the input descriptor.
     *         An empty {@link Optional} if this particular plugin doesn't want to generate anything based
     *         on enums.
     */
    @Nonnull
    protected Optional<String> generateEnumCode(@Nonnull final EnumDescriptor enumDescriptor) {
        return Optional.empty();
    }

    /**
     * Generate the Java code for a particular {@link MessageDescriptor}.
     *
     * @param messageDescriptor The {@link MessageDescriptor} for a message defined in the .proto file.
     * @return An {@link Optional} containing a string of Java code generated from the input descriptor.
     *         An empty {@link Optional} if this particular plugin doesn't want to generate anything based
     *         on messages.
     */
    @Nonnull
    protected Optional<String> generateMessageCode(@Nonnull final MessageDescriptor messageDescriptor) {
        return Optional.empty();
    }

    /**
     * Generate the Java code for a particular {@link ServiceDescriptor}.
     *
     * @param serviceDescriptor The {@link ServiceDescriptor} for a message defined in the .proto file.
     * @return An {@link Optional} containing a string of Java code generated from the input descriptor.
     *         An empty {@link Optional} if this particular plugin doesn't want to generate anything based
     *         on services.
     */
    @Nonnull
    protected Optional<String> generateServiceCode(@Nonnull final ServiceDescriptor serviceDescriptor) {
        return Optional.empty();
    }

    /**
     * Generate the Java code for a particular oneof parent.
     *
     * @param oneOfDescriptor The {@link OneOfDescriptor} for a oneof defined in the .proto file.
     *                        Note that this is the oneOf parent, and not the individual oneOf variants.
     *                        The oneOf variants have their own message descriptors that are processed
     *                        independently.
     * @return An {@link Optional} containing a string of Java code generated from the input descriptor.
     *         An empty {@link Optional} if this particular plugin doesn't want to generate anything based
     *         on services.
     */
    protected Optional<String> generateOneOfCode(@Nonnull final OneOfDescriptor oneOfDescriptor) {
        return Optional.empty();
    }

    /**
     * Generate miscellaneous extra files not associated with any particular proto file.
     * This can be used to, for example, declare an interface only one time that may be shared
     * by multiple generated files.
     *
     * @return The list of miscellaneous files to be add to the list of generated files.
     */
    @Nonnull
    protected List<File> generateMiscellaneousFiles() {
        return Collections.emptyList();
    }

    /**
     * Whether or not to skip generating code for a particular file.
     * Some plugins may only want to generate code for specific files (e.g. files with services
     * defined in them). This method provides a way to do that.
     *
     * Skipped files still get processed for types defined in them. The skipping applies only
     * to the code generation step.
     *
     * @param fileDescriptorProto The descriptor of the file.
     * @return True if the plugin does not want to generate any code for this file. False otherwise.
     */
    protected boolean skipFile(@Nonnull final FileDescriptorProto fileDescriptorProto) {
        return false;
    }

    /**
     * Get the data source for the protobuf files to read.
     *
     * @return The input stream source.
     */
    public InputStream inputStreamSource() {
        return System.in;
    }

    /**
     * The output stream for the Java source files generated.
     *
     * @return The output stream sink.
     */
    public OutputStream outputStreamSink() {
        return System.out;
    }

    @Nonnull
    public final Optional<String> generateCode(@Nonnull final AbstractDescriptor abstractDescriptor) {
        if (abstractDescriptor instanceof EnumDescriptor) {
            return generateEnumCode((EnumDescriptor)abstractDescriptor);
        } else if (abstractDescriptor instanceof MessageDescriptor) {
            return generateMessageCode((MessageDescriptor)abstractDescriptor);
        } else if (abstractDescriptor instanceof ServiceDescriptor) {
            return generateServiceCode((ServiceDescriptor)abstractDescriptor);
        } else if (abstractDescriptor instanceof OneOfDescriptor) {
            return generateOneOfCode((OneOfDescriptor)abstractDescriptor);
        } else {
            throw new IllegalArgumentException("Unsupported abstract descriptor of class " +
                    abstractDescriptor.getClass().getName());
        }
    }

    /**
     * This is the interface into the {@link ProtocPluginCodeGenerator}.
     * Plugin implementations should call this method from their Main class to:
     * 1) Read the {@link CodeGeneratorRequest} from stdin.
     * 2) Generate code and format a {@link CodeGeneratorResponse}.
     * 3) Write the response to stdout.
     *
     * @throws IOException If there is an issue with reading/writing from/to input/output streams.
     */
    public final void generate() throws IOException {
        final ExtensionRegistry extensionRegistry = ExtensionRegistry.newInstance();
        extensionRegistry.add(AnnotationsProto.http);

        final CodeGeneratorRequest req =
                CodeGeneratorRequest.parseFrom(new BufferedInputStream(inputStreamSource()), extensionRegistry);
        final AtomicReference<String> maximalSubpackage = new AtomicReference<>();
        req.getProtoFileList()
            .forEach(file -> updateMaximalSubPackage(file, maximalSubpackage));

        // The request presents the proto file descriptors in topological order
        // w.r.t. dependencies - i.e. the dependencies appear before the dependents.
        // This means we can process one file at a time without a separate linking step,
        // as long as we record the processed messages in the registry.
        final CodeGeneratorResponse.Builder response = CodeGeneratorResponse.newBuilder()
                .addAllFile(req.getProtoFileList().stream()
                        .map(file -> generateFile(file, maximalSubpackage.get()))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList()));

        // Add all miscellaneous files as well.
        response.addAllFile(generateMiscellaneousFiles());

        final BufferedOutputStream outputStream = new BufferedOutputStream(outputStreamSink());
        response.build().writeTo(outputStream);
        outputStream.flush();
    }

    private void updateMaximalSubPackage(@Nonnull final FileDescriptorProto file,
                                         @Nonnull final AtomicReference<String> maximalSubpackage) {
        final String curMax = maximalSubpackage.get();
        if (file.getOptions().hasJavaPackage()) {
            maximalSubpackage.set(computeMaximalSubpackage(curMax, file.getOptions().getJavaPackage()));
        } else if (file.hasPackage()) {
            maximalSubpackage.set(computeMaximalSubpackage(curMax, file.getPackage()));
        }

        if (maximalSubpackage.get() != null && maximalSubpackage.get().endsWith(".")) {
            maximalSubpackage.set(StringUtils.removeEnd(maximalSubpackage.get(), "."));
        }
    }

    private String computeMaximalSubpackage(@Nullable String curMax, @Nonnull final String nextPackage) {
        return curMax == null
            ? nextPackage
            : StringUtils.getCommonPrefix(curMax, nextPackage);
    }

    /**
     * Generate a {@link FileDescriptorProcessingContext} for use in processing proto files.
     *
     * @param registry The registry to pass to the processing context.
     * @param fileDescriptorProto The proto for the file to process.
     * @param maximalSubpackage The maximal Java subpackage shared by all files to be processed.
     * @return a {@link FileDescriptorProcessingContext} for use in processing proto files.
     */
    @Nonnull
    protected FileDescriptorProcessingContext createFileDescriptorProcessingContext(
        @Nonnull final Registry registry, @Nonnull final FileDescriptorProto fileDescriptorProto,
        @Nullable String maximalSubpackage) {
        return new FileDescriptorProcessingContext(this, registry, fileDescriptorProto,
            new IdentityTypeNameFormatter());
    }

    @Nonnull
    private Optional<File> generateFile(@Nonnull final FileDescriptorProto fileDescriptorProto,
                                        @Nullable final String maximalSubpackage) {
        logger.info("Registering messages in file: {} in package: {}",
                fileDescriptorProto.getName(),
                fileDescriptorProto.getPackage());

        final FileDescriptorProcessingContext context =
            createFileDescriptorProcessingContext(registry, fileDescriptorProto, maximalSubpackage);
        context.startEnumList();
        for (int enumIdx = 0; enumIdx < fileDescriptorProto.getEnumTypeCount(); ++enumIdx) {
            context.startListElement(enumIdx);
            final EnumDescriptorProto enumDescriptor = fileDescriptorProto.getEnumType(enumIdx);
            registry.registerEnum(context, enumDescriptor, context.getTypeNameFormatter());
            context.endListElement();
        }
        context.endEnumList();

        context.startMessageList();
        for (int msgIdx = 0; msgIdx < fileDescriptorProto.getMessageTypeCount(); ++msgIdx) {
            context.startListElement(msgIdx);
            final DescriptorProto msgDescriptor = fileDescriptorProto.getMessageType(msgIdx);
            registry.registerMessage(context, msgDescriptor);
            context.endListElement();
        }
        context.endMessageList();

        context.startServiceList();
        for (int svcIdx = 0; svcIdx < fileDescriptorProto.getServiceCount(); ++svcIdx) {
            context.startListElement(svcIdx);
            final ServiceDescriptorProto svcDescriptor = fileDescriptorProto.getService(svcIdx);
            registry.registerService(context, svcDescriptor, context.getTypeNameFormatter());
            context.endListElement();
        }
        context.endServiceList();

        logger.info("Generating messages in file: {} in package: {}",
                fileDescriptorProto.getName(),
                fileDescriptorProto.getPackage());

        if (skipFile(fileDescriptorProto)) {
            return Optional.empty();
        } else {
            return Optional.of(context.generateFile());
        }
    }
}
