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

import arc.Files.FileType;
import arc.files.Fi;
import arc.net.ArcNet;
import arc.util.ColorCodes;
import arc.util.Log;
import arc.util.Strings;

import com.xpdustry.claj.server.plugin.Plugins;
import com.xpdustry.claj.server.util.EventLoop;
import com.xpdustry.claj.server.util.NetworkSpeed;


public class ClajVars {
  public static ClajRelay relay;
  public static ClajControl control;
  
  public static int port = 7000;
  public static String version;
  
  public static Fi workingDirectory = new Fi("", FileType.local);
  public static Fi pluginsDirectory = workingDirectory.child("plugins");
  
  public static Plugins plugins;
  public static EventLoop loop;
  public static NetworkSpeed networkSpeed;
  
  public static final String[] tags = {"&lc&fb[D]&fr", "&lb&fb[I]&fr", "&ly&fb[W]&fr", "&lr&fb[E]", ""};
  public static final DateTimeFormatter dateformat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
  public static final String logFormat = "&lk&fb[@]&fr @ @&fr";

  public static void init() {
    // Init event loop
    ClajVars.loop = new EventLoop();
    ClajVars.loop.start();
    
    // Load settings and init server
    ClajConfig.load();
    Log.level = ClajConfig.debug ? Log.LogLevel.debug : Log.LogLevel.info; // set log level
    ClajVars.networkSpeed = new NetworkSpeed(8);
    ClajVars.relay = new ClajRelay(ClajVars.networkSpeed);      
    ClajVars.control = new ClajControl();

    // Load plugins
    ClajVars.plugins = new Plugins();
    ClajVars.pluginsDirectory.mkdirs();
    ClajVars.plugins.load();
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
    
    Log.logger = (level, text) -> {
      for (String line : text.split("\n")) {
        //err has red text instead of reset.
        if(level == Log.LogLevel.err) line = line.replace(ColorCodes.reset, ColorCodes.lightRed + ColorCodes.bold);
  
        line = Log.format(Strings.format(logFormat, dateformat.format(LocalDateTime.now()), tags[level.ordinal()], line));
        System.out.println(line);          
      }
    };
    
    Log.formatter = (text, useColors, arg) -> {
      text = Strings.format(text.replace("@", "&fb&lb@&fr"), arg);
      return useColors ? Log.addColors(text) : Log.removeColors(text);
    };  
  }
}
