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

package com.xpdustry.claj.api;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;

import arc.func.Cons;
import arc.net.ArcNet;
import arc.net.Client;
import arc.net.DcReason;
import arc.net.FrameworkMessage;
import arc.struct.Seq;
import arc.util.Reflect;

import com.xpdustry.claj.common.ClajNet;
import com.xpdustry.claj.common.net.ClientReceiver;
import com.xpdustry.claj.common.packets.*;
import com.xpdustry.claj.common.status.*;


// FIXME: see ClajPingerManager comment
/**
 * Note that {@link #pingHost}, {@link #requestRoomList} and {@link #joinRoom} are async operations.<br>
 * If one of them are called while another is running, the last one is canceled.
 */
public class ClajPinger extends Client {
  public static final short NO_PASSWORD = -1;
  public static int defaultTimeout = 5000; //ms
  public static int pingTimeout = 5000; //ms

  protected final ClajProvider provider;
  protected String connectHost;
  protected int connectPort;
  protected volatile boolean shutdown = true, connecting;
  /** If {@code true}, current and future operations will be canceled. */
  public volatile boolean canceling;

  protected Cons<ServerState> pingSuccess;
  protected Cons<Exception> pingFailed;
  protected volatile long time, timeout;
  protected volatile boolean pingReceived, pinging;

  protected Cons<Seq<ClajRoom<?>>> listInfo;
  protected Cons<Exception> listFailed;
  protected volatile boolean listing;

  protected Runnable joinSuccess;
  protected Cons<RejectReason> joinDenied;
  protected Cons<Exception> joinFailed;
  protected volatile long requestedRoom = -1;
  protected volatile boolean joining;

  protected Cons<ClajRoom<?>> infoSuccess;
  protected Runnable infoNotFound;
  protected Cons<Exception> infoFailed;
  protected volatile boolean infoing;

  public ClajPinger(ClajProvider provider) {
    super(8192, 8192, new Serializer());
    ((Serializer)getSerialization()).set(this);
    this.provider = provider;
    ClientReceiver receiver = new ClientReceiver(this, null); // no need to delegate to the main thread

    receiver.handle(RoomJoinAcceptedPacket.class, p -> {
      if (p.roomId != -1 && p.roomId == requestedRoom)
        runJoinSuccess();
    });
    receiver.handle(RoomJoinDeniedPacket.class, p -> {
      if (p.roomId != -1 && p.roomId == requestedRoom)
        runJoinDenied(p.reason);
    });

    receiver.handle(RoomListPacket.class, p -> {
      runListInfo(p.size, p.rooms, p.isProtected, p.states);
    });
    receiver.handle(RoomInfoPacket.class, p -> {
      if (p.roomId == requestedRoom)
        runInfoSuccess(p.roomId, p.isProtected, p.type, p.state);
    });
    receiver.handle(RoomInfoDeniedPacket.class, this::runInfoNotFound);

    receiver.handle(ServerInfoPacket.class, p -> {
      runPingSuccess(p.version);
    });
  }

  @Override
  public void update(int timeout) {
    try {
      super.update(canceling ? 0 : timeout);
      if (pinging && !pingReceived && System.currentTimeMillis() - time >= pingTimeout)
        runPingFailed(new RuntimeException("Ping timed out"));
      if (canceling) close();
    } catch (Exception e) { ArcNet.handleError(e); }
  }

  @Override
  public void run() {
    shutdown = false;
    super.run();
  }

  @Override
  public void start() {
    if (getUpdateThread() != null) shutdown = true;
    super.start();
  }

  @Override
  public void stop() {
    if(shutdown) return;
    super.stop();
    shutdown = true;
  }

  @Override
  public void close() {
    if (canceling) {
      close(DcReason.closed);
      // Makes #close() doesn't wait for 'updateLock', which will makes #connect() cancelable
      Reflect.<Selector>get(Client.class, this, "selector").wakeup();
    } else super.close();
  }

  @Override
  public void close(DcReason reason) {
    cancel();
    super.close(reason);
  }

