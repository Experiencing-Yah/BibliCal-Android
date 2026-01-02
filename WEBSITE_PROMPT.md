# Website Pages Prompt for BibliCal App

Use this prompt with your website development agent to create the necessary pages for the BibliCal Android app on ExperiencingYah.com.

---

## Prompt for Website Agent

Please create the following pages for the BibliCal Android app on the ExperiencingYah.com website. The app is a moon-sighting-based Biblical calendar application for Android devices.

### Required Pages:

#### 1. Privacy Policy Page (`/bibliCal/privacy` or `/bibliCal/privacy-policy`)

**Purpose:** Required by Google Play Store for apps that collect location data. Must be publicly accessible via HTTPS URL.

**Key Points to Include:**

- **App Name:** BibliCal
- **Developer:** ExperiencingYah.com
- **Last Updated:** [Current Date]

**Data Collection and Usage:**

1. **Location Data:**
   - The app requests location permissions (ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION)
   - Location data is collected ONLY for calculating local sunset times, which are used to determine the start of Biblical days (which begin at sunset)
   - Location data is stored locally on the device using Android DataStore
   - Location data is cached for up to 24 hours to improve widget performance
   - Location data is NEVER transmitted off the device
   - Location data is NEVER shared with third parties
   - Users can deny location permissions, and the app will use default coordinates (latitude 40.0, longitude -74.0) as a fallback

2. **Calendar Data (Optional):**
   - The app requests calendar permissions (READ_CALENDAR and WRITE_CALENDAR) ONLY if the user explicitly enables the calendar export feature in Settings
   - If enabled, the app writes Biblical month starts and feast days to a user-selected calendar on the device
   - The app does NOT read existing calendar events
   - Calendar data remains on the device and is NEVER transmitted

3. **App Data Storage:**
   - The app stores the following data locally on the device:
     - Biblical month start dates (stored using Room database)
     - Year decisions (whether a year has 12 or 13 months, stored using Room database)
     - User preferences and settings (notification preferences, month naming mode, etc., stored using Android DataStore)
     - Cached location coordinates (as described above)
   - All data is stored locally using Android's Room database and DataStore
   - No data is transmitted to external servers
   - No data is backed up to cloud services by the app (users may have device-level backups enabled)

4. **No Data Collection:**
   - The app does NOT collect analytics data
   - The app does NOT use third-party analytics services
   - The app does NOT use advertising services
   - The app does NOT collect crash reports
   - The app does NOT collect user behavior data
   - The app does NOT require internet connection (works completely offline)
   - The app does NOT transmit any data to external servers

5. **Permissions:**
   - POST_NOTIFICATIONS: For displaying Biblical date notifications (optional, user can disable)
   - RECEIVE_BOOT_COMPLETED: To reschedule notifications after device restart
   - WAKE_LOCK: To ensure notifications are delivered on time
   - Location permissions: As described above
   - Calendar permissions: As described above (optional)

**Data Security:**
- All data is stored locally on the device
- No encryption is required as no sensitive data is transmitted
- Users can uninstall the app at any time, which will delete all locally stored data

**User Rights:**
- Users can revoke location permissions at any time through Android settings
- Users can revoke calendar permissions at any time through Android settings
- Users can disable notifications through app settings
- Users can delete all app data by uninstalling the app

**Contact Information:**
- For privacy-related questions, contact: [Your contact email or contact form URL]
- Website: https://ExperiencingYah.com

**Changes to Privacy Policy:**
- We reserve the right to update this privacy policy
- Users will be notified of significant changes through app updates

---

#### 2. Support/Contact Page (`/bibliCal/support` or `/bibliCal/contact`)

**Purpose:** Provide users with a way to get help, report bugs, or contact the developer.

**Content to Include:**

- **App Name:** BibliCal
- **Description:** Brief description of the app (moon-sighting-based Biblical calendar)
- **Contact Methods:**
  - Email: [Your support email]
  - Contact form: [If available]
  - GitHub Issues: [If you have a public repo]
- **Common Questions/FAQ:**
  - How do I set the initial date?
  - How do I confirm a new moon sighting?
  - How do I enable calendar export?
  - How do I change notification settings?
  - Why does the app need location permissions?
- **Bug Reports:** Instructions for reporting bugs
- **Feature Requests:** How users can suggest features

---

#### 3. About Page (`/bibliCal/about` or `/bibliCal`)

**Purpose:** Provide information about the app, its purpose, and the developer.

**Content to Include:**

- **App Name:** BibliCal
- **Version:** 0.1.4 (or current version)
- **Description:** 
  - BibliCal is an Android app that helps you track and manage a moon-sighting-based Biblical calendar system
  - Features include:
    - Today screen showing current Biblical date (Month, Day, Year)
    - Calendar view with projected months
    - New moon sighting prompts on days 29/30
    - Aviv barley prompt to determine new year vs 13th month
    - Optional persistent notification
    - Home screen widgets
    - Calendar export to device calendar
- **Developer:** ExperiencingYah.com
- **Technology:** Built with Kotlin and Jetpack Compose
- **Privacy:** Link to privacy policy
- **Support:** Link to support page
- **Download:** Link to Google Play Store (once published)

---

### Design Requirements:

- All pages should match the existing ExperiencingYah.com website design and branding
- Pages should be mobile-responsive
- Privacy policy should be easy to read and understand
- Include navigation back to main site
- Use clear headings and sections
- Privacy policy should be in plain language (not overly legal)

### Technical Requirements:

- Privacy policy URL must be accessible via HTTPS
- Privacy policy should be a static page (no login required)
- URL should be stable and not change (Google Play Store will link to it)
- Consider adding the privacy policy URL to a sitemap

### Suggested URLs:

- Privacy Policy: `https://ExperiencingYah.com/bibliCal/privacy` or `https://ExperiencingYah.com/bibliCal/privacy-policy`
- Support: `https://ExperiencingYah.com/bibliCal/support`
- About: `https://ExperiencingYah.com/bibliCal` or `https://ExperiencingYah.com/bibliCal/about`

---

## Additional Notes for Website Agent:

1. The privacy policy is the most critical page - it's required by Google Play Store before the app can be published
2. The privacy policy URL will be entered in the Google Play Console's Data Safety section
3. Make sure the privacy policy is comprehensive but also easy for users to understand
4. Consider adding a link to the privacy policy in the app's settings screen (this can be added later)
5. The support page will help reduce support requests and provide a professional presence
6. The about page helps establish credibility and provides context about the app

---

## For Google Play Console:

Once the privacy policy page is created, you'll need:
- The exact HTTPS URL (e.g., `https://ExperiencingYah.com/bibliCal/privacy`)
- This URL will be entered in the Play Console under "Store settings" > "App content" > "Privacy Policy"


