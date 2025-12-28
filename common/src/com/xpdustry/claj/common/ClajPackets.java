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
  public static void register() {
    ClajNet.register(ConnectionPacketWrapPacket::new);
    ClajNet.register(ConnectionClosedPacket::new);
    ClajNet.register(ConnectionJoinPacket::new);
    ClajNet.register(ConnectionIdlingPacket::new);
    ClajNet.register(RoomCreationRequestPacket::new);
    ClajNet.register(RoomClosureRequestPacket::new);
    ClajNet.register(RoomClosedPacket::new);
    ClajNet.register(RoomLinkPacket::new);
    ClajNet.register(RoomJoinPacket::new);
    ClajNet.register(ClajTextMessagePacket::new);
    ClajNet.register(ClajMessagePacket::new);
    ClajNet.register(ClajPopupPacket::new);
  }

  
  public static enum CloseReason {
    /** Closed without reason. */
    closed,
    /** Incompatible claj client. */
    obsoleteClient, 
    /** Old claj version. */
    outdatedVersion,
    /** Server is shutting down. */
    serverClosed,
    /** Defined room capacity is reached. */
    roomFull;
    
    public static final CloseReason[] all = values();
  }
  
  public static enum MessageType {
    serverClosing,
    packetSpamming,
    alreadyHosting,
    roomClosureDenied,
    conClosureDenied;
    
    public static final MessageType[] all = values();
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
    
    public RawPacket r(ByteBufferInput read) {
      read(read);
      return this;
    }
    public RawPacket w(ByteBufferOutput write) { 
      write(write);
      return this;
    }
  }
}
