package com.example.pekko.spring.optionalcluster.actor

/**
 * Marker interface for CBOR serialization in cluster mode.
 * All messages that need to be sent across cluster nodes must implement this interface.
 */
interface CborSerializable
