package com.app.service.ansible

import com.app.model.AnsibleExecutionJob
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.*

/**
 * Runs connectivity checks against a single [AnsibleHost] using [PING_PLAYBOOK]
 * (ansible.builtin.ping over SSH).
 */
@Service
class AnsibleHostConnectivityService(
    private val ansibleService: AnsibleService,
    private val ansibleInventoryGenerator: AnsibleInventoryGenerator,
    private val ansiblePlaybookExecutor: AnsiblePlaybookExecutor,
    @Value("\${ansible.callback.host}") private val callbackHost: String
) {

    fun runPing(hostId: UUID): AnsibleExecutionJob {
        val host = ansibleService.getHost(hostId)
        check(ansiblePlaybookExecutor.playbookExists(PING_PLAYBOOK)) {
            "Playbook not found: $PING_PLAYBOOK"
        }

        val inventory = ansibleInventoryGenerator.inventoryForSinglePlaybookTarget(host, WG_INVENTORY_GROUP)
        val extraVars = mapOf(
            "wg_target_hosts" to WG_INVENTORY_GROUP,
            "wg_callback_host" to callbackHost,
            "wg_host_id" to host.id.toString()
        )

        return ansiblePlaybookExecutor.executePlaybook(
            inventoryContent = inventory,
            playbook = PING_PLAYBOOK,
            extraVars = extraVars,
            triggeredBy = "AnsibleHostConnectivityService",
            notes = "Connectivity check (ansible ping) for host '${host.hostname}'",
        )
    }

    companion object {
        /** Must match [com.app.service.AnsibleWireGuardManagementService] single-host inventory group. */
        private const val WG_INVENTORY_GROUP = "wireguard_servers"

        /** Bundled playbook under `classpath:ansible/` (YAML `.yml` extension). */
        const val PING_PLAYBOOK = "ping.yml"
    }
}
