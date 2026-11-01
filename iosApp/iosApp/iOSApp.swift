import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        // Start Koin once, before the first Compose view controller is created. initKoinIos() also creates the
        // application CoroutineScope that repository work runs in, so it outlives any screen.
        KoinIosKt.doInitKoinIos()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
