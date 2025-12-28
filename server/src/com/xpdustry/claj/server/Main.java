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

import java.util.jar.Manifest;

import arc.Events;
import arc.util.Log;

import com.xpdustry.claj.server.plugin.Plugin;
import com.xpdustry.claj.server.plugin.Plugins;


public class Main {
  public static void main(String[] args) {
    // Set loggers and formatter
    ClajVars.initLogger();
    // Load environment things
    if (!loadEnv(args)) System.exit(1);
    // Init server
    init();
    // Start server
    run();
  }
  
  public static void init() {
    try {
      // Init vars
      ClajVars.init();

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
      ClajVars.relay.bind(ClajVars.port, ClajVars.port);
      // Start command handler
      ClajVars.control.start();
      
      // Loading finished, fire an event
      Events.fire(new ClajEvents.ServerLoadedEvent());
      Log.info("Server loaded and hosted on port @. Type @ for help.", ClajVars.port, "'help'");
      
    } catch (Throwable t) {
      Log.err("Failed to load server", t);
      ClajVars.loop.stop(true);
      System.exit(1);
    }
  }
  
  public static void run() {
    if (ClajVars.relay == null) throw new IllegalStateException("server not initialized");
    
    boolean error = false;
    try { 
      ClajVars.relay.run(); 
    } catch (Throwable t) { 
      Log.err(t); 
      error = true;
    } finally {
      ClajVars.loop.stop(true);
      ClajVars.relay.close();
      ClajConfig.save();
      if (error) {
        Log.err("Server closed with error(s).");
        System.exit(1);
      } else Log.info("Server closed.");
    }
  }
  
  public static boolean loadEnv(String[] args) {
    // Parse server port
    if (args.length == 0) {
      Log.err("Need a port as an argument!");
      return false;
    }
    int port = Integer.parseInt(args[0]);
    if (port < 0 || port > 0xffff) {
      Log.err("Invalid port range");
      return false;
    }
    ClajVars.port = port;
    
    // Get the server version from manifest or command line property
    String version = null;
    try { 
      version = new Manifest(Main.class.getResourceAsStream("/META-INF/MANIFEST.MF"))
                                       .getMainAttributes().getValue("Claj-Version");
    } catch (Exception e) {
      Log.err("Unable to locate manifest properties", e);
      return false;
    }
    // Fallback to java property
    if (version == null) version = System.getProperty("Claj-Version");
    if (version == null) {
      Log.err("The 'Claj-Version' property is missing in the jar manifest.");
      return false;
    }
    
    ClajVars.version = version;
    return true;
  }
}
