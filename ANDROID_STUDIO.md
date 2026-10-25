# Ouvrir OctoEQ-TV et tester les autres lecteurs

OctoEQ-TV est un dépôt indépendant d’octoStream. Ouvrez-le dans une deuxième fenêtre Android Studio.

1. File > New > Project from Version Control (ou Clone Repository).
2. URL : https://github.com/maisolo22/OctoEQ-TV.git
3. Dossier conseillé : D:\AndroidProjects\OctoEQ-TV. Ne pas choisir le dossier octoStream.
4. Open in New Window, puis Trust Project.
5. Sélectionner la branche feature/session-studio. Si elle manque : Git > Fetch. Si le clone ne suit que main, dans le terminal :

```powershell
git remote set-branches --add origin feature/session-studio
git fetch origin
git switch --track origin/feature/session-studio
```

6. SDK Manager : Android API 35 et Build Tools 35.0.0. Gradle JDK : JDK intégré Android Studio (17 minimum, 21 convient avec Gradle 8.11.1). Garder les versions du dépôt, sans mise à niveau automatique AGP.
7. Sync Project with Gradle Files. Le wrapper Gradle est maintenant fourni.
8. Choisir un appareil Android TV et le module app, puis Run. Le téléphone n’est pas la cible de ce projet.

Pour compiler une installation séparée OctoEQ TV Test :

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat testDebugUnitTest lintDebug :app:assembleDebug -PoctoPreview=true
explorer.exe ".\app\build\outputs\apk\debug"
```

Adapter JAVA_HOME si Android Studio est installé ailleurs. Le terminal affiche BUILD SUCCESSFUL à la fin. Le fichier est app-debug.apk ; si les sorties ont été déplacées localement, chercher avec Get-ChildItem -Path .\app\build -Filter *.apk -Recurse.

## Essai Spotify, Nuvio et Stremio sur la vraie box

1. Installer et ouvrir OctoEQ. Commencer par le profil Plat, basses 0 %, espace sonore 0 %, protection auto active.
2. Applications / sortie > Automatique. Le service reste actif après Retour ; notification disponible selon la box et ses autorisations.
3. Ouvrir le lecteur désiré via Ouvrir une application, puis arrêter/reprendre un titre ou changer de titre pour qu’il ouvre sa session pendant qu’OctoEQ écoute les annonces.
4. Revenir à OctoEQ et lire le diagnostic : application annoncée, moteur, bandes et contrôle. Sans session annoncée, Auto utilise la sortie globale expérimentale. Le mode Sessions uniquement reste en attente ; il ne devine pas les identifiants.
5. Essayer Vocal ou Basses + à volume modéré, puis Comparer avec l’original. La comparaison est temporaire ; quitter l’écran remet les réglages. Contrôle acquis ne prouve pas l’effet sonore : vérifier à l’écoute.
6. Pour Nuvio/Stremio, désactiver le passthrough dans le lecteur et essayer PCM dans les réglages audio de la box. En bitstream HDMI, l’ampli peut recevoir un flux qui contourne les effets Android.
7. Si rien ne change : essayer Global expérimental puis Rebrancher les effets ; relancer la lecture externe. Si la box refuse toujours le traitement, ne pas conclure que les curseurs agissent.
8. Désactiver les autres égaliseurs pendant le test. Dans octoStream, désactiver son Studio si l’on veut comparer uniquement OctoEQ.

Le contrôle du son global de toutes les applications n’est pas garanti sans intégration dans le lecteur ou privilèges système. Aucune capture microphone, MediaProjection, root ou service d’accessibilité n’est ajouté. Virtualizer ne remplace pas le traitement largeur stéréo M/S d’octoStream et ne crée pas du 5.1.
