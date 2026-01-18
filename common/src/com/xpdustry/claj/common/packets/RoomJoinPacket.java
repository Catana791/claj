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

package com.xpdustry.claj.common.packets;

import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import com.xpdustry.claj.common.status.ClajType;


public class RoomJoinPacket extends RoomLinkPacket {
  /** Room pin password. */
  public short password = -1;
  /** CLaJ Implementation type. 16 bytes max. */
  public ClajType type;

  @Override
  protected void readImpl(ByteBufferInput read) {
    super.readImpl(read);
    // Make it compatible with older versions where room password wasn't here
    // This will only work if the room doesn't have a password set
    boolean remaining = read.buffer.hasRemaining();
    password = remaining ? read.readShort() : -1;
    type = remaining ? ClajType.read(read.buffer) : null;
  }

  @Override
  public void write(ByteBufferOutput write) {
    super.write(write);
    write.writeShort(password);
    type.write(write.buffer);
  }
}
