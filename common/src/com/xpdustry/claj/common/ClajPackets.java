/**
 * This file is part of CLaJ. The system that allows you to play with your friends, 
 * just by creating a room, copying the link and sending it to your friends.
 * Copyright (c) 2025  Xpdustry
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

package com.xpdustry.claj.common;

import arc.net.DcReason;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import com.xpdustry.claj.common.packets.*;


public class ClajPackets {  
  public static void init() {
    ClajNet.register(ConnectionJoinPacket::new);
    ClajNet.register(ConnectionClosedPacket::new);
    ClajNet.register(ConnectionPacketWrapPacket::new);
    ClajNet.register(ConnectionIdlingPacket::new);
    ClajNet.register(RoomCreationRequestPacket::new); // <-- should be the 5th
    ClajNet.register(RoomClosureRequestPacket::new);  // These two MUST not be moved.
    ClajNet.register(RoomClosedPacket::new);          // They are here for compatibility reason.
    ClajNet.register(RoomJoinPacket::new);            // <-- should be the 8th
    ClajNet.register(RoomJoinAcceptedPacket::new);
    ClajNet.register(RoomJoinDeniedPacket::new);
    ClajNet.register(RoomLinkPacket::new);
    ClajNet.register(RoomConfigPacket::new);
    ClajNet.register(RoomListRequestPacket::new);
    ClajNet.register(RoomListPacket::new);
    ClajNet.register(RoomInfoRequestPacket::new);
    ClajNet.register(RoomInfoPacket::new);
    ClajNet.register(ServerInfoPacket::new);
    ClajNet.register(ClajTextMessagePacket::new);
    ClajNet.register(ClajMessagePacket::new);
    ClajNet.register(ClajPopupPacket::new);
  }
  
  
  /** Generic client connection event. */
  public static class Connect implements Packet {
    public String address;
  }

  /** Generic client disconnection event. */
  public static class Disconnect implements Packet {
    public DcReason reason;
  }
  
  public static class RawPacket implements Packet {
    public byte[] data = {};

    public void read(ByteBufferInput read) { 
      data = new byte[read.buffer.remaining()];
      read.readFully(data);
    }
    
    public void write(ByteBufferOutput write) {
      write.write(data);
    }
  }
}
