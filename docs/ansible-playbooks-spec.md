# Ansible Playbooks Specification (Draft)

## 1. Purpose and scope

This document describes the **expected playbook set and responsibility boundaries** when **WireGuard nodes may be deployed on Ansible remote targets**. Implementation can live in this repo’s `ansible/` tree (or a separate deployment repo). Division of responsibilities with **wg-control-plane**:


| Component            | Responsibility                                                                                                                                 |
| -------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| **wg-control-plane** | WG logic and data (servers/clients, keys, listen port, `post_up`/`post_down`, etc.) and generating Ansible **inventory** (hosts, groups, SSH vars). |
| **Ansible**          | Install packages on remote OS, write config files, tune kernel/firewall, start/stop `wg-quick`/systemd, verify node state.                         |


This spec does **not** mandate Ansible directory layout or role naming; it only defines **which playbooks are needed, what each does, dependencies, and when to run them**.

**Implementation directory (this repo):** `src/main/resources/ansible/` (including `ansible.cfg`, `requirements.yml`, playbooks, roles, `group_vars`, sample `inventory`).

**Operations and locally generated inventory/vars:** see [ansible-user-guide.md](./ansible-user-guide.md).

---

## 2. Assumptions and constraints

- **Remote OS:** Common server distributions (e.g. Debian/Ubuntu, RHEL family); if only a subset is supported, document it in the implementation README.
- **Inventory:** Produced by the control-plane API and downloaded/synced to the environment where Ansible runs; playbooks assume working `ansible_host`, `ansible_user`, `ansible_ssh_private_key_file`, `ansible_become`, etc. (aligned with existing inventory generation).
- **Config source:** The following playbooks must support at least one strategy (choose one or support both in implementation):
  - **A.** Injected via playbook variables/templates (manually kept in sync with external systems); or  
  - **B.** Deployment step fetches from the control-plane API then writes remotely (needs auth and network reachability design).
- **Local WG:** The control plane may today write `/path/to/wg/*.conf` on the **application host** and run `wg-quick`; **remote** nodes are **not** operated by this program directly—Ansible performs equivalent steps.

---

## 3. Playbook inventory

### 3.1 `bootstrap.yml` (or `site-bootstrap.yml`)


| Field            | Description                                                                                                                                 |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| **Purpose**      | First-time host onboarding: ensure Ansible can run reliably (Python, sudo, package indexes, etc.).                                          |
| **When to run**  | Host newly added to inventory, or after a major OS upgrade.                                                                                  |
| **Main tasks**   | Install/verify Python 3 and modules Ansible needs; verify `become`; optional: timezone, base packages.                                      |
| **Outcome**      | Host can run subsequent playbooks without interaction.                                                                                       |
| **Dependencies** | Inventory connectivity is correct.                                                                                                           |


---

### 3.2 `wireguard-install.yml`


| Field            | Description                                                                                                                                 |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| **Purpose**      | Install WireGuard userspace tools and distro dependencies on the target (kernel module/DKMS, etc., OS-dependent).                           |
| **When to run**  | First deploy of a new WG node, or when an upgrade policy requires it.                                                                      |
| **Main tasks**   | Install `wireguard-tools` (and related packages); optional: pin package versions.                                                            |
| **Outcome**      | Host has `wg`, `wg-quick`, etc.                                                                                                              |
| **Dependencies** | `bootstrap.yml` completed (or equivalent prerequisites).                                                                                     |


---

### 3.3 `wireguard-sysctl.yml` (may be merged into `wireguard-install.yml`; still described separately at spec level)


| Field            | Description                                                                                                                                 |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| **Purpose**      | Set kernel parameters needed when the node routes/NATs (e.g. IPv4/IPv6 forwarding).                                                          |
| **When to run**  | Same run or after `wireguard-install.yml`; when settings change.                                                                             |
| **Main tasks**   | Persist `sysctl` (e.g. `net.ipv4.ip_forward=1`); narrow scope if the product is IPv4-only.                                                    |
| **Outcome**      | Sysctl settings survive reboot (use distro-appropriate persistence).                                                                         |
| **Dependencies** | OS privileges and `become`.                                                                                                                  |


---

### 3.4 `wireguard-firewall.yml`


