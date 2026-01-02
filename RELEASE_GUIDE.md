# Building and Releasing BibliCal APK

## Step 1: Set Up Signing Configuration

For release builds, Android requires signing. You have two options:

### Option A: Create a Release Keystore (Recommended for Production)

1. Create a keystore file:
```bash
keytool -genkey -v -keystore bibliCal-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias bibliCal
```

You'll be prompted for:
- Password (remember this!)
- Your name and organization details

2. Create a `keystore.properties` file in the project root (add to `.gitignore`):
```properties
storePassword=YOUR_STORE_PASSWORD
keyPassword=YOUR_KEY_PASSWORD
keyAlias=bibliCal
storeFile=../bibliCal-release-key.jks
```

3. Update `app/build.gradle.kts` to use the keystore (see below)

### Option B: Use Debug Keystore (Quick Testing)

For quick testing, you can use the debug keystore. This is less secure but fine for beta testing.

## Step 2: Update build.gradle.kts

Add signing configuration to `app/build.gradle.kts`:

```kotlin
android {
    // ... existing code ...
    
    signingConfigs {
        create("release") {
            // Option A: Use release keystore
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = java.util.Properties()
                keystoreProperties.load(java.io.FileInputStream(keystorePropertiesFile))
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            } else {
                // Option B: Use debug keystore for testing
                storeFile = file("${System.getProperty("user.home")}/.android/debug.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }
    
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

## Step 3: Build the Release APK

Run this command in the project root:

```bash
./gradlew assembleRelease
```

The APK will be created at:
`app/build/outputs/apk/release/app-release.apk`

## Step 4: Create GitHub Release

1. Go to your GitHub repository
2. Click "Releases" → "Create a new release"
3. Fill in:
   - **Tag**: `v0.1.0` (or your version)
   - **Title**: `BibliCal v0.1.0` (or your version name)
   - **Description**: Add release notes
4. **Attach the APK**: Drag and drop `app-release.apk` or click "attach binaries"
5. Click "Publish release"

## Step 5: Share with Testers

Users can:
1. Go to your GitHub Releases page
2. Download the APK
3. Enable "Install from Unknown Sources" on their Android device
4. Install the APK

## Notes

- **Version Updates**: Before each release, update `versionCode` and `versionName` in `app/build.gradle.kts`
- **Security**: Never commit `keystore.properties` or `.jks` files to git
- **Testing**: Test the release APK on a device before distributing