  /** Cancel running operation. */
  public synchronized void cancel() {
    if (pinging) runPingFailed(new RuntimeException("Ping canceled"));
    if (listing) runListFailed(new RuntimeException("Room listing canceled"));
    if (joining) runJoinFailed(new RuntimeException("Room join canceled"));
    if (infoing) runInfoFailed(new RuntimeException("Room info canceled"));
    if (connecting) {
      //Reflect.set(Client.class, this, "tcpRegistered", true);
      //Reflect.set(Client.class, this, "udpRegistered", true);
      super.close(DcReason.closed);
    }
  }

  public boolean isRunning() {
    return !shutdown;
  }

  public boolean isConnecting() {
    return connecting;
  }

  public synchronized boolean isWorking() {
    return pinging || listing || joining || infoing;
  }

  // Helpers
  protected <T> void postTask(Cons<T> consumer, T object) { postTask(() -> consumer.get(object)); }
  protected void postTask(Runnable run) { provider.postTask(run); }

  protected synchronized void resetPingState(Cons<ServerState> success, Cons<Exception> failed) {
    time = System.currentTimeMillis();
    pingSuccess = success;
    pingFailed = failed;
    pingReceived = false;
    pinging = false;
  }

  protected void runPingSuccess(int version) {
    pingReceived = true;
    if (pingSuccess != null) {
      int ping = (int)(System.currentTimeMillis() - time);
      postTask(pingSuccess, new ServerState(connectHost, connectPort, version, ping));
    }
    resetPingState(null, null);
    close();
  }

  protected void runPingFailed(Exception e) {
    if (pingFailed != null) postTask(pingFailed, e);
    resetPingState(null, null);
    close();
  }

  @SuppressWarnings({"unchecked", "rawtypes"}) // meh...
  protected synchronized <T> void resetListState(Cons<Seq<ClajRoom<T>>> rooms, Cons<Exception> failed) {
    listInfo = (Cons)rooms;
    listFailed = failed;
    listing = false;
  }

  protected void runListInfo(int size, long[] rooms, boolean[] isProtected, ByteBuffer[] states) {
    // Avoid creating useless objects if the callback is not defined.
    if (listInfo == null) return;
    Seq<ClajRoom<?>> roomList = new Seq<>(size);
    ClajType type = provider.getType();
    for (int i=0; i<size; i++) {
      if (rooms[i] == ClajProxy.UNCREATED_ROOM) continue; // ignore invalid rooms
      roomList.add(new ClajRoom<>(
        rooms[i], true, isProtected[i],
        provider.readRoomState(rooms[i], type, states[i]),
        new ClajLink(connectHost, connectPort, rooms[i])
      ));
    }

    postTask(listInfo, roomList);
    resetListState(null, null);
    close();
  }

  protected void runListFailed(Exception e) {
    if (listFailed != null) postTask(listFailed, e);
    resetListState(null, null);
    close();
  }

  protected synchronized void resetJoinState(Runnable success, Cons<RejectReason> reject, Cons<Exception> failed) {
    joinSuccess = success;
    joinDenied = reject;
    joinFailed = failed;
    requestedRoom = -1;
    joining = false;
  }

  protected void runJoinSuccess() {
    if (joinSuccess != null) postTask(joinSuccess);
    resetJoinState(null, null, null);
    close();
  }

  protected void runJoinDenied(RejectReason reason) {
    if (joinDenied != null) postTask(joinDenied, reason);
    resetJoinState(null, null, null);
    close();
  }

