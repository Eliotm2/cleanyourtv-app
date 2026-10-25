// Fork (Thomas): "Install an app". Pick an APK on the phone, see what it is and what the TV has,
// install it through the guarded path. The words are this module's installapk_* strings.

package tv.cleanyour.feature.manage

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tv.cleanyour.core.flows.ApkStep
import tv.cleanyour.core.flows.InstallApkIntent
import tv.cleanyour.core.flows.InstallApkState
import tv.cleanyour.ui.design.component.Banner
import tv.cleanyour.ui.design.component.BannerTone
import tv.cleanyour.ui.design.component.CytTopBar
import tv.cleanyour.ui.design.component.ListDivider
import tv.cleanyour.ui.design.component.ListGroup
import tv.cleanyour.ui.design.component.ListRow
import tv.cleanyour.ui.design.component.PrimaryButton
import tv.cleanyour.ui.design.component.ProgressBar
import tv.cleanyour.ui.design.component.ScreenScaffold
import tv.cleanyour.ui.design.component.ScreenTitle
import tv.cleanyour.ui.design.component.SecondaryButton
import tv.cleanyour.ui.design.theme.Glow

@Composable
fun InstallApkScreen(state: InstallApkState, onIntent: (InstallApkIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val picked = state.picked
    val step = state.step
    ScreenScaffold(Glow.Tv, modifier, topBar = { CytTopBar(onBack = onBack) }) {
        ScreenTitle(stringResource(R.string.installapk_title), Modifier.padding(top = 20.dp))
        Body(msg(R.string.installapk_lead, "tv" to state.tvName), Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp))
        // Navigation into the phone's file picker: not a write to the TV, so not guarded.
        SecondaryButton(
            stringResource(if (picked == null) R.string.installapk_pick else R.string.installapk_pick_other),
            { onIntent(InstallApkIntent.Pick) }, Modifier.padding(top = 20.dp), enabled = !state.busy,
        )
        if (state.notAnApk) Banner(stringResource(R.string.installapk_not_apk), BannerTone.WARNING, Modifier.padding(top = 16.dp))
        if (step is ApkStep.Reading) Body(stringResource(R.string.installapk_reading), Modifier.padding(top = 16.dp, start = 4.dp))
        if (picked != null) {
            ListGroup(Modifier.padding(top = 20.dp)) {
                ListRow(stringResource(R.string.installapk_file), value = picked.name)
                ListDivider()
                ListRow(stringResource(R.string.installapk_package), subtitle = picked.info.pkg.value)   // long: under the label, not beside it
                ListDivider()
                ListRow(stringResource(R.string.installapk_version), value = "${picked.info.versionName} (${picked.info.versionCode})")
                ListDivider()
                ListRow(stringResource(R.string.installapk_size), value = msg(R.string.installapk_size_mb, "mb" to "%.1f".format(picked.info.size / 1_048_576.0)))
                ListDivider()
                ListRow(stringResource(R.string.installapk_key), value = picked.key12)
                ListDivider()
                val onTv = state.onTv
                ListRow(
                    stringResource(R.string.installapk_on_tv),
                    subtitle = when {
                        onTv == null -> null
                        onTv.sameKey == false -> stringResource(R.string.installapk_on_tv_other_key)
                        onTv.versionCode > picked.info.versionCode -> stringResource(R.string.installapk_on_tv_newer)
                        onTv.versionCode == picked.info.versionCode -> stringResource(R.string.installapk_on_tv_same)
                        else -> stringResource(R.string.installapk_on_tv_older)
                    },
                    value = if (onTv == null) stringResource(R.string.installapk_on_tv_no) else msg(R.string.installapk_on_tv_version, "code" to onTv.versionCode),
                )
            }
            when (step) {
                is ApkStep.Installing -> {
                    Body(stringResource(R.string.installapk_installing), Modifier.padding(top = 20.dp, start = 4.dp))
                    ProgressBar(step.percent / 100f, Modifier.padding(top = 8.dp))
                }
                is ApkStep.Done -> Banner(msg(R.string.installapk_done, "name" to step.name), BannerTone.SUCCESS, Modifier.padding(top = 16.dp))
                is ApkStep.Refused -> Banner(step.reasons.joinToString("\n"), BannerTone.WARNING, Modifier.padding(top = 16.dp))
                is ApkStep.Failed -> Banner(msg(R.string.installapk_failed, "name" to step.name), BannerTone.DANGER, Modifier.padding(top = 16.dp))
                else -> {}
            }
            // The write to the TV: guarded, like every control that changes the TV.
            PrimaryButton(
                stringResource(if (state.onTv != null) R.string.installapk_install_again else R.string.installapk_install),
                { onIntent(InstallApkIntent.Install) }, Modifier.padding(top = 20.dp, bottom = 24.dp),
                enabled = !state.busy && state.onTv?.sameKey != false, loading = step is ApkStep.Installing,
            )
        }
        Body(stringResource(R.string.installapk_note), Modifier.padding(top = 12.dp, start = 4.dp, end = 4.dp, bottom = 24.dp))
    }
}
