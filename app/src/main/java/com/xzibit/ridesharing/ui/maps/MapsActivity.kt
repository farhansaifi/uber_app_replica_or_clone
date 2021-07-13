package com.xzibit.ridesharing.ui.maps

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.xzibit.ridesharing.R
import com.xzibit.ridesharing.data.network.NetworkService
import com.xzibit.ridesharing.utils.AnimationUtils
import com.xzibit.ridesharing.utils.MapUtils
import com.xzibit.ridesharing.utils.PermissionUtils
import com.xzibit.ridesharing.utils.ViewUtils
import kotlinx.android.synthetic.main.activity_maps.*

class MapsActivity : AppCompatActivity(),MapsView, OnMapReadyCallback {

    companion object{
        private const val tag = "MapsActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 999
        private const val PICKUP_REQUEST_CODE = 1
        private const val DROP_REQUEST_CODE = 2
    }

    private lateinit var presenter: MapsPresenter
    private lateinit var googleMap: GoogleMap
    // variable for fetch current location
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null
    private lateinit var locationCallback: LocationCallback
    private var currentLatLng: LatLng? = null
    private val nearByCabsMarkerList = arrayListOf<Marker>()
    // variable for fetch drop and current location
    private var pickUpLatLng :LatLng? = null
    private var dropLatLng :LatLng? = null
    // variables for draw path on map
    private var greyPolyLine : Polyline? = null
    private var blackPolyLine : Polyline? = null
    private var originMarker: Marker? = null
    private var destinationMarker: Marker? = null
    // variables for cab moving to pickUp point
    private var movingCabMarker: Marker? = null
    private var previousLatLngFromServer: LatLng? = null
    private var currentLatLngFromServer: LatLng? = null
    

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        // make a status bar transparent
        ViewUtils.enableTransparentStatusBar(window)

        // set map on fragment
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        presenter = MapsPresenter(NetworkService())
        presenter.onAttach(this)

