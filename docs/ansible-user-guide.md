# Ansible 使用手冊（wg-control-plane）

本文件說明 **inventory、group_vars、host_vars** 在 Ansible 裡如何作用，以及在本專案中如何搭配 **本機產生** 的檔案使用 playbook，而不必依賴 repo 內「範例」路徑。

Ansible 目錄位置：`src/main/resources/ansible/`（以下簡稱 **`ansible/`**）。

---

## 1. Repo 裡的 inventory / group_vars 是什麼？

| 路徑 | 用途 |
|------|------|
| `ansible/inventory/static-example.ini` | **範例**：示範 `[wg_servers]` 長相；可刪可不刪，**不應**當成你環境的唯一真相。 |
| `ansible/group_vars/all.yml` | **範例預設**：定義 `wg_target_hosts`（playbook 的 `hosts:` 用字串群組名）。 |
| `ansible/group_vars/wg_servers.yml` | **範例預設**：`wg_interface_name`、`wg_listen_port`、`wg_config_content` 等。 |
| `ansible/ansible.cfg` | 預設 `inventory = inventory/static-example.ini`；**僅在你不加 `-i` 時生效**。 |

重點：**這些是「可提交的預設／範例」**。真實環境通常會在本機 **generate** inventory（例如從 wg-control-plane API 下載），以及 **generate 或手寫** host/group 變數；做法見下文。

---

## 2. Playbook 如何決定「打哪幾台」？

本專案 playbook 使用：

```yaml
hosts: "{{ wg_target_hosts }}"
```

預設在 `group_vars/all.yml` 為：

```yaml
wg_target_hosts: wg_servers
```

因此 **inventory 裡必須有一個群組名稱與 `wg_target_hosts` 一致**（預設即 **`[wg_servers]`**）。  
若你產生的 inventory 群組叫 `production_wg`，執行時請二選一：

- 在變數中設 `wg_target_hosts: production_wg`（見第 4 節），或  
- 改你的 inventory 群組名為 `wg_servers`。

---

## 3. Ansible 怎麼「找」inventory 與變數？

### 3.1 Inventory（主機清單）

- **單一檔案**：`hosts.ini`、`hosts.yaml` 皆可。  
- **目錄當 inventory**：資料夾內可放多個 `*.ini` / `*.yml`，並可並排 `group_vars/`、`host_vars/`（見下）。  
- **指定方式**：執行時 **`ansible-playbook -i <路徑>`** 會覆寫 `ansible.cfg` 的預設 inventory。

因此：**本機 gen 的檔案只要用 `-i` 指到即可**，不必放進 git。

### 3.2 group_vars / host_vars

Ansible 會在 **inventory 所在脈絡** 載入變數（與「劇本目錄旁的 group_vars」可並存，順序依 Ansible 版本與設定略有差異；實務上建議 **把「環境專用」變數集中在你控制的 inventory 目錄**）。

常見結構（**目錄型 inventory**）：

```text
my-ansible-env/
  inventory/
    hosts.ini          # 內含 [wg_servers] ...
    group_vars/
      wg_servers.yml   # 只給 wg_servers 的變數
      all.yml          # 可選：等同 all 群組
    host_vars/
      node-a.yml       # 單台覆寫
```

執行：

```bash
ansible-playbook -i my-ansible-env/inventory site.yml
```

這樣 **完全不依賴** repo 內的 `ansible/group_vars/wg_servers.yml`。

### 3.3 變數優先級（實務記這幾個就夠）

由低到高（大略）：

1. Role `defaults`（roles 裡 `defaults/main.yml`）  
2. `group_vars` / `host_vars`  
3. **`ansible-playbook -e` / `-e @file`（extra vars，權重很高）**

因此：**本機 gen 的 `extra-vars.yml` 可以覆蓋** repo 範例中的 `group_vars`。

---

## 4. 本機產生檔案：推薦用法

### 4.1 方式 A：只產生 inventory，變數沿用 repo 內範例（不建議生產）

適合本機試跑。

```bash
cd ansible
ansible-playbook -i /path/to/generated/inventory.ini wireguard-install.yml
```

注意：若仍使用 repo 內 `group_vars`，會讀到範例的 `wg_config_content` 等。生產環境請改用方式 B 或 C。

