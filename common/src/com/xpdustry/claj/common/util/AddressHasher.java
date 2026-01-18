package com.xpdustry.claj.common.util;

import java.net.InetAddress;


public class AddressHasher {
  /** Hashes the address using FNV-1a 64-bit. */
  public static long hash(InetAddress address) {
    long hash = 0xcbf29ce484222325L;
    for (byte b : address.getAddress()) {
      hash ^= b & 0xff;
      hash *= 0x100000001b3L;
    }
    return hash;
  }

  /** Generates an IPv6 address using the hash. */
  public static InetAddress generate(long addressHash) {
    byte[] bytes = new byte[16];
    // Use IPv6 Unique Local Address (fc00::/7), specifically fd00::/8
    bytes[0] = (byte) 0xfd;

    // Fill the last 8 bytes with the hash
    for (int i = 0; i < 8; i++) {
      bytes[8 + i] = (byte) ((addressHash >> ((7 - i) * 8)) & 0xFF);
    }

    try { return InetAddress.getByAddress(bytes); }
    catch (Exception ignored) { return null; } // cannot happen
  }

  /** Hashes the address then converts it back to an address. */
  public static InetAddress obfuscate(InetAddress address) {
    return generate(hash(address));
  }
}
