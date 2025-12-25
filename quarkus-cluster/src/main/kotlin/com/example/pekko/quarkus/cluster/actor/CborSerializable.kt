package com.example.pekko.quarkus.cluster.actor

/**
 * Marker interface for cluster serialization.
 * All messages sent between cluster nodes must implement this interface.
 */
interface CborSerializable
