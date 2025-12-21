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

import arc.net.ArcNet;
import arc.util.ColorCodes;
import arc.util.Log;
import arc.util.Strings;

import com.xpdustry.claj.server.plugin.Plugin;
import com.xpdustry.claj.server.plugin.Plugins;
import com.xpdustry.claj.server.util.EventLoop;
import com.xpdustry.claj.server.util.NetworkSpeed;


public class Main {
  public static final String[] tags = {"&lc&fb[D]&fr", "&lb&fb[I]&fr", "&ly&fb[W]&fr", "&lr&fb[E]", ""};
  public static final DateTimeFormatter dateformat = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
  public static final String logFormat = "&lk&fb[@]&fr @ @&fr";

  public static void main(String[] args) {
    try {
      // Sets loggers and formatter
      ArcNet.errorHandler = e -> { 
        // Ignore connection reset, closed and broken errors
        if (Strings.getFinalCause(e) instanceof java.net.SocketException) return;
        String m = e.getMessage().toLowerCase();
        if (m.contains("reset") || m.contains("closed") || m.contains("broken pipe")) return;
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

      // Parse server port
      if (args.length == 0) throw new RuntimeException("Need a port as an argument!");
      int port = Integer.parseInt(args[0]);
      if (port < 0 || port > 0xffff) throw new RuntimeException("Invalid port range");
      
      // Get the server version from manifest or command line property
      try { 
        ClajVars.serverVersion = new java.util.jar.Manifest(Main.class.getResourceAsStream("/META-INF/MANIFEST.MF"))
                                         .getMainAttributes().getValue("Claj-Version");
      } catch (Exception e) {
        throw new RuntimeException("Unable to locate manifest properties.", e);
      }
      // Fallback to java property
      if (ClajVars.serverVersion == null) ClajVars.serverVersion = System.getProperty("Claj-Version");
      if (ClajVars.serverVersion == null) throw new RuntimeException("The 'Claj-Version' property is missing in the jar manifest.");
      
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

      // Register commands
      ClajVars.control.registerCommands();
      ClajVars.plugins.eachClass(p -> p.registerCommands(ClajVars.control));
      
      // Check loaded plugins
      if (!ClajVars.plugins.orderedPlugins().isEmpty())
        Log.info("@ plugins loaded.", ClajVars.plugins.orderedPlugins().size);
      int unsupported = ClajVars.plugins.list().count(l -> !l.enabled());
      if (unsupported > 0) {
        Log.err("There were errors loading @ plugin" + (unsupported > 1 ? "s" : "") + ":", unsupported);
        for (Plugins.LoadedPlugin mod : ClajVars.plugins.list().select(l -> !l.enabled()))
            Log.err("- @ &ly(" + mod.state + ")", mod.meta.name);
      }

      // Finish plugins loading
      ClajVars.plugins.eachClass(Plugin::init);
      
      // Bind port
      ClajVars.relay.bind(port, port);
      
      // Start command handler
      ClajVars.control.start();
     
      // Loading finished, fire an event
      ClajEvents.fire(new ClajEvents.ServerLoadedEvent());
      Log.info("Server loaded and hosted on port @. Type @ for help.", port, "'help'");
      
    } catch (Throwable t) {
      Log.err("Failed to load server", t);
      ClajVars.loop.stop(true);
      System.exit(1);
      return;
    }

    // Start the server
    try { ClajVars.relay.run(); } 
    catch (Throwable t) { Log.err(t); } 
    finally {
      ClajVars.loop.stop(true);
      ClajVars.relay.close();
      ClajConfig.save();
      Log.info("Server closed.");
    }
  }
}
