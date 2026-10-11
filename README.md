# PressGo — Campus Printing, Document Conversion and Order Management System

PressGo is a comprehensive, production-grade Android application designed for university campus environments. It bridges university students, faculty members, and campus printing-shop administrators through an end-to-end digital printing workflow.

---

## 🏛️ Academic Syllabus Coverage (Weeks 8–11)

This project has been engineered to demonstrate complete mastery of the Android Development Curriculum:

### **Week 8: Project Architecture, Gradle, and UI Foundations**
* **Language**: 100% pure Java (Java 11 compatibility) with ViewBinding enabled.
* **Gradle Build System**: Modern Gradle 7.5 with Android Gradle Plugin (AGP) 7.4.2, modular dependencies, and AndroidX libraries.
* **Material Design 3**: Material Design components (`MaterialCardView`, `MaterialButton`, `ChipGroup`, `TextInputLayout`, `BottomNavigationView`, `CollapsingToolbarLayout`).
* **Design System**: Structured XML design tokens in `res/values/` (`strings.xml`, `colors.xml`, `dimens.xml`, `themes.xml`, `attrs.xml`).
* **Adaptive Icons & Vector Drawables**: Adaptive launcher icons for all density buckets (`hdpi`, `mdpi`, `xhdpi`, `xxhdpi`, `xxxhdpi`) and crisp vector assets.

### **Week 9: Activities, Lifecycle, and Intents**
* **Activities & Lifecycle**: Complete lifecycle logging across 25+ activities (`onCreate`, `onStart`, `onResume`, `onPause`, `onStop`, `onDestroy`).
* **Explicit Intents**: Deep navigation flows for authentication, customer workflows, and administrative dashboards.
* **Implicit Intents**:
  * `ACTION_DIAL`: Instant phone calls to printing shops from details view.
  * `ACTION_SEND`: Share digital receipts and exported reports with external apps.
  * `ACTION_VIEW`: View converted PDFs and documents via `FileProvider`.
  * `ACTION_OPEN_DOCUMENT`: Native Storage Access Framework (SAF) multi-file picker.
* **Event Handling**: TextWatchers for dynamic search, chip selection listeners, seekbars, dialog callbacks.

### **Week 10: Advanced UI, Custom Views, and RecyclerViews**
* **Custom Views**:
  * `StatusBadgeView`: Dynamic, color-coded status badges with rounded badge styling.
  * `OrderProgressView`: Custom canvas-rendered horizontal step stepper representing order lifecycle (`PENDING` → `ACCEPTED` → `PRINTING` → `READY_FOR_PICKUP` → `COMPLETED`).
  * `PriceSummaryView`: Dynamic linear bill breakdown with currency formatting and discount subtotals.
* **RecyclerView & ViewHolder Patterns**:
  * `ShopAdapter`: Searchable, filterable list of campus shops with queue estimations.
  * `OrderAdapter`: Active orders and order history with colored status indicators.
  * `AdminOrderAdapter`: Administrative view with batch selection and contextual quick actions.
  * `SelectedDocumentAdapter`: Upload queue with file metadata, extension badges, and progress.
  * `PrinterAdapter`: Real-time hardware telemetry (paper, ink, toner gauges).
  * `InventoryAdapter`: Material categories with low-stock warnings.
  * `ConversionHistoryAdapter`: Local document conversion logs.
  * `RecentOrderAdapter`: Compact dashboard order highlights.

### **Week 11: Background Processing, Storage, Networking, and Firebase**
* **Android Services**:
  * `ConversionService`: Foreground Service displaying notification progress while executing iText7 PDF generation, image transformation, Word-to-PDF, PowerPoint-to-PDF, and ZIP compression.
