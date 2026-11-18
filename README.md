# Arziki Files Share (Android) - GitHub Actions version

1. Create a new repository on github.com (name it anything, e.g. arziki-files-share).
2. Extract this zip. Upload EVERYTHING inside the extracted folder to the repository root
   (Add file > Upload files). Important: the hidden ".github" folder must be uploaded too.
   If your browser upload skips it, create the file manually: Add file > Create new file,
   type  .github/workflows/build.yml  as the name, and paste the contents of that file from this zip.
3. Open the repo's "Actions" tab. The "Build APK" job starts by itself after the upload
   (or click "Build APK" > "Run workflow").
4. When it shows a green tick (about 3-5 minutes), open the run and download
   "Arziki-Files-Share-APK" at the bottom. Unzip it to get app-debug.apk, install on your phone.

Using it:
- Open Arziki Files Share on the PC. Enter the PC's address (e.g. 192.168.43.1) in the phone app.
- Enter the 6-digit code from the PC, or leave it empty and tap Approve on the PC.
- Receive tab = download PC files. Send tab = upload files to the PC.
