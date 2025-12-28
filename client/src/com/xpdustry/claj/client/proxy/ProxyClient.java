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

package com.xpdustry.claj.client.proxy;

import java.io.IOException;
import java.net.InetAddress;

import arc.net.ArcNetException;
import arc.net.Client;
import arc.net.Connection;
import arc.net.DcReason;
import arc.net.NetListener;
import arc.net.NetSerializer;
import arc.struct.IntMap;
import arc.util.Log;
import arc.util.Reflect;
import arc.util.Structs;

/** 
 * A client that act like a server. <br>
 * The proxy doesn't do all the job: <br>
 * - Packet reception must be done manually (by overriding {@link #received}). <br>
 * - Notifying methods must be called ({@link #conConnected}, {@link #conDisconnected}, {@link #conReceived} and
 * {@link #conIdle}). <br>
 * - Packet making methods must be defined ({@link #makeWrapPacket} and {@link #makeClosePacket}).
 */
public abstract class ProxyClient extends Client implements NetListener {
  public static int defaultTimeout = 5000; //ms

  /** For faster get. */
  protected final IntMap<VirtualConnection> connectionsMap = new IntMap<>();
  /** For faster iteration. */
  protected VirtualConnection[] connections = {};
  protected final NetListener dispatchListener; 
  protected volatile boolean shutdown, ignoreExceptions, connecting;
  
  public ProxyClient(int writeBufferSize, int objectBufferSize, NetSerializer serialization, 
                     NetListener dispatchListener) { 
    super(writeBufferSize, objectBufferSize, serialization); 
    this.dispatchListener = dispatchListener;
    addListener(this);
  }
  
  @Override
  public void connect(int timeout, InetAddress host, int tcpPort, int udpPort) throws IOException {
    connecting = true;
    try { super.connect(timeout, host, tcpPort, udpPort); }
    catch (Exception e) {
      connecting = false;
      throw e;
    }
  }
 
  /** 
   * Ignore exceptions when possible, and maintain idle state of virtual connections. <br>
   * This also tries to ignore errors, to avoid stopping the proxy every time a virtual connection is doing a mess.
   */
  @Override
  public void run() {
    shutdown = false;
    while(!shutdown) {
      try {
        update(250); 
        // update idle
        for (VirtualConnection c : connections) {
          if (c.isIdle()) c.notifyIdle0();
        }
      } catch (IOException ex) { 
        close(); 
      } catch (ArcNetException ex) {
        if (ignoreExceptions) Log.err("Ignored Exception", ex);
        else {
          // Reflection is needed because the field is package-protected
          Reflect.set(Connection.class, this, "lastProtocolError", ex);
          close();
          throw ex;
        }
      }
    }
  }
  
  @Override
  public void stop() {
    if(shutdown) return;
    super.stop();
    shutdown = true;
  }

  public boolean isRunning() {
    return !shutdown;
  }
  
  public boolean isConnecting() {
    return connecting;
  }
  
  public void closeAllConnections(DcReason reason) {
    for (VirtualConnection c : connections) c.closeQuietly(reason);
    clearConnections();
  }
  
  public void addConnection(VirtualConnection con) {
    connectionsMap.put(con.getID(), con);
    connections = Structs.add(connections, con);
  }
  
  public void removeConnection(VirtualConnection con) {
    connectionsMap.remove(con.getID());
    connections = Structs.remove(connections, con);
  }
  
  public void clearConnections() {
    connectionsMap.clear();
    connections = new VirtualConnection[0];
  }

  public VirtualConnection getConnection(int id) {
    return connectionsMap.get(id);
  }
  
  public VirtualConnection[] getConnections() {
    return connections;
  }

  public int send(VirtualConnection con, Object object, boolean tcp) {
    if(object == null) throw new IllegalArgumentException("object cannot be null.");
    Object p = makeConWrapPacket(con.getID(), object, tcp);
    return tcp ? sendTCP(p) : sendUDP(p);
  }
  
  public void close(VirtualConnection con, DcReason reason) {
    boolean wasConnected = con.isConnected();
    con.setConnected0(false);
    if(!wasConnected) return;
    
    Object p = makeConClosePacket(con.getID(), reason);
    sendTCP(p);
    con.notifyDisconnected0(reason);
  }
  
  public void closeQuietly(VirtualConnection con, DcReason reason) {
    boolean wasConnected = con.isConnected();
    con.setConnected0(false);
    if(wasConnected) con.notifyDisconnected0(reason);
  }

  // end region
  // region listener
  
  @Override
  public void connected(Connection connection) {
    connecting = false;
  }
  
  @Override
  public void disconnected(Connection connection, DcReason reason) {
    // We cannot communicate with the server anymore, so close all virtual connections
    closeAllConnections(reason);
  }
  
  @Override
  public abstract void received(Connection connection, Object object);
  
  @Override
  public void idle(Connection connection) {}
  
  // end region
  // region notifier
  
  protected VirtualConnection conConnected(int conId) {
    VirtualConnection con = getConnection(conId);
    if (con == null) {
      con = new VirtualConnection(this, conId);
      con.addListener(dispatchListener);
      addConnection(con);  
    }
    con.notifyConnected0();
    return con;
  }
  
  protected VirtualConnection conDisconnected(int conId, DcReason reason) {
    VirtualConnection con = getConnection(conId);
    if (con != null) con.notifyDisconnected0(reason);
    return con;
  }
  
  protected VirtualConnection conReceived(int conId, Object object) {
    VirtualConnection con = getConnection(conId);
    if (con != null) con.notifyReceived0(object);
    return con;
  }
  
  protected VirtualConnection conIdle(int conId) {
    VirtualConnection con = getConnection(conId);
    if (con != null) con.notifyIdle0();
    return con;
  }
  
  // end region
  // region packet making
  
  protected abstract Object makeConWrapPacket(int conId, Object object, boolean tcp);
  protected abstract Object makeConClosePacket(int conId, DcReason reason);
  
  // end region
}
