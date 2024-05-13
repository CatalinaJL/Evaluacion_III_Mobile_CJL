package com.cjl.evaluacion_iii_mobile_cjl

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cjl.evaluacion_iii_mobile_cjl.ui.theme.Evaluacion_III_Mobile_CJLTheme
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.LocalDateTime


// Clase que va a contener las pantallas
enum class Pantallas{
    FORMULARIO,
    FOTO,
    MAPA
}


// ViewModel para poder manejar todos los estados necesarios
// de la app y que se pueda conectar con las distintas funciones
// que van a componer la app

class AppVM: ViewModel(){
    // Variables para dirigir a pantallas
    val pantallaActual= mutableStateOf(Pantallas.FORMULARIO)
    // Variables que van a contener los datos
    val nombreLugar = mutableStateOf("")
    val foto = mutableStateOf<Uri?>(null)
    val latitud = mutableStateOf(0.0)
    val longitud = mutableStateOf(0.0)

    // Variables que van a tener los permisos requeridos.
    var permisoUbicacionOk :() ->Unit = {}
    var onPermisoCamaraOk: () -> Unit = {}
}


class MainActivity : ComponentActivity() {
    // Instancia de VM para que se puedan acceder a las distintas variables
    val appVM:AppVM by viewModels()

    // instanciador launcher controller de CameraX
    lateinit var cameraController: LifecycleCameraController
    // Permisos CameraX
    val lanzadorPermisosCamara = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){
        if( it[android.Manifest.permission.CAMERA]?:false){
            appVM.onPermisoCamaraOk
        }
    }

    // Permisos Ubicación,
    // en la cual se consulta por cualquiera de los dos permisos agregados en Manifest relacionados con acceso ubicacion
    val lanzadorPermisosUbicacion = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
        if( (it[android.Manifest.permission.ACCESS_FINE_LOCATION] ?:false) or
            (it[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?:false) )
        {
            appVM.permisoUbicacionOk()
        }else {
            Log.v("lanzadorPermisos callback", "Se denegaron los permisos")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //Se agregan elementos relacionados con el control de la camara para su uso
        cameraController = LifecycleCameraController(this)
        //metodo para vincular al ciclo de vida de la actividad la camara
        cameraController.bindToLifecycle(this)
        // Se deja definido la camara a usar por default
        cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        setContent {
           AppFotosUI(appVM, lanzadorPermisosCamara, lanzadorPermisosUbicacion, cameraController)
        }
    }
}



// UI de la App
@Composable
fun AppFotosUI(appVM: AppVM,
               lanzadorPermisosCamara:ActivityResultLauncher<Array<String>>,
               lanzadorPermisosUbicacion:ActivityResultLauncher<Array<String>>,
               cameraController: LifecycleCameraController){

    val appVM:AppVM = viewModel()

    when(appVM.pantallaActual.value){
        Pantallas.FORMULARIO ->{
            FormularioUI(lanzadorPermisosUbicacion)
        }
        Pantallas.FOTO -> {
            TomarFotoUI(lanzadorPermisosCamara, cameraController)
        }
        Pantallas.MAPA ->{
            MapaUI(appVM)
        }
    }
}

// Funciones  que son llamadas por UI's

// Función para conseguir ubicación y que sea llamada por boton cuando va a mostrar mapa
fun getLocation(contexto : Context, onSuccess:(ubicacion: Location)-> Unit){
    try{
        val servs = LocationServices.getFusedLocationProviderClient(contexto)
        val task = servs.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            null
        )
        task.addOnSuccessListener {
            onSuccess(it)
        }
    }catch (se:SecurityException){
        throw Error("Sin permisos de ubicación")
    }

}

// Función para hacer una Uri en imagen
fun uri2imageBitmap(uri:Uri, contexto:Context) =
    BitmapFactory.decodeStream(
        contexto.contentResolver.openInputStream(uri)
    ).asImageBitmap()

