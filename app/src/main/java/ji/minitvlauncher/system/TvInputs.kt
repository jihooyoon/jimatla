package ji.minitvlauncher.system

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager

enum class InputSignal { Connected, Standby, None }

/** A source the TV can switch to: HDMI ports, antenna, AV. */
data class TvInputItem(
    val id: String,
    val label: String,
    val isPassthrough: Boolean,
    val signal: InputSignal = InputSignal.Connected,
)

/** Physical inputs first (HDMI, AV…), then tuners; empty on devices without TV inputs. */
fun tvInputs(context: Context): List<TvInputItem> {
    val tim = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
    return runCatching { tim.tvInputList }.getOrDefault(emptyList())
        .filter { it.parentId == null || it.isPassthroughInput }
        .filter { !it.isHidden(context) }
        .sortedWith(compareBy({ !it.isPassthroughInput }, { it.type }))
        .map { info ->
            val signal = when (runCatching { tim.getInputState(info.id) }.getOrDefault(TvInputManager.INPUT_STATE_CONNECTED)) {
                TvInputManager.INPUT_STATE_DISCONNECTED -> InputSignal.None
                TvInputManager.INPUT_STATE_CONNECTED_STANDBY -> InputSignal.Standby
                else -> InputSignal.Connected
            }
            TvInputItem(info.id, info.loadCustomLabel(context)?.toString() ?: info.loadLabel(context).toString(), info.isPassthroughInput, signal)
        }
}

/** Live TV app intent that tunes to [input]: HDMI and AV directly, tuners at their channel list. */
fun switchToInputIntent(input: TvInputItem): Intent {
    val uri = if (input.isPassthrough) TvContract.buildChannelUriForPassthroughInput(input.id)
    else TvContract.buildChannelsUriForInput(input.id)
    return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

