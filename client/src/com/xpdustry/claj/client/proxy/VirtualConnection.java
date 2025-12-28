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

import java.net.InetSocketAddress;

import arc.net.ArcNetException;
import arc.net.Connection;
import arc.net.DcReason;
import arc.net.EndPoint;
import arc.net.NetListener;
import arc.util.Structs;


/** 
 * A connection that doesn't have a socket and buffer behind. <br>
 * Every writes is done to the proxy, and listeners are triggered by him. <br>
 * And some internal {@link Connection} states are exposed for more control.
 */
public class VirtualConnection extends Connection {
  /** The real client, aka the proxy. */
  public final ProxyClient proxy;
  
  /** {@link arc.net.Connection#id}. */
  protected final int id;
  /** {@link arc.net.Connection#listeners}. */
  protected NetListener[] listeners = {};
  /** {@link arc.net.Connection#name}. */
  protected String name;

  /** 
   * A virtual connection is always connected until we closing it, 
   * so the proxy will notify the server to close the connection in turn,
   * or when the server notifies that the connection has been closed.
   * <p>
   * {@link arc.net.Connection#isConnected}.
   */
  private volatile boolean isConnected = true;
  /** The server will notify if the client is idling. */
  private volatile boolean isIdling = true;

  public VirtualConnection(ProxyClient proxy, int id) {
    this.proxy = proxy;
    this.id = id;
  }

  @Override
  public int sendTCP(Object object) {
    return proxy.send(this, object, true);
  }

  @Override
  public int sendUDP(Object object) {
    return proxy.send(this, object, false);
  }

  @Override
  public void close(DcReason reason) {
    proxy.close(this, reason);
  }
  
  /** 
   * Close the connection without notify the server about that. <br>
   * Common use is when the server itself is saying to close the connection.
   */
  public void closeQuietly(DcReason reason) {
    proxy.closeQuietly(this, reason);
  }

  @Override
  public int getID() { return id; }
  @Override
  public boolean isConnected() { return isConnected; }
  @Override
  public ArcNetException getLastProtocolError() { return proxy.getLastProtocolError(); }
  @Override
  public void updateReturnTripTime() { proxy.updateReturnTripTime(); }
  @Override
  public int getReturnTripTime() { return proxy.getReturnTripTime(); }
  @Override
  public void setKeepAliveTCP(int keepAliveMillis) {} // never used
  @Override
  public void setTimeout(int timeoutMillis) {} // never used
  @Override
  public EndPoint getEndPoint() { return proxy.getEndPoint(); } // never used
  @Override 
  public InetSocketAddress getRemoteAddressTCP() { return isConnected() ? proxy.getRemoteAddressTCP() : null; } 
  @Override
  public InetSocketAddress getRemoteAddressUDP() { return isConnected() ? proxy.getRemoteAddressUDP() : null; }
  @Override
  public void setName(String name) { this.name = name; } // never used
  @Override
  public int getTcpWriteBufferSize() { return proxy.getTcpWriteBufferSize(); } // never used
  /** The server will notify if the client is idling. */
  @Override
  public boolean isIdle() { return isIdling; }
  @Override
  public void setIdleThreshold(float idleThreshold) {} // never used
  @Override
  public String toString() { return name != null ? name : "Connection " + id; }

  /** Only used when sending world data */
  @Override
  public void addListener(NetListener listener) {
    if(listener == null) throw new IllegalArgumentException("listener cannot be null.");
    if (Structs.contains(listeners, listener)) return;
    listeners = Structs.add(listeners, listener);
  }
  
  /** Only used when sending world data */
  @Override
  public void removeListener(NetListener listener) {
    if(listener == null) throw new IllegalArgumentException("listener cannot be null.");
    listeners = Structs.remove(listeners, listener);
  }
  
  public void notifyConnected0() {
    NetListener[] listeners = this.listeners;
    for (NetListener l : listeners) l.connected(this);
  }

  public void notifyDisconnected0(DcReason reason) {
    proxy.removeConnection(this);
    NetListener[] listeners = this.listeners;
    for (NetListener l : listeners) l.disconnected(this, reason);
  }

  public void setIdle() {
    isIdling = true;
  }
      
  public void notifyIdle0() {
    NetListener[] listeners = this.listeners;
    for (NetListener l : listeners) {
      l.idle(this);
      if (!isIdle()) break;
    }
  }
  
  public void notifyReceived0(Object object) {
    NetListener[] listeners = this.listeners;
    for (NetListener l : listeners) l.received(this, object);
  }
   
  public void setConnected0(boolean isConnected) {
    this.isConnected = isConnected;
    if (isConnected && name == null) name = "Connection " + id;
  }
}