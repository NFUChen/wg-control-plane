# Ansible Playbooks 規格（草案）

## 1. 目的與範圍

本文件描述在 **WireGuard 節點可能部署於 Ansible 遠端 target** 的前提下，目前預期需要的 **playbook 集合與職責邊界**。實作可落在本 repo 的 `ansible/`（或獨立部署 repo），與 **wg-control-plane** 的職責區分如下。


| 元件                   | 職責                                                                                               |
| -------------------- | ------------------------------------------------------------------------------------------------ |
| **wg-control-plane** | WG 邏輯與資料（伺服器／客戶端、金鑰、Listen port、`post_up`/`post_down` 等）、產生 Ansible **inventory**（主機、群組、SSH 變數）。 |
| **Ansible**          | 在遠端 OS 上安裝套件、寫入設定檔、調整 kernel／防火牆、啟停 `wg-quick`／systemd、驗證節點狀態。                                   |


本 spec **不** 規定 Ansible 目錄結構或 role 命名；僅定義「需要哪些 playbook、各自做什麼、依賴與觸發時機」。

**實作目錄（本 repo）：** `src/main/resources/ansible/`（含 `ansible.cfg`、`requirements.yml`、playbook、roles、`group_vars`、`inventory` 範例）。

**操作與本機產生 inventory／變數：** 見 [ansible-user-guide.md](./ansible-user-guide.md)。

---

## 2. 假設與約束

- **遠端 OS**：以常見伺服器發行版為主（如 Debian/Ubuntu、RHEL 系系）；若僅支援子集，應在實作時於 README 註明。
- **Inventory**：由控制面 API 產生並下載／同步至執行 Ansible 的環境；playbook 假設已具備可連線的 `ansible_host`、`ansible_user`、`ansible_ssh_private_key_file`、`ansible_become` 等變數（與現有 inventory 產生邏輯對齊）。
- **設定檔來源**：下列 playbook 需支援至少一種策略（實作階段二選一或並存）：
  - **A. 由 playbook 變數／template 注入**（與外部系統手動同步）；或  
  - **B. 部署步驟從控制面 API 取得**再寫入遠端（需額外認證與網路可達性設計）。
- **本機 WG**：控制面目前可在 **應用所在主機** 直接寫 `/path/to/wg/*.conf` 並執行 `wg-quick`；遠端節點則 **不** 由此程式直接操作，改由 Ansible 履行等價步驟。

---

## 3. Playbook 清單

### 3.1 `bootstrap.yml`（或 `site-bootstrap.yml`）


| 項目       | 說明                                                         |
| -------- | ---------------------------------------------------------- |
| **目的**   | 新主機首次納管：確保可透過 Ansible 穩定執行（Python、sudo、套件索引等）。             |
| **建議觸發** | 主機初次加入 inventory、或 OS 大版本升級後。                              |
| **主要任務** | 安裝/確認 Python 3 與 `ansible` 所需模組；確認 `become` 可用；可選：時區、基礎套件。 |
| **產出**   | 主機可重複執行後續 playbook 而不需互動。                                  |
| **依賴**   | Inventory 連線資訊正確。                                          |


---

### 3.2 `wireguard-install.yml`


| 項目       | 說明                                                                        |
| -------- | ------------------------------------------------------------------------- |
| **目的**   | 在 target 上安裝 WireGuard 使用者空間工具與發行版所需相依（含 kernel module / DKMS 等，依 OS 而定）。 |
| **建議觸發** | 新 WG 節點首次部署、或版本升級策略要求時。                                                   |
| **主要任務** | 安裝 `wireguard-tools`（及對應套件）；可選：固定套件版本。                                    |
| **產出**   | 主機具備 `wg`、`wg-quick` 等指令。                                                 |
| **依賴**   | `bootstrap.yml` 已完成（或同等前提）。                                               |


---

### 3.3 `wireguard-sysctl.yml`（可合併至 `wireguard-install.yml`，建議 spec 層級獨立描述）


| 項目       | 說明                                                       |
| -------- | -------------------------------------------------------- |
| **目的**   | 設定節點作為路由／NAT 時所需的 kernel 參數（例如 IPv4/IPv6 轉發）。            |
| **建議觸發** | 與 `wireguard-install.yml` 同次或之後；設定變更時。                   |
| **主要任務** | `sysctl` 持久化（如 `net.ipv4.ip_forward=1`）；若產品僅 IPv4，可縮小範圍。 |
| **產出**   | reboot 後仍保留之 sysctl 設定（實作應使用發行版慣用方式）。                    |
| **依賴**   | OS 權限與 `become`。                                         |


---

### 3.4 `wireguard-firewall.yml`