* **Document Converters (100% Pure Java & XML Parsing)**:
  * `WordToPdfConverter`: Full DOCX (OpenXML) and legacy DOC/RTF converter with paragraph styles, headings, tables, bold/italic typography, and embedded images.
  * `PptxToPdfConverter`: Multi-slide PPTX and PPT presentation converter into landscape PDF slides with card layout, slide numbers, titles, bullet hierarchy, tables, and embedded graphics.
  * `PdfToWordConverter`: Converts multi-page PDF documents into standard Microsoft Word (`.docx`) packages preserving headings, body typography, bulleted structures, and page breaks.
  * `PdfToPptxConverter`: Converts multi-page PDF documents into editable 16:9 widescreen PowerPoint (`.pptx`) presentations with structured titles and slide bullet hierarchies.
* **Broadcast Receivers**:
  * `NetworkChangeReceiver`: Connectivity listener adjusting sync behavior and notifying components via `LocalBroadcastManager`.
  * `BootReceiver`: `BOOT_COMPLETED` listener rescheduling periodic background tasks.
* **WorkManager**:
  * `OrderUploadWorker`: Deferrable, reliable background document upload to Firebase Storage with retry policy.
  * `NotificationSyncWorker`: Periodic background sync for campus alerts.
  * `CleanupWorker`: Automated maintenance worker deleting temporary files older than 24 hours.
* **Content Providers & SAF**:
  * Android `FileProvider` (`com.pressgo.app.fileprovider`) for secure content URI sharing without exposing local storage paths.
* **Networking & Firebase**:
  * **Firebase Authentication**: Email/password authentication, role validation, session caching.
  * **Cloud Firestore**: Realtime document listeners (`addSnapshotListener`) for order tracking and shop queues.
  * **Firebase Storage**: Secure cloud storage for print files (`orders/{orderId}/files/`).
  * **Firebase Cloud Messaging (FCM)**: Push notification handling via `PressGoMessagingService`.
* **Hardware & Third-Party Libraries**:
  * **ZXing**: QR Code generation (`BarcodeEncoder`) and pickup verification scanner (`IntentIntegrator`).
  * **iText 7**: PDF manipulation, document synthesis, and text extraction.
  * **Glide**: Asynchronous image loading with placeholder and error fallbacks.

---

## 🚀 Portals & Roles

1. **Customer Portal** (`CUSTOMER`):
   - Browse and search campus printing shops with ratings and queue time.
   - Smart document upload (PDF, DOC/DOCX, PPT/PPTX, JPG/PNG) with page counting.
   - Print customization (Color vs B&W, A4/A3/Letter, Single/Double sided, Orientation, Copies, Page Range, Binding, Stapling, Priority).
   - Real-time price breakdown calculator with budget warning.
   - Order placement, live progress tracking stepper, and pickup QR code.
   - Built-in document conversion center and PDF/image compression tool.

2. **Admin Portal** (`ADMIN` / `STAFF`):
   - Shop dashboard with today's orders, revenue, and active printer status.
   - Order management with multi-selection batch acceptance and status transitions.
   - Physical pickup verification via built-in QR Code Camera Scanner.
   - Printer management: Paper, ink, and toner level monitoring and maintenance toggles.
   - Inventory tracking: Paper reams, toner cartridges, binding combs with low-stock alerts.
   - Shop pricing management: Authoritative pricing rates stored on Firestore.
   - Business reports: Volume breakdown, revenue, and order completion analytics.

---

## 🛠️ Build and Compilation

### Prerequisites
- Android Studio Ladybug / Meerkat (or newer)
- JDK 17+ (e.g. bundled OpenJDK at `Android Studio\jbr`)
- Android SDK Platform 34 / 35 / 37 (Min SDK 26, Target SDK 37)

### Command-line Build
To verify and compile the project using Gradle:
```powershell
# Set JAVA_HOME to Android Studio bundled JDK (or your JDK 17+ path)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# Run Unit Tests
.\gradlew.bat test

# Build Debug APK
.\gradlew.bat assembleDebug
```
The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 🔒 Security & Firebase Configuration

- `firestore.rules`: Enforces role-based permissions preventing customers from tampering with pricing, inventory, or orders of other users.
- `storage.rules`: Restricts document file access to authorized users and limits upload sizes to 50MB.
- `google-services.json`: Configured with client credentials for package `com.pressgo.app`.
