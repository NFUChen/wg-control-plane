# Ansible user guide (wg-control-plane)

This document explains how **inventory**, **group_vars**, and **host_vars** work in Ansible, and how to use playbooks in this project with **locally generated** files instead of relying only on committed “example” paths in the repo.

Ansible content lives under `src/main/resources/ansible/` (referred to below as **`ansible/`**).

---

## 1. What are the repo’s inventory / group_vars?

| Path | Purpose |
|------|---------|
| `ansible/inventory/static-example.ini` | **Example** showing what `[wg_servers]` looks like; optional to keep; **do not** treat it as the single source of truth for your environment. |
| `ansible/group_vars/all.yml` | **Example default:** defines `wg_target_hosts` (group name string for playbook `hosts:`). |
| `ansible/group_vars/wg_servers.yml` | **Example default:** `wg_interface_name`, `wg_listen_port`, `wg_config_content`, etc. |
| `ansible/ansible.cfg` | Default `inventory = inventory/static-example.ini`; **only applies when you omit `-i`**. |

**Takeaway:** these are **committable defaults/examples**. Real environments usually **generate** inventory locally (e.g. from the wg-control-plane API) and **generate or hand-write** host/group variables; see below.

---

## 2. How does a playbook choose which hosts to target?

Project playbooks use:

```yaml
hosts: "{{ wg_target_hosts }}"
```

The default in `group_vars/all.yml` is:

```yaml
wg_target_hosts: wg_servers
```

So **inventory must contain a group whose name matches `wg_target_hosts`** (default **`[wg_servers]`**).  
If your generated inventory uses e.g. `production_wg`, either:

- Set `wg_target_hosts: production_wg` in variables (see section 4), or  
- Rename the inventory group to `wg_servers`.

---

## 3. How does Ansible resolve inventory and variables?

### 3.1 Inventory (host list)

- **Single file:** `hosts.ini`, `hosts.yaml`, etc.  
- **Directory as inventory:** Multiple `*.ini` / `*.yml` files plus adjacent `group_vars/` and `host_vars/` (below).  
- **Selection:** **`ansible-playbook -i <path>`** overrides the default inventory from `ansible.cfg`.

So: **point `-i` at your locally generated files**; they do not need to be in git.

### 3.2 group_vars / host_vars

Ansible loads variables in the **inventory context** (can coexist with `group_vars` next to the playbook directory; precedence depends on Ansible version and settings). In practice, **keep environment-specific vars under an inventory directory you control**.

Typical layout (**directory inventory**):

```text
my-ansible-env/
  inventory/
    hosts.ini          # contains [wg_servers] ...
    group_vars/
      wg_servers.yml   # vars for wg_servers only
      all.yml          # optional: all group
    host_vars/
      node-a.yml       # per-host overrides
```

Run:

```bash
ansible-playbook -i my-ansible-env/inventory site.yml
```

This **does not depend** on the repo’s `ansible/group_vars/wg_servers.yml`.

### 3.3 Variable precedence (practical subset)

Roughly low to high:

1. Role `defaults` (`roles/.../defaults/main.yml`)  
2. `group_vars` / `host_vars`  
3. **`ansible-playbook -e` / `-e @file` (extra vars — very high)**

So: **a locally generated `extra-vars.yml` can override** the repo’s example `group_vars`.

---

## 4. Locally generated files: recommended patterns

### 4.1 Option A: generate inventory only, keep repo group_vars (not for production)

Good for quick local trials.

```bash
cd ansible
ansible-playbook -i /path/to/generated/inventory.ini wireguard-install.yml
```

Note: if you still use the repo’s `group_vars`, you will pick up example values like `wg_config_content`. For production use option B or C.

### 4.2 Option B: inventory + separate vars file (`-e @file`)

1. Generate `inventory.ini` (with `[wg_servers]` and hosts).  
2. Generate `wg-extra-vars.yml` (e.g. from a template or API):

```yaml
---
wg_target_hosts: wg_servers
wg_interface_name: wg0
wg_listen_port: 51820
wg_config_source: inline
wg_config_content: |
  [Interface]
  PrivateKey = ...
  Address = 10.0.0.1/24
  ListenPort = 51820
```

3. Run:

```bash
cd ansible
ansible-playbook -i /path/to/inventory.ini -e @/path/to/wg-extra-vars.yml wireguard-deploy-config.yml
```

`-e @file` overrides most sources—good for **CI-generated** files.

### 4.3 Option C: directory inventory + `group_vars` beside it (“one env folder”)

```text
~/wg-ops/env-prod/
  inventory/
    hosts.ini
    group_vars/
      wg_servers.yml
```

Run:

```bash
cd ansible
ansible-playbook -i ~/wg-ops/env-prod/inventory site.yml
```

Keep `group_vars/wg_servers.yml` only on disk or in a private repo—**no need** to commit it to wg-control-plane.

### 4.4 Option D: `ansible/local/` in this repo (listed in `.gitignore`)

For a fixed path without committing secrets or real IPs:

```text
ansible/local/
  inventory.ini
  group_vars/
    wg_servers.yml
```

```bash
cd ansible
ansible-playbook -i local/inventory.ini site.yml
```

(If `group_vars` lives under `local/group_vars/`, use a **directory inventory** pointing at `local/`—see the note in section 5.)

---

## 5. Notes when using a directory as `-i`

- If `ansible/local/` is a **directory** inventory, put `hosts.ini` and `group_vars/` **under the same inventory root** (structure as in 3.2).  
- If you pass a **single `inventory.ini` file** as `-i`, **adjacent `group_vars` may not apply** unless Ansible’s rules for adjacent inventory files match. In practice, a **directory inventory** is the clearest approach.

---

## 6. Working with wg-control-plane (API-generated inventory)

1. Use the control-plane API to produce **inventory text** and write a local file, e.g. `~/wg/generated/inventory.ini`.  
2. Ensure it contains a group matching `wg_target_hosts` (default `wg_servers`).  
3. SSH private key paths must match `ansible_ssh_private_key_file` in inventory (the generator’s example is `/tmp/keys/<id>.pem`; that file must exist on the machine running Ansible).  
4. If WireGuard config content is still owned by the control plane, either:  
   - Fetch via API and generate `wg_extra_vars.yml` (option B), or  
   - Set `wg_config_source: url` and `wg_config_fetch_url` in extra vars (playbooks support fetching from the controller and writing remotely).

---

## 7. Common commands

| Need | Example |
|------|---------|
| Specify inventory | `ansible-playbook -i path/to/inv site.yml` |
| Inject one variable | `ansible-playbook -i inv -e wg_target_hosts=wg_servers site.yml` |
| Inject from file | `ansible-playbook -i inv -e @extra-vars.yml site.yml` |
| Limit hosts | `ansible-playbook -i inv site.yml --limit wg-node-1` |
| Syntax check | `ansible-playbook --syntax-check -i inv site.yml` |

---

## 8. FAQ

**Q: I changed repo `group_vars` but don’t want to commit—why?**  
A: Don’t change the repo; override with local `inventory/group_vars/` or `-e @file`.

**Q: Do playbooks “support local generation”?**  
A: Yes—**natively**, via `-i`, inventory directories, `group_vars`/`host_vars`, and `-e`. Project playbooks do not hard-code paths; only `ansible.cfg`’s default inventory applies when `-i` is omitted.

**Q: `community.general` errors?**  
A: Run `ansible-galaxy collection install -r requirements.yml` from the `ansible/` directory.

---

## 9. Related documents

- [ansible-playbooks-spec.md](./ansible-playbooks-spec.md): playbook responsibilities and scope.
