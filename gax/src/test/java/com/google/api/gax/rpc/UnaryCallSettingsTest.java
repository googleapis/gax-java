/*
 * Copyright 2017 Google LLC
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
 *     * Neither the name of Google LLC nor the names of its
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
package com.google.api.gax.rpc;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.google.api.gax.retrying.RetrySettings;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.threeten.bp.Duration;

@RunWith(JUnit4.class)
public class UnaryCallSettingsTest {

  @Test
  public void testSetSimpleTimeoutNoRetries() {
    UnaryCallSettings.Builder<?, ?> builder = new UnaryCallSettings.Builder<Object, Object>();
    builder.setSimpleTimeoutNoRetries(Duration.ofSeconds(13));

    assertThat(builder.getRetryableCodes().size()).isEqualTo(0);
    assertThat(builder.getRetrySettings().getMaxAttempts()).isEqualTo(1);
    assertThat(builder.getRetrySettings().getTotalTimeout()).isEqualTo(Duration.ofSeconds(13));
  }

  @Test
  public void testEquals() {
    UnaryCallSettings.Builder<?, ?> builder = new UnaryCallSettings.Builder<Object, Object>();
    builder.setSimpleTimeoutNoRetries(Duration.ofSeconds(13));

    UnaryCallSettings<?, ?> settings13 = builder.build();
    assertEquals(settings13, settings13);
    assertNotEquals(settings13, null);
    assertNotEquals(settings13, "a string");
    assertEquals(settings13.hashCode(), settings13.hashCode());

    UnaryCallSettings.Builder<?, ?> builder5 = new UnaryCallSettings.Builder<Object, Object>();
    builder5.setSimpleTimeoutNoRetries(Duration.ofSeconds(5));

    UnaryCallSettings<?, ?> settings5 = builder5.build();
    assertNotEquals(settings13, settings5);
    assertNotEquals(settings13.hashCode(), settings5.hashCode());
  }

  @Test
  public void testEquals_retrySettings() {
    RetrySettings initialSettings =
        RetrySettings.newBuilder()
            .setInitialRetryDelay(Duration.ofMillis(5))
            .setMaxRetryDelay(Duration.ofSeconds(1))
            .setRetryDelayMultiplier(2)
            .setInitialRpcTimeout(Duration.ofMillis(100))
            .setMaxRpcTimeout(Duration.ofMillis(200))
            .setRpcTimeoutMultiplier(1.1)
            .setJittered(true)
            .setMaxAttempts(10)
            .build();

    UnaryCallSettings.Builder<?, ?> builder = new UnaryCallSettings.Builder<Object, Object>();
    UnaryCallSettings<?, ?> settingsNoRetry = builder.build();

    builder.setRetrySettings(initialSettings);
    UnaryCallSettings<?, ?> settingsRetry = builder.build();

    assertNotEquals(settingsRetry, settingsNoRetry);
  }

  @Test
  public void testEquals_retryableCodes() {
    UnaryCallSettings.Builder<?, ?> builder = new UnaryCallSettings.Builder<Object, Object>();
    UnaryCallSettings<?, ?> settingsNoCodes = builder.build();

    builder.setRetryableCodes(StatusCode.Code.CANCELLED);
    UnaryCallSettings<?, ?> settingsCodes = builder.build();

    assertNotEquals(settingsCodes, settingsNoCodes);

    UnaryCallSettings<?, ?> settingsCodes2 = builder.build();
    assertEquals(settingsCodes, settingsCodes2);
  }

  @Test
  public void testRetrySettingsBuilder() {
    RetrySettings initialSettings =
        RetrySettings.newBuilder()
            .setInitialRetryDelay(Duration.ofMillis(5))
            .setMaxRetryDelay(Duration.ofSeconds(1))
            .setRetryDelayMultiplier(2)
            .setInitialRpcTimeout(Duration.ofMillis(100))
            .setMaxRpcTimeout(Duration.ofMillis(200))
            .setRpcTimeoutMultiplier(1.1)
            .setJittered(true)
            .setMaxAttempts(10)
            .build();

    UnaryCallSettings.Builder<Object, Object> builder =
        new UnaryCallSettings.Builder<Object, Object>().setRetrySettings(initialSettings);

    builder.retrySettings().setMaxRetryDelay(Duration.ofMinutes(1));

    assertThat(builder.getRetrySettings().getMaxRetryDelay()).isEqualTo(Duration.ofMinutes(1));
    assertThat(builder.build().getRetrySettings().getMaxRetryDelay())
        .isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  public void testToString() {
    RetrySettings retrySettings = RetrySettings.newBuilder().build();
    Set<StatusCode.Code> retryableCodes = ImmutableSet.of(StatusCode.Code.DEADLINE_EXCEEDED);
    UnaryCallSettings<?, ?> unaryCallSettings =
        UnaryCallSettings.newUnaryCallSettingsBuilder()
            .setRetrySettings(retrySettings)
            .setRetryableCodes(retryableCodes)
            .build();
    assertThat(unaryCallSettings.toString()).contains("retryableCodes=" + retryableCodes);
    assertThat(unaryCallSettings.toString()).contains("retrySettings=" + retrySettings);
  }
}
