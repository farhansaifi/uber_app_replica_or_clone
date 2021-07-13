package com.xzibit.ridesharing.ui.maps

import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.xzibit.ridesharing.data.network.NetworkService
import com.xzibit.ridesharing.simulator.WebSocket
import com.xzibit.ridesharing.simulator.WebSocketListener
import com.xzibit.ridesharing.utils.Constants
import org.json.JSONObject

class MapsPresenter(private val networkService: NetworkService):WebSocketListener {

    companion object{
        private const val tag = "MapsPresenter"
    }
    private var view:MapsView? = null
    private lateinit var webSocket: WebSocket


    fun onAttach(view: MapsView){
        this.view = view
        webSocket = networkService.createWebSocket(this)
        webSocket.connect()
    }

    fun requestNearByCabs(latLng: LatLng){
        val jsonObject = JSONObject()
        jsonObject.put(Constants.TYPE,Constants.NEAR_BY_CABS)
        jsonObject.put(Constants.LAT,latLng.latitude)
        jsonObject.put(Constants.LNG,latLng.longitude)
        webSocket.sendMessage(jsonObject.toString())
    }

    fun requestCab (pickUpLatLng: LatLng,dropLatLng: LatLng){
        val jsonObject = JSONObject()
        jsonObject.put(Constants.TYPE,Constants.REQUEST_CAB)
        jsonObject.put("pickUpLat",pickUpLatLng.latitude)
        jsonObject.put("pickUpLng",pickUpLatLng.longitude)
        jsonObject.put("dropLat",dropLatLng.latitude)
        jsonObject.put("dropLng",dropLatLng.longitude)
        webSocket.sendMessage(jsonObject.toString())
    }

    private fun handleOnMessageNearByCabs(jsonObject: JSONObject) {
        val nearByCabLocation = arrayListOf<LatLng>()
        val jsonArray = jsonObject.getJSONArray(Constants.LOCATIONS)
        for (i in 0 until jsonArray.length()){
            val lat = (jsonArray.get(i) as JSONObject).getDouble(Constants.LAT)
            val lng = (jsonArray.get(i) as JSONObject).getDouble(Constants.LNG)
            val latLng = LatLng(lat,lng)
            nearByCabLocation.add(latLng)
        }
        view?.showNearByCabs(nearByCabLocation)
    }

    fun onDetach() {
        webSocket.disconnect()
        view = null
    }

    override fun onConnect() {

        Log.d(tag,"onConnect")

    }

    override fun onMessage(data: String) {
        Log.d(tag,"onMessage : $data")
        val jsonObject = JSONObject(data)
        when (jsonObject.getString(Constants.TYPE)){
            Constants.NEAR_BY_CABS -> {
                // after getting json result we handle the json object on below function
                handleOnMessageNearByCabs(jsonObject)
            }
            Constants.CAB_BOOKED ->{
                view?.informCabBooked()
            }
            Constants.PICKUP_PATH,Constants.TRIP_PATH -> {
                val jsonArray = jsonObject.getJSONArray("path")
                val pickupPath = arrayListOf<LatLng>()
                for (i in 0 until jsonArray.length()){
                    val lat = (jsonArray.get(i) as JSONObject).getDouble(Constants.LAT)
                    val lng = (jsonArray.get(i) as JSONObject).getDouble(Constants.LNG)
                    val latLng = LatLng(lat,lng)
                    pickupPath.add(latLng)
                }
                view?.showPath(pickupPath)
            }
            Constants.LOCATION -> {
                val latCurrent = jsonObject.getDouble("lat")
                val lngCurrent = jsonObject.getDouble("lng")
                view?.updateCabLocation(LatLng(latCurrent , lngCurrent))
            }
            Constants.CAB_IS_ARRIVING ->{
                view?.informCabIsArriving()
            }
            Constants.CAB_ARRIVED -> {
                view?.informCabArrived()
            }
            Constants.TRIP_START -> {
                view?.informTripStart()
            }
            Constants.TRIP_END -> {
                view?.informTripEnd()
            }
        }
    }

    override fun onDisconnect() {

        Log.d(tag,"onDisconnect")
    }

    override fun onError(error: String) {
        Log.d(tag,"onError : $error")
        val jsonObject = JSONObject(error)
        when (jsonObject.getString(Constants.TYPE)) {
            Constants.ROUTES_NOT_AVAILABLE -> {
                view?.showRoutesNotAvailableError()
            }
            Constants.DIRECTION_API_FAILED -> {
                view?.showDirectionApiFailedError("Direction API Failed :" +
                        jsonObject.getString(Constants.ERROR))
            }
        }
    }
}