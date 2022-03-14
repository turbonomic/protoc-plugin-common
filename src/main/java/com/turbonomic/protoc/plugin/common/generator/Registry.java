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

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;

import org.apache.commons.lang3.StringUtils;


/**
 * The registry keeps track of already-processed descriptors.
 * Since there are links between the different descriptors (when they
 * reference other messages and/or packages) we need a central place to
 * index descriptor information. This is that place.
 */
public class Registry {

    private final Map<String, AbstractDescriptor> messageDescriptorMap = new HashMap<>();

    @Nonnull
    ServiceDescriptor registerService(final FileDescriptorProcessingContext context,
                                      final ServiceDescriptorProto serviceDescriptor,
                                      @Nonnull final TypeNameFormatter typeNameFormatter) {
        final ServiceDescriptor descriptor = new ServiceDescriptor(context, serviceDescriptor);
        messageDescriptorMap.put(descriptor.getQualifiedProtoName(), descriptor);
        messageDescriptorMap.put(descriptor.getNameWithinOuterClass(typeNameFormatter), descriptor);
        return descriptor;
    }

    @Nonnull
    EnumDescriptor registerEnum(final FileDescriptorProcessingContext context,
                                final EnumDescriptorProto enumDescriptor,
                                @Nonnull final TypeNameFormatter typeNameFormatter) {
        final EnumDescriptor descriptor = new EnumDescriptor(context, enumDescriptor);
        messageDescriptorMap.put(descriptor.getQualifiedProtoName(), descriptor);
        messageDescriptorMap.put(descriptor.getNameWithinOuterClass(typeNameFormatter), descriptor);
        return descriptor;
    }

    @Nonnull
    OneOfDescriptor registerOneOf(final FileDescriptorProcessingContext context,
                                  final OneofDescriptorProto oneofDescriptorProto,
                                  final int oneOfIndex,
                                  @Nonnull final TypeNameFormatter typeNameFormatter) {
        final OneOfDescriptor descriptor = new OneOfDescriptor(context, oneofDescriptorProto, oneOfIndex);
        messageDescriptorMap.put(descriptor.getQualifiedProtoName(), descriptor);
        messageDescriptorMap.put(descriptor.getNameWithinOuterClass(typeNameFormatter), descriptor);
        return descriptor;
    }

    @Nonnull
    MessageDescriptor registerMessage(final FileDescriptorProcessingContext context,
                                      final DescriptorProto descriptorProto) {
        final ImmutableList.Builder<AbstractDescriptor> childrenBuilder = new ImmutableList.Builder<>();

        // Processing the messages, need to include that in the path.
        context.startNestedMessageList(descriptorProto.getName());
        for (int i = 0; i < descriptorProto.getNestedTypeCount(); ++i) {
            context.startListElement(i);
            final DescriptorProto nestedDescriptor = descriptorProto.getNestedType(i);
            final MessageDescriptor descriptor = registerMessage(
                    context,
                    nestedDescriptor);
            childrenBuilder.add(descriptor);

            context.endListElement();
        }
        // Done processing messages.
        context.endNestedMessageList();

        // Process nested enums.
        context.startNestedEnumList(descriptorProto.getName());
        for (int nestedEnumIdx = 0; nestedEnumIdx < descriptorProto.getEnumTypeCount(); ++nestedEnumIdx) {
            context.startListElement(nestedEnumIdx);
            final EnumDescriptorProto nestedEnum = descriptorProto.getEnumType(nestedEnumIdx);

            final EnumDescriptor descriptor = registerEnum(context, nestedEnum, context.getTypeNameFormatter());
            childrenBuilder.add(descriptor);

            context.endListElement();
        }
        // Done processing nested enums.
        context.endNestedEnumList();

        context.startNestedOneOfList(descriptorProto.getName());
        for (int oneOfIdx = 0; oneOfIdx < descriptorProto.getOneofDeclCount(); ++oneOfIdx) {
            context.startListElement(oneOfIdx);
            final OneofDescriptorProto nestedOneOf = descriptorProto.getOneofDecl(oneOfIdx);

            final OneOfDescriptor descriptor = registerOneOf(context, nestedOneOf, oneOfIdx, context.getTypeNameFormatter());
            childrenBuilder.add(descriptor);

            context.endListElement();
        }
        context.endNestedOneOfList();

        MessageDescriptor typeDescriptor = new MessageDescriptor(context, descriptorProto,
            childrenBuilder.build(), context.getTypeNameFormatter());

        messageDescriptorMap.put(typeDescriptor.getQualifiedProtoName(), typeDescriptor);
        messageDescriptorMap.put(typeDescriptor.getNameWithinOuterClass(context.getTypeNameFormatter()), typeDescriptor);
        return typeDescriptor;
    }

    /**
     * Gets a message descriptor in the registry by name.
     * Since we should never try to retrieve descriptors that we haven't processed
     * this method throws a runtime exception if the descriptor is not registered.
     *
     * @param name The name of the descriptor. This can be the short name
     *             (e.g. TestMessage) or the fully qualified name prefixed with
     *             a "." (e.g. .testPkg.TestMessage).
     * @return The descriptor associated with the name.
     * @throws IllegalStateException If the descriptor is not present in the registry.
     */
    @Nonnull
    public AbstractDescriptor getMessageDescriptor(@Nonnull final String name) {
        AbstractDescriptor result = null;
        // The protobuf compiler presents fully qualified names prefixed with a ".".
        // although if the message descriptor is from a proto file with no proto
        // package it may also be missing the dot.
        if (name.startsWith(".")) {
            result = messageDescriptorMap.get(StringUtils.removeStart(name, "."));
        }

        // If the lookup above failed, or if we are looking up a message descriptor
        // by its compiler plugin equivalent name, do the associated lookup.
        if (result == null) {
            result = messageDescriptorMap.get(name);
        }

        if (result == null) {
            throw new IllegalStateException("Message descriptor " + name
                    + " is not present in the registry.");
        }
        return result;
    }

}
