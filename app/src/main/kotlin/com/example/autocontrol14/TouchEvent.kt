/*
 * autocontrol14 - Version 4.0
 * Copyright © 2025 Z.chao Zhang
 * Licensed under Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0).
 * Free for non-commercial use only. Credit TouchWizard. See https://creativecommons.org/licenses/by-nc/4.0/.
 * Provided "as is," use at your own risk.
 */
package com.example.autocontrol14

import android.os.Parcel
import android.os.Parcelable

data class TouchEvent(
    val action: String,
    val pointerId: Int,
    val x: Float,
    val y: Float,
    val time: Double,
    val size: Float = 0.04f
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readFloat(),
        parcel.readFloat(),
        parcel.readDouble(),
        parcel.readFloat()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(action)
        parcel.writeInt(pointerId)
        parcel.writeFloat(x)
        parcel.writeFloat(y)
        parcel.writeDouble(time)
        parcel.writeFloat(size)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<TouchEvent> {
        override fun createFromParcel(parcel: Parcel): TouchEvent = TouchEvent(parcel)
        override fun newArray(size: Int): Array<TouchEvent?> = arrayOfNulls(size)
    }
}
