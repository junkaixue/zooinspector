/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.zookeeper.inspector.ssh;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.zookeeper.inspector.logger.LoggerFactory;

/**
 * Loads named ssh tunnel definitions from ~/.zooinspector/sshTunnels.cfg and manages the
 * lifecycle of the ssh processes that implement them.
 *
 * Config file format (java properties), one tunnel per entry:
 *
 * <pre>
 * # &lt;name&gt;.tunnel = &lt;localPort&gt;:&lt;remoteHost&gt;:&lt;remotePort&gt; &lt;sshHost&gt; [extra ssh args...]
 * my-cluster.tunnel = 8080:zk-cluster-1.internal.example.com:2181 bastion
 * </pre>
 */
public class SshTunnelManager
{
  private static final String TUNNEL_KEY_SUFFIX = ".tunnel";
  private static final File tunnelsFile =
      new File(System.getProperty("user.home") + "/.zooinspector/sshTunnels.cfg");

  private static final int CONNECT_POLL_INTERVAL_MS = 200;
  private static final int CONNECT_TIMEOUT_MS = 15000;

  private static final SshTunnelManager instance = new SshTunnelManager();

  private final Map<String, SshTunnelConfig> tunnels =
      new HashMap<String, SshTunnelConfig>();
  private final Map<String, Process> runningTunnels = new HashMap<String, Process>();

  public static SshTunnelManager getInstance()
  {
    return instance;
  }

  private SshTunnelManager()
  {
    loadTunnelsFile();
    Runtime.getRuntime().addShutdownHook(new Thread()
    {
      @Override
      public void run()
      {
        stopAll();
      }
    });
  }

  private void loadTunnelsFile()
  {
    if (!tunnelsFile.exists())
    {
      return;
    }
    Properties props = new Properties();
    try
    {
      FileReader reader = new FileReader(tunnelsFile);
      try
      {
        props.load(reader);
      }
      finally
      {
        reader.close();
      }
    }
    catch (IOException e)
    {
      LoggerFactory.getLogger().error("Error occurred loading ssh tunnels file: "
                                          + tunnelsFile.getAbsolutePath(),
                                      e);
      return;
    }
    for (Object keyObj : props.keySet())
    {
      String key = (String) keyObj;
      if (!key.endsWith(TUNNEL_KEY_SUFFIX))
      {
        continue;
      }
      String name = key.substring(0, key.length() - TUNNEL_KEY_SUFFIX.length());
      try
      {
        tunnels.put(name, SshTunnelConfig.parse(name, props.getProperty(key)));
      }
      catch (RuntimeException e)
      {
        LoggerFactory.getLogger().error("Error occurred parsing ssh tunnel '" + name
                                            + "' from " + tunnelsFile.getAbsolutePath(),
                                        e);
      }
    }
  }

  /**
   * @return the names of all configured tunnels, sorted alphabetically
   */
  public synchronized List<String> getTunnelNames()
  {
    List<String> names = new ArrayList<String>(tunnels.keySet());
    java.util.Collections.sort(names);
    return names;
  }

  public synchronized SshTunnelConfig getTunnel(String name)
  {
    return tunnels.get(name);
  }

  /**
   * Ensures the named tunnel is up and returns the local connect string to use for the
   * zookeeper connection. If the local port is already accepting connections (either a
   * previously started tunnel or one managed outside this application) it is reused.
   *
   * @return the connect string, e.g. "localhost:8080"
   * @throws IOException
   *           - if the tunnel is unknown, ssh fails to start, or the local port never
   *           becomes reachable
   */
  public synchronized String ensureTunnel(String name) throws IOException
  {
    SshTunnelConfig tunnel = tunnels.get(name);
    if (tunnel == null)
    {
      throw new IOException("Unknown ssh tunnel: " + name + " (configured in "
          + tunnelsFile.getAbsolutePath() + ")");
    }

    Process existing = runningTunnels.get(name);
    if (existing != null && isAlive(existing) && isPortOpen(tunnel.getLocalPort()))
    {
      return tunnel.getLocalConnectString();
    }
    // port already served by an externally managed tunnel
    if (isPortOpen(tunnel.getLocalPort()))
    {
      return tunnel.getLocalConnectString();
    }

    List<String> command = new ArrayList<String>();
    command.add("ssh");
    command.add("-N");
    command.add("-o");
    command.add("ExitOnForwardFailure=yes");
    command.add("-o");
    command.add("BatchMode=yes");
    command.add("-L");
    command.add(tunnel.getLocalPort() + ":" + tunnel.getRemoteHost() + ":"
        + tunnel.getRemotePort());
    command.addAll(tunnel.getSshArgs());

    LoggerFactory.getLogger().info("Starting ssh tunnel '" + name + "': " + command);
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.redirectErrorStream(true);
    Process process = builder.start();

    // capture ssh output so failures are diagnosable
    final StringBuilder output = new StringBuilder();
    final BufferedReader outReader =
        new BufferedReader(new InputStreamReader(process.getInputStream()));
    Thread outputDrainer = new Thread()
    {
      @Override
      public void run()
      {
        try
        {
          String line;
          while ((line = outReader.readLine()) != null)
          {
            synchronized (output)
            {
              output.append(line).append("\n");
            }
          }
        }
        catch (IOException e)
        {
          // process exited, nothing to do
        }
      }
    };
    outputDrainer.setDaemon(true);
    outputDrainer.start();

    long deadline = System.currentTimeMillis() + CONNECT_TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline)
    {
      if (!isAlive(process))
      {
        String detail;
        synchronized (output)
        {
          detail = output.toString().trim();
        }
        throw new IOException("ssh tunnel '" + name + "' exited (code "
            + process.exitValue() + ")" + (detail.isEmpty() ? "" : ":\n" + detail));
      }
      if (isPortOpen(tunnel.getLocalPort()))
      {
        runningTunnels.put(name, process);
        return tunnel.getLocalConnectString();
      }
      try
      {
        Thread.sleep(CONNECT_POLL_INTERVAL_MS);
      }
      catch (InterruptedException e)
      {
        Thread.currentThread().interrupt();
        process.destroy();
        throw new IOException("Interrupted while waiting for ssh tunnel '" + name + "'");
      }
    }
    process.destroy();
    throw new IOException("Timed out waiting for ssh tunnel '" + name
        + "' to open localhost:" + tunnel.getLocalPort());
  }

  /**
   * Stops the named tunnel if this application started it.
   */
  public synchronized void stopTunnel(String name)
  {
    Process process = runningTunnels.remove(name);
    if (process != null)
    {
      process.destroy();
    }
  }

  /**
   * Stops all tunnels started by this application.
   */
  public synchronized void stopAll()
  {
    for (Process process : runningTunnels.values())
    {
      process.destroy();
    }
    runningTunnels.clear();
  }

  private static boolean isAlive(Process process)
  {
    try
    {
      process.exitValue();
      return false;
    }
    catch (IllegalThreadStateException e)
    {
      return true;
    }
  }

  private static boolean isPortOpen(int port)
  {
    Socket socket = new Socket();
    try
    {
      socket.connect(new InetSocketAddress("localhost", port), 500);
      return true;
    }
    catch (IOException e)
    {
      return false;
    }
    finally
    {
      try
      {
        socket.close();
      }
      catch (IOException e)
      {
        // ignore
      }
    }
  }
}
