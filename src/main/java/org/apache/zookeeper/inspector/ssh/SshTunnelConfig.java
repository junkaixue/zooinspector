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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A single named ssh local port forward, equivalent to:
 *
 * <pre>
 * ssh -N -L &lt;localPort&gt;:&lt;remoteHost&gt;:&lt;remotePort&gt; &lt;sshArgs...&gt;
 * </pre>
 */
public class SshTunnelConfig
{
  private final String name;
  private final int localPort;
  private final String remoteHost;
  private final int remotePort;
  private final List<String> sshArgs;

  public SshTunnelConfig(String name,
                         int localPort,
                         String remoteHost,
                         int remotePort,
                         List<String> sshArgs)
  {
    this.name = name;
    this.localPort = localPort;
    this.remoteHost = remoteHost;
    this.remotePort = remotePort;
    this.sshArgs = Collections.unmodifiableList(new ArrayList<String>(sshArgs));
  }

  /**
   * Parses a tunnel definition in the same format as the -L argument of ssh
   * followed by the ssh destination (and any extra ssh options), e.g.
   * "8080:zk-cluster-1.internal.example.com:2181 bastion"
   *
   * @param name
   *          - the tunnel name (properties key without the ".tunnel" suffix)
   * @param value
   *          - "&lt;localPort&gt;:&lt;remoteHost&gt;:&lt;remotePort&gt; &lt;sshHost&gt; [extra ssh args...]"
   * @throws IllegalArgumentException
   *           - if the value cannot be parsed
   */
  public static SshTunnelConfig parse(String name, String value)
  {
    String[] tokens = value.trim().split("\\s+");
    if (tokens.length < 2)
    {
      throw new IllegalArgumentException("Tunnel '" + name
          + "' must be of the form <localPort>:<remoteHost>:<remotePort> <sshHost>: " + value);
    }
    String[] forward = tokens[0].split(":");
    if (forward.length != 3)
    {
      throw new IllegalArgumentException("Tunnel '" + name
          + "' has an invalid forward spec (expected <localPort>:<remoteHost>:<remotePort>): "
          + tokens[0]);
    }
    int localPort = Integer.parseInt(forward[0]);
    int remotePort = Integer.parseInt(forward[2]);
    List<String> sshArgs = Arrays.asList(tokens).subList(1, tokens.length);
    return new SshTunnelConfig(name, localPort, forward[1], remotePort, sshArgs);
  }

  public String getName()
  {
    return name;
  }

  public int getLocalPort()
  {
    return localPort;
  }

  public String getRemoteHost()
  {
    return remoteHost;
  }

  public int getRemotePort()
  {
    return remotePort;
  }

  public List<String> getSshArgs()
  {
    return sshArgs;
  }

  /**
   * @return the connect string clients should use to reach zookeeper through this tunnel
   */
  public String getLocalConnectString()
  {
    return "localhost:" + localPort;
  }

  @Override
  public String toString()
  {
    return name + " (" + localPort + ":" + remoteHost + ":" + remotePort + ")";
  }
}
