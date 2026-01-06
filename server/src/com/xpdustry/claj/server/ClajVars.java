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

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import arc.Core;
import arc.Files.FileType;
import arc.files.Fi;
import arc.net.ArcNet;
import arc.util.ColorCodes;
import arc.util.Log;
import arc.util.Strings;

import com.xpdustry.claj.common.ClajPackets;
import com.xpdustry.claj.server.plugin.Plugins;
import com.xpdustry.claj.server.util.*;


public class ClajVars {
  public static ClajRelay relay;
  public static ClajControl control;
  
  public static int port = 7000;
  public static String version;
  
  public static Fi workingDirectory = new Fi("", FileType.local);
  public static Fi pluginsDirectory = workingDirectory.child("plugins");
  
  public static Plugins plugins;
  public static Autosaver autosave;
  public static EventLoop loop;
  public static NetworkSpeed networkSpeed;
  
  public static final String[] tags = {"&lc&fb[D]&fr", "&lb&fb[I]&fr", "&ly&fb[W]&fr", "&lr&fb[E]", ""};
  public static DateTimeFormatter dateformat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
  public static String logFormat = "&lk&fb[@]&fr @ @&fr";
  public static String textFormat = "&fb&lb@&fr";

  public static void init() {
    // Init event loop
    loop = new EventLoop();
    loop.start();
    
    // Init auto saver
    autosave = new Autosaver();
    Core.app.addListener(autosave);
    
    // Load settings and register packets
    ClajConfig.load();
    Log.level = ClajConfig.debug ? Log.LogLevel.debug : Log.LogLevel.info; // set log level
    ClajPackets.init();
    
    // Init server
    networkSpeed = new NetworkSpeed(8);
    relay = new ClajRelay(networkSpeed);      
    control = new ClajControl();

    // Load plugins
    pluginsDirectory.mkdirs();
    plugins = new Plugins();
    plugins.load();
  }  
  
  public static void initLogger() {
    ArcNet.errorHandler = e -> { 
      // Ignore connection reset, closed and broken errors
      String m = Strings.getFinalMessage(e);
      if (m != null) {
        m = m.toLowerCase();
        if (m.contains("reset") || m.contains("closed") || m.contains("broken pipe")) return;
      }
      
      Log.err(e); 
    };
    
    Log.LogHandler log = (level, text) -> {
      //err has red text instead of reset.
      if(level == Log.LogLevel.err) text = text.replace(ColorCodes.reset, ColorCodes.lightRed + ColorCodes.bold);

      text = Log.format(Strings.format(logFormat, dateformat.format(LocalDateTime.now()), tags[level.ordinal()], text));
      System.out.println(text);  
    };
    
    Log.logger = (level, text) -> {
      int i = 0, nl = text.indexOf('\n');
      while (nl >= 0) {
        log.log(level, text.substring(i, nl));
        i = nl + 1;
        nl = text.indexOf('\n', i);
      } 
      log.log(level, i == 0 ? text : text.substring(i));
    };
    
    Log.formatter = (text, useColors, arg) -> {
      text = Strings.format(text.replace("@", textFormat), arg);
      return useColors ? Log.addColors(text) : Log.removeColors(text);
    };  
  }
}
