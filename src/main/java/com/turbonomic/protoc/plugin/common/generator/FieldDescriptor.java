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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import com.google.common.base.CaseFormat;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A wrapper around {@link FieldDescriptorProto}, which describes a single field in a message.
 * i.e:
 *    message TestMsg {
 *       optional int64 field = 1; <- Descriptor for this
 *   }
 */
@Immutable
public class FieldDescriptor {

    /**
     * The suffix to use when converting the field name to a Java name. We always add a suffix
     * to prevent clashes with default Java words.
     */
    private static final String JAVA_SUFFIX = "_";

    /**
     * The comment on the field.
     */
    private final String comment;

    private final Registry registry;

    private final MessageDescriptor parentMessage;

    private final FieldDescriptorProto fieldDescriptorProto;

    /**
     * If true, append the number/index of the field to the Java name.
     * This is required when multiple snake-case field names get converted to a single
     * camel-case name (e.g. my_field and my_field_).
     */
    private final boolean appendFieldNumber;

    /**
     * True iff the field is declared using proto3 syntax (i.e. no optional/required).
     */
    private final boolean isProto3;

    /**
     * Formatter for type name of the field.
     */
    private final TypeNameFormatter typeNameFormatter;

    private static final Logger logger = LogManager.getLogger();

    /**
     * @param context The context where the fields will be processed.
     * @param parentMessage The message within which the field is declared.
     * @param fieldDescriptorProto proto for the field.
     * @param duplicateNameMap A map containing the names of fields that end up being duplicates
     *                         in the message the field is defined in. Names end up as duplicates
     *                         when the only thing that distinguishes them is the underscore.
     * @param typeNameFormatter Formatter for the type used here.
     */
    public FieldDescriptor(@Nonnull final FileDescriptorProcessingContext context,
                           @Nonnull final MessageDescriptor parentMessage,
                           @Nonnull final FieldDescriptorProto fieldDescriptorProto,
                           @Nonnull final Map<String, Boolean> duplicateNameMap,
                           @Nonnull final TypeNameFormatter typeNameFormatter) {
        this.comment = context.getCommentAtPath();
        this.registry = context.getRegistry();
        this.fieldDescriptorProto = fieldDescriptorProto;
        this.parentMessage = parentMessage;
        appendFieldNumber =
                duplicateNameMap.getOrDefault(formatFieldName(fieldDescriptorProto.getName()),
                        false);
        this.isProto3 = context.isProto3Syntax();
        this.typeNameFormatter = Objects.requireNonNull(typeNameFormatter);
    }

