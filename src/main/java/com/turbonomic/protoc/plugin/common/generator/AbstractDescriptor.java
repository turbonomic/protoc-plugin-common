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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import com.turbonomic.protoc.plugin.common.generator.FileDescriptorProcessingContext.OuterClass;

@Immutable
public abstract class AbstractDescriptor {

    /**
     * The non-qualified name of this descriptor.
     */
    protected final String name;

    /**
     * The Java package this descriptor's code will be in.
     */
    protected final String javaPkgName;

    /**
     * The protobuf package that defined the descriptor.
     */
    protected final String protoPkgName;

    /**
     * The outer class the descriptor's code will be in.
     */
    protected final OuterClass outerClass;

    protected final Registry registry;

    /**
     * The names of the outer messages for this descriptor.
     * Only non-empty for nested {@link MessageDescriptor} and {@link EnumDescriptor}
     * definitions.
     */
    protected final List<String> outerMessages;

    protected AbstractDescriptor(@Nonnull final FileDescriptorProcessingContext context,
                              @Nonnull final String name) {
        this.name = name;
        this.javaPkgName = context.getJavaPackage();
        this.protoPkgName = context.getProtobufPackage();
        this.outerClass = context.getOuterClass();

        this.registry = context.getRegistry();

        // Report the name of the newly instantiated descriptor
        // to the outer class. This is to track name collisions
        // between the outer class and the descriptors within it.
        outerClass.onNewDescriptor(name);

        // Make a copy, because the outers in the context change
        // during processing.
        this.outerMessages = new ArrayList<>(context.getOuters());
    }

    /**
     * Get the unqualified name of this message. For example:
     * message Msg { <-- Msg
     *     message Msg2 { <-- Msg2
     *         message Msg3 {} <-- Msg3
     *     }
     * }
     * @return The unqualified name of the message.
     */
    @Nonnull
    public String getName() {
        return name;
    }

    /**
     * Gets the name of this message within the overarching outer class
     * that's generated for every .proto.
     * <p>
     * If the message is NOT nested, this name is equivalent to
     * {@link AbstractDescriptor#getName}.
     * <p>
     * Otherwise, it's the path to the nested message. For example:
     * message Msg { <-- Msg
     *     message Msg2 { <-- Msg.Msg2
     *         message Msg3 {} <-- Msg.Msg2.Msg3
     *     }
     * }
     * @return The name of this message qualified within the overarching
     *         outer class.
     */
    @Nonnull
    public String getNameWithinOuterClass(@Nonnull final TypeNameFormatter formatter) {
        if (outerMessages == null || outerMessages.isEmpty()) {
            return formatter.formatTypeName(name);
        } else {
            return outerMessages.stream()
                .map(formatter::formatTypeName)
                .collect(Collectors.joining(".")) + "." + formatter.formatTypeName(name);
        }
    }

    /**
     * Get the fully qualified name of the class this descriptor
     * will generate.
     *
     * @return The name.
     */
    @Nonnull
    public String getQualifiedName(@Nonnull final TypeNameFormatter formatter) {
        return javaPkgName + "." + outerClass.getPluginJavaClass() + "." + getNameWithinOuterClass(formatter);
    }

    @Nonnull
    public String getJavaPkgName() {
        return javaPkgName;
    }

    /**
     * Get the name of the original Java class generated by the
     * protobuf compiler for the message this descriptor relates
     * to.
     *
     * @return The name.
     */
    @Nonnull
    public String getQualifiedOriginalName() {
        // If multiple files is enabled, the protobuf compiler will NOT nest the classes for
        // messages in an outer class.
        if (outerClass.isMultipleFilesEnabled()) {
            return javaPkgName + "." + getNameWithinOuterClass(TypeNameFormatter.IDENTITY);
        } else {
            return javaPkgName + "." + outerClass.getProtoJavaClass() + "."
                + getNameWithinOuterClass(TypeNameFormatter.IDENTITY);
        }
    }

    /**
     * Get the fully qualified name of the protobuf message
     * this descriptor relates to.
     *
     * @return The name.
     */
    @Nonnull
    public String getQualifiedProtoName() {
        return protoPkgName + "." + getNameWithinOuterClass(TypeNameFormatter.IDENTITY);
    }
}
