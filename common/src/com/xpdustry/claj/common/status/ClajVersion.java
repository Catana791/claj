/**
 * This file is part of CLaJ. The system that allows you to play with your friends,
 * just by creating a room, copying the link and sending it to your friends.
 * Copyright (c) 2025-2026  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.xpdustry.claj.common.status;

import arc.util.Strings;

/**
 * CLaJ versions are always in format: {@code protocolVersion.majorVersion.minorVersion}. <br>
 * Only {@code majorVersion} is important. <br>
 * {@code protocolVersion} is discarded, as it's always {@code 2} (for this project).
 * Different CLaJ types must not be compatible with each others. <br>
 * As well as {@code minorVersion} (optional), because it's represents changes that doesn't affect the protocol itself.
 */
public class ClajVersion {
  public final int protocolVersion, majorVersion, minorVersion;

  public ClajVersion(int protocolVersion, int majorVersion, int minorVersion) {
    this.protocolVersion = protocolVersion;
    if (protocolVersion < 0) throw makeFormatError("protocolVersion", -3);
    this.majorVersion = majorVersion;
    if (majorVersion < 0) throw makeFormatError("majorVersion", -3);
    this.minorVersion = minorVersion;
    if (minorVersion < 0) throw makeFormatError("minorVersion", -3);
  }

  public ClajVersion(String version) throws IllegalArgumentException {
    int i = -1;

    protocolVersion = parse(version, ++i, i = version.indexOf('.', i));
    if (protocolVersion < 0) throw makeFormatError("protocolVersion", protocolVersion);
    majorVersion = parse(version, ++i, (i = version.indexOf('.', i)) == -1 ? version.length() : i);
    if (majorVersion < 0) throw makeFormatError("majorVersion", majorVersion);
    minorVersion = i == -1 ? 0 : parse(version, ++i, (i = version.indexOf('.', i)) == -1 ? version.length() : i);
    if (minorVersion < 0) throw makeFormatError("minorVersion", minorVersion);
    if (i != -1) throw new IllegalArgumentException("Too many version parts");
  }

  @Override
  public String toString() {
    return protocolVersion + "." + majorVersion + "." + minorVersion;
  }


  protected static IllegalArgumentException makeFormatError(String var, int value) {
    return new IllegalArgumentException((value == -1 ? "Missing " : "Invalid ") + var + (value == -3 ? " range" : ""));
  }

  protected static int parse(String str, int start, int stop) {
    if (stop < 0 || start > stop) return -1;
    int parsed = Strings.parseInt(str, 10, Integer.MIN_VALUE, start, stop);
    return parsed == Integer.MIN_VALUE ? -2 : parsed < 0 ? -3 : parsed;
  }
}
