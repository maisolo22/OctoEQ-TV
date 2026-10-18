# OctoEQ TV — prototype autonome

Égaliseur et virtualisation sonore pour Android TV. Application indépendante de **octoStream**.

## Statut
Prototype **non testé sur matériel**. Les effets globaux Android utilisent la session audio 0, dont l'usage est **déprécié** : certaines TV n'appliqueront aucun effet. Ne pas présenter ce prototype comme un égaliseur garanti pour Netflix, YouTube, etc.

## Fonctionnalités
- Égaliseur : nombre de bandes détecté sur l'appareil et ajustements au D-pad
- Bass Boost si disponible
- Surround virtuel si disponible (ce n'est pas du 5.1 réel)
- Sauvegarde locale et bouton de réinitialisation
- Affichage Android TV

## Compiler
Ouvrir la racine du dépôt dans Android Studio (JDK 17, SDK Android 35), synchroniser Gradle puis lancer `assembleDebug`. APK : `app/build/outputs/apk/debug/app-debug.apk`.

Pour une application audio qui fonctionne *réellement* avec octoStream : **réutiliser son processeur PCM existant** : `tv/src/main/java/com/example/tv/audio/StudioAudioProcessor.kt`. Une application tierce ne peut pas se brancher librement au flux audio des autres applications.

Documentation : https://developer.android.com/reference/android/media/audiofx/AudioEffect