        // setUp click Listener
        setUpClickListener()

    }
    // create a function for setUp clickListener listeners
    private fun setUpClickListener(){
        pickUpTextView.setOnClickListener {
            launchLocationAutoCompleteActivity(PICKUP_REQUEST_CODE)
        }
        dropTextView.setOnClickListener {
            launchLocationAutoCompleteActivity(DROP_REQUEST_CODE)
        }
        requestCabButton.setOnClickListener(){
            statusTextView.visibility = View.VISIBLE
            statusTextView.text = getString(R.string.requesting_your_cab)
            requestCabButton.isEnabled = false
            pickUpTextView.isEnabled = false
            dropTextView.isEnabled = false
            presenter.requestCab(pickUpLatLng!!,dropLatLng!!)
        }
        nextRideButton.setOnClickListener {
            reset()
        }
    }
    // create a function for getting data location(current and drop both) latLng with request code
    private fun launchLocationAutoCompleteActivity(requestCode: Int){
        val fields: List<Place.Field> = listOf(Place.Field.ID,
            Place.Field.NAME,
            Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.FULLSCREEN,fields).build(this)
        startActivityForResult(intent,requestCode)
        Log.d(tag,intent.data.toString())
    }
    // create a function for set current location to pickup location on app start only
    private fun setCurrentLocationPickUp(){
        pickUpLatLng = currentLatLng
        pickUpTextView.text = getString(R.string.current_location)
    }

    // create a function for googleCamera movement on map
    private fun moveCamera(latLng: LatLng?){
        googleMap.moveCamera(CameraUpdateFactory.newLatLng(latLng))
    }

    // create a function for camera animation
    private fun animateCamera(latLng: LatLng?){
        val cameraPosition = CameraPosition.Builder().target(latLng).zoom(15.5f).build()
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    // create function for add car marker on google's map
    private fun addCarMarkerAndGet(latLng: LatLng):Marker {
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUtils.getCarBitmap(this))
        return googleMap.addMarker(MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor))
    }
    // create a function for destination marker and pick/drop marker (square box )
    private fun addOriginDestinationMarkerAndGet(latLng: LatLng) : Marker{
        val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(MapUtils.getDestinationBitmap())
        return googleMap.addMarker(MarkerOptions().position(latLng).flat(true).icon(bitmapDescriptor))
    }

    // create a function for enable_my_location_on_map means show my location tag on map
    @SuppressLint("MissingPermission")
    private fun enableMyLocationOnMap(){
        // set padding throw google api on map current position button
        googleMap.setPadding(0,ViewUtils.dpToPx(48f),0,0)
        googleMap.isMyLocationEnabled= true
    }

    // create a function for locationListener
    @SuppressLint("MissingPermission")
    private fun setUpLocationListener(){
        fusedLocationProviderClient = FusedLocationProviderClient(this)
        //for getting the current location update after every 2 seconds
        val locationRequest = LocationRequest()
            .setInterval(2000)
            .setFastestInterval(2000)
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)

        // initialize a location_callback
        locationCallback = object :LocationCallback() {
            override fun onLocationResult(locationRequest: LocationResult) {
                super.onLocationResult(locationRequest)
                // below condition is first time null we need the current location
                if (currentLatLng == null){
                    // in here we looping on the array of the location
                    for (location in locationRequest.locations){
                        if (currentLatLng == null) {
                            currentLatLng = LatLng(location.latitude, location.longitude)
                            setCurrentLocationPickUp()
                            enableMyLocationOnMap()
                            moveCamera(currentLatLng)
                            animateCamera(currentLatLng)
                            presenter.requestNearByCabs(currentLatLng!!)
                        }
                    }
                }
            }
        }
        fusedLocationProviderClient?.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.myLooper()
        )
    }
    // create a function for request cab button visibility set on conditions
    private fun checkAndShowRequestButton(){
        if (pickUpLatLng != null && dropLatLng != null){
            requestCabButton.visibility = View.VISIBLE
            requestCabButton.isEnabled = true
        }
    }
    // create a function for reset every thing start a new ride
    private fun reset(){
        statusTextView.visibility = View.GONE
        nextRideButton.visibility = View.GONE
        nearByCabsMarkerList.forEach {
                it.remove()
        }
        nearByCabsMarkerList.clear()
        currentLatLngFromServer = null
        previousLatLngFromServer = null
        if (currentLatLng != null){
            moveCamera(currentLatLng)
            animateCamera(currentLatLng)
            setCurrentLocationPickUp()
            presenter.requestNearByCabs(currentLatLng!!)
        } else {
            pickUpTextView.text = ""
        }
        pickUpTextView.isEnabled = true
        dropTextView.isEnabled = true
        dropTextView.text = ""
        movingCabMarker?.remove()
        greyPolyLine?.remove()
        blackPolyLine?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
        dropLatLng = null
        greyPolyLine = null
        blackPolyLine = null
        originMarker = null
        destinationMarker = null
        movingCabMarker = null
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
    }

    override fun onStart() {
        super.onStart()
        if (currentLatLng == null) {
            when {
                PermissionUtils.isAccessFineLocationGranted(this) -> {
                    when {
                        PermissionUtils.isLocationEnabled(this) -> {
                            setUpLocationListener()
                        }
                        else -> {
                            PermissionUtils.showGPSNotEnabledDialog(this)
                        }
                    }
                }
                else -> {
                    PermissionUtils.requestAccessFineLocationPermission(
                        this,
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // check the request id
        when(requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty()
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED){

                    when {
                        PermissionUtils.isLocationEnabled(this) -> {

                            setUpLocationListener()
                        }
                        else -> {
                        PermissionUtils.showGPSNotEnabledDialog(this)
                    }
                    }
                }
                else {
                    Toast.makeText(this,"Location Permission not granted",
                    Toast.LENGTH_LONG).show()
                    onStart()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICKUP_REQUEST_CODE || requestCode == DROP_REQUEST_CODE) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    val place = Autocomplete.getPlaceFromIntent(data!!)
                    Log.d(tag, "Place: " + place.name + ", " + place.id + ", " + place.latLng)
                    when (requestCode) {
                        PICKUP_REQUEST_CODE -> {
                            pickUpTextView.text = place.name
                            pickUpLatLng = place.latLng
                            checkAndShowRequestButton()
                        }
                        DROP_REQUEST_CODE -> {
                            dropTextView.text = place.name
                            dropLatLng = place.latLng
                            checkAndShowRequestButton()
                        }
                    }
                }
                AutocompleteActivity.RESULT_ERROR -> {
                    val status: Status = Autocomplete.getStatusFromIntent(data!!)
                    Log.d(tag, status.statusMessage!!)
                }
                Activity.RESULT_CANCELED -> {
                    Log.d(tag, "Place Selection Canceled")
                }
            }
        }
    }

    override fun onDestroy() {
        presenter.onDetach()
        fusedLocationProviderClient?.removeLocationUpdates(locationCallback)
        super.onDestroy()
    }

    override fun showNearByCabs(latLangList: List<LatLng>) {
        nearByCabsMarkerList.clear()
        for (latLng in latLangList){
            val nearByCarMarker = addCarMarkerAndGet(latLng)
            nearByCabsMarkerList.add(nearByCarMarker)
        }
    }

    override fun informCabBooked() {
        // after book a cab remove all near_by_cabs
        nearByCabsMarkerList.forEach { it.remove() }
        nearByCabsMarkerList.clear()
        requestCabButton.visibility = View.GONE
        statusTextView.text = getString(R.string.cab_booked_mesg)
    }

    override fun showPath(latLangList: List<LatLng>) {
        // Show path on map between to locations
        val builder = LatLngBounds.Builder()
        for (latLng in latLangList) {
            builder.include(latLng)
        }
        val bounds = builder.build()
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds,2))

        val polylineOptions = PolylineOptions()
        polylineOptions.color(Color.GRAY)
        polylineOptions.width(5f)
        polylineOptions.addAll(latLangList)
        greyPolyLine = googleMap.addPolyline(polylineOptions)


        val blackPolylineOptions = PolylineOptions()
        blackPolylineOptions.color(Color.BLACK)
        blackPolylineOptions.width(5f)
        blackPolyLine = googleMap.addPolyline(blackPolylineOptions)

        originMarker = addOriginDestinationMarkerAndGet(latLangList[0])
        originMarker?.setAnchor(0.5f,.05f)
        destinationMarker = addOriginDestinationMarkerAndGet(latLangList[latLangList.size -1])
        destinationMarker?.setAnchor(0.5f,.05f)

        // implement a animation
        val polylineAnimator = AnimationUtils.polyLineAnimator()
        polylineAnimator.addUpdateListener { valueAnimator ->
            val percentValue = (valueAnimator.animatedValue as Int)
            val index = (greyPolyLine?.points!!.size) * (percentValue / 100.0f).toInt()
            blackPolyLine?.points = greyPolyLine?.points!!.subList(0,index)
        }
        polylineAnimator.start()
    }

    override fun updateCabLocation(latLng: LatLng) {
        if (movingCabMarker == null) {
            movingCabMarker = addCarMarkerAndGet(latLng)
        }
        if (previousLatLngFromServer == null) {
            currentLatLngFromServer = latLng
            previousLatLngFromServer = currentLatLngFromServer
            movingCabMarker?.position = currentLatLngFromServer
            movingCabMarker?.setAnchor(0.5f,0.5f)
            animateCamera(currentLatLngFromServer)
        } else {
            previousLatLngFromServer =currentLatLngFromServer
            currentLatLngFromServer = latLng
            val valueAnimator = AnimationUtils.cabAnimator()
            valueAnimator.addUpdateListener { va ->
                if (currentLatLngFromServer != null && previousLatLngFromServer != null){
                    val multiplier = va.animatedFraction
                    val nextLocation  = LatLng(
                        multiplier * currentLatLngFromServer!!.latitude +
                                (1-multiplier) * previousLatLngFromServer!!.latitude,
                        multiplier * currentLatLngFromServer!!.longitude +
                                (1-multiplier) * previousLatLngFromServer!!.longitude
                    )
                    movingCabMarker?.position = nextLocation
                    // get and set cab rotation on map using correct angle calculation
                    val rotation = MapUtils.getRotation(previousLatLngFromServer!!,nextLocation)
                    if (!rotation.isNaN()){
                        movingCabMarker?.rotation = rotation
                    }
                    movingCabMarker?.setAnchor(0.5f,.05f)
                    animateCamera(nextLocation)
                }
            }
            valueAnimator.start()
        }
    }

    override fun informCabIsArriving() {
        statusTextView.text = getString(R.string.cab_is_arriving)
    }

    override fun informCabArrived() {
        statusTextView.text = getString(R.string.cab_has_arrived)
        greyPolyLine?.remove()
        blackPolyLine?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
    }

    override fun informTripStart() {
        statusTextView.text = getString(R.string.you_are_on_trip)
        previousLatLngFromServer = null
    }

    override fun informTripEnd() {
        statusTextView.text = getString(R.string.trip_end)
        nextRideButton.visibility = View.VISIBLE
        greyPolyLine?.remove()
        blackPolyLine?.remove()
        originMarker?.remove()
        destinationMarker?.remove()
    }

    override fun showRoutesNotAvailableError() {
        val error = getString(R.string.error_mesg_route_not_found)
        Toast.makeText(this,error,Toast.LENGTH_LONG).show()
        reset()
    }

    override fun showDirectionApiFailedError(error: String) {
        Toast.makeText(this,error,Toast.LENGTH_LONG).show()
        reset()
    }
}
