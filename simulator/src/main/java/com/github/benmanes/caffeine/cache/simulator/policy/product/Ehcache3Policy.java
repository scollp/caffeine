/*
 * Copyright 2015 Ben Manes. All Rights Reserved.
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
package com.github.benmanes.caffeine.cache.simulator.policy.product;

import static com.google.common.base.Preconditions.checkState;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.core.internal.statistics.DefaultStatisticsService;
import org.ehcache.core.statistics.CacheStatistics;

import com.github.benmanes.caffeine.cache.simulator.BasicSettings;
import com.github.benmanes.caffeine.cache.simulator.policy.AccessEvent;
import com.github.benmanes.caffeine.cache.simulator.policy.Policy;
import com.github.benmanes.caffeine.cache.simulator.policy.Policy.PolicySpec;
import com.github.benmanes.caffeine.cache.simulator.policy.PolicyStats;
import com.typesafe.config.Config;

/**
 * Ehcache 3 implementation.
 *
 * @author ben.manes@gmail.com (Ben Manes)
 */
@PolicySpec(name = "product.Ehcache3")
public final class Ehcache3Policy implements Policy {
  private final Cache<Long, Boolean> cache;
  private final CacheManager cacheManager;
  private final PolicyStats policyStats;
  private final CacheStatistics stats;

  public Ehcache3Policy(Config config) {
    policyStats = new PolicyStats(name());
    var settings = new BasicSettings(config);
    var statistics = new DefaultStatisticsService();
    cacheManager = CacheManagerBuilder.newCacheManagerBuilder()
        .using(statistics)
        .build(true);
    cache = cacheManager.createCache("ehcache3",
        CacheConfigurationBuilder.newCacheConfigurationBuilder(Long.class, Boolean.class,
            ResourcePoolsBuilder.newResourcePoolsBuilder()
                .heap(settings.maximumSize(), EntryUnit.ENTRIES))
            .build());
    stats = statistics.getCacheStatistics("ehcache3");
  }

  @Override
  public void record(AccessEvent event) {
    Long key = event.longKey();
    var value = cache.get(key);
    if (value == null) {
      cache.put(key, true);
      policyStats.recordMiss();
    } else {
      policyStats.recordHit();
    }
  }

  @Override
  public PolicyStats stats() {
    return policyStats;
  }

  @Override
  public void finished() {
    cacheManager.close();
    policyStats.addEvictions(stats.getCacheEvictions());
    checkState(policyStats.hitCount() == stats.getCacheHits());
    checkState(policyStats.missCount() == stats.getCacheMisses());
  }
}
