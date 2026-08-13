zooinspector
============

An improved zookeeper inspector

See the [User Guide](docs/user-guide/README.md) for full usage documentation.

- Use async operations to speed up read
- Znodes sorted by names in tree viewer
- Timestamp and session id in more readable format in node metadata viewer
- Add a dropdown menu to show the last 10 successfully connected zookeeper addresses
- Support text search in node data viewer
- Support read-only mode for node data viewer
- Support connecting through ssh tunnels defined in a config file

SSH Tunnels
-----------

For zookeeper clusters that are only reachable through a bastion host, define named
tunnels in `~/.zooinspector/sshTunnels.cfg` (java properties format). Each entry uses
the same syntax as the ssh `-L` flag followed by the ssh destination:

    # <name>.tunnel = <localPort>:<remoteHost>:<remotePort> <sshHost> [extra ssh args...]
    my-cluster.tunnel = 8080:zk-cluster-1.internal.example.com:2181 bastion

The connection dialog then shows an "SSH Tunnel" dropdown. Selecting a tunnel fills in
the connect string (`localhost:<localPort>`) automatically — no manual input needed.
On connect, zooinspector spawns `ssh -N -L ...` (using your normal `~/.ssh/config`,
agent and keys), waits for the local port to come up, and connects through it. If the
local port is already open (e.g. a tunnel you started yourself), it is reused as-is.
Tunnels started by zooinspector are closed on disconnect and on exit.

Note: ssh runs with `BatchMode=yes`, so authentication must be non-interactive. If your
bastion requires MFA, open a master session first (e.g. `ssh bastion`) and let ssh
connection sharing reuse it.

Build
- $git clone https://github.com/zzhang5/zooinspector.git
- $cd zooinspector/
- $mvn clean package

Run
- $chmod +x target/zooinspector-pkg/bin/zooinspector.sh
- $target/zooinspector-pkg/bin/zooinspector.sh
