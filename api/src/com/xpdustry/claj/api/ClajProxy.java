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

package com.xpdustry.claj.api;

import java.nio.ByteBuffer;

import arc.Core;
import arc.func.Cons;
import arc.net.*;
import arc.util.io.ByteBufferInput;
import arc.util.io.ByteBufferOutput;

import com.xpdustry.claj.api.net.ProxyClient;
import com.xpdustry.claj.common.*;
import com.xpdustry.claj.common.ClajPackets.*;
import com.xpdustry.claj.common.net.FrameworkSerializer;
import com.xpdustry.claj.common.packets.*;
import com.xpdustry.claj.common.status.*;


/** The claj client that redirects packets from the relay to the local mindustry server. */
public class ClajProxy extends ProxyClient {
  /** Constant value saying that no room is created. This should be handled as an invalid id. */
  public static final long UNCREATED_ROOM = -1;
  
  public final ClajProvider provider;
  public boolean isPublic, isProtected;
  public short roomPassword;
  
  protected Cons<Long> roomCreated;
  protected Cons<CloseReason> roomClosed;
  protected long roomId = UNCREATED_ROOM;

  public ClajProxy(ClajProvider provider) {
    super(32768, 16384, new Serializer(), provider.getConnectionListener());
    this.provider = provider;
    
    receiver.handle(Connect.class, this::requestRoomId);
    receiver.handle(Disconnect.class, () -> runRoomClose(CloseReason.error));
    
    receiver.handle(ConnectionJoinPacket.class, p -> {
      if (!roomCreated()) return;
      if (getConnection(p.conID) != null) return;
      // Check if the link is the right
      if (p.roomId != roomId) close(p.conID, DcReason.error);
      else conConnected(p.conID, p.addressHash);
    });
    receiver.handle(ConnectionClosedPacket.class, p -> {
      if (roomCreated()) conDisconnected(p.conID, p.reason);
    });
    receiver.handle(ConnectionPacketWrapPacket.class, p -> {
      if (roomCreated()) conReceived(p.conID, p.object);
    });
    receiver.handle(ConnectionIdlingPacket.class, p -> {
      if (roomCreated()) conIdle(p.conID);
    });
    
    receiver.handle(RoomClosedPacket.class, p -> {
      // Ignore if room is already closed
      if (!roomCreated()) return;
      runRoomClose(p.reason);
    });
    receiver.handle(RoomLinkPacket.class, p -> {
      // Ignore if room is already created
      if (roomCreated()) return;
      roomId = p.roomId;
      // -1 is not allowed since it's used to specify an uncreated room
      if (roomId == UNCREATED_ROOM) return;
      runRoomCreated(); 
    });
    receiver.handle(RoomInfoRequestPacket.class, p -> {
      if (p.roomId == roomId) notifyGameState();
    });
    
    receiver.handle(ClajTextMessagePacket.class, p -> {
      provider.showTextMessage(p.message);
    });
    receiver.handle(ClajMessagePacket.class, p -> {
      provider.showMessage(p.message);
    });
    receiver.handle(ClajPopupPacket.class, p -> {
      provider.showPopup(p.message);
    });
  }
  
  /** This method must be used instead of others connect methods */
  public void connect(String host, int port, Cons<Long> created, Cons<CloseReason> closed, Cons<Throwable> failed) {
    try { 
      connect(host, port); 
      roomCreated = created;
      roomClosed = closed;
      ignoreExceptions = false;
    } catch (Exception e) {
      runRoomClose(CloseReason.error);
      failed.get(e);
    }
  }
  
  // Helpers
  protected <T> void postTask(Cons<T> consumer, T object) { postTask(() -> consumer.get(object)); }
  protected void postTask(Runnable run) { Core.app.post(run); }
  
  protected void runRoomCreated() {
    ignoreExceptions = true;
    if (roomCreated == null) return;
    postTask(roomCreated, roomId);
  }
  
