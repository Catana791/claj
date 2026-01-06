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

import arc.ApplicationListener;
import arc.Core;
import arc.func.Cons;

import com.xpdustry.claj.common.ClajPackets;
import com.xpdustry.claj.common.packets.ConnectionPacketWrapPacket;
import com.xpdustry.claj.common.status.*;


public class Claj {
  protected static Claj INSTANCE;
  
  public static boolean initialized() {
    return INSTANCE != null;
  }
  
  public static Claj get() {
    if (!initialized()) throw new IllegalStateException("Claj#init() must be called before");
    return INSTANCE;
  }
  
  /** Initializes the global CLaJ manager using {@code 1} proxy and {@code 4} pingers. */
  public static Claj init(ClajProvider provider) { 
    return init(provider, 1, 4); 
  }
  
  /** 
   * Initializes the global CLaJ manager, and the {@link ConnectionPacketWrapPacket} serializer.
   * @param provider the implementation specific things
   * @param proxies number of pooled proxies
   * @param pingers number of pooled pingers
   */
  public static Claj init(ClajProvider provider, int proxies, int pingers) {
    if (initialized()) throw new IllegalStateException("Claj already initialized");
    ClajPackets.init(); // Register packets first
    ConnectionPacketWrapPacket.serializer = provider.getPacketWrapperSerializer();
    INSTANCE = new Claj(provider, new ClajProxyManager(provider, proxies), 
                        new ClajPingerManager(provider, pingers));
    return INSTANCE;
  }
  
  
  public final ClajProvider provider;
  public final ClajProxyManager proxies;
  public final ClajPingerManager pingers;
  
  public Claj(ClajProvider provider, ClajProxyManager proxies, ClajPingerManager pingers) {
    this.provider = provider;
    this.proxies = proxies;
    this.pingers = pingers;
    Core.app.addListener(new ApplicationListener() { public void dispose() { dispose(); } }); 
  }

  public boolean hasOpenRoom() {
    return proxies.hasOpenRoom();
  }
  
  public void closeRooms() {
    proxies.closeAllRooms();
  }
  
  /** Stop the room and the joiner. */
  public void dispose() {
    proxies.dispose();
    pingers.dispose();
  }

  public void createRoom(String ip, int port, Cons<ClajLink> created, Cons<CloseReason> closed, 
                         Cons<Throwable> failed) {
    proxies.createRoom(ip, port, created, closed, failed);
  }

  public void joinRoom(ClajLink link, Runnable success, Cons<RejectReason> reject, Cons<Exception> failed) {
    joinRoom(link, ClajPinger.NO_PASSWORD, success, reject, failed);
  }
  
  public void joinRoom(ClajLink link, short password, Runnable success, Cons<RejectReason> reject, 
                       Cons<Exception> failed) {
    pingers.joinRoom(link, password, success, reject, failed);
  }

  public void pingHost(String ip, int port, Cons<Integer> success, Cons<Exception> failed) {
    pingers.pingHost(ip, port, success, failed);
  }
  
  public void serverRooms(String ip, int port, Cons<ClajRoom> room, Cons<GameState> state, Runnable done, 
                          Cons<Exception> failed) {
    pingers.serverRooms(ip, port, room, state, done, failed);
  }
}
