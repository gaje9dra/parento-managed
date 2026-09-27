package com.parento.managed.application

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.parento.managed.device.ManagementMode

enum class ApplicationDesiredAction { ALLOW, BLOCK }

data class ApplicationPolicyRule(
    val packageName: String,
    val action: ApplicationDesiredAction,
)

data class ApplicationEnforcementTarget(
    val packageName: String,
    val desiredAction: ApplicationDesiredAction,
    val currentlySuspended: Boolean,
    val managedByParento: Boolean,
)

data class ApplicationEvaluation(
    val targets: List<ApplicationEnforcementTarget>,
)

class ApplicationPolicyEvaluator {
    fun evaluate(
        installedPackages: List<Pair<String, Boolean>>,
        rules: List<ApplicationPolicyRule>,
        managedPackageName: String,
        previouslyManagedBlockedPackages: Set<String> = emptySet(),
    ): ApplicationEvaluation {
        val ruleMap = LinkedHashMap<String, ApplicationDesiredAction>()
        for (rule in rules.sortedBy { it.packageName }) {
            val previous = ruleMap.put(rule.packageName, rule.action)
            require(previous == null || previous == rule.action) {
                "Conflicting application rules are not enforceable."
            }
        }

        return ApplicationEvaluation(
            installedPackages
                .asSequence()
                .distinctBy { it.first }
                .sortedBy { it.first }
                .filter { it.first != managedPackageName }
                .map { (packageName, suspended) ->
                    ApplicationEnforcementTarget(
                        packageName = packageName,
                        desiredAction = when {
                            ruleMap[packageName] != null -> ruleMap[packageName]!!
                            packageName in previouslyManagedBlockedPackages -> ApplicationDesiredAction.ALLOW
                            else -> ApplicationDesiredAction.ALLOW
                        },
                        currentlySuspended = suspended,
                        managedByParento = packageName in ruleMap || packageName in previouslyManagedBlockedPackages,
                    )
                }
                .toList(),
        )
    }
}

data class ApplicationEnforcementOutcome(
    val policyId: String?,
    val policyVersion: Int?,
    val attempted: Int,
    val succeeded: Int,
    val failed: Int,
    val unsupported: Int,
    val errorCode: String?,
    val resultingManagedBlockedPackages: Set<String> = emptySet(),
) {
    val status: ApplicationEnforcementStatus
        get() = when {
            unsupported > 0 && succeeded == 0 -> ApplicationEnforcementStatus.FAILED
            failed == 0 && unsupported == 0 -> ApplicationEnforcementStatus.APPLIED
            succeeded > 0 -> ApplicationEnforcementStatus.PARTIALLY_APPLIED
            else -> ApplicationEnforcementStatus.FAILED
        }
}

interface ApplicationEnforcementPlatform {
    fun managementMode(): ManagementMode
    fun installedPackagesWithSuspensionState(): List<Pair<String, Boolean>>
    fun setSuspended(packageName: String, suspended: Boolean): Boolean
}

class AndroidApplicationEnforcementPlatform(
    context: Context,
) : ApplicationEnforcementPlatform {
    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val devicePolicyManager =
        appContext.getSystemService(DevicePolicyManager::class.java)
    private val adminComponent =
        ComponentName(appContext, com.parento.managed.device.ManagedDeviceAdminReceiver::class.java)
    private val managementPlatform =
        com.parento.managed.device.AndroidDeviceManagementPlatform(appContext)

    override fun managementMode(): ManagementMode = when {
        managementPlatform.isDeviceOwner() -> ManagementMode.DEVICE_OWNER
        managementPlatform.isProfileOwner() -> ManagementMode.PROFILE_OWNER
        else -> ManagementMode.NOT_MANAGED
    }

    override fun installedPackagesWithSuspensionState(): List<Pair<String, Boolean>> =
        packageManager.getInstalledApplications(0)
            .asSequence()
            .map { info ->
                val suspended = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
                info.packageName to suspended
            }
            .distinctBy { it.first }
            .sortedBy { it.first }
            .toList()

    override fun setSuspended(packageName: String, suspended: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val failed = devicePolicyManager?.setPackagesSuspended(
            adminComponent,
            arrayOf(packageName),
            suspended,
        ) ?: return false
        if (failed.any { it == packageName }) return false

        val info = runCatching { packageManager.getApplicationInfo(packageName, 0) }.getOrNull()
            ?: return false
        val actual = (info.flags and ApplicationInfo.FLAG_SUSPENDED) != 0
        return actual == suspended
    }
}

class ApplicationEnforcementEngine(
    private val platform: ApplicationEnforcementPlatform,
    private val evaluator: ApplicationPolicyEvaluator = ApplicationPolicyEvaluator(),
    private val managedPackageName: String,
) {
    fun enforce(
        policyId: String?,
        policyVersion: Int?,
        rules: List<ApplicationPolicyRule>,
        previouslyManagedBlockedPackages: Set<String> = emptySet(),
    ): ApplicationEnforcementOutcome {
        val mode = platform.managementMode()
        if (mode != ManagementMode.DEVICE_OWNER && mode != ManagementMode.PROFILE_OWNER) {
            return ApplicationEnforcementOutcome(
                policyId, policyVersion, 0, 0, 0, 1,
                "UNSUPPORTED_MANAGEMENT_MODE",
            )
        }

        val evaluation = runCatching {
            evaluator.evaluate(
                platform.installedPackagesWithSuspensionState(),
                rules,
                managedPackageName,
                previouslyManagedBlockedPackages,
            )
        }.getOrElse {
            return ApplicationEnforcementOutcome(
                policyId, policyVersion, 0, 0, 1, 0,
                "INVALID_POLICY",
            )
        }

        var attempted = 0
        var succeeded = 0
        var failed = 0
        val resultingManagedBlockedPackages = evaluation.targets
            .filter { it.managedByParento && it.desiredAction == ApplicationDesiredAction.BLOCK && it.currentlySuspended }
            .map { it.packageName }
            .toMutableSet()

        for (target in evaluation.targets) {
            if (!target.managedByParento || (target.desiredAction == ApplicationDesiredAction.BLOCK) == target.currentlySuspended) {
                continue
            }
            attempted++
            val shouldSuspend = target.desiredAction == ApplicationDesiredAction.BLOCK
            val ok = runCatching {
                platform.setSuspended(target.packageName, shouldSuspend)
            }.getOrDefault(false)
            if (ok) {
                succeeded++
                if (shouldSuspend) {
                    resultingManagedBlockedPackages += target.packageName
                } else {
                    resultingManagedBlockedPackages -= target.packageName
                }
            } else {
                failed++
            }
        }

        return ApplicationEnforcementOutcome(
            policyId = policyId,
            policyVersion = policyVersion,
            attempted = attempted,
            succeeded = succeeded,
            failed = failed,
            unsupported = 0,
            resultingManagedBlockedPackages = resultingManagedBlockedPackages,
            errorCode = when {
                failed > 0 && succeeded == 0 -> "ENFORCEMENT_FAILED"
                failed > 0 -> "PARTIAL_ENFORCEMENT"
                else -> null
            },
        )
    }
}
