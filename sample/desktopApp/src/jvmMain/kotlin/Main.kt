import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.klitsie.kameleon.sample.AppConfig
import dev.klitsie.kameleon.sample.app.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Kameleon Sample - ${AppConfig.FLAVOR}"
    ) {
        App()
    }
}
