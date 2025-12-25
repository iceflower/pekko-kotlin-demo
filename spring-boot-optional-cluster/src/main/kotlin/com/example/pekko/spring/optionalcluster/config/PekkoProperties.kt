package com.example.pekko.spring.optionalcluster.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for Pekko ActorSystem.
 *
 * Example usage in application.yml:
 * ```yaml
 * pekko:
 *   cluster:
 *     enabled: true
 *     hostname: 127.0.0.1
 *     port: 25520
 *     seed-nodes:
 *       - "pekko://optional-cluster-system@127.0.0.1:25520"
 * ```
 */
@ConfigurationProperties(prefix = "pekko")
data class PekkoProperties(
    val cluster: ClusterProperties = ClusterProperties()
) {
    data class ClusterProperties(
        /**
         * Enable or disable cluster mode.
         * When false, a standalone ActorSystem is created.
         * When true, a cluster-enabled ActorSystem is created.
         */
        val enabled: Boolean = false,

        /**
         * Hostname for cluster communication (Artery).
         */
        val hostname: String = "127.0.0.1",

        /**
         * Port for cluster communication (Artery).
         */
        val port: Int = 25520,

        /**
         * Seed nodes for cluster formation.
         */
        val seedNodes: List<String> = listOf("pekko://optional-cluster-system@127.0.0.1:25520")
    )
}
