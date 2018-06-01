package com.bungabear.fitsleep2calendar

import java.text.SimpleDateFormat
import java.util.*

data class SleepEvent(var start : Long, var end : Long) {

    private val timeParser = SimpleDateFormat("yyyyMMdd, HH:mm", Locale.getDefault())

    fun getStartAsString() : String{
        return timeParser.format(start)
    }

    fun getEndAsString() : String{
        return timeParser.format(end)
    }
}