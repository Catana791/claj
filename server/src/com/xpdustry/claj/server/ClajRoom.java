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

package com.xpdustry.claj.server;

import arc.net.Connection;
import arc.net.DcReason;
import arc.net.NetListener;
import arc.struct.IntMap;

import com.xpdustry.claj.common.ClajPackets.*;
import com.xpdustry.claj.common.packets.*;
import com.xpdustry.claj.common.util.Strings;
import com.xpdustry.claj.server.util.NetworkSpeed;


public class ClajRoom implements NetListener {
  private boolean closed;
  
  /** The room id. */
  public final long id;
  /** 
   * The room id encoded in an url-safe base64 string.
   * @see com.xpdustry.claj.client.ClajLink
   */
  public final String idString;
  /** The host connection of this room. */
  public final Connection host;
  /** Using IntMap instead of Seq for faster search. */
  public final IntMap<Connection> clients = new IntMap<>();
  /** For debugging, to know how many packets were transferred from a client to a host, and vice versa. */
  public final NetworkSpeed transferredPackets = new NetworkSpeed(8);

  public ClajRoom(long id, Connection host) {
    this.id = id;
    this.idString = Strings.longToBase64(id);
    this.host = host;
  }

  /** Alert the host that a new client is coming */
  @Override
  public void connected(Connection connection) {
    if (closed) return;
    
    // Assume the host is still connected
    ConnectionJoinPacket p = new ConnectionJoinPacket();
    p.conID = connection.getID();
    p.roomId = id;
    host.sendTCP(p);

    clients.put(connection.getID(), connection);
  }

  /** Alert the host that a client disconnected. This doesn't close the connection. */
  @Override
  public void disconnected(Connection connection, DcReason reason) {
    if (closed) return;
    
    if (connection == host) {
      close();
      return;
      
    } else if (host.isConnected()) {
      ConnectionClosedPacket p = new ConnectionClosedPacket();
      p.conID = connection.getID();
      p.reason = reason;
      host.sendTCP(p);
    }

    clients.remove(connection.getID());
  }
  
  /** Doesn't notify the room host about a disconnected client */
  public void disconnectedQuietly(Connection connection, DcReason reason) {
    if (closed) return;
    
    if (connection == host) close();
    else clients.remove(connection.getID());
  }
  
  /** 
   * Wrap and re-send the packet to the host, if it come from a connection, 
   * else un-wrap and re-send the packet to the specified connection. <br>
   * Only {@link ClajPackets.ConnectionPacketWrapPacket} and {@link ByteBuffer} are allowed.
   */
  @Override
  public void received(Connection connection, Object object) {
    if (closed) return;
    
    if (connection == host) {
      // Only claj packets are allowed in the host's connection
      // and can only be ConnectionPacketWrapPacket at this point.
      if (!(object instanceof ConnectionPacketWrapPacket wrap)) return;

      int conID = wrap.conID;
      Connection con = clients.get(conID);
      
      if (con != null && con.isConnected()) {
        if (wrap.isTCP) con.sendTCP(wrap.raw);
        else con.sendUDP(wrap.raw);
        transferredPackets.addUploadMark();

      // Notify that this connection doesn't exist, this case normally never happen
      } else if (host.isConnected()) { 
        ConnectionClosedPacket p = new ConnectionClosedPacket();
        p.conID = conID;
        p.reason = DcReason.error;
        host.sendTCP(p);
      }
      
    } else if (host.isConnected() && clients.containsKey(connection.getID())) {
      // Only raw buffers are allowed here.
      // We never send claj packets to anyone other than the room host, framework packets are ignored
      // and mindustry packets are saved as raw buffer.
      if (!(object instanceof RawPacket raw)) return;
      
      ConnectionPacketWrapPacket p = new ConnectionPacketWrapPacket();
      p.conID = connection.getID();
      p.raw = raw;
      host.sendTCP(p);
      transferredPackets.addDownloadMark();
    }
  }
  
  /** Notify the host of an idle connection. */
  @Override
  public void idle(Connection connection) {
    if (closed) return;

    if (connection == host) {
      // Ignore if this is the room host
      
    } else if (host.isConnected() && clients.containsKey(connection.getID())) {
      ConnectionIdlingPacket p = new ConnectionIdlingPacket();
      p.conID = connection.getID();
      host.sendTCP(p);
    }
  }
  
  /** Notify the room id to the host. Must be called once. */
  public void create() {
    if (closed) return;
    
    // Assume the host is still connected
    RoomLinkPacket p = new RoomLinkPacket();
    p.roomId = id;
    host.sendTCP(p);
  }
  
  /** @return whether the room is closed or not */
  public boolean isClosed() {
    return closed;
  }
  
  public void close() {
    close(CloseReason.closed);
  }
  
  /** 
   * Closes the room and disconnects the host and all clients. 
   * The room object cannot be used anymore after this.
   */
  public void close(CloseReason reason) {
    if (closed) return;
    closed = true; // close before kicking connections, to avoid receiving events
    
    // Alert the close reason to the host
    RoomClosedPacket p = new RoomClosedPacket();
    p.reason = reason;
    host.sendTCP(p);
    
    host.close(DcReason.closed);
    for (Connection c : clients.values()) c.close(DcReason.closed);
    clients.clear();
  }
  
  /** Checks if the connection is the room host or one of his client */
  public boolean contains(Connection con) {
    if (closed || con == null) return false;
    if (con == host) return true;
    return clients.containsKey(con.getID());
  }
  
  /** Send a message to the host and clients. */
  public void message(String text) {
    if (closed) return;
    
    // Just send to host, it will re-send it properly to all clients
    ClajTextMessagePacket p = new ClajTextMessagePacket();
    p.message = text;
    host.sendTCP(p);
  }
  
  /** Send a message the host and clients. Will be translated by the room host. */
  public void message(MessageType message) {
    if (closed) return;
    
    ClajMessagePacket p = new ClajMessagePacket();
    p.message = message;
    host.sendTCP(p);
  }
  
  /** Send a popup to the room host. */
  public void popup(String text) {
    if (closed) return;
    
    ClajPopupPacket p = new ClajPopupPacket();
    p.message = text;
    host.sendTCP(p);
  }
}
