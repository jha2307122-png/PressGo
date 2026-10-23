# PressGo — Campus Printing, Document Conversion and Order Management System

PressGo is a comprehensive, production-grade Android application designed for university campus environments. It bridges university students, faculty members, and campus printing-shop administrators through an end-to-end digital printing workflow.

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
