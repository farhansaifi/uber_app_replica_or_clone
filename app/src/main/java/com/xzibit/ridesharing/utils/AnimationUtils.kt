package com.xzibit.ridesharing.utils

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator

object AnimationUtils {

    // this function is create a animation (draw a line between two locations)
    // on map path using latLng array point
    // between to locations
    fun polyLineAnimator(): ValueAnimator {
        val valueAnimator =ValueAnimator.ofInt(0,100)
        valueAnimator.interpolator = LinearInterpolator()
        valueAnimator.duration = 2000
        return valueAnimator
    }

    // this function is create a car(cab) animation (car move one location to another location)
    fun cabAnimator() :ValueAnimator {
        val valueAnimator = ValueAnimator.ofFloat(0f,1f)
        valueAnimator.duration = 3000
        valueAnimator.interpolator = LinearInterpolator()
        return valueAnimator
    }
}