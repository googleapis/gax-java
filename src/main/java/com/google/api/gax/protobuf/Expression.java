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

import com.google.auto.value.AutoValue;
import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.inject.Key;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.Message;
import com.google.protobuf.Message.Builder;
import com.google.protobuf.MessageOrBuilder;

import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/**
 * Represents an expression which can be evaluated against a protocol buffer, or used to assign
 * a field value.
 *
 * <p>Currently this supports only path expressions of the form:
 *
 * <pre>
 * pathExpr ::= id | pathExpr '.' id | pathExpr '[' constant ']'
 * </pre>
 */
@Beta
public abstract class Expression {

  // TODO(wrwg): propagate positions into errors (we are tracking them in type Token but
  //   don't do anything about them)

  // TODO(wrwg): implement and test assignment for repeated lists and for maps.

  // Errors
  // ======

  /**
   * An unchecked exception thrown when execution of an expression fails.
   */
  public static class ExecutionException extends IllegalArgumentException {
    private ExecutionException(String format, Object... args) {
      super(String.format(format, args));
    }
  }

  // Parsing
  // ========

  /**
   * Parses a path expression in the context of the given target message type.
   *
   * @throws ValidationException on parse errors.
   */
  public static Expression parsePath(Descriptor targetType, String source) {
    Parser parser = new Parser(targetType, source);
    Expression result = parser.parsePathExpr();
    parser.checkEof();
    return result;
  }

  /**
   * Parses a path expression, and returns null on validation errors.
   */
  public static Expression tryParsePath(Descriptor targetType, String source) {
    try {
      return parsePath(targetType, source);
    } catch (ValidationException e) {
      return null;
    }
  }

  // Typing
  // ======

  /**
   * Returns the type of this expression.
   */
  public Type getType() {
    return getAttribute(TYPE_ATTRIBUTE);
  }

  // Evaluation
  // ==========

  /**
   * Executes the expression in the context of the given target message value. Field
   * selections are evaluated against the target value.
   *
   * @throws ExecutionException on runtime errors.
   */
  public abstract Object eval(MessageOrBuilder targetValue);

  /**
   * Executes the expression in the context of the given target message value, and
   * casts the result to the specified type.
   */
  public <T> T eval(Class<T> type, MessageOrBuilder targetValue) {
    return type.cast(eval(targetValue));
  }

  // Assignment
  // ==========

  /**
   * Assigns a value to the location denoted by the expression. The expression must be
   * assignable, as it is the case for a path expression.
   */
  @SuppressWarnings("unused")
  public Message.Builder assign(Message.Builder targetBuilder, Object value) {
    // By default, expressions are not assignable.
    throw new ExecutionException("expression is not assignable: %s", this);
  }

  /**
   * Gets a builder for the location denoted by the expression. Helper for implementing
   * assign.
   */
  @SuppressWarnings("unused")
  protected Message.Builder getBuilder(Message.Builder targetBuilder) {
    // By default, expressions cannot construct a builder.
    throw new ExecutionException("expression is not assignable: %s", this);
  }

  // Attributes
  // ==========

  static final Key<Type> TYPE_ATTRIBUTE = Key.get(Type.class, Names.named("type"));
  static final Key<FieldDescriptor> FIELD_ATTRIBUTE =
      Key.get(FieldDescriptor.class, Names.named("field"));

  private Map<Key<?>, Object> attributes = Maps.newHashMap();

  /**
   * Gets an attribute value, or null, if the attribute is not present.
   */
  @SuppressWarnings("unchecked")
  <T> T getAttribute(Key<T> key) {
    return (T) attributes.get(key);
  }

  /**
   * Requires an attribute. An exception is thrown if the attribute is not available.
   */
  <T> T requireAttribute(Key<T> key) {
    T x = getAttribute(key);
    if (x != null) {
      return x;
    }
    throw new ExecutionException(
        "%s attribute is not available for: %s", ((Named) key.getAnnotation()).value(), this);
  }

  /**
   * Puts an attribute value, returning the old one.
   */
  @SuppressWarnings("unchecked")
  @Nullable
  <T> T putAttribute(Key<T> key, T value) {
    return (T) attributes.put(key, Preconditions.checkNotNull(value));
  }

  // Abstract Syntax
  // ===============

  /**
   * Checks the node for type errors and attaches attributes. This is currently called
   * directly from the parser as expressions are constructed, and the implementation of
   * eval depends on the check has happened.
   */
  void check(Type targetType) {
    ValidationException.pushCurrentThreadValidationContext(
        new Supplier<String>() {
          @Override
          public String get() {
            return "in expression '" + Expression.this.toString() + "'";
          }
        });
    try {
      doCheck(targetType);
    } finally {
      ValidationException.popCurrentThreadValidationContext();
    }
  }

