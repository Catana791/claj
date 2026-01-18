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


//TODO: not reliable, must be a packet sent/received another way than standard one.
public class ServerInfoPacket extends RoomCreationRequestPacket {
  @Override
  protected void readImpl(ByteBufferInput read) {
    // By default, arc server is configured to reply an empty buffer.
    // This can be used to determine whether this is an old CLaJ server or not.
    // Because on older versions, no discovery was configured.
    // Note: since an empty buffer can be received, this packet is also hard-coded into the api serializer.
    if (!read.buffer.hasRemaining()) version = 0;
    else super.readImpl(read);
  }
}