    /**
     * Format the name of a field from snake case to camel case.
     *
     * @param name The name of the field.
     * @return The formatted field name.
     */
    @Nonnull
    public static String formatFieldName(@Nonnull final String name) {
        return name.contains("_") ? CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.LOWER_CAMEL, name) : name;
    }

    /**
     * Get the suffixed, camelcase name to use to refer to the field.
     *
     * @return The name.
     */
    @Nonnull
    public String getSuffixedName() {
        return getName() + JAVA_SUFFIX;
    }

    /**
     * Get the non-suffixed, camelcase name to use to refer to the field.
     *
     * @return The name.
     */
    @Nonnull
    public String getName() {
        String formattedName = formatFieldName(fieldDescriptorProto.getName());
        if (appendFieldNumber) {
            formattedName += fieldDescriptorProto.getNumber();
        }
        return formattedName;
    }

    @Nonnull
    public FieldDescriptorProto getProto() {
        return fieldDescriptorProto;
    }

    @Nonnull
    public String getComment() {
        return comment;
    }

    public boolean isProto3Syntax() {
        return isProto3;
    }

    public boolean isRequired() {
        return fieldDescriptorProto.getLabel().equals(Label.LABEL_REQUIRED);
    }

    /**
     * Get the camelcase name of the enclosing oneof, if this field is declared inside a oneof.
     *
     * oneof my_oneof {
     *    string this_field = 1;
     * }
     *
     * Will return:
     * myOneof
     *
     * @return An {@link Optional} containing the name of the enclosing oneof. An empty optional
     *         if the field is not enclosed in a oneof.
     */
    public Optional<String> getOneofName() {
        if (fieldDescriptorProto.hasOneofIndex()) {
            return Optional.of(formatFieldName(
                    parentMessage.getDescriptorProto().getOneofDecl(fieldDescriptorProto.getOneofIndex())
                            .getName()));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Get the name of the type for the field. This can either be a base type, or a fully qualified
     * type name referring to a class generated by this plugin according to a
     * {@link MessageDescriptor}.
     *
     * @return The type name.
     */
    @Nonnull
    public String getTypeName() {
        return getContentMessage()
                .map(descriptor -> typeNameFormatter.formatTypeName(
                    descriptor.getQualifiedName(typeNameFormatter)))
                .orElseGet(() -> getBaseFieldType(fieldDescriptorProto.getType()));
    }


    /**
     * Get the proto name of the type for the field. This can either be a base type, or a fully qualified
     * type name referring to a class generated by this plugin according to a
     * {@link MessageDescriptor}.
     *
     * @return The type name for the protobuf-compiler generated type.
     */
    public String getProtoTypeName() {
        return getContentMessage()
            .map(AbstractDescriptor::getQualifiedOriginalName)
            .orElseGet(() -> getBaseFieldType(fieldDescriptorProto.getType()));
    }

    public boolean isMapField() {
        return isList() && getContentMessage()
                .map(descriptor -> descriptor instanceof MessageDescriptor &&
                        ((MessageDescriptor) descriptor).isMapEntry())
                .orElse(false);
    }

    /**
     * Get the full Java type of the field. This is similar to {@link FieldDescriptor#getTypeName},
     * but will convert "repeated" and "map" fields to List and Map of the right type of objects.
     *
     * @return The Java type of the field.
     */
    @Nonnull
    public String getType() {
        String type;
        if (isMapField()) {
            type = getContentMessage()
                    .map(descriptor -> ((MessageDescriptor) descriptor).getMapTypeName())
                    .orElseThrow(() -> new IllegalStateException("Content message not present in map field."));
        } else {
            type = getTypeName();
            if (isList()) {
                type = "List<" + type + ">";
            }
        }
        return type;
    }

    public boolean isList() {
        return fieldDescriptorProto.getLabel() == Label.LABEL_REPEATED;
    }

    /**
     * Whether this field represents an enum.
     *
     * @return whether this field represents an enum.
     */
    public boolean isEnum() {
        return fieldDescriptorProto.getType() == Type.TYPE_ENUM;
    }

    /**
     * Returns the descriptor for the non-base message type of this field.
     *
     * @return An optional containing this descriptor, or an empty optional
     *         if the field is a base type.
     */
    @Nonnull
    public Optional<AbstractDescriptor> getContentMessage() {
        if (fieldDescriptorProto.getType() == Type.TYPE_MESSAGE || fieldDescriptorProto.getType() == Type.TYPE_ENUM) {
            return Optional.of(registry.getMessageDescriptor(fieldDescriptorProto.getTypeName()));
        }
        return Optional.empty();
    }

    @Nonnull
    private static String getBaseFieldType(@Nonnull final FieldDescriptorProto.Type type) {
        switch (type) {
            case TYPE_DOUBLE:
                return "Double";
            case TYPE_FLOAT:
                return "Float";
            case TYPE_INT64:
                return "Long";
            case TYPE_UINT64:
                return "Long";
            case TYPE_INT32:
                return "Integer";
            case TYPE_FIXED64:
                return "Long";
            case TYPE_FIXED32:
                return "Integer";
            case TYPE_BOOL:
                return "Boolean";
            case TYPE_STRING:
                return "String";
            case TYPE_BYTES:
                return "ByteString";
            case TYPE_UINT32:
                return "Integer";
            case TYPE_SFIXED32:
                return "Integer";
            case TYPE_SFIXED64:
                return "Long";
            case TYPE_SINT32:
                return "Integer";
            case TYPE_SINT64:
                return "Long";
            case TYPE_GROUP:
            case TYPE_MESSAGE:
            case TYPE_ENUM:
            default:
                throw new IllegalArgumentException("Unexpected non-base type: " + type);
        }
    }
}
