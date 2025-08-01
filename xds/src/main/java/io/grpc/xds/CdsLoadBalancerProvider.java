/*
 * Copyright 2019 The gRPC Authors
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

package io.grpc.xds;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.MoreObjects;
import io.grpc.Internal;
import io.grpc.LoadBalancer;
import io.grpc.LoadBalancer.Helper;
import io.grpc.LoadBalancerProvider;
import io.grpc.LoadBalancerRegistry;
import io.grpc.NameResolver.ConfigOrError;
import io.grpc.Status;
import io.grpc.internal.JsonUtil;
import java.util.Map;

/**
 * The provider for the "cds" balancing policy.  This class should not be directly referenced in
 * code.  The policy should be accessed through {@link io.grpc.LoadBalancerRegistry#getProvider}
 * with the name "cds" (currently "cds_experimental").
 */
@Internal
public class CdsLoadBalancerProvider extends LoadBalancerProvider {

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public int getPriority() {
    return 5;
  }

  @Override
  public String getPolicyName() {
    return XdsLbPolicies.CDS_POLICY_NAME;
  }

  private final LoadBalancerRegistry loadBalancerRegistry;

  public CdsLoadBalancerProvider() {
    this.loadBalancerRegistry = null;
  }

  public CdsLoadBalancerProvider(LoadBalancerRegistry loadBalancerRegistry) {
    this.loadBalancerRegistry = loadBalancerRegistry;
  }

  @Override
  public LoadBalancer newLoadBalancer(Helper helper) {
    LoadBalancerRegistry loadBalancerRegistry = this.loadBalancerRegistry;
    if (loadBalancerRegistry == null) {
      loadBalancerRegistry = LoadBalancerRegistry.getDefaultRegistry();
    }

    return new CdsLoadBalancer2(helper, loadBalancerRegistry);
  }

  @Override
  public ConfigOrError parseLoadBalancingPolicyConfig(
      Map<String, ?> rawLoadBalancingPolicyConfig) {
    return parseLoadBalancingConfigPolicy(rawLoadBalancingPolicyConfig);
  }

  /**
   * Parses raw load balancing config and returns a {@link ConfigOrError} that contains a
   * {@link CdsConfig} if parsing is successful.
   */
  static ConfigOrError parseLoadBalancingConfigPolicy(Map<String, ?> rawLoadBalancingPolicyConfig) {
    try {
      String cluster = JsonUtil.getString(rawLoadBalancingPolicyConfig, "cluster");
      Boolean isDynamic = JsonUtil.getBoolean(rawLoadBalancingPolicyConfig, "is_dynamic");
      if (isDynamic == null) {
        isDynamic = Boolean.FALSE;
      }
      return ConfigOrError.fromConfig(new CdsConfig(cluster, isDynamic));
    } catch (RuntimeException e) {
      return ConfigOrError.fromError(
          Status.UNAVAILABLE.withCause(e).withDescription(
              "Failed to parse CDS LB config: " + rawLoadBalancingPolicyConfig));
    }
  }

  /**
   * Represents a successfully parsed and validated LoadBalancingConfig for CDS.
   */
  static final class CdsConfig {

    /**
     * Name of cluster to query CDS for.
     */
    final String name;
    /**
     * Whether this cluster was dynamically chosen, so the XdsDependencyManager may be unaware of
     * it without an explicit cluster subscription.
     */
    final boolean isDynamic;

    CdsConfig(String name) {
      this(name, false);
    }

    CdsConfig(String name, boolean isDynamic) {
      checkArgument(name != null && !name.isEmpty(), "name is null or empty");
      this.name = name;
      this.isDynamic = isDynamic;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("name", name)
          .add("isDynamic", isDynamic)
          .toString();
    }
  }
}
