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

import com.google.common.collect.ImmutableMap;
import com.google.common.truth.Truth;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Map;

/**
 * Tests for {@link PathTemplate}.
 */
@RunWith(JUnit4.class)
public class PathTemplateTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  // Match
  // =====

  @Test
  public void matchAtomicResourceName() {
    PathTemplate template = PathTemplate.create("buckets/*/*/objects/*");
    assertPositionalMatch(template.match("buckets/f/o/objects/bar"), "f", "o", "bar");
  }

  @Test
  public void matchTemplateWithUnboundedWildcard() {
    PathTemplate template = PathTemplate.create("buckets/*/objects/**");
    assertPositionalMatch(template.match("buckets/foo/objects/bar/baz"), "foo", "bar/baz");
  }

  @Test
  public void matchWithForcedHostName() {
    PathTemplate template = PathTemplate.create("buckets/*/objects/*");
    Map<String, String> match = template.matchFromFullName("somewhere.io/buckets/b/objects/o");
    Truth.assertThat(match).isNotNull();
    Truth.assertThat(match.get(PathTemplate.HOSTNAME_VAR)).isEqualTo("somewhere.io");
    Truth.assertThat(match.get("$0")).isEqualTo("b");
    Truth.assertThat(match.get("$1")).isEqualTo("o");
  }

  @Test
  public void matchWithHostName() {
    PathTemplate template = PathTemplate.create("buckets/*/objects/*");
    Map<String, String> match = template.match("//somewhere.io/buckets/b/objects/o");
    Truth.assertThat(match).isNotNull();
    Truth.assertThat(match.get(PathTemplate.HOSTNAME_VAR)).isEqualTo("//somewhere.io");
    Truth.assertThat(match.get("$0")).isEqualTo("b");
    Truth.assertThat(match.get("$1")).isEqualTo("o");
  }

  @Test
  public void matchWithCustomMethod() {
    PathTemplate template = PathTemplate.create("buckets/*/objects/*:custom");
    Map<String, String> match = template.match("buckets/b/objects/o:custom");
    Truth.assertThat(match).isNotNull();
    Truth.assertThat(match.get("$0")).isEqualTo("b");
    Truth.assertThat(match.get("$1")).isEqualTo("o");
  }

  @Test
  public void matchFailWhenPathMismatch() {
    PathTemplate template = PathTemplate.create("buckets/*/*/objects/*");
    Truth.assertThat(template.match("buckets/f/o/o/objects/bar")).isNull();
  }

  @Test
  public void matchFailWhenPathTooShort() {
    PathTemplate template = PathTemplate.create("buckets/*/*/objects/*");
    Truth.assertThat(template.match("buckets/f/o/objects")).isNull();
  }

  @Test
  public void matchFailWhenPathTooLong() {
    PathTemplate template = PathTemplate.create("buckets/*/*/objects/*");
    Truth.assertThat(template.match("buckets/f/o/objects/too/long")).isNull();
  }

  @Test
  public void matchWithUnboundInMiddle() {
    PathTemplate template = PathTemplate.create("bar/**/foo/*");
    assertPositionalMatch(template.match("bar/foo/foo/foo/bar"), "foo/foo", "bar");
  }

  // Validate
  // ========

  @Test
  public void validateSuccess() {
    String templateString = "buckets/*/objects/*";
    String pathString = "buckets/bucket/objects/object";
    PathTemplate template = PathTemplate.create(templateString);
    template.validate(pathString, "");
    // No assertion - success is no exception thrown from template.validate
  }

  @Test
  public void validateFailure() {
    thrown.expect(ValidationException.class);
    String templateString = "buckets/*/objects/*";
    String pathString = "buckets/bucket/invalid/object";
    thrown.expectMessage(
        String.format("Parameter \"%s\" must be in the form \"%s\"", pathString, templateString));
    PathTemplate template = PathTemplate.create(templateString);
    template.validate(pathString, "");
  }

  @Test
  public void validateMatchSuccess() {
    String templateString = "buckets/*/objects/{object_id}";
    String pathString = "buckets/bucket/objects/object";
    PathTemplate template = PathTemplate.create(templateString);
    ImmutableMap<String, String> matchMap = template.validatedMatch(pathString, "");
    Truth.assertThat(matchMap.get("$0")).isEqualTo("bucket");
    Truth.assertThat(matchMap.get("object_id")).isEqualTo("object");
  }

  @Test
  public void validateMatchFailure() {
    thrown.expect(ValidationException.class);
    String templateString = "buckets/*/objects/*";
    String pathString = "buckets/bucket/invalid/object";
    thrown.expectMessage(
        String.format("Parameter \"%s\" must be in the form \"%s\"", pathString, templateString));
    PathTemplate template = PathTemplate.create(templateString);
    template.validatedMatch(pathString, "");
  }

  // Instantiate
  // ===========

  @Test
  public void instantiateAtomicResource() {
    PathTemplate template = PathTemplate.create("buckets/*/*/*/objects/*");
    String url = template.instantiate("$0", "f", "$1", "o", "$2", "o", "$3", "bar");
    Truth.assertThat(url).isEqualTo("buckets/f/o/o/objects/bar");
  }

  @Test
  public void instantiateEscapeUnsafeChar() {
    PathTemplate template = PathTemplate.create("buckets/*/objects/*");
    Truth.assertThat(template.instantiate("$0", "f/o/o", "$1", "b/a/r"))
        .isEqualTo("buckets/f%2Fo%2Fo/objects/b%2Fa%2Fr");
  }

  @Test
  public void instantiateNotEscapeForUnboundedWildcard() {
    PathTemplate template = PathTemplate.create("buckets/*/objects/**");
    Truth.assertThat(template.instantiate("$0", "f/o/o", "$1", "b/a/r"))
        .isEqualTo("buckets/f%2Fo%2Fo/objects/b/a/r");
  }

  @Test
  public void instantiateFailWhenTooFewVariables() {
    thrown.expect(ValidationException.class);
    PathTemplate template = PathTemplate.create("buckets/*/*/*/objects/*");
    template.instantiate("$0", "f", "1", "o");
  }

  @Test
  public void instantiateWithUnboundInMiddle() {
    PathTemplate template = PathTemplate.create("bar/**/foo/*");
    Truth.assertThat(template.instantiate("$0", "1/2", "$1", "3")).isEqualTo("bar/1/2/foo/3");
  }

  @Test
  public void instantiatePartial() {
    PathTemplate template = PathTemplate.create("bar/*/foo/*");
    String instance = template.instantiatePartial(ImmutableMap.of("$0", "_1"));
    Truth.assertThat(instance).isEqualTo("bar/_1/foo/*");
  }

  @Test
  public void instantiateWithHostName() {
    PathTemplate template = PathTemplate.create("bar/*");
    String instance =
        template.instantiate(
            ImmutableMap.of(PathTemplate.HOSTNAME_VAR, "//somewhere.io", "$0", "foo"));
    Truth.assertThat(instance).isEqualTo("//somewhere.io/bar/foo");
  }

  @Test
  public void instantiateEscapeUnsafeCharNoEncoding() {
    thrown.expect(ValidationException.class);
    thrown.expectMessage("Invalid character \"/\" in path section \"f/o/o\".");
    PathTemplate template = PathTemplate.createWithoutUrlEncoding("buckets/*/objects/*");
    template.instantiate("$0", "f/o/o", "$1", "b/a/r");
  }

  @Test
  public void instantiateNotEscapeForUnboundedWildcardNoEncoding() {
    PathTemplate template = PathTemplate.createWithoutUrlEncoding("buckets/*/objects/**");
    Truth.assertThat(template.instantiate("$0", "foo", "$1", "b/a/r"))
        .isEqualTo("buckets/foo/objects/b/a/r");
  }

  @Test
  public void instantiateWithGoogProject() {
    PathTemplate template = PathTemplate.create("projects/{project}");
    String instance = template.instantiate(ImmutableMap.of("project", "google.com:test-proj"));
    Truth.assertThat(instance).isEqualTo("projects/google.com%3Atest-proj");
  }

  @Test
  public void instantiateWithGoogProjectNoEncoding() {
    PathTemplate template = PathTemplate.createWithoutUrlEncoding("projects/{project}");
    String instance = template.instantiate(ImmutableMap.of("project", "google.com:test-proj"));
    Truth.assertThat(instance).isEqualTo("projects/google.com:test-proj");
  }

  @Test
  public void instantiateWithUnusualCharactersNoEncoding() {
    PathTemplate template = PathTemplate.createWithoutUrlEncoding("bar/*");
    String instance = template.instantiate(ImmutableMap.of("$0", "asdf:;`~,.<>[]!@#$%^&*()"));
    Truth.assertThat(instance).isEqualTo("bar/asdf:;`~,.<>[]!@#$%^&*()");
  }

  // Other
  // =====

  @Test
  public void testMultiplePathWildcardFailure() {
    thrown.expect(IllegalArgumentException.class);
    PathTemplate.create("bar/**/{name=foo/**}:verb");
  }

  @Test
  public void testTemplateWithSimpleBinding() {
    PathTemplate template = PathTemplate.create("/v1/messages/{message_id}");
    String url = template.instantiate("message_id", "mymessage");
    Truth.assertThat(url).isEqualTo("v1/messages/mymessage");
  }

  @Test
  public void testTemplateWithMultipleSimpleBindings() {
    PathTemplate template = PathTemplate.create("v1/shelves/{shelf}/books/{book}");
    String url = template.instantiate("shelf", "s1", "book", "b1");
    Truth.assertThat(url).isEqualTo("v1/shelves/s1/books/b1");
  }

  private static void assertPositionalMatch(Map<String, String> match, String... expected) {
    Truth.assertThat(match).isNotNull();
    int i = 0;
    for (; i < expected.length; ++i) {
      Truth.assertThat(expected[i]).isEqualTo(match.get("$" + i));
    }
    Truth.assertThat(i).isEqualTo(match.size());
  }
}
