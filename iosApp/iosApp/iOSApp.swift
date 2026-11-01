import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        // Start Koin once, before the first Compose view controller is created.
        KoinKt.doInitKoin(config: nil)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
