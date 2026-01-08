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

import com.xpdustry.claj.common.status.GameState;
import com.xpdustry.claj.common.status.MindustryGamemode;
import com.xpdustry.claj.common.util.Strings;


/** {@link GameState}'s {@link String} fields will be truncated 
 *  according to {@link mindustry.net.NetworkIO#writeServerData()}. */
public class RoomInfoPacket extends RoomLinkPacket {
  /** {@code null} if the room is private or no state was received from the host. */
  public GameState state;

  @Override
  protected void readImpl(ByteBufferInput read) {
    roomId = read.readLong();
    if (read.readBoolean()) return;
    state = new GameState(
      roomId,
      Strings.truncate(Strings.readUTF(read), 40/*Vars.maxNameLength*/),
      Strings.truncate(Strings.readUTF(read), 64),
      read.readInt(),
      read.readInt(),
      read.readInt(),
      read.readInt(),
      Strings.truncate(Strings.readUTF(read), 32),
      MindustryGamemode.all[read.readByte()],
      readUTFNullable(read, 50)
    );
  }  
 
  @Override
  public void write(ByteBufferOutput write) {
    write.writeLong(roomId);
    write.writeBoolean(state == null);
    if (state == null) return;
    Strings.writeUTF(write, Strings.truncate(state.name(), 40/*Vars.maxNameLength*/));
    Strings.writeUTF(write, Strings.truncate(state.mapname(), 64));
    write.writeInt(state.wave());
    write.writeInt(state.players());
    write.writeInt(state.playerLimit());
    write.writeInt(state.version());
    Strings.writeUTF(write, Strings.truncate(state.versionType(), 32));
    write.writeByte((byte)state.mode().ordinal());
    writeUTFNullable(write, state.modeName(), 50);
  }
  
  private static String readUTFNullable(ByteBufferInput read, int maxlen) {
    String str = Strings.readUTF(read);
    return str.isEmpty() ? null : Strings.truncate(str, maxlen);
  }
  
  private static void writeUTFNullable(ByteBufferOutput write, String str, int maxlen) {
    Strings.writeUTF(write, str == null ? "" : Strings.truncate(str, maxlen));
  }
}