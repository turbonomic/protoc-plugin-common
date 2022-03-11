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

import static com.turbonomic.protoc.plugin.common.generator.FieldDescriptor.formatFieldName;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import com.google.protobuf.DescriptorProtos.OneofDescriptorProto;

import org.apache.commons.lang3.StringUtils;

/**
 * A wrapper around {@link com.google.protobuf.DescriptorProtos.OneofDescriptorProto} with additional information.
 */
@Immutable
public class OneOfDescriptor extends AbstractDescriptor {

    /**
     * Comment for the entire enum.
     */
    private final String comment;

    /**
     * The index of this oneOf in its containing message relative to the others.
     */
    private final int oneOfIndex;

    OneOfDescriptor(@Nonnull final FileDescriptorProcessingContext context,
                    @Nonnull final OneofDescriptorProto oneofDescriptorProto,
                    final int oneOfIndex) {
        super(context, oneofDescriptorProto.getName());
        this.oneOfIndex = oneOfIndex;

        // Check for comments on the enum.
        comment = context.getCommentAtPath();
    }

    /**
     * Get the qualified name of the protobuf-generated class for this oneOf.
     *
     * @return the qualified name of the protobuf-generated class for this oneOf.
     */
    @Nonnull
    public String getQualifiedProtoName() {
        // If multiple files is enabled, the protobuf compiler will NOT nest the classes for
        // messages in an outer class.
        if (outerClass.isMultipleFilesEnabled()) {
            return javaPkgName + "." + getCapitalizedNameWithoutOuterClass();
        } else {
            return javaPkgName + "." + outerClass.getProtoJavaClass() + "." + getCapitalizedNameWithoutOuterClass();
        }
    }

    /**
     * Get the comment on this enum.
     * <p/>
     * // This is super important <- this comment
     * enum TestEnum {
     *    ...
     * }
     *
     * @return The comment.
     */
    @Nonnull
    public String getComment() {
        return comment;
    }

    /**
     * Get the index for this oneOf within its parent message.
     *
     * @return the index for this oneOf within its parent message.
     */
    public int getOneOfIndex() {
        return oneOfIndex;
    }

    /**
     * Similar to {@link #getNameWithinOuterClass()} except that we format and capitalize the name.
     *
     * @return The capitalized name of this oneOf qualified within the overarching
     *         outer class.
     */
    @Nonnull
    private String getCapitalizedNameWithoutOuterClass() {
        if (outerMessages == null || outerMessages.isEmpty()) {
            return capitalizedFormattedName();
        } else {
            return StringUtils.join(outerMessages, ".") + "." + capitalizedFormattedName();
        }
    }

    @Nonnull
    private String capitalizedFormattedName() {
        return StringUtils.capitalize(formatFieldName(name));
    }
}