  /** This also resets room id and removes callbacks. */
  protected void runRoomClose(CloseReason reason) {
    ignoreExceptions = false;
    roomId = UNCREATED_ROOM;
    if (roomClosed != null) postTask(roomClosed, reason);
    roomCreated = null;
    roomClosed = null;
    close();
  }
  
  /** {@code -1} means no room created. */
  public long roomId() {
    return roomId;
  }
  
  public boolean roomCreated() {
    return roomId != UNCREATED_ROOM;
  }
  
  /** @apiNote untested. */
  public ClajLink getLink() {
    return new ClajLink(getRemoteAddressTCP().getHostString(), getRemoteAddressTCP().getPort(), roomId);
  }
  
  public GameState getState() {
    return provider.getRoomState(this);
  }
  
  @Override
  public void close() {
    if (isConnected()) closeRoom();
    super.close();
  }
  
  public void closeRoom() {
    sendTCP(makeRoomClosePacket());
  }
  
  public void requestRoomId() {
    if (roomCreated()) return;
    sendTCP(makeRoomCreatePacket(provider.getVersion()));
  }
  
  public void notifyConfiguration() {
    sendTCP(makeRoomConfigPacket(isPublic, isProtected, roomPassword));
  }
  
  public void notifyGameState() {
    GameState state = getState();
    if (state != null)
      sendTCP(makeRoomStatePacket(roomId, state));
  }

  protected Object makeRoomStatePacket(long roomId, GameState state) {
    RoomInfoPacket p = new RoomInfoPacket();
    p.roomId = roomId;
    p.state = state;
    return p;
  }
  
  protected Object makeRoomConfigPacket(boolean isPublic, boolean isProtected, short password) {
    RoomConfigPacket p = new RoomConfigPacket();
    p.isPublic = isPublic;
    p.isProtected = isProtected;
    p.password = password;
    return p;
  }
  
  protected Object makeRoomCreatePacket(String version) {
    RoomCreationRequestPacket p = new RoomCreationRequestPacket();
    p.version = version;
    return p;
  }
  
  protected Object makeRoomClosePacket() {
    return new RoomClosureRequestPacket();
  }
  
  @Override
  protected Object makeConWrapPacket(int conId, Object object, boolean tcp) { 
    ConnectionPacketWrapPacket p = new ConnectionPacketWrapPacket();
    p.conID = conId;
    p.isTCP = tcp;
    p.object = object;
    return p;
  }

  @Override
  protected Object makeConClosePacket(int conId, DcReason reason) { 
    ConnectionClosedPacket p = new ConnectionClosedPacket();
    p.conID = conId;
    p.reason = reason;
    return p;
  }
  
  
  public static class Serializer implements NetSerializer, FrameworkSerializer {
    //maybe faster without ThreadLocal?
    protected final ByteBufferInput read = new ByteBufferInput();
    protected final ByteBufferOutput write = new ByteBufferOutput();

    @Override
    public Object read(ByteBuffer buffer) {
      switch (buffer.get()) {
        case ClajNet.frameworkId: 
          return readFramework(buffer);
        
        case ClajNet.oldId:
          throw new ArcNetException("Received a packet from the old CLaJ protocol");
          
        case ClajNet.id:
          read.buffer = buffer;
          Packet packet = ClajNet.newPacket(buffer.get());
          packet.read(read);
          return packet;
          
        default:
          buffer.position(buffer.position()-1);
          throw new ArcNetException("Unknown protocol id: " + buffer.get());
      }
    }
    
    @Override
    public void write(ByteBuffer buffer, Object object) {
      if (object instanceof ByteBuffer buf) {
        buffer.put(buf);
        
      } else if (object instanceof FrameworkMessage framework) {
        buffer.put(ClajNet.frameworkId);
        writeFramework(buffer, framework);

      } else if (object instanceof Packet packet) {
        write.buffer = buffer;
        if (!(object instanceof RawPacket)) 
          buffer.put(ClajNet.id).put(ClajNet.getId(packet));
        packet.write(write);
        
      } else {
        throw new ArcNetException("Unknown packet type: " + object.getClass());
      }
    }
  }
}
