package com.xzibit.ridesharing.ui.maps

import com.google.android.gms.maps.model.LatLng

interface MapsView {

    fun showNearByCabs(latLangList: List<LatLng>)

    fun informCabBooked()

    fun showPath (latLangList: List<LatLng>)

    fun updateCabLocation(latLng: LatLng)

    fun informCabIsArriving ()

    fun informCabArrived ()

    fun informTripStart ()

    fun informTripEnd ()

    fun showRoutesNotAvailableError ()

    fun showDirectionApiFailedError (error : String)
}