### 4.2 方式 B：inventory + 獨立 vars 檔（`-e @file`）

1. 產生 `inventory.ini`（含 `[wg_servers]` 與主機）。  
2. 產生 `wg-extra-vars.yml`（例如由模板或 API 資料 gen）：

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

3. 執行：

```bash
cd ansible
ansible-playbook -i /path/to/inventory.ini -e @/path/to/wg-extra-vars.yml wireguard-deploy-config.yml
```

`-e @file` 的變數會強制覆蓋多數來源，適合 **CI 產生檔**。

### 4.3 方式 C：目錄型 inventory + 同目錄 `group_vars`（最像「環境一份」）

```text
~/wg-ops/env-prod/
  inventory/
    hosts.ini
    group_vars/
      wg_servers.yml
```

執行：

```bash
cd ansible
ansible-playbook -i ~/wg-ops/env-prod/inventory site.yml
```

`group_vars/wg_servers.yml` 只放在本機或私密 repo，**不必**提交到 wg-control-plane。

### 4.4 方式 D：本 repo 內 `ansible/local/`（已加入 .gitignore）

若希望路徑固定、又不要 commit 密鑰與真實 IP：

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

（若 `group_vars` 放在 `local/group_vars/`，請用 **目錄 inventory** 一併指向 `local/`，見下節「注意」。）

---

## 5. 目錄當 `-i` 時的注意事項

- 若使用 `ansible/local/` **目錄** 作為 inventory，請把 `hosts.ini` 與 `group_vars/` **放在同一個 inventory 根目錄底下**，結構見 3.2。  
- 若只用 **單一 `inventory.ini` 檔** 當 `-i`，則 **同目錄的 `group_vars` 不會自動套用**（除非該檔與 group_vars 的相對關係符合 Ansible 對「相鄰 inventory 檔」的規則）。實務上 **目錄型 inventory 最直覺**。

---

## 6. 與 wg-control-plane（API 產生 inventory）的配合

1. 使用控制面 API 產生 **inventory 字串** 並寫入本機檔案，例如 `~/wg/generated/inventory.ini`。  
2. 確認其中有與 `wg_target_hosts` 一致的群組（預設 `wg_servers`）。  
3. SSH 私鑰路徑須與 inventory 內 `ansible_ssh_private_key_file` 一致（控制面產生器預設範例為 `/tmp/keys/<id>.pem`，執行 Ansible 的機器上必須真有該檔）。  
4. WireGuard 設定內容若仍由控制面持有，可：  
   - 用 API 拉取後 gen 成 `wg_extra_vars.yml`（方式 B），或  
   - 在 extra vars 設定 `wg_config_source: url` 與 `wg_config_fetch_url`（playbook 已支援由 controller 取回再寫入遠端）。

---

## 7. 常用指令對照

| 需求 | 範例 |
|------|------|
| 指定 inventory | `ansible-playbook -i path/to/inv site.yml` |
| 注入單一變數 | `ansible-playbook -i inv -e wg_target_hosts=wg_servers site.yml` |
| 注入檔案 | `ansible-playbook -i inv -e @extra-vars.yml site.yml` |
| 限縮主機 | `ansible-playbook -i inv site.yml --limit wg-node-1` |
| 語法檢查 | `ansible-playbook --syntax-check -i inv site.yml` |

---

## 8. 常見問題

**Q：為什麼我改了 repo 的 `group_vars` 卻不想提交？**  
A：不要改 repo；改在你本機的 `inventory/group_vars/` 或 `-e @file` 覆蓋。

**Q：playbook 有沒有「支援本機 gen」？**  
A：有。**Ansible 原生**就是透過 `-i`、inventory 目錄、`group_vars`/`host_vars`、`-e` 來支援；本專案 playbook 沒有寫死路徑，只有 `ansible.cfg` 預設 inventory 在沒指定 `-i` 時會用到範例檔。

**Q：`community.general` 報錯？**  
A：執行 `ansible-galaxy collection install -r requirements.yml`（在 `ansible/` 目錄）。

---

## 9. 相關文件

- [ansible-playbooks-spec.md](./ansible-playbooks-spec.md)：playbook 職責與範圍說明。
