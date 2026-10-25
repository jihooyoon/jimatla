package ji.minitvlauncher.system

import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager

/** A source the TV can switch to: HDMI ports, antenna, AV. */
data class TvInputItem(val id: String, val label: String, val isPassthrough: Boolean)

/** Physical inputs first (HDMI, AV…), then tuners; empty on devices without TV inputs. */
fun tvInputs(context: Context): List<TvInputItem> {
    val tim = context.getSystemService(TvInputManager::class.java) ?: return emptyList()
    return runCatching { tim.tvInputList }.getOrDefault(emptyList())
        .filter { it.parentId == null || it.isPassthroughInput }
        .filter { !it.isHidden(context) }
        .sortedWith(compareBy({ !it.isPassthroughInput }, { it.type }))
        .map { TvInputItem(it.id, it.loadCustomLabel(context)?.toString() ?: it.loadLabel(context).toString(), it.isPassthroughInput) }
}

/** Live TV app intent that tunes to [input]: HDMI and AV directly, tuners at their channel list. */
fun switchToInputIntent(input: TvInputItem): Intent {
    val uri = if (input.isPassthrough) TvContract.buildChannelUriForPassthroughInput(input.id)
    else TvContract.buildChannelsUriForInput(input.id)
    return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

