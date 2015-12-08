/*
 * Copyright (c) 2015 FUJI Goro (gfx).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.gfx.android.orma.processor;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.lang.model.element.Modifier;

public class ConditionQueryHelpers {

    private final SchemaDefinition schema;

    private final ClassName targetClassName;

    private final SqlGenerator sql = new SqlGenerator();

    public ConditionQueryHelpers(SchemaDefinition schema, ClassName targetClassName) {
        this.schema = schema;
        this.targetClassName = targetClassName;
    }

    public List<MethodSpec> buildConditionHelpers() {
        List<MethodSpec> methodSpecs = new ArrayList<>();
        schema.getColumns()
                .stream()
                .filter(column -> column.indexed || column.primaryKey)
                .forEach(column -> buildConditionHelpersForEachColumn(methodSpecs, column));
        return methodSpecs;
    }

    void buildConditionHelpersForEachColumn(List<MethodSpec> methodSpecs, ColumnDefinition column) {

        ParameterSpec.Builder paramSpecBuilder = conditionParamSpecBuilder(column, column.name);

        if (column.primaryKey) {
            methodSpecs.add(
                    MethodSpec.methodBuilder("find")
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(paramSpecBuilder.build())
                            .returns(targetClassName)
                            .addStatement("return where($S, $L)",
                                    sql.quoteIdentifier(column.columnName) + " = ?", column.name)
                            .build()
            );

            return;
        }

        if (column.nullable) {
            methodSpecs.add(
                    MethodSpec.methodBuilder(column.name + "IsNull")
                            .addModifiers(Modifier.PUBLIC)
                            .returns(targetClassName)
                            .addStatement("return where($S)", sql.quoteIdentifier(column.columnName) + " IS NULL")
                            .build()
            );

            methodSpecs.add(
                    MethodSpec.methodBuilder(column.name + "IsNotNull")
                            .addModifiers(Modifier.PUBLIC)
                            .returns(targetClassName)
                            .addStatement("return where($S)", sql.quoteIdentifier(column.columnName) + " IS NOT NULL")
                            .build()
            );
        }

        methodSpecs.add(
                MethodSpec.methodBuilder(column.name + "Eq")
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(paramSpecBuilder.build())
                        .returns(targetClassName)
                        .addStatement("return where($S, $L)", sql.quoteIdentifier(column.columnName) + " = ?", column.name)
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder(column.name + "NotEq")
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(paramSpecBuilder.build())
                        .returns(targetClassName)
                        .addStatement("return where($S, $L)", sql.quoteIdentifier(column.columnName) + " <> ?", column.name)
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder(column.name + "In")
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ParameterSpec.builder(Types.getCollection(column.getBoxType()), "values")
                                .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                .build())
                        .returns(targetClassName)
                        .addStatement("return in(false, $S, values)", sql.quoteIdentifier(column.columnName))
                        .build()
        );

        methodSpecs.add(
                MethodSpec.methodBuilder(column.name + "NotIn")
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ParameterSpec.builder(Types.getCollection(column.getBoxType()), "values")
                                .addAnnotation(Specs.buildNonNullAnnotationSpec())
                                .build())
                        .returns(targetClassName)
                        .addStatement("return in(true, $S, values)", sql.quoteIdentifier(column.columnName))
                        .build()
        );

        if (isNumberType(column.getUnboxType())) {
            methodSpecs.add(
                    MethodSpec.methodBuilder(column.name + "Lt")
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(paramSpecBuilder.build())
                            .returns(targetClassName)
                            .addStatement("return where($S, $L)",
                                    sql.quoteIdentifier(column.columnName) + " < ?", column.name)
                            .build()
            );
            methodSpecs.add(
                    MethodSpec.methodBuilder(column.name + "Le")
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(paramSpecBuilder.build())
                            .returns(targetClassName)
                            .addStatement("return where($S, $L)",
                                    sql.quoteIdentifier(column.columnName) + " <= ?", column.name)
                            .build()
            );
            methodSpecs.add(
                    MethodSpec.methodBuilder(column.name + "Gt")
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(paramSpecBuilder.build())
                            .returns(targetClassName)
                            .addStatement("return where($S, $L)",
                                    sql.quoteIdentifier(column.columnName) + " > ?", column.name)
                            .build()
            );
            methodSpecs.add(
                    MethodSpec.methodBuilder(column.name + "Ge")
                            .addModifiers(Modifier.PUBLIC)
                            .addParameter(paramSpecBuilder.build())
                            .returns(targetClassName)
                            .addStatement("return where($S, $L)",
                                    sql.quoteIdentifier(column.columnName) + " >= ?", column.name)
                            .build()
            );
        }
    }

    public boolean isNumberType(TypeName typeName) {
        return typeName.equals(TypeName.BYTE)
                || typeName.equals(TypeName.SHORT)
                || typeName.equals(TypeName.INT)
                || typeName.equals(TypeName.LONG)
                || typeName.equals(TypeName.FLOAT)
                || typeName.equals(TypeName.DOUBLE);
    }

    public ParameterSpec.Builder conditionParamSpecBuilder(ColumnDefinition column, String name) {
        return ParameterSpec.builder(column.getType(), name)
                .addAnnotations(nullabilityAnnotations(column));
    }

    public List<AnnotationSpec> nullabilityAnnotations(ColumnDefinition column) {
        if (column.getType().isPrimitive()) {
            return Collections.emptyList();
        }

        if (column.nullable) {
            return Collections.singletonList(Specs.buildNullableAnnotationSpec());
        } else {
            return Collections.singletonList(Specs.buildNonNullAnnotationSpec());
        }
    }

}