// Función para obtener el nombre de la imagen a capturar
fun generarNombreSegunFechaHastaSegundo():String = LocalDateTime
    .now().toString().replace(Regex("[T:.-]"), "").substring(0, 14)

// Función para que se guarde la imagen como publica con Media Api
fun guardaArchivoPublico(nombreArchivo:String, contexto: Context):Uri? = ContentValues().run {
    put (MediaStore.MediaColumns.DISPLAY_NAME, nombreArchivo)
    put (MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
    contexto.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        this
    )
}

// Función para capturar Foto

fun capturaFoto(cameraController: LifecycleCameraController,
                archivo: File,
                contexto :Context,
                onImagenGuardada:(uri:Uri) -> Unit
){
    val opciones = ImageCapture.OutputFileOptions.Builder(archivo).build()
    cameraController.takePicture(
        opciones,
        ContextCompat.getMainExecutor(contexto),
        object: ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.let{
                    onImagenGuardada(it)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("capturaFoto::Error", exception.message?:"Error")
            }
        }

    )

}




@Composable
fun FormularioUI(lanzadorPermisosUbicacion:ActivityResultLauncher<Array<String>>){

    val appVM:AppVM = viewModel()
    val contexto = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ){

        Button(onClick = {
            appVM.pantallaActual.value=Pantallas.FOTO
        }) {
            Text( "Tomar Foto")

        }

        Spacer(modifier=Modifier.height(40.dp))

        TextField(
            value = "" ,
            onValueChange = {},
            label = { Text("Nombre Lugar") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier=Modifier.height(20.dp))

        Button(onClick = { /*TODO*/ }) {
            Text("Guardar Localizacion")

        }

        Button(onClick = {
            appVM.pantallaActual.value=Pantallas.MAPA
            appVM.permisoUbicacionOk = {
                getLocation(contexto){
                    appVM.latitud.value = it.latitude
                    appVM.longitud.value = it.longitude
                }
            }
            lanzadorPermisosUbicacion.launch(arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }) {
            Text("Mostrar Ubicación")

        }


    }

}



@Composable
fun TomarFotoUI(lanzadorPermisosCamara:ActivityResultLauncher<Array<String>>,
                cameraController: LifecycleCameraController){
    val appVM:AppVM = viewModel()

    lanzadorPermisosCamara.launch(arrayOf(android.Manifest.permission.CAMERA))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ){

        AndroidView(
            modifier = Modifier.height(300.dp),
            factory = {
                PreviewView(it).apply {
                   controller = cameraController
                }
            }
        )
        Spacer(modifier=Modifier.height(10.dp))
        Button(onClick = {
            //capturaFoto(
            //    cameraController,
           // )
        }){
            Text("Captura")
        }

        Spacer(modifier=Modifier.height(30.dp))
        Button(onClick = {
            appVM.pantallaActual.value=Pantallas.FORMULARIO
        }){
            Text("Inicio")
        }

    }

}



@Composable
fun MapaUI(appVM: AppVM){

    val appVM:AppVM = viewModel()
    val contexto = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Lat:${appVM.latitud.value}, Long:${appVM.longitud.value}")
        Spacer(modifier=Modifier.height(10.dp))

        AndroidView(
            modifier = Modifier.height(200.dp),
            factory={
                   MapView(it).apply{
                        setTileSource(TileSourceFactory.MAPNIK)
                        Configuration.getInstance().userAgentValue = contexto.packageName
                        controller.setZoom(15.0)
                   }
            },
            update={
                    it.overlays.removeIf {true}
                    it.invalidate()
                    val geoPoint= GeoPoint(appVM.latitud.value, appVM.longitud.value)
                    it.controller.animateTo(geoPoint)

                    val marcador = Marker(it)
                    marcador.position= geoPoint
                    marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    it.overlays.add(marcador)
            }
        )
        Spacer(modifier=Modifier.height(30.dp))

        Button(onClick = {
            appVM.pantallaActual.value=Pantallas.FORMULARIO
        }){
            Text("Inicio")
        }
    }

}



