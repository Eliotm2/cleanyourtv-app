// Fork (Thomas): "Install an app" in Manage. The person picks an APK on the phone (SAF, in :app), the
// app copies it into the BlobStore and reads its package, version and signing key, and this machine
// shows what it is, what the TV already has, and installs it through the same gated, journaled
// path a module takes (InstallApk plan). Undo everything removes it like any app we installed.

package tv.cleanyour.core.flows

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import tv.cleanyour.core.engine.RunState
import tv.cleanyour.core.engine.StartRefusal
import tv.cleanyour.core.model.ApkInfo
import tv.cleanyour.core.model.BlobRef
import tv.cleanyour.core.model.TvId
import tv.cleanyour.core.planner.PlanRequest
import tv.cleanyour.core.planner.PlanResult

// The APK as the phone read it. key12: the first 12 hex digits of the signing certificate's SHA-256.
data class PickedApk(val name: String, val blob: BlobRef, val info: ApkInfo, val key12: String)

// What the TV's last snapshot says about that package.
data class OnTv(val versionCode: Long, val label: String?, val sameKey: Boolean?)

sealed interface ApkStep {
    data object Reading : ApkStep                      // the file is being copied and inspected
    data class Installing(val percent: Int) : ApkStep
    data class Done(val name: String) : ApkStep
    data class Refused(val reasons: List<String>) : ApkStep
    data class Failed(val name: String) : ApkStep
}

data class InstallApkState(
    val tvName: String = "",
    val picked: PickedApk? = null,
    val onTv: OnTv? = null,
    val step: ApkStep? = null,
    val notAnApk: Boolean = false,
) {
    val busy: Boolean get() = step is ApkStep.Reading || step is ApkStep.Installing
}

sealed interface InstallApkIntent {
    data object Pick : InstallApkIntent                                      // opens the file picker (ManageEffect.PickDocument)
    data object Reading : InstallApkIntent                                   // the app started copying the chosen file
    data class Picked(val name: String, val blob: BlobRef, val info: ApkInfo) : InstallApkIntent
    data object NotAnApk : InstallApkIntent                                  // the chosen file could not be read as an APK
    data object Install : InstallApkIntent
    data object Dismiss : InstallApkIntent                                   // clears a Done / Refused / Failed step
}

class InstallApkMachine(
    private val d: ManageDeps,
    scope: CoroutineScope,
    private val tvId: TvId,
) : Machine<InstallApkState, InstallApkIntent, ManageEffect>(InstallApkState(), scope) {

    private data class Name(val tvName: String)
    private data class Step(val step: ApkStep?)
    private data class Have(val onTv: OnTv?)

    init {
        send(Name(d.tv(tvId)?.nickname ?: ""))
    }

    override fun reduce(state: InstallApkState, event: Any): InstallApkState = when (event) {
        is Name -> state.copy(tvName = event.tvName)
        InstallApkIntent.Reading -> state.copy(step = ApkStep.Reading, notAnApk = false)
        is InstallApkIntent.Picked -> state.copy(
            picked = PickedApk(event.name, event.blob, event.info, event.info.signer.hex.take(12)),
            onTv = null, step = null, notAnApk = false)
        InstallApkIntent.NotAnApk -> state.copy(step = null, notAnApk = true)
        is Have -> state.copy(onTv = event.onTv)
        is Step -> state.copy(step = event.step)
        InstallApkIntent.Dismiss -> if (state.busy) state else state.copy(step = null)
        else -> state
    }

    override fun handle(event: Any, before: InstallApkState, after: InstallApkState) {
        when (event) {
            InstallApkIntent.Pick -> if (!before.busy) effect(ManageEffect.PickDocument)
            is InstallApkIntent.Picked -> scope.launch { lookUp(event.info) }
            InstallApkIntent.Install -> if (!before.busy && before.picked != null) scope.launch { install(before.picked) }
            else -> {}
        }
    }

    private suspend fun lookUp(info: ApkInfo) {
        val have = d.snapshots.latest(tvId)?.packages?.get(info.pkg)
        send(Have(have?.let { OnTv(it.versionCode, it.label, it.signer?.let { s -> s == info.signer }) }))
    }

    // The gate, the plan (refusals are shown as they are), the run, then a fresh snapshot and restore point.
    private suspend fun install(p: PickedApk) {
        val tv = d.tv(tvId) ?: return
        val session = (d.access.session(tvId) as? AccessResult.Ok)?.session ?: return send(Step(ApkStep.Failed(p.name)))
        if (d.engine.runState(tvId).value is RunState.Paused) { TvWriter(d).openRun(tv, session); return }
        val pass = d.gate.pass(tvId, tv.nickname) ?: return
        send(Step(ApkStep.Installing(0)))
        var refused: List<String>? = null
        val outcome = attempt {
            val snapshot = d.runner.snapshotFor(tv, session)
            when (val r = d.runner.plan(tv, PlanRequest.InstallApk(p.blob, p.info, p.name), snapshot)) {
                is PlanResult.Refused -> { refused = r.reasons; null }
                is PlanResult.Ok -> d.runner.run(tv, r.plan, session, pass, onStarted = { d.sessionEnd.touched(tvId) }) { s ->
                    if (s is RunState.Running) send(Step(ApkStep.Installing(s.percent)))
                }
            }
        }.getOrNull()
        refused?.let { return send(Step(ApkStep.Refused(it))) }
        if (outcome is RunOutcome.Paused) { d.openWrites.show(tv, outcome.state); return send(Step(null)) }
        if (outcome is RunOutcome.StartRefused && outcome.refusal == StartRefusal.RUN_OPEN && TvWriter(d).openRun(tv, session) != null) return send(Step(null))
        if (outcome !is RunOutcome.Finished) return send(Step(ApkStep.Failed(p.name)))
        attempt { d.snapshots.save(d.runner.freshSnapshot(session)) }
        d.restorePoint.write(tv, session, d.profileOf(tv))
        lookUp(p.info)
        send(Step(ApkStep.Done(p.name)))
    }
}
