# WireGuard Control Plane

**WireGuard Control Plane** is a central place to run your WireGuard VPN: you define **servers** and **clients**, connect them to **machines you manage**, and let the system push configuration and updates for you. It is aimed at people who operate networks—security, platform, or IT teams—not at reading source code.

> **⚠️ Active Development Warning**
>
> This project is under **active development**. Breaking changes may occur frequently as we iterate on features and improve the architecture. APIs, database schemas, and configuration formats may change without prior notice.
>
> - **Not recommended for production use** at this stage
> - Backup your data regularly
> - Review release notes and migration guides before upgrading
> - Pin to specific versions if stability is critical

---

## What you use it for

- **Run WireGuard on remote hosts** without logging into each box to edit `wg0.conf` by hand. You pick a managed host; the system deploys and updates the server there.
- **Onboard users or devices** as VPN **clients**: each client gets keys, allowed IPs, and a profile you can **download** as a standard WireGuard `.conf` file (or preview it first).
- **Keep Ansible inventory in one place**: register **hosts** and **groups**, generate inventory files for automation, and **check connectivity** to a host before you rely on it.
- **Track automation**: see **Ansible execution jobs** (what ran, whether it succeeded) when the platform deploys WireGuard or related changes.
- **Adjust global settings** (for example how clients reach the VPN) with **versioned** configuration and the option to **roll back** if something goes wrong.

---

## How the pieces fit together

| Area | What it means for you |
|------|------------------------|
| **WireGuard servers** | A VPN hub tied to a specific **managed host**. You can start or stop the service on that host, inspect whether the interface looks **online**, and see **statistics** when available. |
| **WireGuard clients** | Endpoints (people or systems) that connect **through** a server. You can add, edit, enable or disable, and remove clients. If a deployment step fails, you can **retry** once the underlying issue is fixed. |
| **Client profiles** | For each client you can **preview** the config, **download** the `.conf` file for phones or laptops, or **validate** that the generated file looks correct. You can choose whether the tunnel carries **all traffic** through the VPN or only the routes you configured. |
| **Ansible hosts & groups** | Machines the control plane knows about for automation. You organize them into **groups**, build **inventory** files (full or per-group), preview or download them, and run a **health check** (connectivity test) on a host. |
| **SSH private keys** | Stored credentials used so automation can reach your hosts—managed from the same product surface as the rest of the inventory (create, update, or retire keys as your security policy requires). |
| **Global configuration** | Settings that apply across the deployment (for example public **endpoint** information clients use to connect). Changes are **versioned**; you can browse **history** and **rollback** to an earlier version. |

Deployments to remote machines are driven by **Ansible playbooks** bundled with the product (install, deploy config, client-side deploy, stop, firewall-related steps, and so on). You do not need to run those playbooks manually for normal server start/stop and client lifecycle—the control plane triggers them when you take actions in the UI or API.

---

## Access and accounts

- **Human users** sign in with the credentials your organization configures. Self-service **registration** may be available depending on policy. If you use a **local** account and forget your password, a **password reset** flow can send a link by email when mail is configured.
- **Service accounts** exist for **machine-to-machine** access: you create them in the product, receive a **client secret** once, and use them to obtain **tokens** for automated integrations.

Treat downloaded **client configurations** and **private key** material as sensitive—anyone with a client `.conf` can often join the VPN as that identity.

---

## Where to go next

- Day-to-day **Ansible inventory and playbook usage** outside the API (local files, `group_vars`, running playbooks by hand) is described in [`docs/ansible-user-guide.md`](docs/ansible-user-guide.md).
- **Build, deploy, and developer-oriented** notes live elsewhere in the repository (for example `BUILD.md` and `HELP.md`); this README stays at the **usage** level.

If you are new to the product, a practical order is: configure **global** connectivity settings → register **Ansible hosts** (and keys) → create a **WireGuard server** on a host → **start** it → add **clients** and **download** their profiles.