| 項目       | 說明                                                                                                                 |
| -------- | ------------------------------------------------------------------------------------------------------------------ |
| **目的**   | 開放 WG **UDP listen port**，並與 `post_up`/`post_down` 所用 **iptables/nftables** 策略一致（若 NAT／FORWARD 在防火牆層而非僅介面 script）。 |
| **建議觸發** | `listen_port` 或防火牆政策變更；新節點部署。                                                                                      |
| **主要任務** | 持久化防火牆規則；與資料庫中該伺服器的 `listen_port` 對齊。                                                                              |
| **產出**   | 對外可連線至 WG port；內部轉發/NAT 行為符合設計。                                                                                    |
| **依賴**   | 已決定使用 iptables 或 nftables 及與 `post_up` 的責任切分（避免重複或互相清除規則）。                                                         |


---

### 3.5 `wireguard-deploy-config.yml`（核心；名稱可為 `deploy-wg-server.yml`）


| 項目       | 說明                                                                                             |
| -------- | ---------------------------------------------------------------------------------------------- |
| **目的**   | 將 **單一或多個** WG 介面設定寫入遠端約定路徑（例如 `/etc/wireguard/<iface>.conf`），並 **reload** 服務使設定生效。            |
| **建議觸發** | 控制面變更伺服器／對等設定後，由 CI/CD 或排程呼叫；或手動限縮至特定 `--limit`。                                               |
| **主要任務** | 寫入設定檔；使用 `wg-quick@<iface>.service` 或等價流程 **安全重載**（避免長時間中斷的策略由實作定義）；可選：備份舊檔。                   |
| **產出**   | 遠端 WG 介面與控制面定義一致且處於預期 up/down 狀態。                                                              |
| **依賴**   | `wireguard-install.yml`、`wireguard-sysctl.yml`、（視需求）`wireguard-firewall.yml`；設定內容來源策略（見第 2 節）。 |


---

### 3.6 `wireguard-stop.yml`（或 `wireguard-drain.yml`）


| 項目       | 說明                                                   |
| -------- | ---------------------------------------------------- |
| **目的**   | 維護／遷移時將介面或節點 **優雅下線**（`wg-quick down` 或 stop unit）。  |
| **建議觸發** | 維護窗口、節點汰換前。                                          |
| **主要任務** | 停止指定介面；可選：不刪除 conf，僅 stop。                           |
| **產出**   | 該介面不再轉發流量。                                           |
| **依賴**   | 與 `wireguard-deploy-config.yml` 使用相同介面名稱與 unit 命名約定。 |


---

### 3.7 `wireguard-verify.yml`


| 項目       | 說明                                                      |
| -------- | ------------------------------------------------------- |
| **目的**   | 釋出後或變更後 **自動驗證**：介面存在、`wg show` 狀態、UDP port listen 等。   |
| **建議觸發** | 每次 `wireguard-deploy-config.yml` 成功後串接，或獨立健康檢查 job。     |
| **主要任務** | 斷言指令輸出與預期一致；可選：從另一台測試 UDP 連通（需額外 inventory 或 delegate）。 |
| **產出**   | 明確 pass/fail，供 CI 或告警使用。                                |
| **依賴**   | 節點已部署且防火牆允許管理路徑連線。                                      |


---

### 3.8 `site.yml`（總入口，可選）


| 項目       | 說明                                                                                                       |
| -------- | -------------------------------------------------------------------------------------------------------- |
| **目的**   | 以 **import_play** 或 **role** 順序編排：`bootstrap` → `install` → `sysctl` → `firewall` → `deploy` → `verify`。 |
| **建議觸發** | 綠地安裝一臺完整 WG 節點時一鍵執行；棕地變更則多半只跑子集合。                                                                        |
| **主要任務** | 僅編排，不重複業務邏輯。                                                                                             |
| **產出**   | 與分段 playbook 相同，但操作者介面單一。                                                                                |
| **依賴**   | 各子 playbook 已定義變數介面。                                                                                     |


---

## 4. 與控制面 inventory 的對齊（非功能性需求）

- **群組**：建議在 Ansible 端使用與控制面 **inventory group** 可對應的命名（例如 `wg_servers`），便於 `ansible-playbook -l wg_servers`。
- **每主機變數**：介面名稱、`listen_port`、設定檔路徑等，宜透過 `group_vars`／`host_vars` 或動態從 API 拉取後注入，與 `WireGuardServer` 實體欄位一致，避免與 DB 漂移。
- **SSH 金鑰**：inventory 產生器目前假設私鑰路徑為執行環境上的 `/tmp/keys/<id>.pem`；playbook 執行前須確保該路徑與權限與實際部署一致。

---

## 5. 刻意不包含（後續再開 spec）

- **SSH 強化、fail2ban、OS 全機加固**：可獨立為 `hardening.yml`，與 WG 生命週期分離。
- **控制面應用本身的部署**（Docker/K8s）：維持既有 `deployment/` 流程，本 spec 不覆蓋。
- **在遠端安裝「回呼控制面」的 agent**：若產品決定以 Ansible 為唯一操作面，可不實作；若需要 API 觸發遠端啟停，需另列 **agent spec**。

---

## 6. 修訂紀錄


| 版本  | 日期         | 說明                                |
| --- | ---------- | --------------------------------- |
| 0.1 | 2026-04-02 | 初稿：依遠端 WG 節點部署假設整理 playbook 清單與職責 |


