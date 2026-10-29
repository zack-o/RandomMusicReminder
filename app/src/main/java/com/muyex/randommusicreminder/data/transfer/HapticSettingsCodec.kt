package com.muyex.randommusicreminder.data.transfer

import com.muyex.randommusicreminder.domain.haptics.*
import org.json.JSONObject

object HapticSettingsCodec {
    fun encode(value: HapticSettings): JSONObject {
        value.validate()
        return JSONObject().put("preset",value.preset.name).put("strength",value.strength.name)
            .put("repeatGapMillis",value.repeatGapMillis).put("useMusicRhythm",value.useMusicRhythm)
    }
    fun decode(value: JSONObject): HapticSettings = HapticSettings(HapticPreset.valueOf(value.str("preset")),
        HapticStrength.valueOf(value.str("strength")),value.int("repeatGapMillis"),value.bool("useMusicRhythm")).also { it.validate() }
}