| Field            | Description                                                                                                                                 |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| **Purpose**      | Open the WG **UDP listen port** and align **iptables/nftables** with `post_up`/`post_down` (when NAT/FORWARD lives in the firewall layer, not only interface scripts). |
| **When to run**  | `listen_port` or firewall policy changes; new node deploy.                                                                                 |
| **Main tasks**   | Persist firewall rules; align with the server’s `listen_port` in the database.                                                               |
| **Outcome**      | WG port reachable from outside; internal forward/NAT matches design.                                                                          |
| **Dependencies** | Chosen iptables vs nftables and split of responsibility with `post_up` (avoid duplicate or conflicting rules).                                |


---

### 3.5 `wireguard-deploy-config.yml` (core; may be named `deploy-wg-server.yml`)


| Field            | Description                                                                                                                                 |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| **Purpose**      | Write **one or more** WG interface configs to the agreed remote path (e.g. `/etc/wireguard/<iface>.conf`) and **reload** so config applies.     |
| **When to run**  | After control-plane changes to server/peer config, via CI/CD or schedule; or manual `--limit`.                                                |
| **Main tasks**   | Write config files; **safe reload** via `wg-quick@<iface>.service` or equivalent (long-outage avoidance is implementation-defined); optional: backup old files. |
| **Outcome**      | Remote WG interfaces match the control plane and expected up/down state.                                                                     |
| **Dependencies** | `wireguard-install.yml`, `wireguard-sysctl.yml`, and optionally `wireguard-firewall.yml`; config source strategy (section 2).                 |


---

### 3.6 `wireguard-stop.yml` (or `wireguard-drain.yml`)


| Field            | Description                                                                                                                                 |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| **Purpose**      | **Gracefully** take an interface or node offline for maintenance/migration (`wg-quick down` or stop unit).                                   |
| **When to run**  | Maintenance window, before node decommission.                                                                                                |
| **Main tasks**   | Stop the given interface; optional: keep conf, only stop.                                                                                    |
| **Outcome**      | That interface no longer forwards traffic.                                                                                                  |
| **Dependencies** | Same interface name and unit naming as `wireguard-deploy-config.yml`.                                                                         |


---

### 3.7 `wireguard-verify.yml`


| Field            | Description                                                                                                                                 |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| **Purpose**      | **Automated verification** after release or change: interface exists, `wg show`, UDP listen, etc.                                            |
| **When to run**  | Chained after successful `wireguard-deploy-config.yml`, or standalone health job.                                                            |
| **Main tasks**   | Assert command output vs expectations; optional: UDP check from another host (extra inventory or delegate).                                  |
| **Outcome**      | Clear pass/fail for CI or alerting.                                                                                                          |
| **Dependencies** | Node deployed and firewall allows admin access path.                                                                                         |


---

### 3.8 `site.yml` (umbrella, optional)


| Field            | Description                                                                                                                                 |
| ---------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| **Purpose**      | Order via **import_play** or **roles**: `bootstrap` → `install` → `sysctl` → `firewall` → `deploy` → `verify`.                               |
| **When to run**  | One-shot greenfield full WG node; brownfield changes often run subsets only.                                                               |
| **Main tasks**   | Orchestration only—no duplicated business logic.                                                                                             |
| **Outcome**      | Same as individual playbooks, single operator entrypoint.                                                                                  |
| **Dependencies** | Variable contracts defined for each sub-playbook.                                                                                            |


---

## 4. Alignment with control-plane inventory (non-functional)

- **Groups:** Prefer Ansible group names that map to control-plane **inventory groups** (e.g. `wg_servers`) for `ansible-playbook -l wg_servers`.
- **Per-host variables:** Interface name, `listen_port`, config paths, etc., should come from `group_vars`/`host_vars` or API-injected data, aligned with `WireGuardServer` fields, to avoid drift from the DB.
- **SSH keys:** The inventory generator assumes private keys at `/tmp/keys/<id>.pem` on the machine running Ansible; ensure that path and permissions match deployment before running playbooks.

---

## 5. Explicitly out of scope (future specs)

- **SSH hardening, fail2ban, full-OS hardening:** Can be a separate `hardening.yml`, decoupled from WG lifecycle.
- **Deploying the control-plane app itself** (Docker/K8s): Keep existing `deployment/` flows; not covered here.
- **Remote “callback to control-plane” agent:** If Ansible is the only ops surface, may be unnecessary; if API-driven remote start/stop is required, define a separate **agent spec**.

---

## 6. Revision history


| Version | Date       | Notes                                                                 |
| ------- | ---------- | --------------------------------------------------------------------- |
| 0.1     | 2026-04-02 | Initial draft: playbook list and responsibilities for remote WG nodes. |