  /**
   * Abstract method for checking implemented by node classes.
   */
  // Note: we can't declare this abstract because AutoValue does not like this.
  @SuppressWarnings("unused")
  protected void doCheck(Type targetType) {}

  /**
   * Expression node for identifiers.
   */
  @AutoValue
  abstract static class Identifier extends Expression {
    abstract String id();

    @Override
    public String toString() {
      return id();
    }

    @Override
    protected void doCheck(Type targetType) {
      FieldDescriptor field = resolveField(targetType, id());
      putAttribute(FIELD_ATTRIBUTE, field);
      putAttribute(TYPE_ATTRIBUTE, Type.forField(field));
    }

    @Override
    public Object eval(MessageOrBuilder targetValue) {
      // TODO(wrwg): see if we can avoid the cast. proto reflection api currently does _not_
      // supports maps, that seems to be a bug which should be resolved on their side.
      return ProtoReflectionUtil.getField(
          (GeneratedMessage) targetValue, requireAttribute(FIELD_ATTRIBUTE));
    }

    @Override
    public Message.Builder assign(Builder targetBuilder, Object value) {
      targetBuilder.setField(requireAttribute(FIELD_ATTRIBUTE), value);
      return targetBuilder;
    }

    @Override
    protected Message.Builder getBuilder(Message.Builder targetBuilder) {
      return targetBuilder.getFieldBuilder(requireAttribute(FIELD_ATTRIBUTE));
    }
  }

  /**
   * Expression node for constants.
   */
  @AutoValue
  abstract static class Constant extends Expression {
    abstract Object value();

    @Override
    public String toString() {
      return value().toString(); // TODO(wrwg): escape strings
    }

    @Override
    protected void doCheck(Type targetType) {
      putAttribute(TYPE_ATTRIBUTE, Type.forJavaType(value().getClass()));
    }

    @Override
    public Object eval(MessageOrBuilder targetValue) {
      return value();
    }
  }

  /**
   * Expression node for function calls.
   */
  @AutoValue
  abstract static class Call extends Expression {
    abstract Operation operation();

    abstract ImmutableList<Expression> arguments();

    @Override
    public String toString() {
      return operation()
          .print(
              FluentIterable.from(arguments())
                  .transform(
                      new Function<Expression, String>() {
                        @Override
                        public String apply(Expression arg) {
                          return arg.toString();
                        }
                      })
                  .toList());
    }

    @Override
    protected void doCheck(Type targetType) {
      operation().check(this);
    }

    @Override
    public Object eval(MessageOrBuilder targetValue) {
      return operation().eval(this, evalArgs(targetValue, arguments()));
    }

    private static List<Object> evalArgs(
        final MessageOrBuilder targetValue, List<Expression> args) {
      return FluentIterable.from(args)
          .transform(
              new Function<Expression, Object>() {
                @Override
                public Object apply(Expression expr) {
                  return expr.eval(targetValue);
                }
              })
          .toList();
    }

    @Override
    public Message.Builder assign(Builder targetBuilder, Object value) {
      operation().assign(this, targetBuilder, arguments(), value);
      return targetBuilder;
    }

    @Override
    protected Message.Builder getBuilder(Message.Builder targetBuilder) {
      return operation().getBuilder(this, targetBuilder, arguments());
    }
  }

  // Operations
  // ==========

  /**
   * Abstraction of an operation, as used by a Call expression. Operations are used
   * to represent both builtin operators as regular functions.
   */
  abstract static class Operation {

    /**
     * Prints the application to the given argument.
     */
    abstract String print(List<String> arguments);

    /**
     * Type checks the function. Sub-expressions are considered to be checked already.
     * Type checking attaches attributes for type and field descriptor if applicable.
     */
    abstract void check(Call node);

    /**
     * Evaluates the function, with arguments already evaluated.
     */
    abstract Object eval(Call node, List<Object> arguments);

    /**
     * Assigns a value to the function.
     */
    @SuppressWarnings("unused")
    Message.Builder assign(
        Call node, Message.Builder targetBuilder, List<Expression> args, Object value) {
      throw new ExecutionException("expression is not assignable: %s", node);
    }

    /**
     * Gets a builder for the value the function represents.
     */
    @SuppressWarnings("unused")
    Message.Builder getBuilder(Call node, Message.Builder targetBuilder, List<Expression> args) {
      throw new ExecutionException("expression is not assignable: %s", node);
    }
  }

