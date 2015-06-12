/*
 * SonarQube Eclipse
 * Copyright (C) 2010-2015 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.ide.eclipse.core.internal.servers;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.security.storage.EncodingUtils;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;
import org.sonar.ide.eclipse.common.servers.ISonarServer;
import org.sonar.ide.eclipse.core.internal.SonarCorePlugin;
import org.sonar.ide.eclipse.wsclient.WSClientFactory;

public class ServersManager implements ISonarServersManager {
  static final String PREF_SERVERS = "servers";

  private Map<String, ISonarServer> servers = new HashMap<>();

  private boolean initialized = false;

  public synchronized void init() {
    if (this.initialized) {
      return;
    }
    IEclipsePreferences rootNode = InstanceScope.INSTANCE.getNode(SonarCorePlugin.PLUGIN_ID);
    try {
      rootNode.sync();
      if (rootNode.nodeExists(PREF_SERVERS)) {
        Preferences serversNode = rootNode.node(PREF_SERVERS);
        for (String id : serversNode.childrenNames()) {
          Preferences serverNode = serversNode.node(id);
          String urlStr = serverNode.get("url", null);
          if (urlStr == null) {
            // Migration
            urlStr = EncodingUtils.decodeSlashes(id);
            try {
              URL url = new URL(urlStr);
              id = url.getHost();
            } catch (MalformedURLException e1) {
              id = urlStr;
            }
          }
          boolean auth = serverNode.getBoolean("auth", false);
          SonarServer sonarServer = new SonarServer(id, urlStr, auth);
          String serverVersion = getServerVersion(sonarServer);
          sonarServer.setVersion(serverVersion);
          servers.put(id, sonarServer);
        }
      } else {
        // Defaults
        servers.put(getDefault().getId(), getDefault());
      }
    } catch (BackingStoreException e) {
      SonarCorePlugin.getDefault().error(e.getMessage(), e);
    }
    this.initialized = true;
  }

  @Override
  public Collection<ISonarServer> getServers() {
    init();
    return servers.values();
  }

  @Override
  public void addServer(ISonarServer server) {
    IEclipsePreferences rootNode = InstanceScope.INSTANCE.getNode(SonarCorePlugin.PLUGIN_ID);
    try {
      Preferences serversNode = rootNode.node(PREF_SERVERS);
      serversNode.put("initialized", "true");
      Preferences node = serversNode.node(server.getId());
      node.put("url", server.getUrl());
      node.putBoolean("auth", server.hasCredentials());
      serversNode.flush();
    } catch (BackingStoreException e) {
      SonarCorePlugin.getDefault().error(e.getMessage(), e);
    }
    String serverVersion = getServerVersion(server);
    ((SonarServer) server).setVersion(serverVersion);
    servers.put(server.getId(), server);
  }

  /**
   * For tests.
   */
  public void clean() {
    IEclipsePreferences rootNode = InstanceScope.INSTANCE.getNode(SonarCorePlugin.PLUGIN_ID);
    try {
      rootNode.node(PREF_SERVERS).removeNode();
      rootNode.node(PREF_SERVERS).put("initialized", "true");
      rootNode.flush();
    } catch (BackingStoreException e) {
      SonarCorePlugin.getDefault().error(e.getMessage(), e);
    }
    servers.clear();
  }

  @Override
  public void removeServer(ISonarServer server) {
    String encodedUrl = EncodingUtils.encodeSlashes(server.getUrl());
    IEclipsePreferences rootNode = InstanceScope.INSTANCE.getNode(SonarCorePlugin.PLUGIN_ID);
    try {
      Preferences serversNode = rootNode.node(PREF_SERVERS);
      serversNode.node(server.getId()).removeNode();
      serversNode.node(encodedUrl).removeNode();
      serversNode.flush();
    } catch (BackingStoreException e) {
      SonarCorePlugin.getDefault().error(e.getMessage(), e);
    }
    servers.remove(server.getId());
  }

  @CheckForNull
  @Override
  public ISonarServer getServer(String idOrUrl) {
    if (servers.containsKey(idOrUrl)) {
      return servers.get(idOrUrl);
    }
    for (ISonarServer server : getServers()) {
      if (server.getUrl().equals(idOrUrl)) {
        return server;
      }
    }
    return null;
  }

  @Override
  public ISonarServer getDefault() {
    return new SonarServer("default", "http://localhost:9000");
  }

  @Override
  public ISonarServer create(String id, String location, String username, String password) {
    return new SonarServer(id, location, username, password);
  }

  @CheckForNull
  private String getServerVersion(ISonarServer server) {
    try {
      return WSClientFactory.getSonarClient(server).getServerVersion();
    } catch (Exception e) {
      SonarCorePlugin.getDefault().error("Unable to get version of server " + server.getUrl() + ": " + e.getMessage() + "\n");
    }
    return null;
  }

}