  protected void runJoinFailed(Exception e) {
    if (joinFailed != null) postTask(joinFailed, e);
    resetJoinState(null, null, null);
    close();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  protected synchronized <T> void resetInfoState(Cons<ClajRoom<T>> success, Runnable notFound, Cons<Exception> failed) {
    infoSuccess = (Cons)success;
    infoNotFound = notFound;
    infoFailed = failed;
    requestedRoom = -1;
    infoing = false;
  }

  protected void runInfoSuccess(long roomId, boolean isProtected, ClajType type, ByteBuffer state) {
    if (infoSuccess != null) {
      ClajRoom<?> room = new ClajRoom<>(
        roomId, true, isProtected,
        provider.readRoomState(roomId, type, state),
        new ClajLink(connectHost, connectPort, roomId)
      );
      postTask(infoSuccess, room);
    }
    resetInfoState(null, null, null);
    close();
  }

  protected void runInfoFailed(Exception e) {
    if (infoFailed != null) postTask(infoFailed, e);
    resetInfoState(null, null, null);
    close();
  }

  protected void runInfoNotFound() {
    if (infoNotFound != null) postTask(infoNotFound);
    resetInfoState(null, null, null);
    close();
  }

  /**
   * Connect using {@link #defaultTimeout} and same {@code port} for TCP and UDP. <br>
   * This also ensures that the client is running before connection, and can be canceled.
   */
  public void connect(String host, int port) throws IOException {
    if (!isRunning()) start();
    connecting = true;
    connectHost = host;
    connectPort = port;
    try { connect(defaultTimeout, host, port, port); }
    finally { connecting = false; }
  }

  public void pingHost(String host, int port, Cons<ServerState> success, Cons<Exception> failed) {
    close();
    //arc.util.Log.info("pinger: @, @, @", canceling, Thread.currentThread().getName(), System.currentTimeMillis());
    resetPingState(success, failed);
    pinging = true;
    if (canceling) {
      cancel();
      return;
    }
    try { connect(host, port); }
    catch (Exception e) {
      runPingFailed(e);
      return;
    }
    requestServerStatus();
  }

  public <T> void requestRoomList(String host, int port, Cons<Seq<ClajRoom<T>>> rooms, Cons<Exception> failed) {
    if (!canceling) {
      try { connect(host, port); }
      catch (Exception e) {
        resetListState(rooms, failed);
        runListFailed(e);
        return;
      }
    }
    resetListState(rooms, failed);
    listing = true;
    if (canceling) cancel();
    else requestRoomList();
  }


  public void joinRoom(String host, int port, long roomId, Runnable success, Cons<RejectReason> reject,
                       Cons<Exception> failed) {
    joinRoom(host, port, roomId, NO_PASSWORD, success, reject, failed);
  }

  public void joinRoom(String host, int port, long roomId, short password, Runnable success,
                       Cons<RejectReason> reject, Cons<Exception> failed) {
    if (!canceling) {
      try { connect(host, port); }
      catch (Exception e) {
        resetJoinState(success, reject, failed);
        runJoinFailed(e);
        return;
      }
    }
    resetJoinState(success, reject, failed);
    requestedRoom = roomId;
    joining = true;
    if (canceling) cancel();
    else requestRoomJoin(roomId, password);
  }

  public <T> void requestRoomInfo(String host, int port, long roomId, Cons<ClajRoom<T>> info, Runnable notFound,
                              Cons<Exception> failed) {
    if (!canceling) {
      try { connect(host, port); }
      catch (Exception e) {
        resetInfoState(info, notFound, failed);
        runInfoFailed(e);
        return;
      }
    }
    resetInfoState(info, notFound, failed);
    requestedRoom = roomId;
    infoing = true;
    if (canceling) cancel();
    else requestRoomInfo(roomId);
  }

  protected void requestServerStatus() {
    sendUDP(FrameworkMessage.discoverHost);
  }

  protected void requestRoomInfo(long roomId) {
    RoomInfoRequestPacket p = new RoomInfoRequestPacket();
    p.roomId = roomId;
    sendTCP(p);
  }

  protected void requestRoomList() {
    RoomListRequestPacket p = new RoomListRequestPacket();
    p.type = provider.getType();
    sendTCP(p);
  }

  protected void requestRoomJoin(long roomId, short password) {
    RoomJoinPacket p = new RoomJoinPacket();
    p.roomId = roomId;
    p.password = password;
    p.type = provider.getType();
    sendTCP(p);
  }


  /** Modified serializer that reads only one packet type in {@linkplain ClajPinger#pinging pinging} mode. */
  protected static class Serializer extends ClajSerializer {
    protected ClajPinger pinger;
    public void set(ClajPinger pinger) { this.pinger = pinger; }

    @Override
    public Object read(ByteBuffer buffer) {
      if (pinger.pinging) {
        if (!buffer.hasRemaining() || buffer.get() == ClajNet.id) {
          read.buffer = buffer;
          return new ServerInfoPacket().r(read);
        }
        buffer.position(buffer.position()-1);
      }
      return super.read(buffer);
    }
  }
}
