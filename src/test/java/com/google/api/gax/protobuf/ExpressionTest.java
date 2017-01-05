/*
 * Copyright 2016, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.google.api.gax.protobuf;

import com.google.api.gax.protobuf.Test.NestedMessage;
import com.google.api.gax.protobuf.Test.TestMessage;
import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link Expression}.
 */
@RunWith(JUnit4.class)
public class ExpressionTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  // Parsing
  // =======

  @Test
  public void illegalCharacter() {
    thrown.expectMessage("unrecognized input '+ x'");
    Expression.parsePath(TestMessage.getDescriptor(), "a+ x");
  }

  @Test
  public void wrongSyntax() {
    thrown.expectMessage("unexpected token '['");
    Expression.parsePath(TestMessage.getDescriptor(), "[1]t");
  }

  @Test
  public void superfluousTokens() {
    thrown.expectMessage("unexpected token 'a', expected '<end of input>'");
    Expression.parsePath(TestMessage.getDescriptor(), "int32_field a");
  }

  // Checking
  // ========

  @Test
  public void undeclared() {
    thrown.expectMessage("'a' is not declared");
    Expression.parsePath(TestMessage.getDescriptor(), "a");
  }

  @Test
  public void undeclaredNested() {
    thrown.expectMessage("'a' is not declared");
    Expression.parsePath(TestMessage.getDescriptor(), "nested_field.a");
  }

  @Test
  public void selectOnPrimitive() {
    thrown.expectMessage("'int64' is not a message");
    Expression.parsePath(TestMessage.getDescriptor(), "int32_field.a");
  }

  @Test
  public void indexOnNonRepeated() {
    thrown.expectMessage("index operation not applicable");
    Expression.parsePath(TestMessage.getDescriptor(), "int32_field[0]");
  }

  @Test
  public void stringAsRepeatedIndex() {
    thrown.expectMessage("provided type 'string' does not match expected type 'int64'");
    Expression.parsePath(TestMessage.getDescriptor(), "repeated_nested_field[\"foo\"]");
  }

  @Test
  public void wrongMapKeyIndex() {
    thrown.expectMessage("provided type 'int64' does not match expected type 'string'");
    Expression.parsePath(TestMessage.getDescriptor(), "map_nested_field[0]");
  }

  // Typing
  // ======

  @Test
  public void typing() {
    Truth.assertThat(Expression.parsePath(TestMessage.getDescriptor(), "int32_field").getType())
        .isEqualTo(Type.INT64);
    Truth.assertThat(Expression.parsePath(TestMessage.getDescriptor(), "string_field").getType())
        .isEqualTo(Type.STRING);
    Truth.assertThat(Expression.parsePath(TestMessage.getDescriptor(), "nested_field").getType())
        .isEqualTo(Type.forMessage(NestedMessage.getDescriptor()));
    Truth.assertThat(
            Expression.parsePath(TestMessage.getDescriptor(), "nested_field.nested_nested_field")
                .getType())
        .isEqualTo(Type.forMessage(NestedMessage.getDescriptor()));
    Truth.assertThat(
            Expression.parsePath(TestMessage.getDescriptor(), "nested_field.repeated_field")
                .getType())
        .isEqualTo(Type.forRepeated(Type.STRING));
    Truth.assertThat(
            Expression.parsePath(TestMessage.getDescriptor(), "map_nested_field").getType())
        .isEqualTo(Type.forMap(Type.STRING, Type.forMessage(NestedMessage.getDescriptor())));
  }

  // Evaluation
  // ==========

  private void expectEval(TestMessage target, String source, Object result) {
    Expression expr = Expression.parsePath(target.getDescriptorForType(), source);
    Truth.assertThat(expr.eval(target)).isEqualTo(result);
  }

  @Test
  public void simpleIntFieldEval() {
    expectEval(TestMessage.newBuilder().setInt32Field(23).build(), "int32_field", 23);
  }

  @Test
  public void simpleStringFieldEval() {
    expectEval(TestMessage.newBuilder().setStringField("yes").build(), "string_field", "yes");
  }

  @Test
  public void nestedStringFieldEval() {
    expectEval(
        TestMessage.newBuilder()
            .setNestedField(NestedMessage.newBuilder().setNestedStringField("yeah"))
            .build(),
        "nested_field.nested_string_field",
        "yeah");
  }

  @Test
  public void nestedRepeatedFieldEval() {
    expectEval(
        TestMessage.newBuilder()
            .setNestedField(
                NestedMessage.newBuilder().addRepeatedField("yeah").addRepeatedField("yes"))
            .build(),
        "nested_field.repeated_field[1]",
        "yes");
  }

  @Test
  public void repeatedNestedSelectEval() {
    expectEval(
        TestMessage.newBuilder()
            .addRepeatedNestedField(NestedMessage.newBuilder().setNestedStringField("hi"))
            .build(),
        "repeated_nested_field[0].nested_string_field",
        "hi");
  }

  @Test
  public void mapNestedSelectEval() {
    expectEval(
        TestMessage.newBuilder()
            .putAllMapNestedField(
                ImmutableMap.of("a", NestedMessage.newBuilder().setNestedStringField("hi").build()))
            .build(),
        "map_nested_field[\"a\"].nested_string_field",
        "hi");
  }

  // Assignment
  // ==========

  private void expectAssign(String source, Object value) {
    Expression expr = Expression.parsePath(TestMessage.getDescriptor(), source);
    TestMessage.Builder builder = TestMessage.newBuilder();
    expr.assign(builder, value);
    Truth.assertThat(expr.eval(builder.build())).isEqualTo(value);
  }

  @Test
  public void simpleIntFieldAssign() {
    expectAssign("int32_field", 23);
  }

  @Test
  public void simpleStringFieldAssign() {
    expectAssign("string_field", "yes");
  }

  @Test
  public void nestedStringFieldAssign() {
    expectAssign("nested_field.nested_string_field", "yeah");
  }

  @Test
  public void notImplementedListAssign() {
    // We haven't yet implemented assign on lists / maps.
    thrown.expectMessage("not assignable");
    expectAssign("nested_field.repeated_field[1]", "yeah");
  }
}
