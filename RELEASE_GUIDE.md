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
            isMinifyEnabled = true  // Enable R8/ProGuard for code obfuscation and size reduction
            isShrinkResources = true  // Remove unused resources
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

## Step 3: Build the Release Bundle (AAB) for Google Play

For Google Play Store uploads, build an Android App Bundle:

```bash
./gradlew bundleRelease
```

The AAB will be created at:
`app/build/outputs/bundle/release/app-release.aab`

**Important**: After building, you'll also need to upload the deobfuscation file (mapping.txt) to Google Play Console. This file is located at:
`app/build/outputs/mapping/release/mapping.txt`

When uploading your AAB to Google Play Console:
1. Upload the AAB file
2. In the "App bundle explorer" or "Release" section, upload the `mapping.txt` file for deobfuscation
3. Native debug symbols are automatically included in the AAB (if available from dependencies)

## Step 3a: Build the Release APK (Alternative)

If you need an APK instead of an AAB, run:

```bash
./gradlew assembleRelease
```

The APK will be created at:
`app/build/outputs/apk/release/app-release.apk`

## Step 4: Generate Release Notes

Release notes are generated from git commits. Use the pre-made template or generate from history:

```bash
# Commit messages since last tag (e.g. v0.2.0)
git log v0.2.0..HEAD --pretty=format:"- %s (%h)" --reverse
```

A `RELEASE_NOTES_v1.1.md` template is included for each release — copy and customize as needed.

## Step 5: Create GitHub Release

1. **Commit and push** your changes, then create a tag:
   ```bash
   git tag v1.1
   git push origin v1.1
   ```

2. Go to [GitHub Releases](https://github.com/Experiencing-Yah/BibliCal-Android/releases/new?tag=v1.1)

3. Fill in:
   - **Tag**: `v1.1` (or your version — must match the tag you pushed)
   - **Title**: `BibliCal v1.1`
   - **Description**: Paste contents of `RELEASE_NOTES_v1.1.md` (or your release notes)

4. **Attach artifacts**:
   - `app/build/outputs/apk/release/app-release.apk` (for direct install)
   - `app/build/outputs/bundle/release/app-release.aab` (for Play Store)

5. Click **Publish release**

## Step 6: Share with Testers

Users can:
1. Go to your GitHub Releases page
2. Download the APK
3. Enable "Install from Unknown Sources" on their Android device
4. Install the APK

## Notes

- **Version Updates**: Before each release, update `versionCode` and `versionName` in `app/build.gradle.kts`
- **Security**: Never commit `keystore.properties` or `.jks` files to git
- **Testing**: Test the release APK on a device before distributing


