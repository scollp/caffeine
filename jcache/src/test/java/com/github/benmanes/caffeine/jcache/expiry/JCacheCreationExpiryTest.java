/*
 * Copyright 2016 Ben Manes. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.benmanes.caffeine.jcache.expiry;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Objects.requireNonNull;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import javax.cache.expiry.CreatedExpiryPolicy;
import javax.cache.expiry.ExpiryPolicy;

import org.testng.annotations.Test;

import com.github.benmanes.caffeine.jcache.AbstractJCacheTest;
import com.github.benmanes.caffeine.jcache.Expirable;
import com.github.benmanes.caffeine.jcache.configuration.CaffeineConfiguration;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * The test cases that ensure the <code>expiry for creation</code> time is set for the created
 * entries. The TCK asserts that the {@link ExpiryPolicy#getExpiryForCreation()} is only called
 * for the following methods, but does not check that the expiration time was updated.
 * <ul>
 *   <li>get (loading)
 *   <li>getAndPut
 *   <li>put
 *   <li>putAll
 *   <li>putIfAbsent
 *   <li>invoke
 *   <li>invokeAll
 * </ul>
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@Test(singleThreaded = true)
public final class JCacheCreationExpiryTest extends AbstractJCacheTest {

  @Override
  protected CaffeineConfiguration<Integer, Integer> getConfiguration() {
    var configuration = new CaffeineConfiguration<Integer, Integer>();
    configuration.setExpiryPolicyFactory(() -> new CreatedExpiryPolicy(
        new javax.cache.expiry.Duration(TimeUnit.MILLISECONDS, EXPIRY_DURATION.toMillis())));
    configuration.setExecutorFactory(MoreExecutors::directExecutor);
    configuration.setTickerFactory(() -> ticker::read);
    configuration.setStatisticsEnabled(true);
    return configuration;
  }

  /* --------------- containsKey --------------- */

  @Test
  public void containsKey_expired() {
    jcache.put(KEY_1, VALUE_1);
    ticker.setAutoIncrementStep(EXPIRY_DURATION.dividedBy(2));

    assertThat(jcache.containsKey(KEY_1)).isFalse();
    assertThat(getExpirable(jcache, KEY_1)).isNull();
  }

  /* --------------- get --------------- */

  @Test
  public void get_expired() {
    jcache.put(KEY_1, VALUE_1);
    ticker.setAutoIncrementStep(EXPIRY_DURATION.dividedBy(2));

    assertThat(jcache.get(KEY_1)).isNull();
    assertThat(getExpirable(jcache, KEY_1)).isNull();
  }

  /* --------------- get (loading) --------------- */

  @Test
  public void get_loading_absent() {
    assertThat(jcacheLoading.get(KEY_1)).isEqualTo(KEY_1);
    Expirable<Integer> expirable = getExpirable(jcacheLoading, KEY_1);
    assertThat(expirable).isNotNull();
    assertThat(expirable.getExpireTimeMillis())
        .isEqualTo(currentTime().plus(EXPIRY_DURATION).toMillis());
  }

  @Test
  public void get_loading_expired() {
    jcacheLoading.put(KEY_1, VALUE_1);
    advancePastExpiry();

    assertThat(jcacheLoading.get(KEY_1)).isEqualTo(KEY_1);
    Expirable<Integer> expirable = getExpirable(jcacheLoading, KEY_1);
    assertThat(expirable).isNotNull();
    assertThat(expirable.getExpireTimeMillis())
        .isEqualTo(currentTime().plus(EXPIRY_DURATION).toMillis());
  }

  @Test
  public void get_loading_expired_lazy() {
    cacheManager.enableStatistics(jcacheLoading.getName(), false);

    jcacheLoading.put(KEY_1, VALUE_1);
    ticker.setAutoIncrementStep(Duration.ofMillis((long) (EXPIRY_DURATION.toMillis() / 1.5)));

    assertThat(jcacheLoading.get(KEY_1)).isEqualTo(KEY_1);
  }

  @Test
  public void get_loading_present() {
    jcacheLoading.put(KEY_1, VALUE_1);
    advanceHalfExpiry();

    assertThat(jcacheLoading.get(KEY_1)).isEqualTo(VALUE_1);
    Expirable<Integer> expirable = getExpirable(jcacheLoading, KEY_1);
    assertThat(expirable).isNotNull();
    assertThat(expirable.getExpireTimeMillis())
        .isEqualTo(START_TIME.plus(EXPIRY_DURATION).toMillis());
  }

  /* --------------- getAndPut --------------- */

  @Test
  public void getAndPut_absent() {
    assertThat(jcache.getAndPut(KEY_1, VALUE_1)).isNull();

    Expirable<Integer> expirable = getExpirable(jcache, KEY_1);
    assertThat(expirable).isNotNull();
    assertThat(expirable.getExpireTimeMillis())
        .isEqualTo(currentTime().plus(EXPIRY_DURATION).toMillis());
  }

  @Test
  public void getAndPut_expired() {
    jcache.put(KEY_1, VALUE_1);
    advancePastExpiry();

    assertThat(jcache.getAndPut(KEY_1, VALUE_1)).isNull();
    Expirable<Integer> expirable = getExpirable(jcache, KEY_1);
    assertThat(expirable).isNotNull();
    assertThat(expirable.getExpireTimeMillis())
        .isEqualTo(currentTime().plus(EXPIRY_DURATION).toMillis());
  }

  @Test
  public void getAndPut_present() {
    jcache.put(KEY_1, VALUE_1);
    advanceHalfExpiry();

    assertThat(jcache.getAndPut(KEY_1, VALUE_2)).isEqualTo(VALUE_1);
    Expirable<Integer> expirable = getExpirable(jcache, KEY_1);
    assertThat(expirable).isNotNull();
    assertThat(expirable.getExpireTimeMillis())
        .isEqualTo(START_TIME.plus(EXPIRY_DURATION).toMillis());
  }

  /* --------------- put --------------- */

  @Test
  public void put_absent() {
    jcache.put(KEY_1, VALUE_1);

    Expirable<Integer> expirable = getExpirable(jcache, KEY_1);
    assertThat(expirable).isNotNull();
    assertThat(expirable.getExpireTimeMillis())
        .isEqualTo(currentTime().plus(EXPIRY_DURATION).toMillis());
  }

  @Test
  public void put_expired() {
    jcache.put(KEY_1, VALUE_1);
    advancePastExpiry();

    jcache.put(KEY_1, VALUE_2);
    Expirable<Integer> expirable = getExpirable(jcache, KEY_1);
    assertThat(expirable).isNotNull();
    assertThat(expirable.getExpireTimeMillis())
        .isEqualTo(currentTime().plus(EXPIRY_DURATION).toMillis());
  }

  @Test
  public void put_present() {
    jcache.put(KEY_1, VALUE_1);
    advanceHalfExpiry();

    jcache.put(KEY_1, VALUE_2);
    Expirable<Integer> expirable = getExpirable(jcache, KEY_1);
    assertThat(expirable).isNotNull();
    assertThat(expirable.getExpireTimeMillis())
        .isEqualTo(START_TIME.plus(EXPIRY_DURATION).toMillis());
  }

  /* --------------- putAll --------------- */

  @Test
  public void putAll_absent() {
    jcache.putAll(entries);

    for (Integer key : keys) {
      Expirable<Integer> expirable = getExpirable(jcache, key);
      assertThat(expirable).isNotNull();
      assertThat(expirable.getExpireTimeMillis())
          .isEqualTo(currentTime().plus(EXPIRY_DURATION).toMillis());
    }
  }

  @Test
  public void putAll_expired() {
    jcache.putAll(entries);
    advancePastExpiry();

    jcache.putAll(entries);
    for (Integer key : keys) {
      Expirable<Integer> expirable = getExpirable(jcache, key);
      assertThat(expirable).isNotNull();
      assertThat(expirable.getExpireTimeMillis())
          .isEqualTo(currentTime().plus(EXPIRY_DURATION).toMillis());
    }
  }

  @Test
  public void putAll_present() {
    jcache.putAll(entries);
    advanceHalfExpiry();

    jcache.putAll(entries);
    for (Integer key : keys) {
      Expirable<Integer> expirable = getExpirable(jcache, key);
      assertThat(expirable).isNotNull();
      assertThat(expirable.getExpireTimeMillis())
          .isEqualTo(START_TIME.plus(EXPIRY_DURATION).toMillis());
    }
  }

  /* --------------- putIfAbsent --------------- */

  @Test
  public void putIfAbsent_absent() {
    assertThat(jcache.putIfAbsent(KEY_1, VALUE_1)).isTrue();

    Expirable<Integer> expirable = getExpirable(jcache, KEY_1);
    assertThat(expirable).isNotNull();
    assertThat(expirable.getExpireTimeMillis())
        .isEqualTo(currentTime().plus(EXPIRY_DURATION).toMillis());
  }

  @Test
  public void putIfAbsent_expired() {
    assertThat(jcache.putIfAbsent(KEY_1, VALUE_1)).isTrue();
    advancePastExpiry();

    assertThat(jcache.putIfAbsent(KEY_1, VALUE_2)).isTrue();
    Expirable<Integer> expirable = getExpirable(jcache, KEY_1);
    assertThat(expirable).isNotNull();
    assertThat(expirable.get()).isEqualTo(VALUE_2);
    assertThat(expirable.getExpireTimeMillis())
        .isEqualTo(currentTime().plus(EXPIRY_DURATION).toMillis());
  }

  @Test
  public void putIfAbsent_expired_lazy() {
    assertThat(jcache.putIfAbsent(KEY_1, VALUE_1)).isTrue();

    ticker.setAutoIncrementStep(EXPIRY_DURATION.dividedBy(2));
    assertThat(jcache.putIfAbsent(KEY_1, VALUE_2)).isTrue();
  }

  @Test
  public void putIfAbsent_present() {
    assertThat(jcache.putIfAbsent(KEY_1, VALUE_1)).isTrue();
    advanceHalfExpiry();

    assertThat(jcache.putIfAbsent(KEY_1, VALUE_2)).isFalse();
    Expirable<Integer> expirable = getExpirable(jcache, KEY_1);
    assertThat(expirable).isNotNull();
    assertThat(expirable.getExpireTimeMillis())
        .isEqualTo(START_TIME.plus(EXPIRY_DURATION).toMillis());
  }

  /* --------------- invoke --------------- */

  @Test
  public void invoke_absent() {
    var result = jcache.invoke(KEY_1, (entry, args) -> {
      entry.setValue(VALUE_2);
      return null;
    });
    assertThat(result).isNull();

    Expirable<Integer> expirable = getExpirable(jcache, KEY_1);
    assertThat(expirable).isNotNull();
    assertThat(expirable.getExpireTimeMillis())
        .isEqualTo(currentTime().plus(EXPIRY_DURATION).toMillis());
  }

  @Test
  public void invoke_expired() {
    jcache.put(KEY_1, VALUE_1);
    advancePastExpiry();

    var result = jcache.invoke(KEY_1, (entry, args) -> {
      entry.setValue(VALUE_2);
      return null;
    });
    assertThat(result).isNull();

    Expirable<Integer> expirable = getExpirable(jcache, KEY_1);
    assertThat(expirable).isNotNull();
    assertThat(expirable.getExpireTimeMillis())
        .isEqualTo(currentTime().plus(EXPIRY_DURATION).toMillis());
  }

  @Test
  public void invoke_present() {
    jcache.put(KEY_1, VALUE_1);
    advanceHalfExpiry();

    var result = jcache.invoke(KEY_1, (entry, args) -> {
      entry.setValue(VALUE_2);
      return null;
    });
    assertThat(result).isNull();

    Expirable<Integer> expirable = getExpirable(jcache, KEY_1);
    assertThat(expirable).isNotNull();
    assertThat(expirable.getExpireTimeMillis())
        .isEqualTo(START_TIME.plus(EXPIRY_DURATION).toMillis());
  }

  /* --------------- invokeAll --------------- */

  @Test
  public void invokeAll_absent() {
    var result = jcache.invokeAll(keys, (entry, args) -> {
      entry.setValue(VALUE_2);
      return null;
    });
    assertThat(result).isEmpty();

    for (Integer key : keys) {
      Expirable<Integer> expirable = getExpirable(jcache, key);
      assertThat(expirable).isNotNull();
      assertThat(expirable.getExpireTimeMillis())
          .isEqualTo(currentTime().plus(EXPIRY_DURATION).toMillis());
    }
  }

  @Test
  public void invokeAll_expired() {
    jcache.putAll(entries);
    advancePastExpiry();

    var result = jcache.invokeAll(keys, (entry, args) -> {
      entry.setValue(VALUE_2);
      return null;
    });
    assertThat(result).isEmpty();

    for (Integer key : keys) {
      Expirable<Integer> expirable = getExpirable(jcache, key);
      assertThat(expirable).isNotNull();
      assertThat(expirable.getExpireTimeMillis())
          .isEqualTo(currentTime().plus(EXPIRY_DURATION).toMillis());
    }
  }

  @Test
  public void invokeAll_present() {
    jcache.putAll(entries);
    advanceHalfExpiry();

    var result = jcache.invokeAll(keys, (entry, args) -> {
      entry.setValue(VALUE_2);
      return null;
    });
    assertThat(result).isEmpty();

    for (Integer key : keys) {
      Expirable<Integer> expirable = getExpirable(jcache, key);
      assertThat(expirable).isNotNull();
      assertThat(expirable.getExpireTimeMillis())
          .isEqualTo(START_TIME.plus(EXPIRY_DURATION).toMillis());
    }
  }

  @Test
  public void iterator() {
    jcache.put(KEY_1, VALUE_1);
    var expirable1 = requireNonNull(getExpirable(jcache, KEY_1));
    advanceHalfExpiry();

    jcache.put(KEY_2, VALUE_2);
    var expirable2 = requireNonNull(getExpirable(jcache, KEY_2));
    assertThat(jcache).hasSize(2);

    advanceHalfExpiry();
    assertThat(jcache).hasSize(1);
    expirable2.setExpireTimeMillis(expirable1.getExpireTimeMillis());
    assertThat(jcache).hasSize(0);
  }
}