  /**
   * Implements the select operation.
   */
  private static final Operation SELECT_OPERATION =
      new Operation() {

        @Override
        String print(List<String> arguments) {
          return arguments.get(0) + "." + arguments.get(1);
        }

        @Override
        void check(Call node) {
          Expression operand = node.arguments().get(0);
          Expression field = node.arguments().get(1);
          if (!(field instanceof AutoValue_Expression_Constant)) {
            throw new ValidationException("field selector must be a constant");
          }
          requireType(field, Type.STRING);
          String fieldName = (String) ((AutoValue_Expression_Constant) field).value();
          FieldDescriptor descriptor =
              resolveField(operand.requireAttribute(TYPE_ATTRIBUTE), fieldName);
          node.putAttribute(TYPE_ATTRIBUTE, Type.forField(descriptor));
          node.putAttribute(FIELD_ATTRIBUTE, descriptor);
        }

        @Override
        Object eval(Call node, List<Object> arguments) {
          Object operand = arguments.get(0);
          FieldDescriptor field = node.requireAttribute(FIELD_ATTRIBUTE);
          return ((Message) operand).getField(field);
        }

        @Override
        Builder assign(Call node, Builder targetBuilder, List<Expression> arguments, Object value) {
          Expression operand = arguments.get(0);
          FieldDescriptor field = node.requireAttribute(FIELD_ATTRIBUTE);
          operand.getBuilder(targetBuilder).setField(field, value);
          return targetBuilder;
        }

        @Override
        Builder getBuilder(Call node, Builder targetBuilder, List<Expression> arguments) {
          FieldDescriptor field = node.requireAttribute(FIELD_ATTRIBUTE);
          return targetBuilder.getFieldBuilder(field);
        }
      };

  /**
   * Implements the index operation.
   */
  private static final Operation INDEX_OPERATION =
      new Operation() {

        @Override
        String print(List<String> arguments) {
          return arguments.get(0) + "[" + arguments.get(1) + "]";
        }

        @Override
        void check(Call node) {
          Type operandType = node.arguments().get(0).requireAttribute(TYPE_ATTRIBUTE);
          if (operandType.isMap()) {
            requireType(node.arguments().get(1), operandType.getMapKeyType());
            node.putAttribute(TYPE_ATTRIBUTE, operandType.getMapValueType());
          } else if (operandType.isRepeated()) {
            requireType(node.arguments().get(1), Type.INT64);
            node.putAttribute(TYPE_ATTRIBUTE, operandType.getRepeatedElemType());
          } else {
            throw new ValidationException("index operation not applicable");
          }
        }

        @Override
        Object eval(Call node, List<Object> arguments) {
          Type operandType = node.arguments().get(0).requireAttribute(TYPE_ATTRIBUTE);
          if (operandType.isMap()) {
            return ((Map<?, ?>) arguments.get(0)).get(arguments.get(1));
          }
          if (operandType.isRepeated()) {
            return ((List<?>) arguments.get(0)).get((int) (long) arguments.get(1));
          }
          // Internal error
          throw new IllegalStateException("unexpected operand type for index operation");
        }

        // TODO(wrwg): implement assign and getBuilder. Example how to do this is found in
        // FieldMask in api.management.common.
      };

  // TODO(wrwg): implement full set of operations.

  /**
   * Helper to check whether an expression has the expected type.
   */
  private static void requireType(Expression node, Type type) {
    Type providedType = node.requireAttribute(TYPE_ATTRIBUTE);
    if (!providedType.equals(type)) {
      throw new ValidationException(
          "provided type '%s' does not match expected type '%s'", providedType, type);
    }
  }

  /**
   * Helper to resolve a field against a message type.
   */
  private static FieldDescriptor resolveField(Type operandType, String name) {
    if (!operandType.isMessage()) {
      throw new ValidationException(
          "'%s' is not a message so field '%s' cannot be selected from it", operandType, name);
    }
    FieldDescriptor field = operandType.getMessageDescriptor().findFieldByName(name);
    if (field == null) {
      throw new ValidationException("field '%s' is not declared in '%s'", name, operandType);
    }
    return field;
  }

  // Parser
  // ======

  // TODO(wrwg): move checking out of parsing in an independent phase

  private static class Parser {
    private final ListIterator<Token> input;
    private Type targetType;

    Parser(Descriptor targetMessage, String source) {
      this.targetType = Type.forMessage(targetMessage);
      this.input = scan(source).listIterator();
    }

    private void checkEof() {
      accept(TokenKind.EOF);
    }

