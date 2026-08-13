# ZooInspector User Guide

ZooInspector is a Swing GUI for browsing and editing the contents of a ZooKeeper
ensemble: the znode tree, each node's data, metadata, and ACLs.

## Contents

- [Building and running](#building-and-running)
- [Connecting to ZooKeeper](#connecting-to-zookeeper)
- [Connecting through an SSH tunnel](#connecting-through-an-ssh-tunnel)
- [Browsing the znode tree](#browsing-the-znode-tree)
- [Viewing and editing node data](#viewing-and-editing-node-data)
- [Node metadata and ACLs](#node-metadata-and-acls)
- [Configuration files](#configuration-files)
- [Troubleshooting](#troubleshooting)

## Building and running

Requirements: JDK and Maven.

```sh
git clone https://github.com/zzhang5/zooinspector.git
cd zooinspector/
mvn clean package

chmod +x target/zooinspector-pkg/bin/zooinspector.sh
target/zooinspector-pkg/bin/zooinspector.sh
```

## Connecting to ZooKeeper

Click the **connect** button (leftmost toolbar icon) to open the connection dialog:

| Field | Meaning | Default |
|---|---|---|
| Connect String | ZooKeeper host:port list, e.g. `host1:2181,host2:2181` | `localhost:2181` |
| Session Timeout | ZooKeeper session timeout in milliseconds | `30000` |
| Data Encryption Manager | Class used to encode/decode node data | `BasicDataEncryptionManager` |
| SSH Tunnel | Optional tunnel to connect through (only shown if tunnels are configured) | `None` |

The Connect String field is a dropdown that remembers the last 10 successfully
connected addresses, so reconnecting to a recent cluster is a two-click operation.

**Load Default** fills the dialog from your saved defaults; **Set As Default** saves
the current values to `~/.zooinspector/defaultConnectionSettings.cfg` so they load
automatically next time.

Use the **disconnect** button (second toolbar icon) to close the session, and
**refresh** (third icon) to re-read the tree from the server.

## Connecting through an SSH tunnel

For clusters only reachable through a bastion host, ZooInspector can start and manage
`ssh -L` port forwards for you.

### 1. Define tunnels

Create `~/.zooinspector/sshTunnels.cfg` (Java properties format). Each entry is a
key ending in `.tunnel`; the part before `.tunnel` is the name shown in the dropdown.
The value uses the same syntax as ssh's `-L` flag, followed by the ssh destination and
any extra ssh arguments:

```properties
# <name>.tunnel = <localPort>:<remoteHost>:<remotePort> <sshHost> [extra ssh args...]
cluster-one.tunnel = 8080:zk-cluster-1.internal.example.com:2181 bastion
cluster-two.tunnel = 8081:zk-cluster-2.internal.example.com:2181 bastion
```

Give each tunnel a distinct local port so they can run at the same time.

The file is read once at startup — restart ZooInspector after editing it.

### 2. Connect

Open the connection dialog and pick a tunnel from the **SSH Tunnel** dropdown. The
Connect String is filled in automatically (`localhost:<localPort>`) and made
read-only. On connect, ZooInspector:

1. Checks whether the local port is already open — if so (e.g. a tunnel you started
   yourself), it is reused as-is.
2. Otherwise spawns `ssh -N -o ExitOnForwardFailure=yes -o BatchMode=yes -L ...`,
   using your normal `~/.ssh/config`, agent, and keys.
3. Waits up to 15 seconds for the local port to come up, then connects through it.

Tunnels started by ZooInspector are closed on disconnect and on application exit.
Externally started tunnels are left alone.

**Authentication must be non-interactive** (ssh runs with `BatchMode=yes`). If your
bastion requires MFA or a password, open a master session first (e.g. `ssh bastion`)
and let ssh connection sharing reuse it.

## Browsing the znode tree

The left pane shows the znode tree, with children sorted by name. Reads are issued
asynchronously, so large trees load quickly. Selecting a node shows its details in
the right pane; selecting multiple nodes shows each in turn.

Toolbar buttons:

- **Add node** — creates a child under the selected node (prompts for a name).
- **Delete node** — deletes the selected node(s) after confirmation.
- **Refresh** — re-reads the tree from the server.

## Viewing and editing node data

The **Node Data** tab shows the selected node's data as text. If the stored bytes are
compressed, they are uncompressed automatically for display.

The tab is read-only by default. Its toolbar has three buttons:

- **Edit** — toggles edit mode on the text area (only while connected).
- **Save** — writes the current text back to the node. Enabled only in edit mode,
  and asks for confirmation first — saving cannot be reverted.
- **Find** — opens a search dialog (also `Ctrl+F`, or `Cmd+F` on macOS). Matches are
  highlighted in the text; press `Esc` to clear the highlights.

## Node metadata and ACLs

- **Node Metadata** tab: the node's Stat fields (czxid, mzxid, version, ephemeral
  owner, etc.). Timestamps and session ids are rendered in human-readable form
  instead of raw numbers.
- **Node ACLs** tab: the node's access control list — scheme, id, and permissions.

The set of tabs is configurable via the **node viewers** toolbar button: add, remove,
or reorder viewer classes, and save your preferred set as the default
(`~/.zooinspector/defaultNodeVeiwers.cfg` — note the historical typo in the filename).

## Configuration files

All user configuration lives in `~/.zooinspector/`:

| File | Purpose |
|---|---|
| `defaultConnectionSettings.cfg` | Default connect string, timeout, and encryption manager for the connection dialog |
| `defaultNodeVeiwers.cfg` | Node viewer tabs to show, one class name per line (filename typo is intentional — it matches the code) |
| `sshTunnels.cfg` | Named SSH tunnel definitions (see [above](#connecting-through-an-ssh-tunnel)) |

## Troubleshooting

**The SSH Tunnel dropdown doesn't appear.** The dropdown is only shown when at least
one tunnel is defined. Check that `~/.zooinspector/sshTunnels.cfg` uses the properties
format (`name.tunnel = ...`) — raw ssh command lines are ignored — and restart the
app, since the file is read once at startup. Parse errors are written to the log.

**Connecting through a tunnel fails with "ssh tunnel exited".** The error dialog
includes ssh's output. Common causes: the local port is in use by something else,
the bastion requires interactive authentication (see the MFA note above), or the
remote host/port is wrong.

**Connecting through a tunnel times out.** The local port never became reachable
within 15 seconds. Verify the forward spec by running the equivalent command by hand:
`ssh -N -L <localPort>:<remoteHost>:<remotePort> <sshHost>`.

**Save button is greyed out.** Click **Edit** first to enable edit mode; editing
requires an active connection.
