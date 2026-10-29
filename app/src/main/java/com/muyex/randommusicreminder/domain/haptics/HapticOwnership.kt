package com.muyex.randommusicreminder.domain.haptics

/** A preview may neither replace an alarm nor cancel a newer owner's vibration. */
class HapticOwnership {
    private var owner: Any?=null
    private var alarmOwner=false
    @Synchronized fun claim(token: Any, alarm: Boolean, action: ()->Unit): Boolean {
        if(owner!=null && alarmOwner && !alarm) return false
        action();owner=token;alarmOwner=alarm;return true
    }
    @Synchronized fun release(token: Any, action: ()->Unit) { if(owner===token) { owner=null;alarmOwner=false;action() } }
}