    private Expression parsePathExpr() {
      Expression result = parseId();
      while (true) {
        if (acceptIf(TokenKind.SPECIAL, ".")) {
          result = parseSelect(result);
          continue;
        }
        if (acceptIf(TokenKind.SPECIAL, "[")) {
          result = parseIndex(result);
          continue;
        }
        break;
      }
      return result;
    }

    private Expression parseId() {
      Token id = accept(TokenKind.IDENTIFIER);
      Expression result = new AutoValue_Expression_Identifier((String) id.value());
      result.check(targetType);
      return result;
    }

    private Expression parseSelect(Expression operand) {
      Token id = accept(TokenKind.IDENTIFIER);
      Expression fieldExpr = new AutoValue_Expression_Constant(id.value());
      fieldExpr.check(targetType);
      Expression result =
          new AutoValue_Expression_Call(SELECT_OPERATION, ImmutableList.of(operand, fieldExpr));
      result.check(targetType);
      return result;
    }

    private Expression parseIndex(Expression operand) {
      Token constant = accept(TokenKind.CONSTANT);
      Expression constantExpr = new AutoValue_Expression_Constant(constant.value());
      constantExpr.check(targetType);
      Expression result =
          new AutoValue_Expression_Call(INDEX_OPERATION, ImmutableList.of(operand, constantExpr));
      accept(TokenKind.SPECIAL, "]");
      result.check(targetType);
      return result;
    }

    private Token accept(TokenKind kind) {
      return accept(kind, null);
    }

    private Token accept(TokenKind kind, @Nullable Object value) {
      Token next = input.next();
      if (next.kind() == kind && (value == null || value.equals(next.value()))) {
        return next;
      }
      input.previous();
      throw new ValidationException(
          "unexpected token '%s', expected '%s'",
          next.value(),
          kind == TokenKind.EOF ? "<end of input>" : kind.toString());
    }

    private boolean acceptIf(TokenKind kind, Object value) {
      Token next = input.next();
      if (next.kind() == kind && next.value().equals(value)) {
        return true;
      }
      input.previous();
      return false;
    }
  }

  // Lexer
  // =====

  private static final String IDENTIFIER_PAT = "[a-zA-Z][a-zA-Z0-9_]*";
  private static final String STRING_PAT = "\"[^\"]*\"";
  private static final String NUMBER_PAT = "-?[0-9]+";
  private static final String SPECIAL_PAT = "\\.|\\[|\\]";
  private static final Pattern TOKEN =
      Pattern.compile(
          String.format(
              "(?m)\\s*((?<identifier>%s)|(?<string>%s)|(?<number>%s)|(?<special>%s))\\s*",
              IDENTIFIER_PAT,
              STRING_PAT,
              NUMBER_PAT,
              SPECIAL_PAT));

  enum TokenKind {
    IDENTIFIER,
    CONSTANT,
    SPECIAL,
    EOF
  }

  @AutoValue
  abstract static class Token {
    abstract TokenKind kind();

    abstract Object value();

    abstract int position();

    static Token create(TokenKind kind, Object value, int position) {
      return new AutoValue_Expression_Token(kind, value, position);
    }
  }

  private static List<Token> scan(String source) {
    ImmutableList.Builder<Token> tokens = ImmutableList.builder();
    Matcher matcher = TOKEN.matcher(source);
    int end = 0;
    for (; ; ) {
      matcher.region(end, source.length());
      if (!matcher.lookingAt()) {
        break;
      }
      int position = matcher.start(1);
      end = matcher.end();
      String match = matcher.group("identifier");
      if (match != null) {
        tokens.add(Token.create(TokenKind.IDENTIFIER, match, position));
        continue;
      }
      match = matcher.group("string");
      if (match != null) {
        match = match.substring(1, match.length() - 1);
        tokens.add(Token.create(TokenKind.CONSTANT, match, position));
        continue;
      }
      match = matcher.group("number");
      if (match != null) {
        long value;
        try {
          value = Long.parseLong(match);
        } catch (NumberFormatException e) {
          throw new ValidationException("Invalid integer number format '%s'", match);
        }
        tokens.add(Token.create(TokenKind.CONSTANT, value, position));
        continue;
      }
      match = matcher.group("special");
      if (match != null) {
        tokens.add(Token.create(TokenKind.SPECIAL, match, position));
        continue;
      }
      throw new IllegalStateException("unexpected expression scan result");
    }
    if (end < source.length()) {
      throw new ValidationException("unrecognized input '%s'", source.substring(end));
    }
    tokens.add(Token.create(TokenKind.EOF, "<eof>", source.length()));
    return tokens.build();
  }
}
