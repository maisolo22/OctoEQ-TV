# Validation locale — 25 octobre 2026

- JDK Temurin 17, Android SDK 35, Gradle 8.11.1.
- testDebugUnitTest : 9 tests, 0 échec (maths, navigation, persistance, protection de gain, cycle de vie des sessions).
- lintDebug : 0 erreur, 7 avertissements (principalement interface TV/accessibilité/traductions et analyse SAM).
- assembleDebug -PoctoPreview=true : réussi.
- Signature APK vérifiée avec apksigner, schéma v2, un signataire debug.
- Manifeste vérifié : com.maisolo.octoeqtv.preview, version 0.3.0-test, minSdk 24, targetSdk 35.

Les outils de préparation et le proxy de test sont propres à l’environnement local ; ils ne font pas partie du projet. Aucun secret ni keystore n'est publié.

Non vérifié sur matériel : rendu sonore externe, disponibilité DynamicsProcessing/Virtualizer sur une vraie box, routage HDMI/DRM, Spotify/Nuvio/Stremio et comportement OEM du service. Des essais à l’écoute restent indispensables.
