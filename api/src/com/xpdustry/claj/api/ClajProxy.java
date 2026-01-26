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

import arc.func.Cons;
import arc.net.DcReason;

import com.xpdustry.claj.api.net.ProxyClient;
import com.xpdustry.claj.common.ClajPackets.Connect;
import com.xpdustry.claj.common.ClajPackets.Disconnect;
import com.xpdustry.claj.common.net.stream.StreamSender;
import com.xpdustry.claj.common.packets.*;
import com.xpdustry.claj.common.status.ClajType;
import com.xpdustry.claj.common.status.CloseReason;


/** The claj client that redirects packets from the relay to the local mindustry server. */
public class ClajProxy extends ProxyClient {
  /** Constant value saying that no room is created. This should be handled as an invalid id. */
  public static final long UNCREATED_ROOM = -1;

  public final ClajProvider provider;
  public boolean isPublic, isProtected;
  public short roomPassword;

  protected Cons<ClajLink> roomCreated;
  protected Cons<CloseReason> roomClosed;
  protected long roomId = UNCREATED_ROOM;
  protected ClajLink link;

  public ClajProxy(ClajProvider provider) {
    super(32768, 16384, new ClajSerializer(), provider.getConnectionListener(), provider::postTask);
    this.provider = provider;

    receiver.handle(Connect.class, this::requestRoomId);
    receiver.handle(Disconnect.class, () -> runRoomClose(CloseReason.error));

    receiver.handle(ConnectionJoinPacket.class, p -> {
      if (!roomCreated() || getConnection(p.conID) != null) return;
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
      if (!roomCreated()) runRoomClose(p.reason);
    });
    receiver.handle(RoomLinkPacket.class, p -> {
      if (!roomCreated()) runRoomCreated(p.roomId);
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
  public void connect(String host, int port, Cons<ClajLink> created, Cons<CloseReason> closed, Cons<Throwable> failed) {
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
  protected void postTask(Runnable run) { provider.postTask(run); }

  protected void runRoomCreated(long roomId) {
    ignoreExceptions = true;
    this.roomId = roomId;
    link = new ClajLink(connectHost.getHostName(), connectTcpPort, roomId);
    // -1 is not allowed since it's used to specify an uncreated room
    if (roomId == UNCREATED_ROOM) return;
    if (roomCreated != null) postTask(roomCreated, link);
    notifyConfiguration();
  }

  /** This also resets room id and removes callbacks. */
  protected void runRoomClose(CloseReason reason) {
    ignoreExceptions = false;
    roomId = UNCREATED_ROOM;
    link = null;
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

  public ClajLink link() {
    return link;
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
    sendTCP(makeRoomCreatePacket(provider.getVersion(), provider.getType()));
  }

  public void setDefaultConfiguration(boolean isPublic, boolean isProtected, short roomPassword) {
    boolean notify = this.isPublic != isPublic
                  || this.isProtected != isProtected
                  || this.roomPassword != roomPassword;
    this.isPublic = isPublic;
    this.isProtected = isProtected;
    this.roomPassword = roomPassword;
    if (notify) notifyConfiguration();
  }

  public void notifyConfiguration() {
    if (!roomCreated()) return;
    sendTCP(makeRoomConfigPacket(isPublic, isProtected, roomPassword));
  }

  public void notifyGameState() {
    if (!roomCreated()) return;
    ByteBuffer state = (ByteBuffer)provider.writeRoomState(this).flip();
    Packet p = makeRoomStatePacket(roomId, state);
    // In case of a big state, chunk it
    if (state.remaining() < 8128) sendTCP(p);
    else if (state.remaining() > Character.MAX_VALUE)
      throw new IllegalArgumentException("Buffer size must be less than " + Character.MAX_VALUE);
    else StreamSender.send(this, p);
  }

  protected Packet makeRoomStatePacket(long roomId, ByteBuffer state) {
    RoomInfoPacket p = new RoomInfoPacket();
    p.roomId = roomId;
    p.state = state;
    return p;
  }

  protected Packet makeRoomConfigPacket(boolean isPublic, boolean isProtected, short password) {
    RoomConfigPacket p = new RoomConfigPacket();
    p.isPublic = isPublic;
    p.isProtected = isProtected;
    p.password = password;
    return p;
  }

  protected Packet makeRoomCreatePacket(int version, ClajType type) {
    RoomCreationRequestPacket p = new RoomCreationRequestPacket();
    p.version = version;
    p.type = type;
    return p;
  }

  protected Packet makeRoomClosePacket() {
    return new RoomClosureRequestPacket();
  }

  @Override
  protected Packet makeConWrapPacket(int conId, Object object, boolean tcp) {
    ConnectionPacketWrapPacket p = new ConnectionPacketWrapPacket();
    p.conID = conId;
    p.isTCP = tcp;
    p.object = object;
    return p;
  }

  @Override
  protected Packet makeConClosePacket(int conId, DcReason reason) {
    ConnectionClosedPacket p = new ConnectionClosedPacket();
    p.conID = conId;
    p.reason = reason;
    return p;
  }
}
